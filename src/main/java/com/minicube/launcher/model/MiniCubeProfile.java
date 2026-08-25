package com.minicube.launcher.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Compte MiniCube : l'identite d'un joueur au sein de votre communaute.
 *
 * <p>A ne pas confondre avec un compte Minecraft. Celui-ci ne permet pas de rejoindre un
 * serveur en ligne : c'est le serveur du jeu qui verifie l'identite aupres de Mojang, et
 * aucun systeme tiers ne peut se substituer a lui. Un compte MiniCube sert a identifier
 * un joueur dans votre projet, lui attribuer un role, et suivre son usage du launcher.</p>
 *
 * <p>Le mot de passe n'est jamais conserve : seule son empreinte l'est, derivee avec
 * PBKDF2 et un sel propre a chaque compte.</p>
 */
public class MiniCubeProfile {

    /** Roles proposes. En local ils sont declaratifs ; un serveur les rendrait fiables. */
    public enum Role {
        MEMBRE("Membre"),
        VIP("VIP"),
        MODERATEUR("Moderateur"),
        ADMINISTRATEUR("Administrateur");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private String username = "";
    private String passwordHash = "";
    private String passwordSalt = "";
    private Role role = Role.MEMBRE;
    /** Couleur d'accent choisie par le joueur, en hexadecimal. */
    private String color = "#7C5CFF";
    private String createdAt = "";
    private String lastSeenAt = "";

    /* --- Statistiques d'usage, tenues localement ------------------------ */

    private int launchCount;
    private String lastVersion = "";
    private long totalPlayMinutes;
    private List<String> recentVersions = new ArrayList<>();

    public String getUsername() {
        return username == null ? "" : username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash == null ? "" : passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt == null ? "" : passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public Role getRole() {
        return role == null ? Role.MEMBRE : role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getColor() {
        return color == null || color.isBlank() ? "#7C5CFF" : color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getCreatedAt() {
        return createdAt == null ? "" : createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getLastSeenAt() {
        return lastSeenAt == null ? "" : lastSeenAt;
    }

    public void setLastSeenAt(String lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public int getLaunchCount() {
        return launchCount;
    }

    public void setLaunchCount(int launchCount) {
        this.launchCount = launchCount;
    }

    public String getLastVersion() {
        return lastVersion == null ? "" : lastVersion;
    }

    public void setLastVersion(String lastVersion) {
        this.lastVersion = lastVersion;
    }

    public long getTotalPlayMinutes() {
        return totalPlayMinutes;
    }

    public void setTotalPlayMinutes(long totalPlayMinutes) {
        this.totalPlayMinutes = totalPlayMinutes;
    }

    public List<String> getRecentVersions() {
        if (recentVersions == null) {
            recentVersions = new ArrayList<>();
        }
        return recentVersions;
    }

    public void setRecentVersions(List<String> recentVersions) {
        this.recentVersions = recentVersions;
    }

    /** Vrai si un compte a ete cree sur cette machine. */
    public boolean exists() {
        return !getUsername().isBlank() && !getPasswordHash().isBlank();
    }

    /** Temps de jeu cumule, en heures et minutes. */
    public String playTimeLabel() {
        long hours = totalPlayMinutes / 60;
        long minutes = totalPlayMinutes % 60;
        return hours > 0 ? hours + " h " + minutes + " min" : minutes + " min";
    }
}
