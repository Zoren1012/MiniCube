package com.minicube.launcher.model;

/**
 * Serveur propose dans l'onglet Serveurs.
 *
 * <p>La liste provient soit du fichier embarque {@code config/servers.json}, soit de
 * l'URL {@code serversUrl} configuree, ce qui permet de la mettre a jour sans
 * redistribuer le launcher.</p>
 */
public class ServerEntry {

    private String name = "Serveur";
    private String address = "localhost";
    private int port = 25565;
    private String description = "";
    /** Version du jeu requise pour rejoindre, par exemple 1.20.4. */
    private String requiredVersion = "";
    /** Identifiant de version a lancer ; vide = version selectionnee par l'utilisateur. */
    private String versionId = "";
    /** URL d'une icone affichee dans la liste (optionnelle). */
    private String iconUrl = "";
    /** Un serveur "officiel" est mis en avant dans l'interface. */
    private boolean official = false;

    public ServerEntry() {
    }

    public ServerEntry(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getPort() {
        return port <= 0 ? 25565 : port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequiredVersion() {
        return requiredVersion == null ? "" : requiredVersion;
    }

    public void setRequiredVersion(String requiredVersion) {
        this.requiredVersion = requiredVersion;
    }

    public String getVersionId() {
        return versionId == null ? "" : versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getIconUrl() {
        return iconUrl == null ? "" : iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
    }

    /** Adresse complete, le port par defaut restant implicite. */
    public String fullAddress() {
        return getPort() == 25565 ? getAddress() : getAddress() + ":" + getPort();
    }

    @Override
    public String toString() {
        return getName() + " - " + fullAddress();
    }
}
