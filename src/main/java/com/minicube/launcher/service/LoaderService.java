package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.Safety;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Installation des chargeurs de mods : Fabric, Quilt, NeoForge et Forge.
 *
 * <p>Jusqu'ici MiniCube savait <i>reconnaitre</i> une version moddee, mais pas la poser :
 * il fallait aller chercher l'installeur officiel soi-meme. C'est l'etape ou l'on perd
 * les joueurs, et c'est celle que ce service supprime.</p>
 *
 * <h2>Deux familles, deux methodes</h2>
 *
 * <p><b>Fabric et Quilt</b> publient le descriptif de version en JSON. L'installation se
 * reduit a l'ecrire dans {@code versions/}: aucun programme n'est telecharge ni execute,
 * et les bibliotheques sont ensuite recuperees par le mecanisme habituel du launcher.</p>
 *
 * <p><b>Forge et NeoForge</b> n'offrent rien de tel : leur installeur est un programme
 * qu'il faut executer. MiniCube le telecharge depuis le depot Maven officiel, en HTTPS,
 * <b>controle son empreinte SHA-1 publiee a cote de lui</b>, puis le lance en mode
 * silencieux. C'est le modele de confiance de Maven et de Gradle ; il protege d'un
 * fichier corrompu, non d'un depot officiel qui serait lui-meme compromis. La difference
 * avec une empreinte figee dans le code merite d'etre connue, mais aucune autre voie
 * n'existe pour ces deux chargeurs.</p>
 */
public class LoaderService {

    /** Les quatre chargeurs reconnus, avec ce qu'il faut pour les atteindre. */
    public enum Loader {
        VANILLA("Vanilla"),
        FABRIC("Fabric"),
        QUILT("Quilt"),
        NEOFORGE("NeoForge"),
        FORGE("Forge");

        private final String label;

        Loader(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Vrai si le chargeur s'installe en ecrivant un simple descriptif. */
        public boolean isDescriptorBased() {
            return this == FABRIC || this == QUILT;
        }
    }

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/";
    private static final String QUILT_META = "https://meta.quiltmc.org/v3/";
    private static final String NEOFORGE_MAVEN =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/";
    private static final String FORGE_MAVEN =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String FORGE_PROMOTIONS =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    /** Un installeur qui depasse ce delai est abandonne plutot que de figer le launcher. */
    private static final int INSTALLER_TIMEOUT_MINUTES = 10;

    private final LauncherPaths paths;
    private final GameFileService gameFiles;
    private final JavaRuntimeService javaRuntime;
    private final ConfigService config;

    public LoaderService(LauncherPaths paths, GameFileService gameFiles,
                         JavaRuntimeService javaRuntime, ConfigService config) {
        this.paths = paths;
        this.gameFiles = gameFiles;
        this.javaRuntime = javaRuntime;
        this.config = config;
    }

    /* ------------------------------------------------------------------ */
    /* Catalogues                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Versions de Minecraft pour lesquelles ce chargeur existe.
     *
     * @param stableOnly ecarte les instantanes et les versions de developpement
     */
    public List<String> gameVersions(Loader loader, boolean stableOnly) throws IOException {
        return switch (loader) {
            case FABRIC -> fabricGameVersions(FABRIC_META, stableOnly);
            case QUILT -> fabricGameVersions(QUILT_META, stableOnly);
            case NEOFORGE -> neoForgeGameVersions();
            case FORGE -> forgeGameVersions();
            case VANILLA -> List.of();
        };
    }

    /** Versions du chargeur disponibles pour cette version du jeu, la plus recente en tete. */
    public List<String> loaderVersions(Loader loader, String gameVersion) throws IOException {
        return switch (loader) {
            case FABRIC -> fabricLoaderVersions(FABRIC_META, gameVersion);
            case QUILT -> fabricLoaderVersions(QUILT_META, gameVersion);
            case NEOFORGE -> neoForgeLoaderVersions(gameVersion);
            case FORGE -> forgeLoaderVersions(gameVersion);
            case VANILLA -> List.of();
        };
    }

    /* --- Fabric et Quilt : meme forme d'API ---------------------------- */

    private List<String> fabricGameVersions(String meta, boolean stableOnly) throws IOException {
        JsonArray entries = Json.parseArray(Http.getString(meta + "versions/game"));
        List<String> versions = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonObject entry = entries.get(index).getAsJsonObject();
            if (stableOnly && !Json.bool(entry, "stable", false)) {
                continue;
            }
            versions.add(Json.string(entry, "version", ""));
        }
        return versions;
    }

    private List<String> fabricLoaderVersions(String meta, String gameVersion)
            throws IOException {
        String response;
        try {
            response = Http.getString(meta + "versions/loader/" + encode(gameVersion));
        } catch (Http.HttpStatusException e) {
            // Interroge sur une version qu'il ne connait pas, Fabric repond 400 plutot
            // qu'une liste vide. Pour l'appelant, cela veut dire la meme chose.
            if (e.status() == 400 || e.status() == 404) {
                return List.of();
            }
            throw e;
        }
        JsonArray entries = Json.parseArray(response);
        List<String> versions = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonObject loader = entries.get(index).getAsJsonObject()
                    .getAsJsonObject("loader");
            if (loader != null) {
                versions.add(Json.string(loader, "version", ""));
            }
        }
        return versions;
    }

    /* --- NeoForge : un seul depot Maven -------------------------------- */

    /**
     * NeoForge numerote ses versions d'apres celle du jeu, mais sa regle a change avec
     * le nouveau nommage de Minecraft. Le resultat est donc recoupe avec le catalogue
     * Mojang : une version que Mojang ne publie pas n'est pas proposee, plutot que de
     * conduire a une installation impossible si la regle changeait encore.
     */
    private List<String> neoForgeGameVersions() throws IOException {
        List<String> known = gameFiles.fetchAvailableVersions().stream()
                .map(GameFileService.AvailableVersion::id)
                .toList();
        List<String> games = new ArrayList<>();
        for (String version : mavenVersions(NEOFORGE_MAVEN)) {
            String game = neoForgeGameOf(version);
            if (!game.isEmpty() && known.contains(game) && !games.contains(game)) {
                games.add(game);
            }
        }
        games.sort(LoaderService::compareVersionsDescending);
        return games;
    }

    /**
     * Deduit la version de Minecraft visee par une version de NeoForge.
     *
     * <p>Deux conventions coexistent. Du temps des versions {@code 1.x}, NeoForge
     * s'ecrivait en trois nombres : {@code 21.1.244} vise {@code 1.21.1}. Depuis que
     * Minecraft a abandonne le {@code 1.} initial, il en compte quatre :
     * {@code 26.2.0.67} vise {@code 26.2}, et {@code 26.1.2.97} vise {@code 26.1.2}.
     * Dans les deux cas, un zero en derniere position du numero de jeu s'efface.</p>
     */
    private String neoForgeGameOf(String loaderVersion) {
        String[] parts = loaderVersion.split("\\.");
        if (parts.length == 4) {
            return "0".equals(parts[2])
                    ? parts[0] + "." + parts[1]
                    : parts[0] + "." + parts[1] + "." + parts[2];
        }
        if (parts.length == 3) {
            return "0".equals(parts[1])
                    ? "1." + parts[0]
                    : "1." + parts[0] + "." + parts[1];
        }
        return "";
    }

    private List<String> neoForgeLoaderVersions(String gameVersion) throws IOException {
        List<String> versions = new ArrayList<>();
        for (String version : mavenVersions(NEOFORGE_MAVEN)) {
            if (gameVersion.equals(neoForgeGameOf(version))) {
                versions.add(version);
            }
        }
        versions.sort(LoaderService::compareVersionsDescending);
        return versions;
    }

    /* --- Forge : les promotions donnent les versions conseillees -------- */

    private List<String> forgeGameVersions() throws IOException {
        JsonObject promos = Json.parseObject(Http.getString(FORGE_PROMOTIONS))
                .getAsJsonObject("promos");
        List<String> games = new ArrayList<>();
        for (String key : promos.keySet()) {
            String game = key.replaceAll("-(latest|recommended)$", "");
            if (!games.contains(game)) {
                games.add(game);
            }
        }
        games.sort(LoaderService::compareVersionsDescending);
        return games;
    }

    private List<String> forgeLoaderVersions(String gameVersion) throws IOException {
        List<String> versions = new ArrayList<>();
        for (String version : mavenVersions(FORGE_MAVEN)) {
            // Le depot Forge nomme ses artefacts <version du jeu>-<version de Forge>.
            if (version.startsWith(gameVersion + "-")) {
                versions.add(version.substring(gameVersion.length() + 1));
            }
        }
        versions.sort(LoaderService::compareVersionsDescending);
        return versions;
    }

    /** Lit les versions publiees dans le maven-metadata.xml d'un depot. */
    private List<String> mavenVersions(String base) throws IOException {
        String xml = Http.getString(base + "maven-metadata.xml");
        List<String> versions = new ArrayList<>();
        Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }
        return versions;
    }

    /* ------------------------------------------------------------------ */
    /* Installation                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Installe un chargeur, en posant d'abord la version vanilla dont il depend.
     *
     * @return l'identifiant de la version installee, a selectionner pour jouer
     */
    public String install(Loader loader, String gameVersion, String loaderVersion,
                          Consumer<Progress> onProgress) throws IOException {
        if (loader == Loader.VANILLA) {
            gameFiles.installVersion(gameVersion, onProgress);
            return gameVersion;
        }
        // Tous les chargeurs se greffent sur la version vanilla : sans elle, le
        // descriptif herite d'un parent absent et le jeu ne demarre pas.
        if (!Files.isRegularFile(paths.versionJson(gameVersion))) {
            onProgress.accept(Progress.indeterminate(
                    "Installation de Minecraft " + gameVersion));
            gameFiles.installVersion(gameVersion, onProgress);
        }

        String versionId = loader.isDescriptorBased()
                ? installFromDescriptor(loader, gameVersion, loaderVersion, onProgress)
                : runOfficialInstaller(loader, gameVersion, loaderVersion, onProgress);

        onProgress.accept(Progress.indeterminate("Telechargement des bibliotheques"));
        gameFiles.ensureGameFiles(gameFiles.resolve(versionId), true, onProgress);

        Log.info("Chargeur installe : " + versionId);
        return versionId;
    }

    /**
     * Fabric et Quilt : le descriptif suffit.
     *
     * <p>Rien n'est execute, et le fichier ecrit est exactement celui que publie le
     * projet. C'est la voie la plus sure des deux.</p>
     */
    private String installFromDescriptor(Loader loader, String gameVersion,
                                         String loaderVersion, Consumer<Progress> onProgress)
            throws IOException {
        String meta = loader == Loader.FABRIC ? FABRIC_META : QUILT_META;
        String url = meta + "versions/loader/" + encode(gameVersion) + "/"
                + encode(loaderVersion) + "/profile/json";
        Safety.requireSecureUrl(url, loader.label());

        onProgress.accept(Progress.of("Descripteur " + loader.label(), 0.1));
        JsonObject profile = Json.parseObject(Http.getString(url));

        String versionId = Json.string(profile, "id", "");
        if (versionId.isBlank()) {
            throw new IOException("Descripteur " + loader.label() + " sans identifiant");
        }
        Path descriptor = paths.versionJson(versionId);
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, Json.GSON.toJson(profile), StandardCharsets.UTF_8);
        Log.info("Descripteur ecrit : " + descriptor);
        return versionId;
    }

    /**
     * Forge et NeoForge : leur installeur officiel doit etre execute.
     *
     * <p>Le fichier est verifie avant d'etre lance, et l'installeur travaille dans le
     * dossier de jeu comme le ferait celui qu'on telecharge a la main.</p>
     */
    private String runOfficialInstaller(Loader loader, String gameVersion,
                                        String loaderVersion, Consumer<Progress> onProgress)
            throws IOException {
        String artifact = loader == Loader.FORGE
                ? gameVersion + "-" + loaderVersion
                : loaderVersion;
        String base = (loader == Loader.FORGE ? FORGE_MAVEN : NEOFORGE_MAVEN)
                + artifact + "/"
                + (loader == Loader.FORGE ? "forge-" : "neoforge-") + artifact
                + "-installer.jar";
        Safety.requireSecureUrl(base, loader.label());

        Path installer = LauncherPaths.launcherDir().resolve("installers")
                .resolve(loader.name().toLowerCase() + "-" + artifact + ".jar");
        Files.createDirectories(installer.getParent());

        onProgress.accept(Progress.indeterminate("Telechargement de l'installeur "
                + loader.label()));
        Http.download(base, installer, null);
        verifySha1(base, installer, loader);

        prepareLauncherProfiles();

        onProgress.accept(Progress.indeterminate("Installation de " + loader.label()
                + " " + loaderVersion));
        runInstaller(installer, gameVersion);
        Files.deleteIfExists(installer);

        return findInstalledVersion(loader, gameVersion, loaderVersion);
    }

    /**
     * Compare l'installeur a l'empreinte publiee a cote de lui.
     *
     * <p>Elle vient du meme depot : elle protege d'un telechargement abime, pas d'un
     * depot compromis. C'est le modele de Maven, et il n'en existe pas d'autre ici.</p>
     */
    private void verifySha1(String url, Path file, Loader loader) throws IOException {
        String expected;
        try {
            expected = Http.getString(url + ".sha1").trim().split("\\s+")[0];
        } catch (IOException e) {
            throw new IOException("Empreinte de l'installeur " + loader.label()
                    + " introuvable : installation interrompue.");
        }
        String actual = Hashing.sha1(file);
        if (!actual.equalsIgnoreCase(expected)) {
            Files.deleteIfExists(file);
            throw new IOException("L'installeur " + loader.label()
                    + " ne correspond pas a son empreinte : il a ete refuse.");
        }
        Log.debug("Installeur " + loader.label() + " verifie");
    }

    /**
     * Les installeurs Forge refusent de travailler sans ce fichier, que seul le launcher
     * officiel cree. Un squelette vide leur suffit.
     */
    private void prepareLauncherProfiles() throws IOException {
        Path profiles = paths.gameDir().resolve("launcher_profiles.json");
        if (Files.isRegularFile(profiles)) {
            return;
        }
        Files.createDirectories(profiles.getParent());
        Files.writeString(profiles, "{\"profiles\":{},\"version\":3}",
                StandardCharsets.UTF_8);
        Log.debug("launcher_profiles.json cree pour l'installeur");
    }

    private void runInstaller(Path installer, String gameVersion) throws IOException {
        // Le meme Java que celui qui lancera le jeu : un installeur Forge recent
        // refuse de tourner sur une version trop ancienne.
        Path java = javaRuntime.resolveJavaExecutable(config.settings(),
                gameFiles.readVersionJson(gameVersion));

        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-jar",
                installer.toString(), "--installClient", paths.gameDir().toString());
        builder.directory(paths.gameDir().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        Process process = builder.start();
        try {
            if (!process.waitFor(INSTALLER_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("L'installeur ne repond plus : installation abandonnee.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Installation interrompue");
        }
        if (process.exitValue() != 0) {
            throw new IOException("L'installeur a echoue (code " + process.exitValue() + ").");
        }
    }

    /**
     * Retrouve le dossier cree par l'installeur.
     *
     * <p>Forge et NeoForge ne nomment pas leurs versions de la meme facon selon les
     * annees : plutot que de deviner, on cherche le dossier qui porte a la fois le nom du
     * chargeur et le numero installe.</p>
     */
    private String findInstalledVersion(Loader loader, String gameVersion,
                                        String loaderVersion) throws IOException {
        String needle = loader.name().toLowerCase();
        try (var dirs = Files.list(paths.versionsDir())) {
            List<String> candidates = dirs.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase().contains(needle)
                            && name.contains(loaderVersion))
                    .toList();
            if (!candidates.isEmpty()) {
                return candidates.get(0);
            }
        }
        throw new IOException("L'installeur " + loader.label()
                + " n'a laisse aucune version reconnaissable.");
    }

    /* ------------------------------------------------------------------ */
    /* Utilitaires                                                         */
    /* ------------------------------------------------------------------ */

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Trie des numeros de version du plus recent au plus ancien. */
    static int compareVersionsDescending(String left, String right) {
        String[] a = left.split("[._-]");
        String[] b = right.split("[._-]");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            long first = index < a.length ? numberOf(a[index]) : -1;
            long second = index < b.length ? numberOf(b[index]) : -1;
            if (first != second) {
                return Long.compare(second, first);
            }
        }
        return right.compareTo(left);
    }

    private static long numberOf(String part) {
        Matcher matcher = Pattern.compile("^(\\d+)").matcher(part);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }
}
