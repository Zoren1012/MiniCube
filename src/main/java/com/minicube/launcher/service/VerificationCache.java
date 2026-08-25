package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoire des fichiers deja verifies, pour ne pas recalculer leur empreinte a chaque
 * lancement.
 *
 * <p>Une version recente du jeu compte plusieurs milliers de ressources. Calculer le
 * SHA-1 de chacune represente plusieurs secondes de lecture disque a chaque partie,
 * pour un resultat presque toujours identique : ces fichiers ne changent pas.</p>
 *
 * <p>Le cache retient, pour chaque fichier, sa taille, sa date de modification et
 * l'empreinte constatee. Tant que taille et date sont inchangees, l'empreinte est
 * reputee valable et la lecture est evitee. Un fichier retelecharge, modifie ou
 * tronque voit forcement l'une des deux changer, et repasse donc par un calcul
 * complet.</p>
 *
 * <p>Cette optimisation repose sur une hypothese : personne ne remplace un fichier par
 * un autre de taille identique en restaurant sa date de modification. Le bouton
 * <i>Verifier les fichiers</i> ignore volontairement le cache et recalcule tout, pour
 * offrir un controle qui ne repose sur aucune hypothese.</p>
 */
public class VerificationCache {

    /**
     * Ce qui a ete constate pour un fichier.
     *
     * @param size     taille en octets au moment du calcul
     * @param modified date de derniere modification, en millisecondes
     * @param sha1     empreinte constatee
     */
    public record Fingerprint(long size, long modified, String sha1) {
    }

    private final Map<String, Fingerprint> entries = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public VerificationCache() {
        load();
    }

    private static Path cacheFile() {
        return LauncherPaths.cacheDir().resolve("verified.json");
    }

    private void load() {
        Path file = cacheFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Map<String, Fingerprint> loaded = Json.read(file,
                    new TypeToken<Map<String, Fingerprint>>() { }.getType());
            if (loaded != null) {
                entries.putAll(loaded);
                Log.debug(entries.size() + " empreintes connues chargees");
            }
        } catch (Exception e) {
            // Un cache illisible n'est jamais bloquant : on repart d'une table vide.
            Log.debug("Cache d'empreintes illisible, il sera reconstruit : " + e.getMessage());
        }
    }

    /** Enregistre le cache si quelque chose a change depuis le dernier appel. */
    public void save() {
        if (!dirty) {
            return;
        }
        try {
            Json.write(cacheFile(), entries);
            dirty = false;
            Log.debug(entries.size() + " empreintes enregistrees");
        } catch (IOException e) {
            Log.debug("Enregistrement du cache d'empreintes impossible : " + e.getMessage());
        }
    }

    /**
     * Verifie un fichier, en evitant le calcul d'empreinte lorsque c'est possible.
     *
     * @param file         fichier a controler
     * @param expectedSha1 empreinte attendue ; vide desactive le controle de contenu et
     *                     seule la presence du fichier est verifiee
     * @param force        true pour recalculer l'empreinte meme si le cache repond
     * @return true si le fichier est present et conforme
     */
    public boolean verify(Path file, String expectedSha1, boolean force) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return true;
        }
        String key = file.toAbsolutePath().toString();
        long size;
        long modified;
        try {
            size = Files.size(file);
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return false;
        }

        if (!force) {
            Fingerprint known = entries.get(key);
            if (known != null && known.size() == size && known.modified() == modified) {
                return known.sha1().equalsIgnoreCase(expectedSha1);
            }
        }

        try {
            String actual = Hashing.sha1(file);
            entries.put(key, new Fingerprint(size, modified, actual));
            dirty = true;
            return actual.equalsIgnoreCase(expectedSha1);
        } catch (IOException e) {
            Log.debug("Empreinte incalculable pour " + file + " : " + e.getMessage());
            return false;
        }
    }

    /** Oublie ce qui est connu d'un fichier, apres un telechargement par exemple. */
    public void forget(Path file) {
        if (entries.remove(file.toAbsolutePath().toString()) != null) {
            dirty = true;
        }
    }

    /** Vide entierement le cache. */
    public void clear() {
        entries.clear();
        dirty = true;
        save();
    }

    /** Nombre d'empreintes memorisees. */
    public int size() {
        return entries.size();
    }
}
