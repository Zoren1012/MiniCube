package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.GameProfile;
import com.minicube.launcher.model.GraphicsSettings;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.minicube.launcher.util.Safety;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gestion des profils de jeu.
 *
 * <p>Un profil rassemble tout ce qui distingue une facon de jouer : version, dossier de
 * jeu, memoire, graphismes, shaders. Basculer d'un profil a l'autre revient a changer de
 * dossier de jeu, ce que le launcher savait deja faire ; le service se charge de ranger
 * les reglages courants dans le profil que l'on quitte, puis d'installer ceux du profil
 * que l'on rejoint.</p>
 *
 * <h2>Pourquoi les reglages sont recopies plutot que consultes</h2>
 *
 * <p>Chaque service — mods, shaders, options du jeu — travaille a partir des reglages
 * globaux. Recopier le profil actif dans ces reglages evite de faire passer un profil a
 * travers toute l'application, et garantit qu'aucun service ne peut en ignorer un.</p>
 */
public class GameProfileService {

    /** Nom du fichier ou vivent les profils. */
    private static final String FILE_NAME = "profiles.json";

    /** Dossier parent des profils isoles. */
    private static final String INSTANCES_DIR = "instances";

    private final ConfigService config;
    private final List<GameProfile> profiles = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String activeId = "";

    public GameProfileService(ConfigService config) {
        this.config = config;
        load();
    }

    /* ------------------------------------------------------------------ */
    /* Chargement et enregistrement                                        */
    /* ------------------------------------------------------------------ */

    /** Etat serialise : la liste des profils et celui qui est actif. */
    private static class Store {
        List<GameProfile> profiles = new ArrayList<>();
        String activeId = "";
    }

    private Path file() {
        return LauncherPaths.launcherDir().resolve(FILE_NAME);
    }

    private void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            createDefaultProfile();
            return;
        }
        try {
            Store store = Json.read(path, new TypeToken<Store>() { }.getType());
            profiles.clear();
            if (store.profiles != null) {
                profiles.addAll(store.profiles);
            }
            activeId = store.activeId == null ? "" : store.activeId;
            Log.info(profiles.size() + " profil(s) charge(s)");
        } catch (Exception e) {
            Log.warn("Profils illisibles, remise a neuf : " + e.getMessage());
            profiles.clear();
        }
        if (profiles.isEmpty()) {
            createDefaultProfile();
        }
        if (active().isEmpty()) {
            activeId = profiles.get(0).getId();
        }
    }

    /**
     * Cree le profil de depart a partir de l'installation existante.
     *
     * <p>Il partage le dossier {@code .minecraft} deja en place : personne ne doit
     * perdre ses mondes ni ses mods parce que les profils sont apparus.</p>
     */
    private void createDefaultProfile() {
        GameProfile profile = new GameProfile("Principal", GameProfile.Preset.VANILLA);
        profile.setDirectory("");
        profile.setVersionId(config.settings().getLastVersionId());
        profile.setRamMb(config.settings().getRamMb());
        profiles.add(profile);
        activeId = profile.getId();
        save();
        Log.info("Profil par defaut cree sur l'installation existante");
    }

    public void save() {
        try {
            Store store = new Store();
            store.profiles = new ArrayList<>(profiles);
            store.activeId = activeId;
            Json.write(file(), store);
            Safety.restrictToOwner(file());
        } catch (IOException e) {
            Log.error("Enregistrement des profils impossible", e);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Consultation                                                        */
    /* ------------------------------------------------------------------ */

    public List<GameProfile> all() {
        return List.copyOf(profiles);
    }

    public Optional<GameProfile> active() {
        return profiles.stream()
                .filter(profile -> profile.getId().equals(activeId))
                .findFirst();
    }

    public Optional<GameProfile> byId(String id) {
        return profiles.stream().filter(profile -> profile.getId().equals(id)).findFirst();
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        listeners.forEach(Runnable::run);
    }

    /* ------------------------------------------------------------------ */
    /* Creation et suppression                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Cree un profil.
     *
     * @param name     nom visible
     * @param preset   modele de depart
     * @param isolated vrai pour un dossier de jeu propre, faux pour partager le
     *                 {@code .minecraft} principal
     */
    public GameProfile create(String name, GameProfile.Preset preset, boolean isolated) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Le profil doit avoir un nom.");
        }
        if (profiles.stream().anyMatch(p -> p.getName().equalsIgnoreCase(cleaned))) {
            throw new IllegalArgumentException("Un profil porte deja ce nom.");
        }
        GameProfile profile = new GameProfile(cleaned, preset);
        profile.setRamMb(LauncherSettings.recommendedRam());

        if (isolated) {
            Path directory = LauncherPaths.launcherDir()
                    .resolve(INSTANCES_DIR).resolve(slug(cleaned));
            try {
                Files.createDirectories(directory.resolve("mods"));
                Files.createDirectories(directory.resolve("shaderpacks"));
                Files.createDirectories(directory.resolve("saves"));
                Files.createDirectories(directory.resolve("versions"));
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Dossier du profil impossible a creer : " + e.getMessage());
            }
            profile.setDirectory(directory.toString());
        }
        profiles.add(profile);
        save();
        notifyListeners();
        Log.info("Profil cree : " + cleaned + (isolated ? " (dossier propre)" : " (partage)"));
        return profile;
    }

    /**
     * Supprime un profil.
     *
     * <p>Le dossier de jeu n'est <b>jamais</b> efface : il contient des mondes. Le profil
     * disparait de la liste, ses fichiers restent sur le disque.</p>
     */
    public void delete(String id) {
        if (profiles.size() <= 1) {
            throw new IllegalStateException("Le dernier profil ne peut pas etre supprime.");
        }
        GameProfile profile = byId(id).orElseThrow(
                () -> new IllegalArgumentException("Profil introuvable."));
        profiles.remove(profile);
        if (activeId.equals(id)) {
            activate(profiles.get(0).getId());
        }
        save();
        notifyListeners();
        Log.info("Profil supprime : " + profile.getName()
                + " (ses fichiers sont conserves)");
    }

    public void rename(String id, String newName) {
        String cleaned = newName == null ? "" : newName.trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Le profil doit avoir un nom.");
        }
        GameProfile profile = byId(id).orElseThrow(
                () -> new IllegalArgumentException("Profil introuvable."));
        profile.setName(cleaned);
        save();
        notifyListeners();
    }

    /* ------------------------------------------------------------------ */
    /* Bascule                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Range les reglages courants dans le profil actif.
     *
     * <p>A appeler avant toute bascule, et avant de quitter le launcher : sans cela, ce
     * que le joueur vient de regler serait perdu au changement de profil.</p>
     */
    public void captureCurrent() {
        active().ifPresent(profile -> {
            LauncherSettings settings = config.settings();
            profile.setVersionId(settings.getLastVersionId());
            profile.setRamMb(settings.getRamMb());
            profile.setJavaPath(settings.getJavaPath());
            profile.setExtraJvmArgs(settings.getExtraJvmArgs());
            profile.setGraphics(copyOf(settings.getGraphics()));
            profile.setShadersEnabled(settings.isShadersEnabled());
            profile.setActiveShaderPack(settings.getActiveShaderPack());
        });
    }

    /**
     * Active un profil : ses reglages deviennent ceux du launcher.
     *
     * @return true si le dossier de jeu a change, ce qui impose de reconstruire les
     *         services qui en dependent
     */
    public boolean activate(String id) {
        GameProfile target = byId(id).orElseThrow(
                () -> new IllegalArgumentException("Profil introuvable."));
        if (!activeId.equals(id)) {
            captureCurrent();
        }
        LauncherSettings settings = config.settings();
        String previousDirectory = settings.getGameDirectory();

        String directory = target.isShared()
                ? OsUtil.defaultMinecraftDir().toString()
                : target.getDirectory();
        settings.setGameDirectory(directory);
        settings.setLastVersionId(target.getVersionId());
        if (target.getRamMb() > 0) {
            settings.setRamMb(target.getRamMb());
        }
        settings.setJavaPath(target.getJavaPath());
        settings.setExtraJvmArgs(target.getExtraJvmArgs());
        settings.setGraphics(copyOf(target.getGraphics()));
        settings.setShadersEnabled(target.isShadersEnabled());
        settings.setActiveShaderPack(target.getActiveShaderPack());

        activeId = id;
        config.save();
        save();
        notifyListeners();
        Log.info("Profil actif : " + target.getName());

        return !directory.equals(previousDirectory);
    }

    /** Comptabilise une partie lancee sur le profil actif. */
    public void recordLaunch() {
        active().ifPresent(profile -> {
            profile.recordLaunch();
            save();
        });
    }

    /* ------------------------------------------------------------------ */
    /* Utilitaires                                                         */
    /* ------------------------------------------------------------------ */

    /** Copie des reglages graphiques : deux profils ne doivent jamais partager l'objet. */
    private GraphicsSettings copyOf(GraphicsSettings source) {
        if (source == null) {
            return new GraphicsSettings();
        }
        return Json.GSON.fromJson(Json.GSON.toJson(source), GraphicsSettings.class);
    }

    /** Nom de dossier sur : lettres, chiffres et tirets uniquement. */
    private String slug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isBlank()) {
            base = "profil";
        }
        Path parent = LauncherPaths.launcherDir().resolve(INSTANCES_DIR);
        String candidate = base;
        int suffix = 2;
        while (Files.exists(parent.resolve(candidate))) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
