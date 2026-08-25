package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.GraphicsSettings;
import com.minicube.launcher.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Lecture et ecriture de {@code options.txt}, le fichier de reglages du jeu.
 *
 * <p>Le format est une simple suite de lignes {@code cle:valeur}. Les cles inconnues du
 * launcher sont preservees telles quelles : les reglages faits dans le jeu ne sont jamais
 * perdus, seules les options pilotees par l'onglet Graphismes sont reecrites.</p>
 */
public class OptionsService {

    private final LauncherPaths paths;

    public OptionsService(LauncherPaths paths) {
        this.paths = paths;
    }

    /** Lit options.txt en conservant l'ordre des lignes. */
    public Map<String, String> readOptions() {
        Map<String, String> options = new LinkedHashMap<>();
        Path file = paths.optionsFile();
        if (!Files.isRegularFile(file)) {
            return options;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int separator = line.indexOf(':');
                if (separator > 0) {
                    options.put(line.substring(0, separator), line.substring(separator + 1));
                }
            }
        } catch (IOException e) {
            Log.warn("Lecture de options.txt impossible : " + e.getMessage());
        }
        return options;
    }

    /** Reecrit options.txt a partir de la table fournie. */
    public void writeOptions(Map<String, String> options) {
        Path file = paths.optionsFile();
        List<String> lines = new ArrayList<>(options.size());
        for (Map.Entry<String, String> entry : options.entrySet()) {
            lines.add(entry.getKey() + ":" + entry.getValue());
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
            Log.debug("options.txt mis a jour (" + lines.size() + " entrees)");
        } catch (IOException e) {
            Log.error("Ecriture de options.txt impossible : " + e.getMessage());
        }
    }

    /**
     * Applique les reglages graphiques du launcher au fichier du jeu.
     *
     * @param graphics       reglages a appliquer
     * @param shadersEnabled etat souhaite des shaders
     * @param shaderPack     nom du pack de shaders actif (peut etre vide)
     */
    public void applyGraphicsSettings(GraphicsSettings graphics, boolean shadersEnabled,
                                      String shaderPack) {
        Map<String, String> options = readOptions();

        options.put("renderDistance", String.valueOf(graphics.getRenderDistance()));
        options.put("simulationDistance", String.valueOf(graphics.getSimulationDistance()));
        options.put("maxFps", String.valueOf(graphics.getMaxFps()));
        options.put("enableVsync", String.valueOf(graphics.isVsync()));
        options.put("fullscreen", String.valueOf(graphics.isFullscreen()));
        options.put("graphicsMode", String.valueOf(graphics.getGraphicsMode()));
        options.put("particles", String.valueOf(graphics.getParticles()));
        options.put("entityShadows", String.valueOf(graphics.isEntityShadows()));
        options.put("guiScale", String.valueOf(graphics.getGuiScale()));
        options.put("gamma", formatDouble(graphics.getBrightness()));
        // Le jeu stocke le champ de vision comme un decalage : 0.0 correspond a 70 degres.
        options.put("fov", formatDouble((graphics.getFov() - 70) / 40d));
        options.put("ao", formatAmbientOcclusion(options.get("ao"), graphics.isSmoothLighting()));

        writeOptions(options);
        applyShaderSelection(shadersEnabled, shaderPack);
    }

    /**
     * L'occlusion ambiante est un booleen depuis la 1.19 et un entier (0, 1, 2) avant.
     * Le format existant est conserve pour ne pas casser un fichier ancien.
     */
    private String formatAmbientOcclusion(String existing, boolean enabled) {
        boolean numeric = existing != null && !existing.equalsIgnoreCase("true")
                && !existing.equalsIgnoreCase("false");
        if (numeric) {
            return enabled ? "2" : "0";
        }
        return String.valueOf(enabled);
    }

    private String formatDouble(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /**
     * Importe les reglages presents dans options.txt vers le modele du launcher.
     * Appele au premier demarrage pour refleter la configuration existante du joueur.
     */
    public void importInto(GraphicsSettings graphics) {
        Map<String, String> options = readOptions();
        if (options.isEmpty()) {
            return;
        }
        graphics.setRenderDistance(parseInt(options.get("renderDistance"),
                graphics.getRenderDistance()));
        graphics.setSimulationDistance(parseInt(options.get("simulationDistance"),
                graphics.getSimulationDistance()));
        graphics.setMaxFps(parseInt(options.get("maxFps"), graphics.getMaxFps()));
        graphics.setVsync(parseBoolean(options.get("enableVsync"), graphics.isVsync()));
        graphics.setFullscreen(parseBoolean(options.get("fullscreen"), graphics.isFullscreen()));
        graphics.setGraphicsMode(parseInt(options.get("graphicsMode"), graphics.getGraphicsMode()));
        graphics.setParticles(parseInt(options.get("particles"), graphics.getParticles()));
        graphics.setEntityShadows(parseBoolean(options.get("entityShadows"),
                graphics.isEntityShadows()));
        graphics.setGuiScale(parseInt(options.get("guiScale"), graphics.getGuiScale()));
        graphics.setPreset(GraphicsSettings.Preset.CUSTOM);
        Log.info("Reglages graphiques importes depuis options.txt");
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean parseBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if ("1".equals(trimmed)) {
            return true;
        }
        if ("0".equals(trimmed)) {
            return false;
        }
        return Boolean.parseBoolean(trimmed);
    }

    /* ------------------------------------------------------------------ */
    /* Shaders                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Ecrit la selection de shaders dans les fichiers de configuration des deux moteurs
     * repandus : Iris (Fabric) et OptiFine.
     *
     * @param enabled    active ou desactive le rendu par shaders
     * @param shaderPack nom du dossier ou de l'archive du pack
     */
    public void applyShaderSelection(boolean enabled, String shaderPack) {
        writeIrisConfig(enabled, shaderPack);
        writeOptifineShaderConfig(enabled, shaderPack);
    }

    /** Fichier {@code config/iris.properties}. */
    private void writeIrisConfig(boolean enabled, String shaderPack) {
        Path file = paths.irisConfigFile();
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                Log.debug("Configuration Iris illisible : " + e.getMessage());
            }
        }
        properties.setProperty("enableShaders", String.valueOf(enabled));
        if (shaderPack != null && !shaderPack.isBlank()) {
            properties.setProperty("shaderPack", shaderPack);
        }
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                properties.store(out, "Genere par MiniCube");
            }
            Log.debug("Configuration Iris mise a jour");
        } catch (IOException e) {
            Log.warn("Ecriture de la configuration Iris impossible : " + e.getMessage());
        }
    }

    /** Fichier {@code optionsshaders.txt} d'OptiFine. */
    private void writeOptifineShaderConfig(boolean enabled, String shaderPack) {
        Path file = paths.optifineShaderOptions();
        Map<String, String> values = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int separator = line.indexOf('=');
                    if (separator > 0) {
                        values.put(line.substring(0, separator), line.substring(separator + 1));
                    }
                }
            } catch (IOException e) {
                Log.debug("Configuration OptiFine illisible : " + e.getMessage());
            }
        }
        // OptiFine desactive les shaders avec la valeur speciale OFF.
        values.put("shaderPack", enabled && shaderPack != null && !shaderPack.isBlank()
                ? shaderPack : "OFF");

        List<String> lines = new ArrayList<>();
        values.forEach((key, value) -> lines.add(key + "=" + value));
        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
            Log.debug("Configuration OptiFine mise a jour");
        } catch (IOException e) {
            Log.warn("Ecriture de la configuration OptiFine impossible : " + e.getMessage());
        }
    }

    /** Nom du pack de shaders actuellement selectionne dans les fichiers du jeu. */
    public String readActiveShaderPack() {
        Path iris = paths.irisConfigFile();
        if (Files.isRegularFile(iris)) {
            Properties properties = new Properties();
            try (var in = Files.newInputStream(iris)) {
                properties.load(in);
                String pack = properties.getProperty("shaderPack", "");
                if (!pack.isBlank()) {
                    return pack;
                }
            } catch (IOException e) {
                Log.debug("Lecture d'iris.properties impossible : " + e.getMessage());
            }
        }
        Path optifine = paths.optifineShaderOptions();
        if (Files.isRegularFile(optifine)) {
            try {
                for (String line : Files.readAllLines(optifine, StandardCharsets.UTF_8)) {
                    if (line.startsWith("shaderPack=")) {
                        String pack = line.substring("shaderPack=".length());
                        return "OFF".equals(pack) ? "" : pack;
                    }
                }
            } catch (IOException e) {
                Log.debug("Lecture d'optionsshaders.txt impossible : " + e.getMessage());
            }
        }
        return "";
    }
}
