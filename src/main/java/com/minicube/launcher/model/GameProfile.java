package com.minicube.launcher.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Un profil de jeu : une configuration complete et independante.
 *
 * <p>Chaque profil porte sa propre version, son propre dossier de jeu — donc ses mods,
 * ses shaders, ses mondes — et ses propres reglages de memoire et de graphismes. Deux
 * profils peuvent ainsi coexister sans se marcher dessus : un pack moddé lourd d'un
 * cote, une partie vanilla legere de l'autre.</p>
 *
 * <p>Un profil peut aussi <b>partager</b> le dossier {@code .minecraft} principal : c'est
 * le cas du profil cree au premier demarrage, pour ne rien deplacer de ce qui existe
 * deja.</p>
 */
public class GameProfile {

    /** Modele de depart choisi a la creation, qui determine l'allure du profil. */
    public enum Preset {
        VANILLA("Vanilla"),
        FABRIC("Fabric"),
        FORGE("Forge"),
        MODDED("Modde"),
        CUSTOM("Personnalise");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private Preset preset = Preset.CUSTOM;

    /**
     * Dossier de jeu du profil.
     *
     * <p>Vide signifie « le dossier .minecraft principal » : le profil partage alors les
     * mods et les mondes de l'installation existante.</p>
     */
    private String directory = "";

    private String versionId = "";

    /** Memoire propre au profil, en Mo ; zero signifie « celle des parametres ». */
    private int ramMb;
    /** Java propre au profil ; vide signifie « celui des parametres ». */
    private String javaPath = "";
    private String extraJvmArgs = "";

    private GraphicsSettings graphics = new GraphicsSettings();
    private boolean shadersEnabled;
    private String activeShaderPack = "";

    private long createdAt = System.currentTimeMillis();
    private long lastPlayedAt;
    private int launchCount;

    public GameProfile() {
    }

    public GameProfile(String name, Preset preset) {
        this.name = name;
        this.preset = preset;
    }

    /** Vrai si le profil utilise le dossier .minecraft principal. */
    public boolean isShared() {
        return directory == null || directory.isBlank();
    }

    /** Date de derniere partie, ou un tiret si le profil n'a jamais servi. */
    public String lastPlayedLabel() {
        if (lastPlayedAt <= 0) {
            return "-";
        }
        return DateTimeFormatter.ofPattern("dd/MM/yyyy")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(lastPlayedAt));
    }

    /** Resume affichable : version et nombre de parties. */
    public String summaryLabel() {
        String version = versionId == null || versionId.isBlank() ? "-" : versionId;
        return version + "  -  " + launchCount + " partie(s)";
    }

    public void recordLaunch() {
        launchCount++;
        lastPlayedAt = System.currentTimeMillis();
    }

    /* --- Accesseurs ----------------------------------------------------- */

    public String getId() {
        return id == null || id.isBlank() ? (id = UUID.randomUUID().toString()) : id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Preset getPreset() {
        return preset == null ? Preset.CUSTOM : preset;
    }

    public void setPreset(Preset preset) {
        this.preset = preset;
    }

    public String getDirectory() {
        return directory == null ? "" : directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getVersionId() {
        return versionId == null ? "" : versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public int getRamMb() {
        return ramMb;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = ramMb;
    }

    public String getJavaPath() {
        return javaPath == null ? "" : javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getExtraJvmArgs() {
        return extraJvmArgs == null ? "" : extraJvmArgs;
    }

    public void setExtraJvmArgs(String extraJvmArgs) {
        this.extraJvmArgs = extraJvmArgs;
    }

    public GraphicsSettings getGraphics() {
        if (graphics == null) {
            graphics = new GraphicsSettings();
        }
        return graphics;
    }

    public void setGraphics(GraphicsSettings graphics) {
        this.graphics = graphics;
    }

    public boolean isShadersEnabled() {
        return shadersEnabled;
    }

    public void setShadersEnabled(boolean shadersEnabled) {
        this.shadersEnabled = shadersEnabled;
    }

    public String getActiveShaderPack() {
        return activeShaderPack == null ? "" : activeShaderPack;
    }

    public void setActiveShaderPack(String activeShaderPack) {
        this.activeShaderPack = activeShaderPack;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(long lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }

    public int getLaunchCount() {
        return launchCount;
    }

    public void setLaunchCount(int launchCount) {
        this.launchCount = launchCount;
    }

    @Override
    public String toString() {
        return getName();
    }
}
