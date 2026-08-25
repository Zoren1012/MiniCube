package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.ModEntry;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.model.RemoteMod;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.Safety;
import com.minicube.launcher.util.Zips;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Inventaire et gestion des mods installes.
 *
 * <p>Les metadonnees sont lues directement dans les archives : {@code fabric.mod.json}
 * pour Fabric et Quilt, {@code META-INF/mods.toml} (ou {@code neoforge.mods.toml}) pour
 * Forge et NeoForge.</p>
 *
 * <p>L'activation et la desactivation se font en deplacant le fichier entre
 * {@code mods/} et {@code mods-disabled/} : le jeu ne charge que le premier dossier.</p>
 */
public class ModService {

    private static final String BUNDLED_MANIFEST = "/config/mods-manifest.json";

    private final LauncherPaths paths;
    private final ConfigService config;

    public ModService(LauncherPaths paths, ConfigService config) {
        this.paths = paths;
        this.config = config;
    }

    /* ------------------------------------------------------------------ */
    /* Inventaire                                                          */
    /* ------------------------------------------------------------------ */

    /** Liste les mods actifs et desactives, tries par nom. */
    public List<ModEntry> scan() {
        List<ModEntry> mods = new ArrayList<>();
        collect(paths.modsDir(), true, mods);
        collect(paths.disabledModsDir(), false, mods);
        mods.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return mods;
    }

    private void collect(Path directory, boolean enabled, List<ModEntry> target) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jar") || name.endsWith(".jar.disabled");
                    })
                    .forEach(path -> target.add(readMetadata(path, enabled)));
        } catch (IOException e) {
            Log.warn("Lecture du dossier " + directory + " impossible : " + e.getMessage());
        }
    }

    /** Extrait les metadonnees d'un jar de mod ; les champs absents restent vides. */
    public ModEntry readMetadata(Path jar, boolean enabled) {
        ModEntry mod = new ModEntry();
        mod.setFile(jar);
        mod.setFileName(jar.getFileName().toString());
        mod.setEnabled(enabled);
        try {
            mod.setFileSize(Files.size(jar));
        } catch (IOException ignored) {
            mod.setFileSize(0);
        }

        String fabric = Zips.readEntry(jar, "fabric.mod.json");
        if (fabric != null) {
            parseFabricMetadata(fabric, mod);
            return mod;
        }
        String quilt = Zips.readEntry(jar, "quilt.mod.json");
        if (quilt != null) {
            parseQuiltMetadata(quilt, mod);
            return mod;
        }
        String forge = Zips.readEntry(jar, "META-INF/mods.toml");
        if (forge == null) {
            forge = Zips.readEntry(jar, "META-INF/neoforge.mods.toml");
            if (forge != null) {
                mod.setLoader("NeoForge");
            }
        } else {
            mod.setLoader("Forge");
        }
        if (forge != null) {
            parseForgeMetadata(forge, mod);
            return mod;
        }
        // Aucun descripteur reconnu : le nom de fichier sert d'identite.
        mod.setName(stripExtension(mod.getFileName()));
        return mod;
    }

    private void parseFabricMetadata(String json, ModEntry mod) {
        try {
            JsonObject root = Json.parseObject(json);
            mod.setLoader("Fabric");
            mod.setId(Json.string(root, "id", ""));
            mod.setName(Json.string(root, "name", stripExtension(mod.getFileName())));
            mod.setVersion(Json.string(root, "version", ""));
            mod.setDescription(Json.string(root, "description", ""));
            mod.setAuthors(joinAuthors(Json.array(root, "authors")));
        } catch (Exception e) {
            Log.debug("fabric.mod.json illisible dans " + mod.getFileName());
            mod.setName(stripExtension(mod.getFileName()));
        }
    }

    private void parseQuiltMetadata(String json, ModEntry mod) {
        try {
            JsonObject root = Json.parseObject(json);
            JsonObject loader = Json.object(root, "quilt_loader");
            mod.setLoader("Quilt");
            if (loader != null) {
                mod.setId(Json.string(loader, "id", ""));
                mod.setVersion(Json.string(loader, "version", ""));
                JsonObject metadata = Json.object(loader, "metadata");
                if (metadata != null) {
                    mod.setName(Json.string(metadata, "name", stripExtension(mod.getFileName())));
                    mod.setDescription(Json.string(metadata, "description", ""));
                }
            }
            if (mod.getName().isBlank()) {
                mod.setName(stripExtension(mod.getFileName()));
            }
        } catch (Exception e) {
            mod.setName(stripExtension(mod.getFileName()));
        }
    }

    /** Espaces ou tabulations autour du signe egal dans un fichier TOML. */
    private static final String GAP = "[ \t]*";

    /**
     * Extraction ciblee des champs du fichier TOML de Forge, sans dependance TOML.
     *
     * <p>Seuls quelques champs sont lus, avec des expressions volontairement simples :
     * la mise en forme de ces lignes est stable depuis les premieres versions de Forge,
     * et une bibliotheque TOML complete serait disproportionnee ici.</p>
     */
    private void parseForgeMetadata(String toml, ModEntry mod) {
        mod.setId(firstMatch(toml, "modId" + GAP + "=" + GAP + "\"([^\"]+)\""));
        String displayName = firstMatch(toml, "displayName" + GAP + "=" + GAP + "\"([^\"]+)\"");
        mod.setName(displayName.isBlank() ? stripExtension(mod.getFileName()) : displayName);
        mod.setVersion(firstMatch(toml, "version" + GAP + "=" + GAP + "\"([^\"]+)\""));
        mod.setAuthors(firstMatch(toml, "authors" + GAP + "=" + GAP + "\"([^\"]+)\""));
        // (?s) autorise le point a franchir les retours a la ligne des descriptions.
        String description = firstMatch(toml,
                "(?s)description" + GAP + "=" + GAP + "'''(.*?)'''");
        mod.setDescription(description.trim());
        if ("Inconnu".equals(mod.getLoader())) {
            mod.setLoader("Forge");
        }
    }

    /** Premier groupe capture par l'expression, ou une chaine vide si rien ne correspond. */
    private String firstMatch(String input, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String joinAuthors(JsonArray authors) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : authors) {
            if (element.isJsonPrimitive()) {
                names.add(element.getAsString());
            } else if (element.isJsonObject()) {
                names.add(Json.string(element.getAsJsonObject(), "name", ""));
            }
        }
        return String.join(", ", names);
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /* ------------------------------------------------------------------ */
    /* Activation et installation                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Active ou desactive un mod en le deplacant entre {@code mods} et
     * {@code mods-disabled}.
     *
     * @throws IOException si le fichier est verrouille (jeu en cours d'execution)
     */
    public void setEnabled(ModEntry mod, boolean enabled) throws IOException {
        if (mod.isRequired() && !enabled) {
            throw new IOException("Ce mod est requis par le serveur et ne peut pas etre "
                    + "desactive.");
        }
        if (mod.isEnabled() == enabled) {
            return;
        }
        Path targetDir = enabled ? paths.modsDir() : paths.disabledModsDir();
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(mod.getFileName());
        Files.move(mod.getFile(), target, StandardCopyOption.REPLACE_EXISTING);
        mod.setFile(target);
        mod.setEnabled(enabled);
        Log.info((enabled ? "Mod active : " : "Mod desactive : ") + mod.getName());
    }

    /** Copie un fichier .jar dans le dossier des mods. */
    public ModEntry install(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Fichier introuvable : " + source);
        }
        if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Un mod doit etre un fichier .jar");
        }
        Files.createDirectories(paths.modsDir());
        Path target = paths.modsDir().resolve(source.getFileName().toString());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        Log.info("Mod installe : " + target.getFileName());
        return readMetadata(target, true);
    }

    /** Supprime definitivement un mod du disque. */
    public void delete(ModEntry mod) throws IOException {
        if (mod.isRequired()) {
            throw new IOException("Ce mod est requis et ne peut pas etre supprime.");
        }
        Files.deleteIfExists(mod.getFile());
        Log.info("Mod supprime : " + mod.getName());
    }

    /* ------------------------------------------------------------------ */
    /* Manifeste du projet                                                 */
    /* ------------------------------------------------------------------ */

    /** Charge le manifeste des mods requis (distant si configure, embarque sinon). */
    public List<RemoteMod> fetchManifest() {
        String url = config.settings().getModsManifestUrl();
        if (!url.isBlank()) {
            try {
                JsonObject payload = Http.getJson(url);
                List<RemoteMod> mods = Json.GSON.fromJson(Json.array(payload, "mods"),
                        new TypeToken<List<RemoteMod>>() { }.getType());
                if (mods != null) {
                    return mods;
                }
            } catch (Exception e) {
                Log.warn("Manifeste de mods distant indisponible : " + e.getMessage());
            }
        }
        try (InputStream in = getClass().getResourceAsStream(BUNDLED_MANIFEST)) {
            if (in == null) {
                return List.of();
            }
            JsonObject payload = Json.GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            List<RemoteMod> mods = Json.GSON.fromJson(Json.array(payload, "mods"),
                    new TypeToken<List<RemoteMod>>() { }.getType());
            return mods == null ? List.of() : mods;
        } catch (Exception e) {
            Log.warn("Manifeste de mods embarque illisible : " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Installe les mods declares obligatoires pour une version donnee.
     *
     * <p>Un mod deja present avec la bonne empreinte n'est pas retelecharge. Un mod
     * present mais different est remplace : c'est le mecanisme de mise a jour
     * automatique du pack.</p>
     *
     * @param versionId  version du jeu ciblee
     * @param onProgress rappel de progression
     * @return le nombre de mods installes ou mis a jour
     */
    public int installRequiredMods(String versionId, java.util.function.Consumer<Progress>
            onProgress) throws IOException {
        List<RemoteMod> manifest = fetchManifest().stream()
                .filter(RemoteMod::isRequired)
                .filter(mod -> mod.matchesVersion(versionId))
                .toList();
        if (manifest.isEmpty()) {
            return 0;
        }
        Files.createDirectories(paths.modsDir());
        int installed = 0;
        int index = 0;

        for (RemoteMod mod : manifest) {
            index++;
            Path target = paths.modsDir().resolve(mod.getFileName());
            boolean upToDate = mod.getSha1().isBlank()
                    ? Files.isRegularFile(target)
                    : Hashing.verify(target, mod.getSha1());
            if (upToDate) {
                continue;
            }
            if (mod.getUrl().isBlank()) {
                Log.warn("Mod requis sans URL de telechargement : " + mod.getName());
                continue;
            }
            onProgress.accept(Progress.of("Installation des mods requis", mod.getName(),
                    (double) index / manifest.size()));
            Safety.requireSecureUrl(mod.getUrl(), "Mod requis " + mod.getName());
            Log.info("Telechargement du mod requis " + mod.getName());
            Http.download(mod.getUrl(), target, null);

            if (!mod.getSha1().isBlank() && !Hashing.verify(target, mod.getSha1())) {
                Files.deleteIfExists(target);
                throw new IOException("Empreinte incorrecte pour " + mod.getName()
                        + " : telechargement rejete.");
            }
            installed++;
        }
        if (installed > 0) {
            Log.info(installed + " mod(s) requis installe(s) ou mis a jour");
        }
        return installed;
    }

    /**
     * Confronte les mods installes au manifeste pour signaler ceux qui sont requis et
     * ceux pour lesquels une version plus recente est disponible.
     *
     * @param mods      liste issue de {@link #scan()}, modifiee sur place
     * @param versionId version du jeu ciblee
     */
    public void annotateWithManifest(List<ModEntry> mods, String versionId) {
        List<RemoteMod> manifest = fetchManifest();
        if (manifest.isEmpty()) {
            return;
        }
        for (ModEntry mod : mods) {
            for (RemoteMod remote : manifest) {
                if (!remote.matchesVersion(versionId)) {
                    continue;
                }
                boolean sameFile = remote.getFileName().equalsIgnoreCase(mod.getFileName());
                boolean sameId = !remote.getId().isBlank() && !mod.getId().isBlank()
                        && remote.getId().equalsIgnoreCase(mod.getId());
                if (sameFile || sameId) {
                    mod.setRequired(remote.isRequired());
                    if (!remote.getVersion().isBlank()) {
                        mod.setAvailableUpdate(remote.getVersion());
                    }
                    break;
                }
            }
        }
    }

    /** Nombre de mods pour lesquels une mise a jour est annoncee. */
    public long countUpdates(List<ModEntry> mods) {
        return mods.stream().filter(ModEntry::hasUpdate).count();
    }
}
