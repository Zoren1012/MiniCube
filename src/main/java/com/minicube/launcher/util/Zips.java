package com.minicube.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Manipulation d'archives ZIP : extraction des bibliotheques natives, inspection des
 * fichiers de mods (fabric.mod.json, mods.toml) et des packs de shaders.
 */
public final class Zips {

    private Zips() {
    }

    /**
     * Extrait une archive dans un repertoire.
     *
     * <p>Chaque entree est validee contre la traversee de repertoire ("zip slip") :
     * une archive malformee ne peut pas ecrire hors du dossier cible.</p>
     *
     * @param archive    archive source
     * @param targetDir  repertoire de destination (cree si besoin)
     * @param exclusions prefixes d'entrees a ignorer, par exemple META-INF/
     */
    public static void extract(Path archive, Path targetDir, Collection<String> exclusions)
            throws IOException {
        Files.createDirectories(targetDir);
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || isExcluded(entry.getName(), exclusions)) {
                    continue;
                }
                Path destination = normalizedTarget.resolve(entry.getName()).normalize();
                if (!destination.startsWith(normalizedTarget)) {
                    Log.warn("Entree ignoree (chemin hors dossier) : " + entry.getName());
                    continue;
                }
                Path parent = destination.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isExcluded(String name, Collection<String> exclusions) {
        if (exclusions == null) {
            return false;
        }
        for (String exclusion : exclusions) {
            if (name.startsWith(exclusion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lit une entree texte de l'archive.
     *
     * @return le contenu, ou null si l'entree n'existe pas
     */
    public static String readEntry(Path archive, String entryName) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.debug("Lecture de " + entryName + " impossible dans " + archive + " : "
                    + e.getMessage());
            return null;
        }
    }

    /** Extrait une entree unique vers un fichier ; renvoie false si l'entree est absente. */
    public static boolean extractEntry(Path archive, String entryName, Path target) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return false;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Log.debug("Extraction de " + entryName + " impossible : " + e.getMessage());
            return false;
        }
    }

    /** Liste les noms d'entrees de l'archive (liste vide si elle est illisible). */
    public static List<String> listEntries(Path archive) {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        } catch (IOException e) {
            Log.debug("Archive illisible " + archive + " : " + e.getMessage());
        }
        return names;
    }

    /** Verifie qu'un fichier est bien une archive ZIP exploitable. */
    public static boolean isValidZip(Path archive) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.size() >= 0;
        } catch (IOException e) {
            return false;
        }
    }
}
