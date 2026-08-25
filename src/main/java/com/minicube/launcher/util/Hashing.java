package com.minicube.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Empreintes cryptographiques : verification d'integrite des fichiers du jeu et
 * generation des UUID hors-ligne.
 */
public final class Hashing {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Hashing() {
    }

    /** Empreinte SHA-1 d'un fichier, en hexadecimal minuscule. */
    public static String sha1(Path file) throws IOException {
        return digest(file, "SHA-1");
    }

    /** Empreinte SHA-256 d'un fichier, en hexadecimal minuscule. */
    public static String sha256(Path file) throws IOException {
        return digest(file, "SHA-256");
    }

    private static String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[8192];
            try (InputStream in = Files.newInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " indisponible sur cette JVM", e);
        }
    }

    /**
     * Compare l'empreinte SHA-1 attendue d'un fichier a sa valeur reelle.
     *
     * @param file         fichier a controler
     * @param expectedSha1 empreinte attendue ; null ou vide desactive le controle et
     *                     seule l'existence du fichier est verifiee
     * @return true si le fichier est present et conforme
     */
    public static boolean verify(Path file, String expectedSha1) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return true;
        }
        try {
            return sha1(file).equalsIgnoreCase(expectedSha1);
        } catch (IOException e) {
            Log.debug("Verification impossible pour " + file + " : " + e.getMessage());
            return false;
        }
    }

    /**
     * UUID hors-ligne, calcule comme le fait le serveur Minecraft : MD5 (UUID version 3)
     * de la chaine "OfflinePlayer:pseudo".
     *
     * <p>Garantit qu'un meme pseudo retrouve son inventaire d'une session a l'autre
     * sur un serveur en mode hors-ligne.</p>
     */
    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    /** UUID sans tirets, format attendu par les arguments de lancement du jeu. */
    public static String undashed(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /** Convertit un UUID sans tirets renvoye par l'API Mojang en UUID Java. */
    public static UUID fromUndashed(String value) {
        String raw = value.replace("-", "");
        if (raw.length() != 32) {
            throw new IllegalArgumentException("UUID invalide : " + value);
        }
        return UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20));
    }

    /** Conversion binaire vers hexadecimal minuscule. */
    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }
}
