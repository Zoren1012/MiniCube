package com.minicube.launcher.model;

/**
 * Version du jeu detectee dans {@code .minecraft/versions}.
 *
 * @param id          identifiant du dossier, par exemple 1.20.4 ou fabric-loader-1.20.4
 * @param type        release, snapshot, modded...
 * @param releaseTime date de publication au format ISO (peut etre vide)
 * @param loader      chargeur de mods detecte : Vanilla, Forge, Fabric, Quilt, NeoForge
 * @param complete    vrai si le descripteur JSON et le jar client sont presents
 */
public record InstalledVersion(String id, String type, String releaseTime, String loader,
                               boolean complete) implements Comparable<InstalledVersion> {

    /** Libelle affiche dans les listes deroulantes. */
    public String displayName() {
        return "Vanilla".equals(loader) ? id : id + "  [" + loader + "]";
    }

    /** Tri antechronologique : les versions les plus recentes en premier. */
    @Override
    public int compareTo(InstalledVersion other) {
        int byDate = other.releaseTime.compareTo(releaseTime);
        return byDate != 0 ? byDate : id.compareToIgnoreCase(other.id);
    }
}
