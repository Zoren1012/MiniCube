package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.InstalledVersion;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Verification de l'installation Minecraft et inventaire des versions disponibles.
 *
 * <p>Utilise par l'assistant de premier demarrage pour valider le dossier choisi, puis
 * par l'onglet Accueil pour alimenter la liste des versions jouables.</p>
 */
public class MinecraftInstallService {

    /**
     * Verdict de validation d'un dossier .minecraft.
     *
     * @param valid        vrai si le dossier est exploitable
     * @param message      resume affiche a l'utilisateur
     * @param versionCount nombre de versions completes trouvees
     * @param warnings     remarques non bloquantes
     */
    public record ValidationResult(boolean valid, String message, int versionCount,
                                   List<String> warnings) {

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, 0, Collections.emptyList());
        }
    }

    /**
     * Controle qu'un dossier ressemble bien a une installation Minecraft.
     *
     * <p>La validation reste tolerante : un dossier vide mais accessible en ecriture est
     * accepte, car le launcher sait telecharger une version depuis zero. Seuls les cas
     * reellement bloquants (chemin inexistant, fichier au lieu d'un dossier, dossier en
     * lecture seule) sont refuses.</p>
     */
    public ValidationResult validate(Path directory) {
        if (directory == null) {
            return ValidationResult.failure("Aucun dossier selectionne.");
        }
        if (!Files.exists(directory)) {
            return ValidationResult.failure("Le dossier " + directory + " n'existe pas.");
        }
        if (!Files.isDirectory(directory)) {
            return ValidationResult.failure(directory + " n'est pas un dossier.");
        }
        if (!Files.isWritable(directory)) {
            return ValidationResult.failure(
                    "Le dossier est en lecture seule : le jeu ne pourra pas y ecrire.");
        }

        List<String> warnings = new ArrayList<>();
        LauncherPaths paths = new LauncherPaths(directory);

        if (!Files.isDirectory(paths.versionsDir())) {
            warnings.add("Aucun dossier versions : la version choisie sera telechargee.");
        }
        if (!Files.isDirectory(paths.assetsDir())) {
            warnings.add("Aucun dossier assets : les ressources seront telechargees.");
        }
        if (!Files.isRegularFile(directory.resolve("launcher_profiles.json"))) {
            warnings.add("launcher_profiles.json absent : certains installateurs de mods "
                    + "(Forge, Fabric) le reclament.");
        }

        int versions = listVersions(paths).size();
        String message = versions > 0
                ? "Installation valide : " + versions + " version(s) detectee(s)."
                : "Dossier valide, mais aucune version installee pour le moment.";
        return new ValidationResult(true, message, versions, warnings);
    }

    /**
     * Inventorie les versions presentes dans {@code versions/}.
     *
     * @return la liste triee de la plus recente a la plus ancienne
     */
    public List<InstalledVersion> listVersions(LauncherPaths paths) {
        List<InstalledVersion> result = new ArrayList<>();
        Path versionsDir = paths.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return result;
        }
        try (Stream<Path> dirs = Files.list(versionsDir)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String id = dir.getFileName().toString();
                Path json = dir.resolve(id + ".json");
                if (!Files.isRegularFile(json)) {
                    continue;
                }
                try {
                    JsonObject descriptor = Json.readObject(json);
                    String type = Json.string(descriptor, "type", "release");
                    String releaseTime = Json.string(descriptor, "releaseTime",
                            Json.string(descriptor, "time", ""));
                    String loader = detectLoader(descriptor, id);
                    boolean complete = isPlayable(paths, descriptor, id);
                    result.add(new InstalledVersion(id, type, releaseTime, loader, complete));
                } catch (IOException e) {
                    Log.warn("Descripteur de version illisible : " + json + " ("
                            + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            Log.error("Lecture du dossier versions impossible : " + e.getMessage());
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Une version est jouable si son jar client existe, ou si elle herite d'une version
     * parente qui le fournit (cas de Forge, Fabric, Quilt et NeoForge).
     */
    private boolean isPlayable(LauncherPaths paths, JsonObject descriptor, String id) {
        if (Files.isRegularFile(paths.versionJar(id))) {
            return true;
        }
        String parent = Json.string(descriptor, "inheritsFrom", "");
        return !parent.isBlank() && Files.isRegularFile(paths.versionJar(parent));
    }

    /**
     * Determine le chargeur de mods d'une version a partir de sa classe principale,
     * de son identifiant et de son parent declare.
     */
    public String detectLoader(JsonObject descriptor, String id) {
        String mainClass = Json.string(descriptor, "mainClass", "").toLowerCase();
        String lowerId = id.toLowerCase();

        if (mainClass.contains("quilt") || lowerId.contains("quilt")) {
            return "Quilt";
        }
        if (mainClass.contains("fabric") || lowerId.contains("fabric")) {
            return "Fabric";
        }
        if (lowerId.contains("neoforge") || mainClass.contains("neoforge")) {
            return "NeoForge";
        }
        if (mainClass.contains("forge") || mainClass.contains("cpw.mods")
                || lowerId.contains("forge")) {
            return "Forge";
        }
        if (lowerId.contains("optifine")) {
            return "OptiFine";
        }
        return "Vanilla";
    }

    /** Version proposee par defaut : la derniere lancee si elle existe, sinon la plus recente. */
    public InstalledVersion pickDefaultVersion(List<InstalledVersion> versions, String lastUsed) {
        if (versions.isEmpty()) {
            return null;
        }
        if (lastUsed != null && !lastUsed.isBlank()) {
            for (InstalledVersion version : versions) {
                if (version.id().equals(lastUsed)) {
                    return version;
                }
            }
        }
        for (InstalledVersion version : versions) {
            if (version.complete()) {
                return version;
            }
        }
        return versions.get(0);
    }
}
