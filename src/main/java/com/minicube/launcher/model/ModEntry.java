package com.minicube.launcher.model;

import java.nio.file.Path;

/**
 * Mod present dans le dossier {@code mods} (ou dans {@code mods-disabled}).
 *
 * <p>Les metadonnees sont extraites du jar : {@code fabric.mod.json} pour Fabric/Quilt,
 * {@code META-INF/mods.toml} pour Forge et NeoForge.</p>
 */
public class ModEntry {

    private String id = "";
    private String name = "";
    private String version = "";
    private String description = "";
    private String authors = "";
    private String loader = "Inconnu";
    private String fileName = "";
    private long fileSize;
    private boolean enabled = true;
    /** Mod declare obligatoire par le manifeste du projet : non desactivable. */
    private boolean required = false;
    /** Version disponible en ligne si une mise a jour existe. */
    private String availableUpdate = "";
    private transient Path file;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null || name.isBlank() ? fileName : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version == null ? "" : version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthors() {
        return authors == null ? "" : authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getLoader() {
        return loader;
    }

    public void setLoader(String loader) {
        this.loader = loader;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    /** Taille lisible, par exemple "3,4 Mo". */
    public String fileSizeLabel() {
        if (fileSize < 1024) {
            return fileSize + " o";
        }
        if (fileSize < 1024 * 1024) {
            return String.format("%.1f Ko", fileSize / 1024d);
        }
        return String.format("%.1f Mo", fileSize / (1024d * 1024d));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getAvailableUpdate() {
        return availableUpdate == null ? "" : availableUpdate;
    }

    public void setAvailableUpdate(String availableUpdate) {
        this.availableUpdate = availableUpdate;
    }

    public boolean hasUpdate() {
        return !getAvailableUpdate().isBlank()
                && !getAvailableUpdate().equalsIgnoreCase(getVersion());
    }

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return getName() + " " + getVersion();
    }
}
