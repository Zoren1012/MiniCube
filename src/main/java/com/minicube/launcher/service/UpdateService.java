package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.minicube.launcher.util.Safety;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mise a jour automatique du launcher.
 *
 * <p>Le launcher interroge un descripteur JSON dont l'adresse est configurable
 * ({@code updateUrl}) :</p>
 * <pre>
 * { "version": "1.1.0",
 *   "url": "https://exemple.fr/MiniCube-1.1.0.jar",
 *   "sha1": "...",
 *   "mandatory": false,
 *   "changelog": "Correction du lancement des versions Forge" }
 * </pre>
 */
public class UpdateService {

    /**
     * Description d'une mise a jour disponible.
     *
     * @param version   numero de la nouvelle version
     * @param url       adresse du jar a telecharger
     * @param sha1      empreinte attendue (facultative)
     * @param changelog resume des nouveautes
     * @param mandatory true si la mise a jour doit etre installee avant de jouer
     */
    public record UpdateInfo(String version, String url, String sha1, String changelog,
                             boolean mandatory) {
    }

    private final ConfigService config;

    public UpdateService(ConfigService config) {
        this.config = config;
    }

    /**
     * Verifie la disponibilite d'une nouvelle version.
     *
     * <p>Methode bloquante : a appeler depuis un thread de fond. Toute erreur est
     * absorbee, une verification de mise a jour ne doit jamais empecher de jouer.</p>
     *
     * @return la mise a jour disponible, ou un optional vide
     */
    public Optional<UpdateInfo> checkForUpdate() {
        String url = config.settings().getUpdateUrl();
        if (url.isBlank() || !config.settings().isAutoUpdateLauncher()) {
            return Optional.empty();
        }
        try {
            JsonObject payload = Http.getJson(url);
            String remoteVersion = Json.string(payload, "version", "");
            if (remoteVersion.isBlank()
                    || compareVersions(remoteVersion, Constants.APP_VERSION) <= 0) {
                Log.debug("Le launcher est a jour (" + Constants.APP_VERSION + ")");
                return Optional.empty();
            }
            UpdateInfo info = new UpdateInfo(
                    remoteVersion,
                    Json.string(payload, "url", ""),
                    Json.string(payload, "sha1", ""),
                    Json.string(payload, "changelog", ""),
                    Json.bool(payload, "mandatory", false));
            Log.info("Mise a jour disponible : " + remoteVersion);
            return Optional.of(info);
        } catch (Exception e) {
            Log.warn("Verification des mises a jour impossible : " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Telecharge le paquet de mise a jour dans {@code ~/.minicube/updates}.
     *
     * <p>Deux exigences sont posees avant tout telechargement, parce que ce fichier
     * sera <b>execute</b> : l'adresse doit etre chiffree, et une empreinte SHA-1 doit
     * accompagner la mise a jour. Sans elles, quiconque parviendrait a se placer entre
     * le launcher et le serveur, ou a prendre la main sur ce serveur, ferait executer
     * le code de son choix sur toutes les machines equipees.</p>
     *
     * @return le fichier telecharge, dont l'empreinte a ete verifiee
     * @throws Safety.UnsafeInputException si l'adresse ou l'empreinte manquent aux regles
     */
    public Path download(UpdateInfo info, Consumer<Progress> onProgress) throws IOException {
        Safety.requireSecureUrl(info.url(), "Mise a jour du launcher");
        if (info.sha1().isBlank()) {
            throw new Safety.UnsafeInputException("Mise a jour refusee : le descripteur ne "
                    + "fournit pas d'empreinte SHA-1. Un paquet execute sans verification "
                    + "d'integrite n'est pas acceptable.");
        }
        Files.createDirectories(LauncherPaths.updatesDir());
        Path target = LauncherPaths.updatesDir()
                .resolve("MiniCube-" + info.version() + ".jar");

        onProgress.accept(Progress.indeterminate("Telechargement de la version "
                + info.version() + "..."));
        long[] received = {0};
        Http.download(info.url(), target, bytes -> {
            received[0] += bytes;
            onProgress.accept(Progress.of("Telechargement de la mise a jour",
                    GameFileServiceSizeHelper.format(received[0]), -1));
        });

        if (!Hashing.verify(target, info.sha1())) {
            Files.deleteIfExists(target);
            throw new IOException("Empreinte incorrecte : mise a jour rejetee.");
        }
        onProgress.accept(Progress.done("Mise a jour telechargee."));
        Log.info("Mise a jour enregistree dans " + target);
        return target;
    }

    /**
     * Redemarre le launcher sur la nouvelle version.
     *
     * <p>Un nouveau processus Java est lance sur le jar telecharge, puis le processus
     * courant se termine. Le remplacement effectif du fichier est laisse au nouveau
     * processus, ce qui evite d'ecraser un jar en cours d'utilisation sous Windows.</p>
     */
    public void restartWith(Path newJar) throws IOException {
        Path java = OsUtil.currentJavaExecutable();
        List<String> command = List.of(java.toString(), "-jar", newJar.toAbsolutePath().toString());
        Log.info("Redemarrage sur " + newJar.getFileName());
        new ProcessBuilder(command).inheritIO().start();
        System.exit(0);
    }

    /**
     * Compare deux numeros de version de la forme {@code 1.2.3}.
     *
     * @return un entier negatif, nul ou positif selon que {@code left} est anterieure,
     *         egale ou posterieure a {@code right}
     */
    public static int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int a = parsePart(leftParts, i);
            int b = parsePart(rightParts, i);
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Petit utilitaire de formatage partage avec le telechargement des fichiers du jeu. */
    static final class GameFileServiceSizeHelper {
        private GameFileServiceSizeHelper() {
        }

        static String format(long bytes) {
            if (bytes < 1024) {
                return bytes + " o";
            }
            if (bytes < 1024 * 1024) {
                return String.format("%.0f Ko", bytes / 1024d);
            }
            return String.format("%.1f Mo", bytes / (1024d * 1024d));
        }
    }
}
