package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chargement, sauvegarde et reinitialisation de la configuration du launcher.
 *
 * <p>La configuration est unique et partagee : tous les controleurs manipulent la meme
 * instance de {@link LauncherSettings} puis appellent {@link #save()}. Un fichier
 * corrompu est mis de cote plutot que supprime, afin de pouvoir etre inspecte.</p>
 */
public class ConfigService {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private LauncherSettings settings;

    public ConfigService() {
        this.settings = load();
    }

    /** Configuration courante (jamais nulle). */
    public LauncherSettings settings() {
        return settings;
    }

    /**
     * Lit le fichier de configuration.
     *
     * @return la configuration lue, ou une configuration par defaut si le fichier est
     *         absent ou illisible
     */
    private LauncherSettings load() {
        Path file = LauncherPaths.configFile();
        if (!Files.isRegularFile(file)) {
            Log.info("Aucune configuration existante, creation des valeurs par defaut");
            LauncherSettings fresh = LauncherSettings.createDefault();
            I18n.setLanguage(fresh.getLanguage());
            return fresh;
        }
        try {
            LauncherSettings loaded = Json.read(file, LauncherSettings.class);
            loaded.sanitize();
            I18n.setLanguage(loaded.getLanguage());
            Log.setDebugEnabled(loaded.isDebugMode());
            Log.info("Configuration chargee depuis " + file);
            return loaded;
        } catch (Exception e) {
            Log.error("Configuration illisible, retour aux valeurs par defaut : "
                    + e.getMessage());
            backupCorruptedFile(file);
            LauncherSettings fresh = LauncherSettings.createDefault();
            I18n.setLanguage(fresh.getLanguage());
            return fresh;
        }
    }

    /** Conserve une copie du fichier fautif sous le nom {@code config.json.invalid}. */
    private void backupCorruptedFile(Path file) {
        try {
            Files.move(file, file.resolveSibling("config.json.invalid"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("Sauvegarde du fichier corrompu impossible : " + e.getMessage());
        }
    }

    /**
     * Ecrit la configuration sur disque et previent les composants abonnes.
     *
     * @return true si l'ecriture a reussi
     */
    public boolean save() {
        try {
            settings.sanitize();
            Json.write(LauncherPaths.configFile(), settings);
            Log.debug("Configuration enregistree");
            notifyListeners();
            return true;
        } catch (IOException e) {
            Log.error("Enregistrement de la configuration impossible : " + e.getMessage());
            return false;
        }
    }

    /**
     * Restaure les valeurs par defaut en conservant le dossier de jeu deja valide :
     * l'utilisateur n'a pas a refaire l'assistant de premier demarrage.
     */
    public void resetToDefaults() {
        String gameDir = settings.getGameDirectory();
        boolean firstRunDone = settings.isFirstRunCompleted();
        String activeAccount = settings.getActiveAccountUuid();

        settings = LauncherSettings.createDefault();
        settings.setGameDirectory(gameDir);
        settings.setFirstRunCompleted(firstRunDone);
        settings.setActiveAccountUuid(activeAccount);

        I18n.setLanguage(settings.getLanguage());
        Log.setDebugEnabled(settings.isDebugMode());
        save();
        Log.info("Parametres reinitialises");
    }

    /** Remplace integralement la configuration (import depuis la sauvegarde cloud). */
    public void replace(LauncherSettings replacement) {
        if (replacement == null) {
            return;
        }
        // Ce qui identifie la machine reste local.
        replacement.setGameDirectory(settings.getGameDirectory());
        replacement.setActiveAccountUuid(settings.getActiveAccountUuid());
        replacement.setFirstRunCompleted(settings.isFirstRunCompleted());

        // Ces deux champs decident quel programme sera execute au lancement : les
        // accepter depuis un service distant reviendrait a lui confier l'execution de
        // code arbitraire sur la machine. Ils ne sont jamais restaures.
        replacement.setJavaPath(settings.getJavaPath());
        replacement.setExtraJvmArgs(settings.getExtraJvmArgs());
        replacement.sanitize();
        settings = replacement;
        I18n.setLanguage(settings.getLanguage());
        Log.setDebugEnabled(settings.isDebugMode());
        save();
    }

    /** Chemins derives du dossier de jeu courant. */
    public LauncherPaths paths() {
        return new LauncherPaths(Path.of(settings.getGameDirectory().isBlank()
                ? com.minicube.launcher.util.OsUtil.defaultMinecraftDir().toString()
                : settings.getGameDirectory()));
    }

    /** Abonne un composant aux changements de configuration. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                Log.warn("Un abonne a la configuration a echoue : " + e.getMessage());
            }
        }
    }
}
