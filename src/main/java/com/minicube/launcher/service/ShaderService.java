package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.ShaderPack;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.Safety;
import com.minicube.launcher.util.Zips;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Gestion des packs de shaders du dossier {@code .minecraft/shaderpacks}.
 *
 * <p>Le launcher n'effectue aucun rendu : il selectionne le pack dans les fichiers de
 * configuration d'Iris ou d'OptiFine (voir {@link OptionsService}), exactement comme le
 * ferait le menu du jeu.</p>
 */
public class ShaderService {

    private final LauncherPaths paths;
    private final ConfigService config;
    private final OptionsService optionsService;

    public ShaderService(LauncherPaths paths, ConfigService config,
                         OptionsService optionsService) {
        this.paths = paths;
        this.config = config;
        this.optionsService = optionsService;
    }

    /** Liste les packs installes, le pack actif etant marque. */
    public List<ShaderPack> scan() {
        List<ShaderPack> packs = new ArrayList<>();
        Path directory = paths.shaderpacksDir();
        if (!Files.isDirectory(directory)) {
            return packs;
        }
        String activeName = config.settings().getActiveShaderPack();

        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                String fileName = path.getFileName().toString();
                boolean isDirectory = Files.isDirectory(path);
                boolean isArchive = fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
                if (!isDirectory && !isArchive) {
                    continue;
                }
                ShaderPack pack = new ShaderPack();
                pack.setFile(path);
                pack.setFileName(fileName);
                pack.setDirectory(isDirectory);
                pack.setName(isArchive ? fileName.substring(0, fileName.length() - 4) : fileName);
                pack.setFileSize(sizeOf(path, isDirectory));
                pack.setActive(fileName.equals(activeName));
                pack.setPreviewImage(extractPreview(path, isDirectory));
                packs.add(pack);
            }
        } catch (IOException e) {
            Log.warn("Lecture du dossier shaderpacks impossible : " + e.getMessage());
        }
        packs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return packs;
    }

    private long sizeOf(Path path, boolean isDirectory) {
        try {
            if (!isDirectory) {
                return Files.size(path);
            }
            try (Stream<Path> stream = Files.walk(path)) {
                return stream.filter(Files::isRegularFile).mapToLong(file -> {
                    try {
                        return Files.size(file);
                    } catch (IOException e) {
                        return 0L;
                    }
                }).sum();
            }
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Extrait l'apercu fourni par le pack ({@code shaders/screenshot.png}) vers le cache
     * du launcher, afin de l'afficher dans l'onglet Shaders.
     *
     * @return le chemin de l'image, ou null si le pack n'en fournit pas
     */
    private Path extractPreview(Path pack, boolean isDirectory) {
        try {
            if (isDirectory) {
                Path candidate = pack.resolve("shaders").resolve("screenshot.png");
                return Files.isRegularFile(candidate) ? candidate : null;
            }
            String entry = Zips.listEntries(pack).stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith("screenshot.png"))
                    .findFirst()
                    .orElse(null);
            if (entry == null) {
                return null;
            }
            Path cacheDir = LauncherPaths.cacheDir().resolve("shader-previews");
            Files.createDirectories(cacheDir);
            Path target = cacheDir.resolve(pack.getFileName() + ".png");
            if (Files.isRegularFile(target)) {
                return target;
            }
            return Zips.extractEntry(pack, entry, target) ? target : null;
        } catch (IOException e) {
            Log.debug("Apercu de shader indisponible pour " + pack + " : " + e.getMessage());
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Installation                                                        */
    /* ------------------------------------------------------------------ */

    /** Installe un pack depuis un fichier local (.zip) choisi par l'utilisateur. */
    public ShaderPack install(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Fichier introuvable : " + source);
        }
        String fileName = source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IOException("Un pack de shaders doit etre une archive .zip");
        }
        if (!Zips.isValidZip(source)) {
            throw new IOException("Archive illisible ou corrompue : " + fileName);
        }
        Files.createDirectories(paths.shaderpacksDir());
        Path target = paths.shaderpacksDir().resolve(fileName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        Log.info("Pack de shaders installe : " + fileName);
        return findByFileName(fileName);
    }

    /**
     * Telecharge et installe un pack depuis une URL.
     *
     * @param url      adresse de l'archive
     * @param fileName nom du fichier a creer ; deduit de l'URL s'il est vide
     */
    public ShaderPack installFromUrl(String url, String fileName) throws IOException {
        String name = (fileName == null || fileName.isBlank())
                ? url.substring(url.lastIndexOf('/') + 1)
                : fileName;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = name + ".zip";
        }
        Files.createDirectories(paths.shaderpacksDir());
        Path target = paths.shaderpacksDir().resolve(name);
        Safety.requireSecureUrl(url, "Pack de shaders");
        Log.info("Telechargement du pack de shaders depuis " + url);
        Http.download(url, target, null);

        if (!Zips.isValidZip(target)) {
            Files.deleteIfExists(target);
            throw new IOException("Le fichier telecharge n'est pas une archive valide.");
        }
        return findByFileName(name);
    }

    private ShaderPack findByFileName(String fileName) throws IOException {
        for (ShaderPack pack : scan()) {
            if (pack.getFileName().equals(fileName)) {
                return pack;
            }
        }
        throw new IOException("Le pack " + fileName + " est introuvable apres installation.");
    }

    /** Supprime un pack du disque. */
    public void delete(ShaderPack pack) throws IOException {
        if (pack.isDirectory()) {
            try (Stream<Path> stream = Files.walk(pack.getFile())) {
                for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } else {
            Files.deleteIfExists(pack.getFile());
        }
        if (pack.getFileName().equals(config.settings().getActiveShaderPack())) {
            config.settings().setActiveShaderPack("");
            config.settings().setShadersEnabled(false);
            config.save();
        }
        Log.info("Pack de shaders supprime : " + pack.getName());
    }

    /* ------------------------------------------------------------------ */
    /* Activation                                                          */
    /* ------------------------------------------------------------------ */

    /** Selectionne un pack et l'active dans la configuration du jeu. */
    public void activate(ShaderPack pack) {
        config.settings().setActiveShaderPack(pack.getFileName());
        config.settings().setShadersEnabled(true);
        config.save();
        optionsService.applyShaderSelection(true, pack.getFileName());
        Log.info("Pack de shaders active : " + pack.getName());
    }

    /** Active ou desactive globalement le rendu par shaders. */
    public void setShadersEnabled(boolean enabled) {
        config.settings().setShadersEnabled(enabled);
        config.save();
        optionsService.applyShaderSelection(enabled, config.settings().getActiveShaderPack());
        Log.info(enabled ? "Shaders actives" : "Shaders desactives");
    }
}
