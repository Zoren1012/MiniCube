package com.minicube.launcher.core;

import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolution centralisee de tous les chemins utilises par le launcher.
 *
 * <p>Deux racines coexistent :</p>
 * <ul>
 *   <li>le <b>dossier launcher</b> ({@code ~/.minicube}) : configuration, journaux,
 *       caches et mises a jour ; il n'est jamais touche par le jeu ;</li>
 *   <li>le <b>dossier de jeu</b> ({@code .minecraft}) choisi par l'utilisateur au premier
 *       demarrage : versions, bibliotheques, assets, mods, shaders.</li>
 * </ul>
 */
public final class LauncherPaths {

    private final Path gameDir;

    /**
     * @param gameDir racine du dossier .minecraft selectionne par l'utilisateur
     */
    public LauncherPaths(Path gameDir) {
        this.gameDir = gameDir.toAbsolutePath().normalize();
    }

    /* ------------------------------------------------------------------ */
    /* Dossier du launcher                                                 */
    /* ------------------------------------------------------------------ */

    public static Path launcherDir() {
        return OsUtil.launcherDir();
    }

    /** Fichier de configuration principal du launcher. */
    public static Path configFile() {
        return launcherDir().resolve("config.json");
    }

    /** Comptes enregistres (jetons de rafraichissement inclus). */
    public static Path accountsFile() {
        return launcherDir().resolve("accounts.json");
    }

    public static Path logsDir() {
        return launcherDir().resolve("logs");
    }

    /** Caches divers : avatars, manifestes distants, apercus de shaders. */
    public static Path cacheDir() {
        return launcherDir().resolve("cache");
    }

    public static Path avatarCacheDir() {
        return cacheDir().resolve("avatars");
    }

    /** Skins importes par l'utilisateur, conserves pour reutilisation. */
    public static Path skinsDir() {
        return launcherDir().resolve("skins");
    }

    /** Destination des paquets de mise a jour du launcher. */
    public static Path updatesDir() {
        return launcherDir().resolve("updates");
    }

    /* ------------------------------------------------------------------ */
    /* Dossier de jeu                                                      */
    /* ------------------------------------------------------------------ */

    public Path gameDir() {
        return gameDir;
    }

    public Path versionsDir() {
        return gameDir.resolve("versions");
    }

    /** Dossier d'une version : {@code versions/<id>}. */
    public Path versionDir(String versionId) {
        return versionsDir().resolve(versionId);
    }

    /** Descripteur JSON d'une version : {@code versions/<id>/<id>.json}. */
    public Path versionJson(String versionId) {
        return versionDir(versionId).resolve(versionId + ".json");
    }

    /** Jar client d'une version : {@code versions/<id>/<id>.jar}. */
    public Path versionJar(String versionId) {
        return versionDir(versionId).resolve(versionId + ".jar");
    }

    public Path librariesDir() {
        return gameDir.resolve("libraries");
    }

    public Path assetsDir() {
        return gameDir.resolve("assets");
    }

    public Path assetIndexesDir() {
        return assetsDir().resolve("indexes");
    }

    public Path assetObjectsDir() {
        return assetsDir().resolve("objects");
    }

    /** Assets "legacy" utilises par les versions anterieures a 1.7.3. */
    public Path assetsVirtualDir(String assetIndexId) {
        return assetsDir().resolve("virtual").resolve(assetIndexId);
    }

    /** Dossier d'extraction des bibliotheques natives pour une version donnee. */
    public Path nativesDir(String versionId) {
        return versionDir(versionId).resolve("natives");
    }

    public Path modsDir() {
        return gameDir.resolve("mods");
    }

    /** Mods desactives : deplaces hors du dossier mods pour ne pas etre charges. */
    public Path disabledModsDir() {
        return gameDir.resolve("mods-disabled");
    }

    public Path shaderpacksDir() {
        return gameDir.resolve("shaderpacks");
    }

    public Path resourcepacksDir() {
        return gameDir.resolve("resourcepacks");
    }

    public Path optionsFile() {
        return gameDir.resolve("options.txt");
    }

    /** Fichier de configuration des shaders OptiFine. */
    public Path optifineShaderOptions() {
        return gameDir.resolve("optionsshaders.txt");
    }

    /** Fichier de configuration des shaders Iris. */
    public Path irisConfigFile() {
        return gameDir.resolve("config").resolve("iris.properties");
    }

    /** Liste des serveurs du jeu (format NBT), utilisee pour ajouter un serveur. */
    public Path serversDat() {
        return gameDir.resolve("servers.dat");
    }

    /* ------------------------------------------------------------------ */
    /* Utilitaires                                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Cree les repertoires necessaires au fonctionnement du launcher et du jeu.
     * Les echecs sont journalises sans interrompre le demarrage : un dossier peut etre
     * en lecture seule sans empecher les autres fonctionnalites.
     */
    public void ensureDirectories() {
        Path[] required = {
                launcherDir(), logsDir(), cacheDir(), avatarCacheDir(), skinsDir(), updatesDir(),
                gameDir, versionsDir(), librariesDir(), assetsDir(), assetIndexesDir(),
                assetObjectsDir(), modsDir(), disabledModsDir(), shaderpacksDir(),
                resourcepacksDir()
        };
        for (Path path : required) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                Log.warn("Creation impossible du dossier " + path + " : " + e.getMessage());
            }
        }
    }
}
