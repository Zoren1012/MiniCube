package com.minicube.launcher.model;

import java.nio.file.Path;

/**
 * Pack de shaders present dans {@code .minecraft/shaderpacks}.
 *
 * <p>Un pack peut etre une archive .zip ou un dossier. L'apercu est extrait de
 * {@code shaders/screenshot.png} lorsqu'il existe, comme le fait le jeu.</p>
 */
public class ShaderPack {

    private String name = "";
    private String fileName = "";
    private long fileSize;
    private boolean directory;
    /** Pack actuellement selectionne dans la configuration Iris ou OptiFine. */
    private boolean active;
    /** Chemin de l'image d'apercu extraite, ou null si le pack n'en fournit pas. */
    private transient Path previewImage;
    private transient Path file;

    public String getName() {
        return name == null || name.isBlank() ? fileName : name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String fileSizeLabel() {
        if (fileSize < 1024 * 1024) {
            return String.format("%.0f Ko", fileSize / 1024d);
        }
        return String.format("%.1f Mo", fileSize / (1024d * 1024d));
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Path getPreviewImage() {
        return previewImage;
    }

    public void setPreviewImage(Path previewImage) {
        this.previewImage = previewImage;
    }

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return getName();
    }
}
