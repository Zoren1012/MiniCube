package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Sauvegarde des parametres utilisateur sur un service distant.
 *
 * <p>Le protocole attendu est volontairement minimal, afin de pouvoir etre implemente
 * par n'importe quel hebergeur :</p>
 * <ul>
 *   <li>{@code GET <cloudSyncUrl>} renvoie le document JSON precedemment sauvegarde ;</li>
 *   <li>{@code PUT <cloudSyncUrl>} enregistre le document envoye.</li>
 * </ul>
 *
 * <p>Un jeton porteur optionnel ({@code cloudSyncToken}) est ajoute a l'en-tete
 * {@code Authorization}. Les donnees propres a la machine (dossier de jeu, comptes)
 * ne sont jamais transmises : seul le confort d'usage est synchronise.</p>
 */
public class CloudSyncService {

    private final ConfigService config;

    public CloudSyncService(ConfigService config) {
        this.config = config;
    }

    /** Vrai si la synchronisation est activee et correctement configuree. */
    public boolean isConfigured() {
        LauncherSettings settings = config.settings();
        return settings.isCloudSyncEnabled() && !settings.getCloudSyncUrl().isBlank();
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", Constants.USER_AGENT);
        String token = config.settings().getCloudSyncToken();
        if (!token.isBlank()) {
            headers.put("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * Envoie les parametres courants vers le service distant.
     *
     * <p>Methode bloquante : a appeler depuis un thread de fond.</p>
     *
     * @throws IOException si la sauvegarde n'est pas configuree ou si le service refuse
     */
    public void push() throws IOException {
        if (!isConfigured()) {
            throw new IOException("La sauvegarde cloud n'est pas configuree.");
        }
        LauncherSettings payload = sanitizedCopy(config.settings());
        Http.putJson(config.settings().getCloudSyncUrl(), payload, headers());
        Log.info("Parametres sauvegardes sur le service distant");
    }

    /**
     * Recupere les parametres depuis le service distant et les applique.
     *
     * @return true si des parametres ont ete restaures
     */
    public boolean pull() throws IOException {
        if (!isConfigured()) {
            throw new IOException("La sauvegarde cloud n'est pas configuree.");
        }
        String body = Http.getString(config.settings().getCloudSyncUrl(), headers());
        if (body == null || body.isBlank()) {
            return false;
        }
        LauncherSettings restored = Json.GSON.fromJson(body, LauncherSettings.class);
        if (restored == null) {
            return false;
        }
        config.replace(restored);
        Log.info("Parametres restaures depuis le service distant");
        return true;
    }

    /**
     * Copie des parametres debarrassee de tout ce qui est propre a la machine ou
     * sensible : chemins locaux, jetons et identifiants de compte.
     */
    private LauncherSettings sanitizedCopy(LauncherSettings source) {
        LauncherSettings copy = Json.GSON.fromJson(Json.GSON.toJson(source),
                LauncherSettings.class);
        copy.setGameDirectory("");
        copy.setJavaPath("");
        copy.setActiveAccountUuid("");
        copy.setCloudSyncToken("");
        copy.setBackgroundImage("");
        return copy;
    }
}
