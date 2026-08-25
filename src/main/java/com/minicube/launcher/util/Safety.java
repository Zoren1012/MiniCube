package com.minicube.launcher.util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Regles de securite appliquees a tout ce qui entre dans le launcher depuis
 * l'exterieur : adresses, telechargements, configuration distante, fichiers sensibles.
 *
 * <p>Le principe retenu est le refus par defaut. Une donnee venue du reseau n'est jamais
 * consideree comme fiable, meme lorsqu'elle provient d'une adresse configuree par
 * l'utilisateur : un serveur peut etre compromis, une adresse peut etre detournee.</p>
 */
public final class Safety {

    /** Levee lorsqu'une entree exterieure ne respecte pas les regles ci-dessous. */
    public static class UnsafeInputException extends IOException {
        public UnsafeInputException(String message) {
            super(message);
        }
    }

    private Safety() {
    }

    /* ------------------------------------------------------------------ */
    /* Adresses                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Verifie qu'une adresse est un lien web ordinaire.
     *
     * <p>Seuls http et https sont acceptes. Sans ce controle, une actualite distante
     * pourrait fournir un lien {@code file:} ou un protocole enregistre par une autre
     * application, et le launcher le remettrait au systeme sans se poser de question.</p>
     */
    public static boolean isWebLink(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String scheme = URI.create(url.trim()).getScheme();
            if (scheme == null) {
                return false;
            }
            String lower = scheme.toLowerCase(Locale.ROOT);
            return lower.equals("http") || lower.equals("https");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Ouvre un lien dans le navigateur, apres controle.
     *
     * @return false si le lien a ete refuse
     */
    public static boolean openWebLink(String url) {
        if (!isWebLink(url)) {
            Log.warn("Lien refuse (protocole non autorise) : " + url);
            return false;
        }
        OsUtil.openUrl(url.trim());
        return true;
    }

    /**
     * Exige une adresse chiffree pour tout contenu destine a etre installe ou execute.
     *
     * <p>Une exception est faite pour les adresses locales, afin de pouvoir developper
     * son propre service de distribution sans certificat.</p>
     *
     * @param url  adresse a controler
     * @param what description reprise dans le message d'erreur
     * @throws UnsafeInputException si l'adresse est absente ou non chiffree
     */
    public static void requireSecureUrl(String url, String what) throws UnsafeInputException {
        if (url == null || url.isBlank()) {
            throw new UnsafeInputException(what + " : aucune adresse fournie.");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new UnsafeInputException(what + " : adresse invalide.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) {
            return;
        }
        if (scheme.equals("http") && isLocalHost(uri.getHost())) {
            Log.warn(what + " : adresse locale non chiffree acceptee (" + uri.getHost() + ")");
            return;
        }
        throw new UnsafeInputException(what + " : seules les adresses https sont acceptees. "
                + "Un telechargement non chiffre peut etre remplace en chemin.");
    }

    private static boolean isLocalHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("localhost") || lower.equals("127.0.0.1") || lower.equals("::1");
    }

    /* ------------------------------------------------------------------ */
    /* Journalisation                                                      */
    /* ------------------------------------------------------------------ */

    /** Marqueurs annoncant la presence probable d'un secret sur la ligne. */
    private static final String[] SECRET_MARKERS = {
            "accessToken", "access_token", "refreshToken", "refresh_token",
            "identityToken", "Bearer", "--session"
    };

    /**
     * Masque les jetons d'une ligne avant qu'elle n'atteigne le journal.
     *
     * <p>La ligne de commande du jeu contient le jeton de session en clair. Les journaux
     * etant frequemment partages pour obtenir de l'aide, un jeton qui s'y trouve doit
     * etre tenu pour divulgue. Le masquage est fait ici, une bonne fois, plutot qu'a
     * chaque endroit qui journalise : un oubli redeviendrait une fuite.</p>
     *
     * @param message ligne d'origine
     * @return la ligne assainie, ou l'originale si elle ne contenait aucun marqueur
     */
    public static String redact(String message) {
        if (message == null || message.length() < 12) {
            return message;
        }
        boolean suspect = false;
        for (String marker : SECRET_MARKERS) {
            if (message.contains(marker)) {
                suspect = true;
                break;
            }
        }
        if (!suspect) {
            return message;
        }
        String cleaned = message;
        for (String marker : SECRET_MARKERS) {
            cleaned = maskAfter(cleaned, marker);
        }
        return cleaned;
    }

    /**
     * Remplace la valeur qui suit un marqueur par un substitut.
     *
     * <p>Le nom de l'option est conserve : il reste utile pour diagnostiquer, alors que
     * sa valeur ne doit jamais etre lisible.</p>
     */
    private static String maskAfter(String input, String marker) {
        StringBuilder builder = new StringBuilder(input.length());
        int cursor = 0;
        while (true) {
            int found = input.indexOf(marker, cursor);
            if (found < 0) {
                builder.append(input, cursor, input.length());
                return builder.toString();
            }
            int valueStart = found + marker.length();
            while (valueStart < input.length() && isSeparator(input.charAt(valueStart))) {
                valueStart++;
            }
            int valueEnd = valueStart;
            while (valueEnd < input.length() && !isBoundary(input.charAt(valueEnd))) {
                valueEnd++;
            }
            // Une valeur courte est un libelle, pas un jeton : on la laisse lisible.
            if (valueEnd - valueStart < 8) {
                builder.append(input, cursor, valueEnd);
                cursor = valueEnd;
                continue;
            }
            builder.append(input, cursor, valueStart).append("[masque]");
            cursor = valueEnd;
        }
    }

    private static boolean isSeparator(char character) {
        return character == ' ' || character == '=' || character == '"' || character == ':';
    }

    private static boolean isBoundary(char character) {
        return Character.isWhitespace(character) || character == '"' || character == ',';
    }

    /* ------------------------------------------------------------------ */
    /* Fichiers sensibles                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Reserve un fichier au seul compte qui l'a cree.
     *
     * <p>Le fichier des comptes contient des jetons de session : sur une machine
     * partagee, un autre utilisateur pourrait autrement le lire. Sous Windows la
     * restriction passe par une liste de controle d'acces reduite au proprietaire ;
     * ailleurs par les permissions POSIX. L'echec n'est pas bloquant, mais il est
     * journalise : mieux vaut un avertissement qu'une fausse impression de securite.</p>
     */
    public static void restrictToOwner(Path file) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            PosixFileAttributeView posix =
                    Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (posix != null) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                return;
            }
            AclFileAttributeView acl =
                    Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl == null) {
                Log.debug("Systeme de fichiers sans controle d'acces : " + file);
                return;
            }
            UserPrincipal owner = Files.getOwner(file);
            AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build();
            // Une liste reduite a cette seule entree remplace les droits herites,
            // y compris ceux des autres utilisateurs de la machine.
            acl.setAcl(List.of(entry));
            Log.debug("Acces restreint au proprietaire : " + file.getFileName());
        } catch (IOException | UnsupportedOperationException e) {
            Log.warn("Restriction d'acces impossible sur " + file.getFileName()
                    + " : " + e.getMessage());
        }
    }
}
