package fr.minicube.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pont vers le launcher MiniCube installe sur la meme machine.
 *
 * <p>Le mod n'a aucun moyen de savoir sur quel serveur joue la communaute : le demander
 * a chaque joueur dans un fichier de configuration reviendrait a ne jamais afficher le
 * bouton, personne n'allant l'editer. Le launcher, lui, connait deja cette adresse.</p>
 *
 * <p>Lecture seule, sur un fichier du meme utilisateur : rien n'est ecrit dans les
 * donnees du launcher, et l'absence de MiniCube n'est pas une erreur — le mod fonctionne
 * simplement sans le bouton.</p>
 */
public final class MiniCubeLink {

    /** Serveur lu chez le launcher. */
    public record Server(String name, String address) {
    }

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("MiniCube HUD");

    private MiniCubeLink() {
    }

    /** Dossier de travail du launcher, a cote de celui du jeu. */
    private static Path launcherDir() {
        return Path.of(System.getProperty("user.home"), ".minicube");
    }

    /**
     * Premier serveur enregistre dans le launcher, s'il y en a un.
     *
     * <p>Le port n'est ajoute que s'il differe du port par defaut : "play.exemple.fr"
     * se lit mieux que "play.exemple.fr:25565", et Minecraft les traite pareil.</p>
     */
    public static Server firstServer() {
        Path file = launcherDir().resolve("custom-servers.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonArray()) {
                return null;
            }
            JsonArray servers = parsed.getAsJsonArray();
            for (JsonElement element : servers) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject server = element.getAsJsonObject();
                String address = optional(server, "address");
                if (address.isBlank()) {
                    continue;
                }
                int port = server.has("port") ? server.get("port").getAsInt() : 25565;
                String full = port == 25565 || port <= 0 ? address : address + ":" + port;
                return new Server(optional(server, "name"), full);
            }
        } catch (Exception e) {
            LOGGER.debug("Serveurs du launcher illisibles : {}", e.getMessage());
        }
        return null;
    }

    private static String optional(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull()
                ? object.get(field).getAsString() : "";
    }
}
