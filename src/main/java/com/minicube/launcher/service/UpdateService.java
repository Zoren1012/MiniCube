package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.minicube.launcher.util.Safety;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Mise a jour du launcher.
 *
 * <p>Deux sources sont possibles, la premiere ayant la priorite :</p>
 *
 * <ul>
 *   <li><b>Les publications GitHub</b>, si {@code githubRepo} est renseigne. Le service
 *       lit la derniere publication du depot, y choisit le fichier adapte a la maniere
 *       dont MiniCube a ete installe, et recupere son empreinte dans le fichier
 *       {@code .sha256} publie a cote.</li>
 *   <li><b>Un descripteur JSON</b> a l'adresse {@code updateUrl}, pour heberger ses
 *       mises a jour ailleurs que sur GitHub.</li>
 * </ul>
 *
 * <p>Dans les deux cas, le fichier telecharge sera <b>execute</b> : une adresse chiffree
 * et une empreinte verifiable sont exigees, sans exception.</p>
 */
public class UpdateService {

    /** Adresse de l'interface de programmation de GitHub. */
    private static final String GITHUB_API = "https://api.github.com/repos/";

    /**
     * Une mise a jour disponible.
     *
     * @param version     numero de la nouvelle version
     * @param url         adresse du fichier a telecharger
     * @param hash        empreinte attendue
     * @param algorithm   algorithme de l'empreinte : SHA-256 ou SHA-1
     * @param changelog   description des nouveautes, telle que publiee
     * @param publishedAt date de publication, au format ISO ou vide
     * @param releaseUrl  page de la publication, pour consulter le detail
     * @param assetName   nom du fichier telecharge
     * @param mandatory   true si la mise a jour doit etre installee avant de jouer
     * @param installer   true si le fichier est un installeur a executer, false pour un
     *                    jar sur lequel il faut redemarrer
     */
    public record UpdateInfo(String version, String url, String hash, String algorithm,
                             String changelog, String publishedAt, String releaseUrl,
                             String assetName, boolean mandatory, boolean installer) {

        /** Taille lisible du changelog, tronque pour l'affichage. */
        public String shortChangelog(int maxLength) {
            String text = changelog == null ? "" : changelog.trim();
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength).trim() + "...";
        }
    }

    /**
     * Issue d'une verification.
     *
     * <p>Distinguer ces cas n'est pas cosmetique : annoncer "vous etes a jour" alors que
     * la verification a echoue, ou qu'aucune version n'a jamais ete publiee, revient a
     * mentir a l'utilisateur. Il croirait avoir la derniere version alors que personne
     * n'a pu le lui confirmer.</p>
     */
    public enum Status {
        /** Une version plus recente existe. */
        AVAILABLE,
        /** La version installee est bien la derniere publiee. */
        UP_TO_DATE,
        /** Le depot existe mais ne contient aucune publication. */
        NO_RELEASE,
        /** Le depot ou l'adresse est introuvable. */
        NOT_FOUND,
        /** Aucune source de mise a jour n'est renseignee. */
        NOT_CONFIGURED,
        /** La publication existe mais a ete refusee, faute d'empreinte par exemple. */
        REJECTED,
        /** La verification n'a pas pu aboutir : reseau, quota, reponse illisible. */
        ERROR
    }

    /**
     * Resultat d'une verification.
     *
     * @param status etat constate
     * @param update mise a jour disponible, uniquement pour {@link Status#AVAILABLE}
     * @param detail precision affichable, vide s'il n'y a rien a ajouter
     */
    public record CheckResult(Status status, UpdateInfo update, String detail) {

        public boolean isAvailable() {
            return status == Status.AVAILABLE && update != null;
        }
    }

    private final ConfigService config;

    public UpdateService(ConfigService config) {
        this.config = config;
    }

    /**
     * Indique si MiniCube s'execute depuis une installation empaquetee.
     *
     * <p>Les lanceurs produits par jpackage renseignent {@code jpackage.app-path}. La
     * distinction compte : une installation empaquetee se met a jour en executant un
     * installeur, alors qu'un jar se met a jour en redemarrant sur le nouveau jar.</p>
     */
    public static boolean isPackagedInstall() {
        return System.getProperty("jpackage.app-path") != null;
    }

    /**
     * Cherche une mise a jour, quelle que soit la source configuree.
     *
     * <p>Methode bloquante : a appeler depuis un thread de fond. Toute erreur est
     * absorbee et journalisee ; une verification de mise a jour ne doit jamais empecher
     * de jouer.</p>
     *
     * @return la mise a jour disponible, ou un optional vide si tout est a jour
     */
    public CheckResult check() {
        String repository = config.settings().getGithubRepo();
        if (!repository.isBlank()) {
            return checkGitHub(repository);
        }
        if (config.settings().getUpdateUrl().isBlank()) {
            return new CheckResult(Status.NOT_CONFIGURED, null, "");
        }
        return checkDescriptor();
    }

    /** Variante respectant le reglage de mise a jour automatique, utilisee au demarrage. */
    public CheckResult checkAtStartup() {
        if (!config.settings().isAutoUpdateLauncher()) {
            return new CheckResult(Status.NOT_CONFIGURED, null, "");
        }
        return check();
    }

    /* ------------------------------------------------------------------ */
    /* Source : publications GitHub                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Lit la derniere publication d'un depot GitHub.
     *
     * @param repository depot au format {@code proprietaire/nom}
     */
    private CheckResult checkGitHub(String repository) {
        String depot = repository.trim();
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/vnd.github+json");
            headers.put("X-GitHub-Api-Version", "2022-11-28");

            JsonObject release = Http.getJson(
                    GITHUB_API + depot + "/releases/latest", headers);

            if (Json.bool(release, "draft", false)
                    || Json.bool(release, "prerelease", false)) {
                // Une version preliminaire ou un brouillon ne concerne pas quelqu'un
                // qui suit les versions stables.
                return new CheckResult(Status.UP_TO_DATE, null,
                        "La derniere publication est un brouillon ou une version "
                        + "preliminaire, elle n'est pas proposee.");
            }

            String version = normalizeVersion(Json.string(release, "tag_name", ""));
            if (version.isBlank()) {
                return new CheckResult(Status.ERROR, null,
                        "La publication ne porte pas d'etiquette de version exploitable.");
            }
            if (compareVersions(version, Constants.APP_VERSION) <= 0) {
                Log.debug("Le launcher est a jour (" + Constants.APP_VERSION + ")");
                return new CheckResult(Status.UP_TO_DATE, null, "");
            }

            List<JsonObject> assets = assetsOf(release);
            JsonObject asset = pickAsset(assets);
            if (asset == null) {
                return new CheckResult(Status.REJECTED, null,
                        "La publication " + version + " ne contient aucun fichier adapte "
                        + "a votre installation.");
            }
            String assetName = Json.string(asset, "name", "");
            String hash = readCompanionHash(assets, assetName);
            if (hash.isBlank()) {
                Log.warn("Publication " + version + " refusee : aucun " + assetName
                        + ".sha256 ne l'accompagne");
                return new CheckResult(Status.REJECTED, null,
                        "La publication " + version + " est refusee : aucun fichier "
                        + assetName + ".sha256 ne l'accompagne. Ce fichier sera execute, "
                        + "son integrite doit pouvoir etre verifiee.");
            }

            UpdateInfo info = new UpdateInfo(
                    version,
                    Json.string(asset, "browser_download_url", ""),
                    hash,
                    "SHA-256",
                    Json.string(release, "body", ""),
                    Json.string(release, "published_at", ""),
                    Json.string(release, "html_url", ""),
                    assetName,
                    false,
                    assetName.toLowerCase(Locale.ROOT).endsWith(".exe"));
            Log.info("Mise a jour disponible sur GitHub : " + version);
            return new CheckResult(Status.AVAILABLE, info, "");

        } catch (Http.HttpStatusException e) {
            // Un 404 sur /releases/latest ne dit pas si le depot est absent ou
            // simplement depourvu de publication. Une seconde requete tranche, et
            // seulement dans ce cas : le message vaut la peine d'etre exact.
            if (e.status() == 404) {
                return diagnose404(depot);
            }
            if (e.status() == 403) {
                return new CheckResult(Status.ERROR, null,
                        "GitHub a refuse la requete : quota de consultation atteint. "
                        + "Reessayez dans une heure.");
            }
            Log.warn("Verification GitHub impossible : " + e.getMessage());
            return new CheckResult(Status.ERROR, null,
                    "GitHub a repondu HTTP " + e.status() + ".");
        } catch (Exception e) {
            Log.warn("Verification GitHub impossible : " + e.getMessage());
            return new CheckResult(Status.ERROR, null,
                    "La verification n'a pas abouti : " + e.getMessage());
        }
    }

    /** Determine si un 404 vient du depot lui-meme ou de l'absence de publication. */
    private CheckResult diagnose404(String depot) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/vnd.github+json");
            Http.getJson(GITHUB_API + depot, headers);
            return new CheckResult(Status.NO_RELEASE, null,
                    "Le depot " + depot + " ne contient encore aucune publication.");
        } catch (Exception e) {
            return new CheckResult(Status.NOT_FOUND, null,
                    "Le depot " + depot + " est introuvable. Verifiez son nom dans les "
                    + "parametres, au format proprietaire/nom.");
        }
    }


    private List<JsonObject> assetsOf(JsonObject release) {
        List<JsonObject> assets = new java.util.ArrayList<>();
        for (JsonElement element : Json.array(release, "assets")) {
            if (element.isJsonObject()) {
                assets.add(element.getAsJsonObject());
            }
        }
        return assets;
    }

    /**
     * Choisit le fichier correspondant a la maniere dont MiniCube est installe.
     *
     * <p>Une installation empaquetee se met a jour avec l'installeur, une execution
     * depuis le jar avec un nouveau jar. Prendre l'un pour l'autre laisserait
     * l'utilisateur avec un fichier qu'il ne peut pas utiliser.</p>
     */
    private JsonObject pickAsset(List<JsonObject> assets) {
        boolean packaged = isPackagedInstall();
        String preferred = packaged ? ".exe" : ".jar";

        for (JsonObject asset : assets) {
            String name = Json.string(asset, "name", "").toLowerCase(Locale.ROOT);
            if (name.endsWith(preferred) && !name.endsWith(".sha256")) {
                return asset;
            }
        }
        // Repli : l'autre format vaut mieux que rien, l'utilisateur saura quoi en faire.
        for (JsonObject asset : assets) {
            String name = Json.string(asset, "name", "").toLowerCase(Locale.ROOT);
            if ((name.endsWith(".exe") || name.endsWith(".jar")) && !name.endsWith(".sha256")) {
                return asset;
            }
        }
        return null;
    }

    /**
     * Recupere l'empreinte publiee a cote du fichier.
     *
     * <p>Convention retenue : un fichier {@code <nom>.sha256} contenant l'empreinte,
     * seule ou au format de la commande {@code sha256sum} (empreinte, espaces, nom).</p>
     *
     * @return l'empreinte en hexadecimal, ou une chaine vide si elle est absente
     */
    private String readCompanionHash(List<JsonObject> assets, String assetName) {
        String expected = (assetName + ".sha256").toLowerCase(Locale.ROOT);
        for (JsonObject asset : assets) {
            if (!Json.string(asset, "name", "").toLowerCase(Locale.ROOT).equals(expected)) {
                continue;
            }
            try {
                String url = Json.string(asset, "browser_download_url", "");
                Safety.requireSecureUrl(url, "Empreinte de la mise a jour");
                String body = Http.getString(url).trim();
                // Le format sha256sum est "empreinte  nom" : on ne garde que le
                // premier champ, quelle que soit la nature du separateur.
                String first = body.split("[ \t]+")[0].trim();
                return first.matches("[0-9a-fA-F]{64}") ? first.toLowerCase(Locale.ROOT) : "";
            } catch (Exception e) {
                Log.warn("Empreinte illisible pour " + assetName + " : " + e.getMessage());
                return "";
            }
        }
        return "";
    }

    /** Retire le "v" des etiquettes de la forme v1.2.3. */
    private String normalizeVersion(String tag) {
        String cleaned = tag == null ? "" : tag.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    /* ------------------------------------------------------------------ */
    /* Source : descripteur JSON                                           */
    /* ------------------------------------------------------------------ */

    /** Lit un descripteur heberge par vos soins, pour ne pas dependre de GitHub. */
    private CheckResult checkDescriptor() {
        String url = config.settings().getUpdateUrl();
        try {
            JsonObject payload = Http.getJson(url);
            String remoteVersion = Json.string(payload, "version", "");
            if (remoteVersion.isBlank()) {
                return new CheckResult(Status.ERROR, null,
                        "Le descripteur ne contient pas de numero de version.");
            }
            if (compareVersions(remoteVersion, Constants.APP_VERSION) <= 0) {
                Log.debug("Le launcher est a jour (" + Constants.APP_VERSION + ")");
                return new CheckResult(Status.UP_TO_DATE, null, "");
            }
            // Les deux algorithmes sont acceptes ; SHA-256 est prefere s'il est fourni.
            String sha256 = Json.string(payload, "sha256", "");
            String sha1 = Json.string(payload, "sha1", "");
            String assetUrl = Json.string(payload, "url", "");

            if (sha256.isBlank() && sha1.isBlank()) {
                return new CheckResult(Status.REJECTED, null,
                        "La version " + remoteVersion + " est refusee : le descripteur ne "
                        + "fournit aucune empreinte permettant d'en verifier l'integrite.");
            }

            UpdateInfo info = new UpdateInfo(
                    remoteVersion,
                    assetUrl,
                    sha256.isBlank() ? sha1 : sha256,
                    sha256.isBlank() ? "SHA-1" : "SHA-256",
                    Json.string(payload, "changelog", ""),
                    Json.string(payload, "publishedAt", ""),
                    Json.string(payload, "releaseUrl", ""),
                    assetUrl.substring(assetUrl.lastIndexOf('/') + 1),
                    Json.bool(payload, "mandatory", false),
                    assetUrl.toLowerCase(Locale.ROOT).endsWith(".exe"));
            Log.info("Mise a jour disponible : " + remoteVersion);
            return new CheckResult(Status.AVAILABLE, info, "");
        } catch (Exception e) {
            Log.warn("Verification des mises a jour impossible : " + e.getMessage());
            return new CheckResult(Status.ERROR, null,
                    "La verification n'a pas abouti : " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Telechargement et installation                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Telecharge la mise a jour dans {@code ~/.minicube/updates}.
     *
     * <p>Deux exigences sont posees avant tout telechargement, parce que ce fichier sera
     * <b>execute</b> : l'adresse doit etre chiffree, et une empreinte doit accompagner la
     * publication. Sans elles, quiconque parviendrait a se placer entre le launcher et le
     * serveur, ou a prendre la main sur ce serveur, ferait executer le code de son choix
     * sur toutes les machines equipees.</p>
     *
     * @return le fichier telecharge, dont l'empreinte a ete verifiee
     */
    public Path download(UpdateInfo info, Consumer<Progress> onProgress) throws IOException {
        Safety.requireSecureUrl(info.url(), "Mise a jour du launcher");
        if (info.hash().isBlank()) {
            throw new Safety.UnsafeInputException("Mise a jour refusee : aucune empreinte "
                    + "ne l'accompagne. Un paquet execute sans verification d'integrite "
                    + "n'est pas acceptable.");
        }
        Files.createDirectories(LauncherPaths.updatesDir());
        String fileName = info.assetName().isBlank()
                ? "MiniCube-" + info.version() + (info.installer() ? ".exe" : ".jar")
                : info.assetName();
        Path target = LauncherPaths.updatesDir().resolve(fileName);

        onProgress.accept(Progress.indeterminate("Telechargement de la version "
                + info.version() + "..."));
        long[] received = {0};
        Http.download(info.url(), target, bytes -> {
            received[0] += bytes;
            onProgress.accept(Progress.of("Telechargement de la mise a jour",
                    formatSize(received[0]), -1));
        });

        onProgress.accept(Progress.indeterminate("Verification de l'integrite..."));
        if (!verify(target, info)) {
            Files.deleteIfExists(target);
            throw new IOException("Empreinte incorrecte : mise a jour rejetee. Le fichier "
                    + "telecharge ne correspond pas a celui publie.");
        }
        onProgress.accept(Progress.done("Mise a jour prete."));
        Log.info("Mise a jour verifiee : " + target.getFileName());
        return target;
    }

    private boolean verify(Path file, UpdateInfo info) throws IOException {
        String actual = "SHA-256".equals(info.algorithm())
                ? Hashing.sha256(file)
                : Hashing.sha1(file);
        return actual.equalsIgnoreCase(info.hash());
    }

    /**
     * Installe la mise a jour telechargee.
     *
     * <p>Deux chemins selon l'installation : un installeur est lance puis MiniCube se
     * retire pour le laisser remplacer les fichiers, tandis qu'un jar est demarre dans
     * un nouveau processus. Dans les deux cas, le processus courant se termine : on ne
     * peut pas remplacer un programme pendant qu'il s'execute.</p>
     *
     * @param downloaded fichier renvoye par {@link #download}
     */
    public void install(Path downloaded) throws IOException {
        if (!Files.isRegularFile(downloaded)) {
            throw new IOException("Fichier de mise a jour introuvable : " + downloaded);
        }
        String name = downloaded.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".exe")) {
            Log.info("Lancement de l'installeur " + downloaded.getFileName());
            new ProcessBuilder(downloaded.toAbsolutePath().toString()).start();
        } else {
            Path java = OsUtil.currentJavaExecutable();
            Log.info("Redemarrage sur " + downloaded.getFileName());
            new ProcessBuilder(java.toString(), "-jar",
                    downloaded.toAbsolutePath().toString()).inheritIO().start();
        }
        Log.close();
        System.exit(0);
    }

    /* ------------------------------------------------------------------ */
    /* Comparaison de versions                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Compare deux numeros de version de la forme {@code 1.2.3}.
     *
     * @return un entier negatif, nul ou positif selon que {@code left} est anterieure,
     *         egale ou posterieure a {@code right}
     */
    public static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int a = parsePart(leftParts, i);
            int b = parsePart(rightParts, i);
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Taille lisible, utilisee pendant le telechargement. */
    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " o";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f Ko", bytes / 1024d);
        }
        return String.format("%.1f Mo", bytes / (1024d * 1024d));
    }
}
