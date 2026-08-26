package com.minicube.launcher.model;

/**
 * Ce que le launcher a observe du joueur, reduit a des nombres.
 *
 * <p>Les defis se lisent sur cet instantane et sur rien d'autre. Sans lui, chaque defi
 * irait interroger un service different — mods, shaders, profils — et deviendrait
 * impossible a verifier sans lancer toute l'application.</p>
 *
 * @param sessions       parties terminees
 * @param playMinutes    minutes de jeu comptabilisees
 * @param distinctLoaders nombre de chargeurs differents utilises (vanilla compris)
 * @param moddedSessions parties lancees sur une version moddee
 * @param ownedLooks     habillages debloques, hors habillages offerts
 */
public record PlayerStats(int sessions, long playMinutes, int distinctLoaders,
                          int moddedSessions, int ownedLooks) {

    /** Instantane vide, pour un joueur qui vient d'installer le launcher. */
    public static PlayerStats empty() {
        return new PlayerStats(0, 0, 0, 0, 0);
    }
}
