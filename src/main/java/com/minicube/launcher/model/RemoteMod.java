package com.minicube.launcher.model;

/**
 * Entree du manifeste de mods du projet.
 *
 * <p>Le manifeste est un JSON de la forme :</p>
 * <pre>
 * { "mods": [
 *     { "name": "Fabric API", "fileName": "fabric-api-0.97.jar",
 *       "url": "https://.../fabric-api-0.97.jar", "sha1": "...",
 *       "version": "0.97.0", "required": true, "mcVersion": "1.20.4" }
 * ]}
 * </pre>
 *
 * <p>Les mods marques {@code required} sont installes automatiquement avant le lancement
 * et ne peuvent pas etre desactives depuis l'interface.</p>
 */
public class RemoteMod {

    private String id = "";
    private String name = "";
    private String version = "";
    private String fileName = "";
    private String url = "";
    private String sha1 = "";
    private long size;
    private boolean required = true;
    /** Version du jeu ciblee ; vide = compatible avec toutes. */
    private String mcVersion = "";
    /** Chargeur cible : fabric, forge, quilt, neoforge ; vide = indifferent. */
    private String loader = "";

    public String getId() {
        return id == null || id.isBlank() ? getFileName() : id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null || name.isBlank() ? getFileName() : name;
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

    public String getFileName() {
        return fileName == null ? "" : fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSha1() {
        return sha1 == null ? "" : sha1;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getMcVersion() {
        return mcVersion == null ? "" : mcVersion;
    }

    public void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion;
    }

    public String getLoader() {
        return loader == null ? "" : loader;
    }

    public void setLoader(String loader) {
        this.loader = loader;
    }

    /** Vrai si ce mod concerne la version de jeu indiquee. */
    public boolean matchesVersion(String versionId) {
        if (getMcVersion().isBlank() || versionId == null) {
            return true;
        }
        return versionId.contains(getMcVersion());
    }
}
