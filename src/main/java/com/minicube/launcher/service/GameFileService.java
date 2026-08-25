package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.minicube.launcher.util.Zips;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Resolution des descripteurs de version, telechargement et verification des fichiers
 * necessaires au jeu (jar client, bibliotheques, natives, assets).
 *
 * <p>Le service sait :</p>
 * <ul>
 *   <li>fusionner un descripteur avec son parent ({@code inheritsFrom}), ce qui permet de
 *       lancer les versions Forge, Fabric, Quilt et NeoForge ;</li>
 *   <li>evaluer les regles {@code rules} pour ne retenir que les bibliotheques du systeme
 *       courant ;</li>
 *   <li>telecharger ce qui manque et re-telecharger ce dont l'empreinte SHA-1 differe.</li>
 * </ul>
 */
public class GameFileService {

    /** Une bibliotheque a mettre a disposition du jeu. */
    public record LibraryFile(Path target, String url, String sha1, long size,
                              boolean nativeLibrary) {
    }

    /**
     * Descripteur de version pret a l'emploi.
     *
     * @param id           identifiant de la version lancee
     * @param json         descripteur fusionne avec ses parents
     * @param mainClass    classe principale a executer
     * @param assetIndexId identifiant de l'index d'assets
     * @param libraries    bibliotheques retenues pour le systeme courant
     * @param clientJar    jar client a placer sur le classpath
     * @param nativesDir   dossier d'extraction des bibliotheques natives
     */
    public record ResolvedVersion(String id, JsonObject json, String mainClass,
                                  String assetIndexId, List<LibraryFile> libraries,
                                  Path clientJar, Path nativesDir) {
    }

    private final LauncherPaths paths;
    private final VerificationCache verificationCache;

    public GameFileService(LauncherPaths paths) {
        this.paths = paths;
        this.verificationCache = new VerificationCache();
    }

    /** Cache d'empreintes, expose pour permettre sa purge depuis les parametres. */
    public VerificationCache verificationCache() {
        return verificationCache;
    }

    /* ------------------------------------------------------------------ */
    /* Resolution du descripteur                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Charge le descripteur d'une version et le fusionne avec ses parents.
     *
     * @param versionId identifiant du dossier de version
     * @throws IOException si le descripteur est absent ou illisible
     */
    public JsonObject readVersionJson(String versionId) throws IOException {
        Path file = paths.versionJson(versionId);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Descripteur introuvable pour la version " + versionId
                    + " (" + file + ")");
        }
        JsonObject descriptor = Json.readObject(file);
        String parentId = Json.string(descriptor, "inheritsFrom", "");
        if (parentId.isBlank()) {
            return descriptor;
        }
        // Les versions moddees ne redefinissent que ce qui change : on complete avec le parent.
        JsonObject parent = readVersionJson(parentId);
        return merge(parent, descriptor);
    }

    /**
     * Fusionne un descripteur enfant sur son parent.
     *
     * <p>Regles appliquees : les valeurs simples de l'enfant l'emportent, les tableaux
     * {@code libraries} et {@code arguments} sont concatenes avec l'enfant en tete afin
     * que ses bibliotheques soient chargees en priorite.</p>
     */
    private JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject merged = parent.deepCopy();

        for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if ("libraries".equals(key) && value.isJsonArray()) {
                JsonArray combined = new JsonArray();
                combined.addAll(value.getAsJsonArray());
                combined.addAll(Json.array(parent, "libraries"));
                merged.add("libraries", combined);
            } else if ("arguments".equals(key) && value.isJsonObject()) {
                merged.add("arguments", mergeArguments(Json.object(parent, "arguments"),
                        value.getAsJsonObject()));
            } else {
                merged.add(key, value);
            }
        }
        merged.remove("inheritsFrom");
        return merged;
    }

    /** Concatene les blocs game/jvm : parent d'abord, enfant ensuite. */
    private JsonObject mergeArguments(JsonObject parent, JsonObject child) {
        JsonObject result = new JsonObject();
        for (String section : new String[]{"game", "jvm"}) {
            JsonArray combined = new JsonArray();
            combined.addAll(Json.array(parent, section));
            combined.addAll(Json.array(child, section));
            result.add(section, combined);
        }
        return result;
    }

    /**
     * Prepare tout ce qui est necessaire pour construire la ligne de commande du jeu.
     *
     * @param versionId identifiant de la version a lancer
     */
    public ResolvedVersion resolve(String versionId) throws IOException {
        JsonObject json = readVersionJson(versionId);
        String mainClass = Json.string(json, "mainClass", "net.minecraft.client.main.Main");

        JsonObject assetIndex = Json.object(json, "assetIndex");
        String assetIndexId = assetIndex != null
                ? Json.string(assetIndex, "id", Json.string(json, "assets", "legacy"))
                : Json.string(json, "assets", "legacy");

        List<LibraryFile> libraries = collectLibraries(json);

        // Une version moddee (Fabric, Forge, Quilt, NeoForge) n'a pas de jar client
        // propre : elle reutilise celui de la version dont elle herite.
        Path clientJar = paths.versionJar(versionId);
        if (!Files.isRegularFile(clientJar)) {
            String jarSource = Json.string(json, "jar", "");
            if (jarSource.isBlank()) {
                jarSource = findInheritedJarVersion(versionId, 0);
            }
            if (!jarSource.isBlank()) {
                clientJar = paths.versionJar(jarSource);
            }
        }
        return new ResolvedVersion(versionId, json, mainClass, assetIndexId, libraries,
                clientJar, paths.nativesDir(versionId));
    }

    /**
     * Remonte la chaine des {@code inheritsFrom} jusqu'a trouver une version dont le jar
     * client est reellement present sur le disque.
     *
     * @param versionId version de depart
     * @param depth     profondeur courante, garde-fou contre une chaine circulaire
     * @return l'identifiant de la version fournissant le jar, ou une chaine vide
     */
    private String findInheritedJarVersion(String versionId, int depth) {
        if (depth > 8) {
            Log.warn("Chaine d'heritage trop profonde depuis " + versionId);
            return "";
        }
        try {
            Path descriptor = paths.versionJson(versionId);
            if (!Files.isRegularFile(descriptor)) {
                return "";
            }
            JsonObject raw = Json.readObject(descriptor);
            String parent = Json.string(raw, "inheritsFrom", "");
            if (parent.isBlank()) {
                return "";
            }
            if (Files.isRegularFile(paths.versionJar(parent))) {
                Log.debug("Jar client herite de la version " + parent);
                return parent;
            }
            return findInheritedJarVersion(parent, depth + 1);
        } catch (IOException e) {
            Log.debug("Lecture du descripteur de " + versionId + " impossible : "
                    + e.getMessage());
            return "";
        }
    }

    /* ------------------------------------------------------------------ */
    /* Bibliotheques                                                       */
    /* ------------------------------------------------------------------ */

    /** Retient les bibliotheques applicables au systeme courant, sans doublon. */
    private List<LibraryFile> collectLibraries(JsonObject versionJson) {
        List<LibraryFile> result = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        JsonArray libraries = Json.array(versionJson, "libraries");

        for (JsonElement element : libraries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject library = element.getAsJsonObject();
            if (!rulesAllow(Json.array(library, "rules"), Map.of())) {
                continue;
            }
            String name = Json.string(library, "name", "");
            JsonObject downloads = Json.object(library, "downloads");

            // Artefact principal.
            JsonObject artifact = downloads == null ? null : Json.object(downloads, "artifact");
            if (artifact != null) {
                addLibrary(result, seen, Json.string(artifact, "path", pathFromName(name)),
                        Json.string(artifact, "url", ""), Json.string(artifact, "sha1", ""),
                        Json.longValue(artifact, "size", 0), isNativeName(name));
            } else if (!name.isBlank() && downloads == null) {
                // Bibliotheque sans bloc downloads (Forge, Fabric) : URL de depot + chemin Maven.
                String repository = Json.string(library, "url", "https://libraries.minecraft.net/");
                String relative = pathFromName(name);
                addLibrary(result, seen, relative,
                        repository.endsWith("/") ? repository + relative : repository + "/" + relative,
                        "", 0, isNativeName(name));
            }

            // Ancien format : classifiers natifs declares dans un bloc "natives".
            JsonObject natives = Json.object(library, "natives");
            if (natives != null && downloads != null) {
                String classifierKey = Json.string(natives, OsUtil.current().mojangName(), "");
                if (!classifierKey.isBlank()) {
                    classifierKey = classifierKey.replace("${arch}", OsUtil.archBits());
                    JsonObject classifiers = Json.object(downloads, "classifiers");
                    JsonObject nativeArtifact = classifiers == null
                            ? null : Json.object(classifiers, classifierKey);
                    if (nativeArtifact != null) {
                        addLibrary(result, seen,
                                Json.string(nativeArtifact, "path",
                                        pathFromName(name + ":" + classifierKey)),
                                Json.string(nativeArtifact, "url", ""),
                                Json.string(nativeArtifact, "sha1", ""),
                                Json.longValue(nativeArtifact, "size", 0), true);
                    }
                }
            }
        }
        return result;
    }

    private void addLibrary(List<LibraryFile> target, Set<Path> seen, String relativePath,
                            String url, String sha1, long size, boolean nativeLibrary) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path file = paths.librariesDir().resolve(relativePath.replace('/', java.io.File.separatorChar));
        if (!seen.add(file)) {
            return;
        }
        target.add(new LibraryFile(file, url, sha1, size, nativeLibrary));
    }

    /** Les bibliotheques natives modernes portent un suffixe {@code natives-<os>}. */
    private boolean isNativeName(String name) {
        return name.contains("natives-");
    }

    /**
     * Convertit une coordonnee Maven en chemin relatif.
     * Exemple : {@code com.mojang:authlib:3.11.50} devient
     * {@code com/mojang/authlib/3.11.50/authlib-3.11.50.jar}.
     */
    public static String pathFromName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.split(":");
        if (parts.length < 3) {
            return "";
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        // Le type eventuel (jar, zip) est colle a la version : com.foo:bar:1.0@zip
        String extension = "jar";
        if (version.contains("@")) {
            String[] split = version.split("@");
            version = split[0];
            extension = split[1];
        }
        return group + "/" + artifact + "/" + version + "/"
                + artifact + "-" + version + classifier + "." + extension;
    }

    /* ------------------------------------------------------------------ */
    /* Regles conditionnelles                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Evalue un bloc {@code rules} tel que defini par Mojang.
     *
     * <p>Chaque regle vaut {@code allow} ou {@code disallow} et peut etre conditionnee par
     * le systeme d'exploitation ou par une fonctionnalite. La derniere regle qui
     * s'applique l'emporte ; sans aucune regle, l'element est autorise.</p>
     *
     * @param rules    tableau de regles (peut etre vide)
     * @param features fonctionnalites actives, par exemple has_custom_resolution
     */
    public static boolean rulesAllow(JsonArray rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean allowed = false;
        for (JsonElement element : rules) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject rule = element.getAsJsonObject();
            if (!ruleMatches(rule, features)) {
                continue;
            }
            allowed = "allow".equals(Json.string(rule, "action", "allow"));
        }
        return allowed;
    }

    private static boolean ruleMatches(JsonObject rule, Map<String, Boolean> features) {
        JsonObject os = Json.object(rule, "os");
        if (os != null) {
            String name = Json.string(os, "name", "");
            if (!name.isBlank() && !name.equals(OsUtil.current().mojangName())) {
                return false;
            }
            String arch = Json.string(os, "arch", "");
            if (!arch.isBlank() && !arch.equals(OsUtil.arch())
                    && !(arch.equals("x86") && "x86".equals(OsUtil.arch()))) {
                return false;
            }
            String version = Json.string(os, "version", "");
            if (!version.isBlank()) {
                try {
                    if (!java.util.regex.Pattern.compile(version)
                            .matcher(OsUtil.osVersion()).find()) {
                        return false;
                    }
                } catch (Exception ignored) {
                    // Expression reguliere invalide dans le descripteur : regle ignoree.
                }
            }
        }
        JsonObject required = Json.object(rule, "features");
        if (required != null) {
            for (Map.Entry<String, JsonElement> entry : required.entrySet()) {
                boolean expected = entry.getValue().getAsBoolean();
                boolean actual = features != null
                        && Boolean.TRUE.equals(features.get(entry.getKey()));
                if (expected != actual) {
                    return false;
                }
            }
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Telechargement et verification                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Verifie et complete tous les fichiers necessaires au lancement.
     *
     * @param version    version resolue
     * @param strictHash true pour recalculer l'empreinte de chaque fichier existant,
     *                   false pour se contenter de verifier leur presence (plus rapide)
     * @param onProgress rappel de progression, appele depuis un thread de fond
     */
    public void ensureGameFiles(ResolvedVersion version, boolean strictHash,
                                Consumer<Progress> onProgress) throws IOException {
        onProgress.accept(Progress.indeterminate("Analyse des fichiers du jeu..."));

        ensureClientJar(version, strictHash, onProgress);
        ensureLibraries(version, strictHash, onProgress);
        extractNatives(version, onProgress);
        ensureAssets(version, strictHash, onProgress);

        // Les empreintes constatees sont conservees : le prochain lancement se
        // contentera de comparer taille et date au lieu de tout relire.
        verificationCache.save();
        onProgress.accept(Progress.done("Fichiers du jeu verifies."));
    }

    /** Telecharge le jar client s'il manque ou si son empreinte ne correspond pas. */
    private void ensureClientJar(ResolvedVersion version, boolean strictHash,
                                 Consumer<Progress> onProgress) throws IOException {
        JsonObject downloads = Json.object(version.json(), "downloads");
        JsonObject client = downloads == null ? null : Json.object(downloads, "client");
        Path jar = version.clientJar();

        boolean ok = strictHash && client != null
                ? verificationCache.verify(jar, Json.string(client, "sha1", ""), false)
                : Files.isRegularFile(jar);
        if (ok) {
            return;
        }
        if (client == null || Json.string(client, "url", "").isBlank()) {
            if (!Files.isRegularFile(jar)) {
                throw new IOException("Le jar client de la version " + version.id()
                        + " est absent et aucune URL de telechargement n'est fournie.");
            }
            return;
        }
        onProgress.accept(Progress.of("Telechargement du client " + version.id(), 0.05));
        Log.info("Telechargement du jar client : " + version.id());
        Http.download(Json.string(client, "url", ""), jar, null);
    }

    /** Telecharge en parallele les bibliotheques manquantes ou corrompues. */
    private void ensureLibraries(ResolvedVersion version, boolean strictHash,
                                 Consumer<Progress> onProgress) throws IOException {
        List<LibraryFile> missing = new ArrayList<>();
        for (LibraryFile library : version.libraries()) {
            boolean ok = strictHash
                    ? verificationCache.verify(library.target(), library.sha1(), false)
                    : Files.isRegularFile(library.target());
            if (!ok) {
                if (library.url() == null || library.url().isBlank()) {
                    Log.warn("Bibliotheque absente et sans URL : " + library.target());
                    continue;
                }
                missing.add(library);
            }
        }
        if (missing.isEmpty()) {
            Log.debug("Toutes les bibliotheques sont presentes");
            return;
        }
        Log.info(missing.size() + " bibliotheque(s) a telecharger");
        downloadAll(missing, onProgress, "Telechargement des bibliotheques");
    }

    /** Telecharge une liste de fichiers avec un pool de threads et un suivi de progression. */
    private void downloadAll(List<LibraryFile> files, Consumer<Progress> onProgress, String label)
            throws IOException {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(
                Math.min(Constants.DOWNLOAD_THREADS, Math.max(1, files.size())));
        AtomicInteger done = new AtomicInteger();
        AtomicLong bytes = new AtomicLong();
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        List<String> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        int total = files.size();

        for (LibraryFile file : files) {
            futures.add(executor.submit(() -> {
                try {
                    Http.download(file.url(), file.target(), bytes::addAndGet);
                } catch (IOException e) {
                    errors.add(file.target().getFileName() + " : " + e.getMessage());
                    Log.warn("Echec du telechargement de " + file.url() + " : " + e.getMessage());
                }
                int completed = done.incrementAndGet();
                onProgress.accept(Progress.of(label,
                        completed + " / " + total + "  (" + formatSize(bytes.get()) + ")",
                        (double) completed / total));
            }));
        }
        try {
            for (var future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Telechargement interrompu", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IOException("Echec d'un telechargement : " + e.getCause(), e);
        } finally {
            executor.shutdown();
        }
        if (!errors.isEmpty()) {
            throw new IOException(errors.size() + " fichier(s) n'ont pas pu etre telecharges : "
                    + String.join(", ", errors.subList(0, Math.min(3, errors.size()))));
        }
    }

    /**
     * Extrait les bibliotheques natives dans {@code versions/<id>/natives}.
     *
     * <p>L'extraction n'est refaite que si le dossier est vide ou plus ancien que les
     * archives, afin de ne pas ralentir chaque lancement.</p>
     */
    private void extractNatives(ResolvedVersion version, Consumer<Progress> onProgress)
            throws IOException {
        List<LibraryFile> natives = version.libraries().stream()
                .filter(LibraryFile::nativeLibrary)
                .filter(library -> Files.isRegularFile(library.target()))
                .toList();
        if (natives.isEmpty()) {
            return;
        }
        Path dir = version.nativesDir();
        Files.createDirectories(dir);

        boolean upToDate = false;
        try (var stream = Files.list(dir)) {
            upToDate = stream.findAny().isPresent();
        } catch (IOException ignored) {
            // Dossier illisible : on force l'extraction.
        }
        if (upToDate) {
            Log.debug("Bibliotheques natives deja extraites");
            return;
        }
        onProgress.accept(Progress.indeterminate("Extraction des bibliotheques natives..."));
        Set<String> exclusions = Set.of("META-INF/", "module-info.class");
        for (LibraryFile library : natives) {
            try {
                Zips.extract(library.target(), dir, exclusions);
            } catch (IOException e) {
                Log.warn("Extraction impossible de " + library.target().getFileName()
                        + " : " + e.getMessage());
            }
        }
        Log.info("Bibliotheques natives extraites dans " + dir);
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " o";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.0f Ko", bytes / 1024d);
        }
        return String.format("%.1f Mo", bytes / (1024d * 1024d));
    }

    /* ------------------------------------------------------------------ */
    /* Assets                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Verifie l'index d'assets et telecharge les objets manquants.
     *
     * <p>Les assets sont stockes par empreinte dans {@code assets/objects/<xy>/<hash>}.
     * Les versions anterieures a la 1.7.3 utilisent en plus une arborescence "virtuelle"
     * ou les fichiers portent leur nom d'origine : elle est reconstituee ici.</p>
     */
    private void ensureAssets(ResolvedVersion version, boolean strictHash,
                              Consumer<Progress> onProgress) throws IOException {
        JsonObject assetIndexInfo = Json.object(version.json(), "assetIndex");
        String indexId = version.assetIndexId();
        Path indexFile = paths.assetIndexesDir().resolve(indexId + ".json");

        if (assetIndexInfo != null) {
            String expectedSha1 = Json.string(assetIndexInfo, "sha1", "");
            if (!Hashing.verify(indexFile, expectedSha1)) {
                String url = Json.string(assetIndexInfo, "url", "");
                if (url.isBlank()) {
                    Log.warn("Index d'assets absent et sans URL : " + indexId);
                    return;
                }
                onProgress.accept(Progress.indeterminate("Telechargement de l'index d'assets..."));
                Http.download(url, indexFile, null);
            }
        }
        if (!Files.isRegularFile(indexFile)) {
            Log.warn("Aucun index d'assets exploitable pour " + indexId);
            return;
        }

        JsonObject index = Json.readObject(indexFile);
        JsonObject objects = Json.object(index, "objects");
        if (objects == null) {
            return;
        }
        boolean virtual = Json.bool(index, "virtual", false);
        boolean mapToResources = Json.bool(index, "map_to_resources", false);

        List<LibraryFile> missing = new ArrayList<>();
        Map<String, String> nameToHash = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            JsonObject object = entry.getValue().getAsJsonObject();
            String hash = Json.string(object, "hash", "");
            if (hash.length() < 2) {
                continue;
            }
            long size = Json.longValue(object, "size", 0);
            String prefix = hash.substring(0, 2);
            Path target = paths.assetObjectsDir().resolve(prefix).resolve(hash);
            nameToHash.put(entry.getKey(), hash);

            boolean ok = strictHash
                    ? verificationCache.verify(target, hash, false)
                    : Files.isRegularFile(target);
            if (!ok) {
                missing.add(new LibraryFile(target, Constants.RESOURCES_BASE_URL + prefix + "/"
                        + hash, hash, size, false));
            }
        }
        if (!missing.isEmpty()) {
            Log.info(missing.size() + " ressource(s) a telecharger");
            downloadAll(missing, onProgress, "Telechargement des ressources");
        }
        if (virtual || mapToResources) {
            Path virtualDir = mapToResources
                    ? paths.gameDir().resolve("resources")
                    : paths.assetsVirtualDir(indexId);
            rebuildVirtualAssets(nameToHash, virtualDir, onProgress);
        }
    }

    /** Recree l'arborescence nommee attendue par les anciennes versions du jeu. */
    private void rebuildVirtualAssets(Map<String, String> nameToHash, Path virtualDir,
                                      Consumer<Progress> onProgress) throws IOException {
        onProgress.accept(Progress.indeterminate("Preparation des ressources heritees..."));
        Files.createDirectories(virtualDir);
        for (Map.Entry<String, String> entry : nameToHash.entrySet()) {
            String hash = entry.getValue();
            Path source = paths.assetObjectsDir().resolve(hash.substring(0, 2)).resolve(hash);
            Path target = virtualDir.resolve(entry.getKey().replace('/',
                    java.io.File.separatorChar));
            if (!Files.isRegularFile(source) || Files.isRegularFile(target)) {
                continue;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Catalogue Mojang                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Version disponible au telechargement chez Mojang.
     *
     * @param id          identifiant, par exemple 1.20.4
     * @param type        release ou snapshot
     * @param url         adresse du descripteur JSON
     * @param releaseTime date de publication
     */
    public record AvailableVersion(String id, String type, String url, String releaseTime) {
    }

    /** Recupere le catalogue officiel des versions. */
    public List<AvailableVersion> fetchAvailableVersions() throws IOException {
        JsonObject manifest = Http.getJson(Constants.VERSION_MANIFEST_URL);
        List<AvailableVersion> versions = new ArrayList<>();
        for (JsonElement element : Json.array(manifest, "versions")) {
            JsonObject entry = element.getAsJsonObject();
            versions.add(new AvailableVersion(
                    Json.string(entry, "id", ""),
                    Json.string(entry, "type", "release"),
                    Json.string(entry, "url", ""),
                    Json.string(entry, "releaseTime", "")));
        }
        return versions;
    }

    /**
     * Installe une version officielle : descripteur JSON puis fichiers associes.
     *
     * @param versionId  identifiant de la version a installer
     * @param onProgress rappel de progression
     */
    public void installVersion(String versionId, Consumer<Progress> onProgress)
            throws IOException {
        onProgress.accept(Progress.indeterminate("Recherche de la version " + versionId + "..."));
        AvailableVersion target = fetchAvailableVersions().stream()
                .filter(version -> version.id().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "Version introuvable dans le catalogue Mojang : " + versionId));

        Path descriptor = paths.versionJson(versionId);
        onProgress.accept(Progress.of("Telechargement du descripteur", 0.02));
        Http.download(target.url(), descriptor, null);
        Log.info("Descripteur installe : " + descriptor);

        ResolvedVersion resolved = resolve(versionId);
        ensureGameFiles(resolved, true, onProgress);
    }

    /* ------------------------------------------------------------------ */
    /* Verification d'integrite                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Bilan d'une verification d'integrite.
     *
     * @param checked   nombre de fichiers controles
     * @param missing   fichiers absents
     * @param corrupted fichiers presents mais dont l'empreinte differe
     */
    public record IntegrityReport(int checked, List<String> missing, List<String> corrupted) {

        public boolean isHealthy() {
            return missing.isEmpty() && corrupted.isEmpty();
        }

        public String summary() {
            if (isHealthy()) {
                return checked + " fichier(s) verifie(s), aucune anomalie.";
            }
            return checked + " fichier(s) verifie(s) : " + missing.size() + " manquant(s), "
                    + corrupted.size() + " corrompu(s).";
        }
    }

    /**
     * Controle l'integrite d'une version sans rien telecharger.
     * Utilise par le bouton "Verifier les fichiers" de l'onglet Parametres.
     */
    public IntegrityReport checkIntegrity(ResolvedVersion version, Consumer<Progress> onProgress) {
        List<String> missing = new ArrayList<>();
        List<String> corrupted = new ArrayList<>();
        int checked = 0;

        JsonObject downloads = Json.object(version.json(), "downloads");
        JsonObject client = downloads == null ? null : Json.object(downloads, "client");
        checked++;
        if (!Files.isRegularFile(version.clientJar())) {
            missing.add(version.clientJar().getFileName().toString());
        } else if (client != null
                && !verificationCache.verify(version.clientJar(), Json.string(client, "sha1", ""), true)) {
            corrupted.add(version.clientJar().getFileName().toString());
        }

        List<LibraryFile> libraries = version.libraries();
        for (int i = 0; i < libraries.size(); i++) {
            LibraryFile library = libraries.get(i);
            checked++;
            if (!Files.isRegularFile(library.target())) {
                missing.add(library.target().getFileName().toString());
            } else if (!verificationCache.verify(library.target(), library.sha1(), true)) {
                corrupted.add(library.target().getFileName().toString());
            }
            if (i % 20 == 0) {
                onProgress.accept(Progress.of("Verification des bibliotheques",
                        (double) i / libraries.size()));
            }
        }
        onProgress.accept(Progress.done("Verification terminee."));
        return new IntegrityReport(checked, missing, corrupted);
    }
}
