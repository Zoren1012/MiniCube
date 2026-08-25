package com.minicube.launcher.service;

import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.model.ServerStatus;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Interrogation d'un serveur Minecraft via le protocole "Server List Ping" (1.7 et
 * suivantes), sans dependance externe.
 *
 * <p>Deroulement : poignee de main, requete de statut, lecture du JSON decrivant le
 * serveur, puis echange ping/pong pour mesurer la latence reelle.</p>
 */
public class ServerPingService {

    private static final int PROTOCOL_UNKNOWN = -1;
    private static final int DEFAULT_TIMEOUT_MS = 4000;

    /**
     * Interroge un serveur.
     *
     * <p>Methode bloquante : a appeler depuis un thread de fond.</p>
     *
     * @param server serveur a contacter
     * @return l'etat du serveur, jamais null (etat hors ligne en cas d'echec)
     */
    public ServerStatus ping(ServerEntry server) {
        return ping(server.getAddress(), server.getPort(), DEFAULT_TIMEOUT_MS);
    }

    /** Variante avec adresse explicite et delai personnalisable. */
    public ServerStatus ping(String host, int port, int timeoutMillis) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMillis);
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            sendHandshake(out, host, port);
            sendStatusRequest(out);

            String payload = readStatusResponse(in);
            long latency = measureLatency(out, in, start);

            return parseStatus(payload, latency);
        } catch (IOException e) {
            Log.debug("Ping de " + host + ":" + port + " echoue : " + e.getMessage());
            return ServerStatus.offline(describeError(e));
        } catch (Exception e) {
            Log.debug("Reponse inattendue de " + host + ":" + port + " : " + e.getMessage());
            return ServerStatus.offline("Reponse invalide du serveur");
        }
    }

    private String describeError(IOException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (e instanceof java.net.UnknownHostException) {
            return "Adresse introuvable";
        }
        if (e instanceof java.net.SocketTimeoutException || message.contains("timed out")) {
            return "Delai depasse";
        }
        if (message.contains("refused")) {
            return "Connexion refusee";
        }
        return "Serveur injoignable";
    }

    /* ------------------------------------------------------------------ */
    /* Trames du protocole                                                 */
    /* ------------------------------------------------------------------ */

    /** Paquet 0x00 en etat "handshaking", avec passage a l'etat "status". */
    private void sendHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream packet = new DataOutputStream(buffer);

        writeVarInt(packet, 0x00);
        writeVarInt(packet, PROTOCOL_UNKNOWN);
        writeString(packet, host);
        packet.writeShort(port);
        writeVarInt(packet, 1);

        writePacket(out, buffer.toByteArray());
    }

    /** Paquet 0x00 en etat "status" : demande des informations du serveur. */
    private void sendStatusRequest(DataOutputStream out) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        writeVarInt(new DataOutputStream(buffer), 0x00);
        writePacket(out, buffer.toByteArray());
    }

    /** Lit la reponse JSON du serveur. */
    private String readStatusResponse(DataInputStream in) throws IOException {
        readVarInt(in);
        int packetId = readVarInt(in);
        if (packetId != 0x00) {
            throw new IOException("Paquet de statut inattendu : " + packetId);
        }
        return readString(in);
    }

    /**
     * Envoie un paquet ping et attend le pong pour mesurer la latence.
     *
     * @param start instant du debut de la connexion, utilise comme repli
     * @return la latence en millisecondes
     */
    private long measureLatency(DataOutputStream out, DataInputStream in, long start) {
        try {
            long token = System.currentTimeMillis();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream packet = new DataOutputStream(buffer);
            writeVarInt(packet, 0x01);
            packet.writeLong(token);
            writePacket(out, buffer.toByteArray());

            long sentAt = System.currentTimeMillis();
            readVarInt(in);
            int packetId = readVarInt(in);
            if (packetId == 0x01) {
                in.readLong();
                return System.currentTimeMillis() - sentAt;
            }
        } catch (IOException e) {
            Log.debug("Mesure de latence indisponible : " + e.getMessage());
        }
        // Certains serveurs ne repondent pas au pong : on utilise le temps total.
        return System.currentTimeMillis() - start;
    }

    private void writePacket(DataOutputStream out, byte[] data) throws IOException {
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    /* ------------------------------------------------------------------ */
    /* Analyse de la reponse                                               */
    /* ------------------------------------------------------------------ */

    /** Transforme le JSON du serveur en {@link ServerStatus}. */
    private ServerStatus parseStatus(String payload, long latency) {
        JsonObject root = Json.parseObject(payload);

        JsonObject players = Json.object(root, "players");
        int online = players == null ? 0 : Json.integer(players, "online", 0);
        int max = players == null ? 0 : Json.integer(players, "max", 0);

        JsonObject version = Json.object(root, "version");
        String versionName = version == null ? "" : Json.string(version, "name", "");

        String motd = flattenDescription(root.get("description"));
        String favicon = Json.string(root, "favicon", null);
        if (favicon != null && favicon.startsWith("data:image/png;base64,")) {
            favicon = favicon.substring("data:image/png;base64,".length());
        }
        return new ServerStatus(true, motd, online, max, versionName, latency, favicon, null);
    }

    /**
     * Aplatit une description de serveur en texte simple.
     *
     * <p>Le champ accepte trois formes : une chaine, un composant {@code {"text": ...}}
     * avec un tableau {@code extra}, ou une combinaison des deux. Les codes couleur
     * (section suivie d'un caractere) sont retires.</p>
     */
    public static String flattenDescription(JsonElement description) {
        if (description == null || description.isJsonNull()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendComponent(description, builder);
        return stripColorCodes(builder.toString()).trim();
    }

    private static void appendComponent(JsonElement element, StringBuilder builder) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive()) {
            builder.append(element.getAsString());
            return;
        }
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> appendComponent(child, builder));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject component = element.getAsJsonObject();
        if (component.has("text")) {
            builder.append(Json.string(component, "text", ""));
        }
        if (component.has("extra")) {
            appendComponent(component.get("extra"), builder);
        }
    }

    /** Retire les codes de formatage Minecraft de la forme section + caractere. */
    public static String stripColorCodes(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\u00A7[0-9a-fk-orA-FK-OR]", "");
    }

    /* ------------------------------------------------------------------ */
    /* Primitives VarInt                                                   */
    /* ------------------------------------------------------------------ */

    /** Ecrit un entier au format VarInt utilise par le protocole Minecraft. */
    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        int remaining = value;
        while (true) {
            if ((remaining & 0xFFFFFF80) == 0) {
                out.writeByte(remaining);
                return;
            }
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    /** Lit un VarInt ; leve une exception si l'encodage depasse cinq octets. */
    static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int position = 0;
        while (true) {
            byte current = in.readByte();
            result |= (current & 0x7F) << (position * 7);
            if ((current & 0x80) == 0) {
                return result;
            }
            position++;
            if (position >= 5) {
                throw new IOException("VarInt trop long");
            }
        }
    }

    /** Ecrit une chaine UTF-8 precedee de sa longueur en VarInt. */
    static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /** Lit une chaine UTF-8 precedee de sa longueur en VarInt. */
    static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 1 << 21) {
            throw new IOException("Longueur de chaine invalide : " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
