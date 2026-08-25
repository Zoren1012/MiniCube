package com.minicube.launcher.util;

import com.minicube.launcher.core.Constants;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Client HTTP unique du launcher, base sur java.net.http (aucune dependance externe).
 *
 * <p>Fournit les operations utilisees partout : GET texte/JSON, POST JSON, POST formulaire,
 * envoi multipart et telechargement de fichier avec rapport de progression.</p>
 */
public final class Http {

    /** Levee lorsqu'un serveur repond avec un code superieur ou egal a 400. */
    public static class HttpStatusException extends IOException {
        private final int status;
        private final String body;

        public HttpStatusException(int status, String body, String url) {
            super("HTTP " + status + " sur " + url
                    + (body == null || body.isBlank() ? "" : " : " + body));
            this.status = status;
            this.body = body;
        }

        public int status() {
            return status;
        }

        public String body() {
            return body;
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Constants.NETWORK_TIMEOUT_SECONDS))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    public static HttpClient client() {
        return CLIENT;
    }

    private static HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Constants.NETWORK_TIMEOUT_SECONDS))
                .header("User-Agent", Constants.USER_AGENT);
    }

    /** GET renvoyant le corps en texte. */
    public static String getString(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = base(url).GET();
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return send(builder.build(), url);
    }

    public static String getString(String url) throws IOException {
        return getString(url, null);
    }

    /** GET renvoyant un objet JSON. */
    public static JsonObject getJson(String url, Map<String, String> headers) throws IOException {
        return Json.parseObject(getString(url, headers));
    }

    public static JsonObject getJson(String url) throws IOException {
        return getJson(url, null);
    }

    /** POST d'un corps JSON, reponse attendue en JSON. */
    public static JsonObject postJson(String url, Object body, Map<String, String> headers)
            throws IOException {
        String payload = Json.COMPACT.toJson(body);
        HttpRequest.Builder builder = base(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return Json.parseObject(send(builder.build(), url));
    }

    /** POST au format application/x-www-form-urlencoded (flux OAuth Microsoft). */
    public static JsonObject postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(java.net.URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = base(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded.toString(),
                        StandardCharsets.UTF_8))
                .build();
        return Json.parseObject(sendAllowingErrors(request));
    }

    /** PUT d'un corps JSON. */
    public static String putJson(String url, Object body, Map<String, String> headers)
            throws IOException {
        String payload = Json.COMPACT.toJson(body);
        HttpRequest.Builder builder = base(url)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return send(builder.build(), url);
    }

    /** DELETE simple (desactivation d'une cape par exemple). */
    public static String delete(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder builder = base(url).DELETE();
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return send(builder.build(), url);
    }

    /**
     * POST multipart/form-data, utilise pour l'envoi d'un skin PNG a l'API Mojang.
     *
     * @param fields  champs texte du formulaire (par exemple variant=classic)
     * @param fileKey nom du champ fichier (par exemple "file")
     * @param file    fichier a transmettre
     */
    public static String postMultipart(String url, Map<String, String> fields, String fileKey,
                                       Path file, Map<String, String> headers) throws IOException {
        String boundary = "MiniCubeBoundary" + System.nanoTime();
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        String dash = "--";
        String crlf = "\r\n";
        String quote = "\"";

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            buffer.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            buffer.write(("Content-Disposition: form-data; name=" + quote + entry.getKey() + quote
                    + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            buffer.write((entry.getValue() + crlf).getBytes(StandardCharsets.UTF_8));
        }
        buffer.write((dash + boundary + crlf).getBytes(StandardCharsets.UTF_8));
        buffer.write(("Content-Disposition: form-data; name=" + quote + fileKey + quote
                + "; filename=" + quote + file.getFileName() + quote + crlf)
                .getBytes(StandardCharsets.UTF_8));
        buffer.write(("Content-Type: image/png" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
        buffer.write(Files.readAllBytes(file));
        buffer.write(crlf.getBytes(StandardCharsets.UTF_8));
        buffer.write((dash + boundary + dash + crlf).getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder builder = base(url)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buffer.toByteArray()));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return send(builder.build(), url);
    }

    /**
     * Telecharge une ressource vers un fichier en signalant l'avancement.
     *
     * <p>Le contenu est ecrit dans un fichier temporaire puis deplace : un telechargement
     * interrompu ne laisse jamais de fichier corrompu dans le dossier du jeu.</p>
     *
     * @param onBytes rappel recevant le nombre d'octets recus a chaque bloc (peut etre null)
     */
    public static void download(String url, Path target, LongConsumer onBytes) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = base(url).GET().build();
        try {
            HttpResponse<InputStream> response =
                    CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new HttpStatusException(response.statusCode(), "", url);
            }
            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(temp)) {
                byte[] chunk = new byte[16384];
                int read;
                while ((read = in.read(chunk)) != -1) {
                    out.write(chunk, 0, read);
                    if (onBytes != null) {
                        onBytes.accept(read);
                    }
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Telechargement interrompu : " + url, e);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String send(HttpRequest request, String url) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new HttpStatusException(response.statusCode(), response.body(), url);
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requete interrompue : " + url, e);
        }
    }

    /**
     * Variante renvoyant le corps meme en cas d'erreur : le flux "device code" de Microsoft
     * utilise des reponses 400 porteuses d'information (authorization_pending).
     */
    private static String sendAllowingErrors(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Requete interrompue", e);
        }
    }
}
