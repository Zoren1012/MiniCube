package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fourniture de la liste des serveurs affichee dans l'onglet Serveurs.
 *
 * <p>Trois sources sont combinees : la liste embarquee dans le jar
 * ({@code config/servers.json}), une liste distante optionnelle configuree par
 * {@code serversUrl}, et les serveurs ajoutes manuellement par l'utilisateur.</p>
 */
public class ServerListService {

    private static final String BUNDLED_RESOURCE = "/config/servers.json";

    private final ConfigService config;

    public ServerListService(ConfigService config) {
        this.config = config;
    }

    /**
     * Charge la liste complete des serveurs.
     *
     * <p>Methode bloquante lorsqu'une URL distante est configuree : a appeler depuis un
     * thread de fond. En cas d'echec reseau, la liste embarquee prend le relais afin que
     * l'onglet reste utilisable hors connexion.</p>
     */
    public List<ServerEntry> loadAll() {
        List<ServerEntry> servers = new ArrayList<>(loadProjectServers());
        servers.addAll(loadCustomServers());
        return servers;
    }

    /** Serveurs du projet : liste distante si disponible, sinon liste embarquee. */
    private List<ServerEntry> loadProjectServers() {
        String url = config.settings().getServersUrl();
        if (!url.isBlank()) {
            try {
                JsonObject payload = Http.getJson(url);
                List<ServerEntry> remote = Json.GSON.fromJson(Json.array(payload, "servers"),
                        new TypeToken<List<ServerEntry>>() { }.getType());
                if (remote != null && !remote.isEmpty()) {
                    Log.info(remote.size() + " serveur(s) charge(s) depuis " + url);
                    return remote;
                }
            } catch (IOException e) {
                Log.warn("Liste de serveurs distante indisponible (" + e.getMessage()
                        + "), utilisation de la liste embarquee");
            } catch (Exception e) {
                Log.warn("Liste de serveurs distante invalide : " + e.getMessage());
            }
        }
        return loadBundled();
    }

    /** Liste livree avec le launcher. */
    private List<ServerEntry> loadBundled() {
        try (InputStream in = getClass().getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                return new ArrayList<>();
            }
            JsonObject payload = Json.GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            List<ServerEntry> servers = Json.GSON.fromJson(Json.array(payload, "servers"),
                    new TypeToken<List<ServerEntry>>() { }.getType());
            return servers == null ? new ArrayList<>() : servers;
        } catch (Exception e) {
            Log.warn("Liste de serveurs embarquee illisible : " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Serveurs personnels                                                 */
    /* ------------------------------------------------------------------ */

    private Path customServersFile() {
        return LauncherPaths.launcherDir().resolve("custom-servers.json");
    }

    /** Serveurs ajoutes par l'utilisateur depuis l'interface. */
    public List<ServerEntry> loadCustomServers() {
        Path file = customServersFile();
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            List<ServerEntry> servers = Json.read(file,
                    new TypeToken<List<ServerEntry>>() { }.getType());
            return servers == null ? new ArrayList<>() : servers;
        } catch (Exception e) {
            Log.warn("Serveurs personnels illisibles : " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Ajoute un serveur personnel et l'enregistre. */
    public void addCustomServer(ServerEntry server) {
        List<ServerEntry> servers = loadCustomServers();
        servers.removeIf(existing -> existing.getAddress().equalsIgnoreCase(server.getAddress())
                && existing.getPort() == server.getPort());
        servers.add(server);
        saveCustomServers(servers);
        Log.info("Serveur ajoute : " + server.fullAddress());
    }

    /** Supprime un serveur personnel. */
    public void removeCustomServer(ServerEntry server) {
        List<ServerEntry> servers = loadCustomServers();
        servers.removeIf(existing -> existing.getAddress().equalsIgnoreCase(server.getAddress())
                && existing.getPort() == server.getPort());
        saveCustomServers(servers);
    }

    private void saveCustomServers(List<ServerEntry> servers) {
        try {
            Json.write(customServersFile(), servers);
        } catch (IOException e) {
            Log.error("Enregistrement des serveurs personnels impossible : " + e.getMessage());
        }
    }
}
