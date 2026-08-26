package fr.minicube.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reglages du HUD, conserves dans {@code config/minicube-hud.json}.
 *
 * <p>Le fichier est ecrit indente : il est fait pour etre modifie a la main, sans que le
 * jeu soit lance. Un champ absent reprend sa valeur par defaut, ce qui permet d'ajouter
 * des reglages sans invalider les fichiers existants.</p>
 */
public class HudConfig {

    /** Coin de l'ecran ou se pose le panneau. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        /** Coin suivant, pour faire le tour a la touche dediee. */
        public Corner next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "minicube-hud.json";

    public boolean enabled = true;
    public Corner corner = Corner.TOP_LEFT;

    public boolean showCoordinates = true;
    public boolean showDirection = true;
    public boolean showFps = true;
    public boolean showPing = true;
    public boolean showTime = true;
    public boolean showServer = true;

    /** Marge entre le panneau et le bord de l ecran, en pixels. */
    public int margin = 6;

    /* --- Menu principal --------------------------------------------- */

    /** Signature MiniCube en bas du menu principal. */
    public boolean menuBranding = true;
    /** Bouton de connexion directe, ajoute sous ceux du jeu. */
    public boolean menuJoinButton = true;
    /** Nom de la communaute, affiche sous la signature. */
    public String communityName = "";
    /** Nom lisible du serveur, pour le bouton de connexion. */
    public String serverName = "";
    /** Adresse du serveur. Vide : le bouton ne s affiche pas. */
    public String serverAddress = "";

    /** Couleur du texte, en ARGB. Le violet de MiniCube sert d'accent. */
    public int textColor = 0xFFE8EAF5;
    public int accentColor = 0xFF9C86FF;
    /** Fond du panneau : volontairement translucide pour ne pas masquer le jeu. */
    public int backgroundColor = 0xA0101018;

    /* ------------------------------------------------------------------ */
    /* Lecture et ecriture                                                 */
    /* ------------------------------------------------------------------ */

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /**
     * Charge la configuration.
     *
     * <p>Un fichier illisible ne doit pas empecher de jouer : dans ce cas les valeurs par
     * defaut sont reprises et l'incident est signale dans le journal.</p>
     */
    public static HudConfig load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            HudConfig config = new HudConfig();
            config.save();
            return config;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            HudConfig config = GSON.fromJson(reader, HudConfig.class);
            return config == null ? new HudConfig() : config.sanitised();
        } catch (Exception e) {
            MiniCubeHudClient.LOGGER.warn("Configuration illisible, valeurs par defaut : {}",
                    e.getMessage());
            return new HudConfig();
        }
    }

    /** Corrige les valeurs aberrantes d'un fichier modifie a la main. */
    private HudConfig sanitised() {
        if (corner == null) {
            corner = Corner.TOP_LEFT;
        }
        margin = Math.max(0, Math.min(64, margin));
        return this;
    }

    public void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            MiniCubeHudClient.LOGGER.warn("Enregistrement de la configuration impossible : {}",
                    e.getMessage());
        }
    }
}
