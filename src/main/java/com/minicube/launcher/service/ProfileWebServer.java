package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.model.MiniCubeProfile;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Petit serveur web local qui sert la page de compte MiniCube.
 *
 * <p>Il n'ecoute que sur <b>127.0.0.1</b>, jamais sur l'adresse de la machine : aucune
 * autre machine du reseau ne peut l'atteindre, meme sur un reseau partage. Le port est
 * attribue par le systeme afin de ne jamais entrer en conflit avec un autre programme.</p>
 *
 * <p>Les routes constituent un <b>contrat</b> que n'importe quel serveur distant peut
 * reprendre a l'identique. Passer d'une gestion locale a un site herbege ne demanderait
 * alors que de changer l'adresse, sans toucher au reste du launcher.</p>
 *
 * <pre>
 *   GET  /                page de connexion et de profil
 *   GET  /api/state       etat : compte existant, session ouverte, profil
 *   POST /api/register    creation du compte      { username, password }
 *   POST /api/login       ouverture de session    { username, password }
 *   POST /api/logout      fermeture de session
 *   POST /api/profile     mise a jour du profil   { role, color }
 *   POST /api/delete      suppression du compte   { password }
 * </pre>
 */
public class ProfileWebServer {

    /** Au-dela, une requete est refusee : la page n'envoie que quelques centaines d'octets. */
    private static final int MAX_BODY_BYTES = 8192;

    private final ProfileService profiles;
    private HttpServer server;
    private int port;

    public ProfileWebServer(ProfileService profiles) {
        this.profiles = profiles;
    }

    /**
     * Demarre le serveur s'il ne l'est pas deja.
     *
     * @return l'adresse a ouvrir dans le navigateur
     */
    public synchronized String start() throws IOException {
        if (server != null) {
            return url();
        }
        // Port 0 : le systeme en choisit un libre. Adresse de bouclage uniquement.
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/", this::servePage);
        server.createContext("/api/", this::serveApi);
        // Un seul thread suffit : une page, un utilisateur, quelques requetes.
        server.setExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "minicube-compte");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();

        Log.info("Page de compte disponible sur " + url());
        return url();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            Log.debug("Serveur de compte arrete");
        }
    }

    public String url() {
        return "http://127.0.0.1:" + port + "/";
    }

    public boolean isRunning() {
        return server != null;
    }

    /* ------------------------------------------------------------------ */
    /* Page                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Sert la page, ou le style et le script qui l'accompagnent.
     *
     * <p>Tous les chemins de sortie passent par le {@code finally} : un echange laisse
     * ouvert bloquerait la connexion, et donc toutes les requetes suivantes du
     * navigateur, qui reutilise la meme connexion.</p>
     */
    private void servePage(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String resource;
            String type;

            switch (path) {
                case "/", "/index.html" -> {
                    resource = "/web/compte.html";
                    type = "text/html; charset=utf-8";
                }
                case "/compte.css" -> {
                    resource = "/web/compte.css";
                    type = "text/css; charset=utf-8";
                }
                case "/compte.js" -> {
                    resource = "/web/compte.js";
                    type = "text/javascript; charset=utf-8";
                }
                default -> {
                    // Les navigateurs reclament /favicon.ico d'eux-memes : une reponse
                    // vide leur suffit et evite une erreur dans leur console.
                    send(exchange, 404, "text/plain; charset=utf-8", "Page introuvable");
                    return;
                }
            }
            try (InputStream in = getClass().getResourceAsStream(resource)) {
                if (in == null) {
                    send(exchange, 500, "text/plain; charset=utf-8",
                            "Ressource absente du launcher : " + resource);
                    return;
                }
                byte[] body = in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", type);
                // La page ne charge rien d'exterieur : la politique de securite du contenu
                // le formalise, et empeche toute injection de ressource distante.
                exchange.getResponseHeaders().add("Content-Security-Policy",
                        "default-src 'self'; img-src 'self' data:; base-uri 'none'");
                exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        } finally {
            exchange.close();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Interface de programmation                                          */
    /* ------------------------------------------------------------------ */

    private void serveApi(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            // Une page consultee ailleurs dans le navigateur peut viser cette adresse :
            // seule notre propre page a le droit d'agir sur le compte.
            if (!isOwnOrigin(exchange)) {
                sendJson(exchange, 403, error("Origine refusee."));
                return;
            }
            // La verification precede l'action : sans cela, une simple image pointant
            // vers /api/delete suffirait a supprimer le compte.
            boolean readOnly = "/api/state".equals(path);
            if (!(readOnly ? "GET".equals(method) : "POST".equals(method))) {
                sendJson(exchange, 405, error("Methode non autorisee."));
                return;
            }
            JsonObject response = switch (path) {
                case "/api/state" -> state();
                case "/api/register" -> register(readBody(exchange));
                case "/api/login" -> login(readBody(exchange));
                case "/api/logout" -> logout();
                case "/api/profile" -> updateProfile(readBody(exchange));
                case "/api/delete" -> deleteAccount(readBody(exchange));
                default -> null;
            };
            if (response == null) {
                sendJson(exchange, 404, error("Route inconnue."));
                return;
            }
            sendJson(exchange, response.get("ok").getAsBoolean() ? 200 : 400, response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            sendJson(exchange, 400, error(e.getMessage()));
        } catch (Exception e) {
            Log.warn("Erreur dans la page de compte : " + e.getMessage());
            sendJson(exchange, 500, error("Erreur interne."));
        } finally {
            exchange.close();
        }
    }

    /**
     * Verifie que la requete vient bien de notre page.
     *
     * <p>Le navigateur renseigne {@code Origin} des qu'une page en vise une autre. Une
     * origine absente correspond a un acces direct depuis la barre d'adresse ou depuis
     * le launcher lui-meme, ce qui reste legitime ; une origine differente de la notre
     * signifie qu'un autre site tente d'agir sur le compte, et est refusee.</p>
     */
    private boolean isOwnOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) {
            return true;
        }
        return origin.equals("http://127.0.0.1:" + port)
                || origin.equals("http://localhost:" + port);
    }

    /** Etat courant, envoye au chargement de la page et apres chaque action. */
    private JsonObject state() {
        MiniCubeProfile profile = profiles.profile();
        JsonObject payload = new JsonObject();
        payload.addProperty("ok", true);
        payload.addProperty("application", Constants.APP_NAME);
        payload.addProperty("version", Constants.APP_VERSION);
        payload.addProperty("hasAccount", profiles.hasAccount());
        payload.addProperty("signedIn", profiles.isSignedIn());

        if (profiles.hasAccount()) {
            JsonObject account = new JsonObject();
            account.addProperty("username", profile.getUsername());
            account.addProperty("role", profile.getRole().name());
            account.addProperty("roleLabel", profile.getRole().label());
            account.addProperty("color", profile.getColor());
            account.addProperty("createdAt", profile.getCreatedAt());
            account.addProperty("lastSeenAt", profile.getLastSeenAt());
            account.addProperty("launchCount", profile.getLaunchCount());
            account.addProperty("lastVersion", profile.getLastVersion());
            account.addProperty("playTime", profile.playTimeLabel());
            account.add("recentVersions", Json.GSON.toJsonTree(profile.getRecentVersions()));
            payload.add("account", account);
        }
        return payload;
    }

    private JsonObject register(JsonObject body) {
        profiles.register(Json.string(body, "username", ""),
                Json.string(body, "password", ""));
        return state();
    }

    private JsonObject login(JsonObject body) {
        boolean granted = profiles.signIn(Json.string(body, "username", ""),
                Json.string(body, "password", ""));
        if (!granted) {
            // Un message unique pour un pseudo inconnu comme pour un mot de passe faux :
            // distinguer les deux revelerait quels comptes existent.
            return error("Identifiants incorrects.");
        }
        return state();
    }

    private JsonObject logout() {
        profiles.signOut();
        return state();
    }

    private JsonObject updateProfile(JsonObject body) {
        if (!profiles.isSignedIn()) {
            return error("Session fermee : reconnectez-vous.");
        }
        MiniCubeProfile.Role role;
        try {
            role = MiniCubeProfile.Role.valueOf(Json.string(body, "role", "MEMBRE"));
        } catch (IllegalArgumentException e) {
            return error("Role inconnu.");
        }
        profiles.updateProfile(role, Json.string(body, "color", ""));
        return state();
    }

    private JsonObject deleteAccount(JsonObject body) {
        // La suppression redemande le mot de passe : c'est irreversible.
        if (!profiles.signIn(profiles.profile().getUsername(),
                Json.string(body, "password", ""))) {
            return error("Mot de passe incorrect.");
        }
        profiles.deleteAccount();
        return state();
    }

    /* ------------------------------------------------------------------ */
    /* Entrees et sorties                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Lit le corps de la requete.
     *
     * <p>La taille est plafonnee : la page n'envoie jamais plus de quelques centaines
     * d'octets, et rien ne doit pouvoir remplir la memoire du launcher.</p>
     */
    private JsonObject readBody(HttpExchange exchange) throws IOException {
        byte[] raw = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (raw.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Requete trop volumineuse.");
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return new JsonObject();
        }
        try {
            return Json.parseObject(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Requete illisible.");
        }
    }

    private JsonObject error(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("ok", false);
        payload.addProperty("error", message == null ? "Erreur inconnue." : message);
        return payload;
    }

    private void sendJson(HttpExchange exchange, int status, JsonObject payload)
            throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Cache-Control", "no-store");
        send(exchange, status, headers, Json.COMPACT.toJson(payload));
    }

    private void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        send(exchange, status, headers, body);
    }

    private void send(HttpExchange exchange, int status, Map<String, String> headers,
                      String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
