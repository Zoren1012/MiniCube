package com.minicube.launcher.model;

/**
 * Resultat d'une interrogation de serveur via le protocole "Server List Ping".
 *
 * @param online        vrai si le serveur a repondu
 * @param motd          message du jour, codes couleur retires
 * @param onlinePlayers joueurs connectes
 * @param maxPlayers    capacite annoncee
 * @param version       version annoncee par le serveur
 * @param pingMillis    latence mesuree en millisecondes (-1 si inconnue)
 * @param faviconBase64 icone du serveur encodee en base64 (peut etre null)
 * @param error         message d'erreur si l'interrogation a echoue
 */
public record ServerStatus(boolean online, String motd, int onlinePlayers, int maxPlayers,
                           String version, long pingMillis, String faviconBase64, String error) {

    /** Etat renvoye lorsque le serveur est injoignable. */
    public static ServerStatus offline(String error) {
        return new ServerStatus(false, "", 0, 0, "", -1, null, error);
    }

    /** Etat initial affiche pendant la mesure. */
    public static ServerStatus pending() {
        return new ServerStatus(false, "", 0, 0, "", -1, null, null);
    }

    /** Rendu "12 / 100" pour l'interface. */
    public String playersLabel() {
        return online ? onlinePlayers + " / " + maxPlayers : "-";
    }

    /** Rendu "38 ms" pour l'interface. */
    public String pingLabel() {
        return pingMillis < 0 ? "-" : pingMillis + " ms";
    }

    /**
     * Qualite de la latence, utilisee pour colorer l'indicateur :
     * 0 = excellente, 1 = correcte, 2 = mediocre, 3 = hors ligne.
     */
    public int pingQuality() {
        if (!online || pingMillis < 0) {
            return 3;
        }
        if (pingMillis < 80) {
            return 0;
        }
        return pingMillis < 200 ? 1 : 2;
    }
}
