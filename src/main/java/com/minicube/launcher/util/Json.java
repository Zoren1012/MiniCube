package com.minicube.launcher.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enveloppe Gson : lecture/ecriture atomique de fichiers JSON et accesseurs surs
 * pour naviguer dans les descripteurs de version Mojang (souvent incomplets).
 */
public final class Json {

    /** Instance partagee, sortie indentee pour que les fichiers restent lisibles. */
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** Instance compacte pour les corps de requetes HTTP. */
    public static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    /** Lit un fichier JSON et le convertit dans le type demande. */
    public static <T> T read(Path file, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(reader, type);
            if (value == null) {
                throw new IOException("Fichier JSON vide : " + file);
            }
            return value;
        }
    }

    /** Lit un fichier JSON generique (listes, types parametres). */
    public static <T> T read(Path file, Type type) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            T value = GSON.fromJson(reader, type);
            if (value == null) {
                throw new IOException("Fichier JSON vide : " + file);
            }
            return value;
        }
    }

    /** Lit un fichier JSON en objet brut. */
    public static JsonObject readObject(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Objet JSON attendu dans " + file);
            }
            return element.getAsJsonObject();
        }
    }

    /** Analyse une chaine en objet JSON. */
    public static JsonObject parseObject(String json) {
        JsonElement element = JsonParser.parseString(json);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Reponse JSON inattendue : " + abbreviate(json));
        }
        return element.getAsJsonObject();
    }

    /**
     * Ecrit un objet en JSON de maniere atomique : ecriture dans un fichier temporaire
     * puis remplacement, afin de ne jamais laisser une configuration tronquee.
     */
    public static void write(Path file, Object value) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(value), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Accesseurs tolerants                                                */
    /* ------------------------------------------------------------------ */

    /** Chaine d'un champ, ou la valeur par defaut si absent ou nul. */
    public static String string(JsonObject object, String field, String fallback) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return fallback;
        }
        return object.get(field).getAsString();
    }

    public static int integer(JsonObject object, String field, int fallback) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(field).getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long longValue(JsonObject object, String field, long fallback) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(field).getAsLong();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static boolean bool(JsonObject object, String field, boolean fallback) {
        if (object == null || !object.has(field) || object.get(field).isJsonNull()) {
            return fallback;
        }
        return object.get(field).getAsBoolean();
    }

    /** Sous-objet, ou null s'il est absent. */
    public static JsonObject object(JsonObject parent, String field) {
        if (parent == null || !parent.has(field) || !parent.get(field).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(field);
    }

    /** Tableau, ou un tableau vide s'il est absent (evite les tests de nullite). */
    public static JsonArray array(JsonObject parent, String field) {
        if (parent == null || !parent.has(field) || !parent.get(field).isJsonArray()) {
            return new JsonArray();
        }
        return parent.getAsJsonArray(field);
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }
}
