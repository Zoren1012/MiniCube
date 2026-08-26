package fr.minicube.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le panneau d'informations dessine par-dessus le jeu.
 *
 * <p>Minecraft separe desormais la <b>collecte</b> de ce qu'il faut dessiner et le dessin
 * lui-meme : cette classe soumet des rectangles et des textes a un extracteur, et le jeu
 * les rend ensuite. Elle ne doit donc rien faire de couteux — pas de lecture de fichier,
 * pas d'appel reseau — puisqu'elle est appelee a chaque image.</p>
 */
public class MiniCubeHudElement implements HudElement {

    /** Hauteur d'une ligne de texte, police vanilla comprise. */
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 6;
    /** Largeur du liseré d'accent colle au bord gauche du panneau. */
    private static final int ACCENT_WIDTH = 2;

    private final HudConfig config;

    public MiniCubeHudElement(HudConfig config) {
        this.config = config;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, DeltaTracker delta) {
        if (!config.enabled) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        // Pas de joueur : on est dans un menu, il n'y a rien a afficher. Le masquage de
        // l'interface par F1 est gere en amont : un element enregistre dans le HUD
        // disparait avec lui.
        if (player == null || client.level == null) {
            return;
        }

        List<String> lines = collectLines(client, player);
        if (lines.isEmpty()) {
            return;
        }
        draw(extractor, client.font, lines);
    }

    /* ------------------------------------------------------------------ */
    /* Contenu                                                             */
    /* ------------------------------------------------------------------ */

    /** Compose les lignes a afficher, dans l'ordre, selon ce qui est active. */
    private List<String> collectLines(Minecraft client, LocalPlayer player) {
        List<String> lines = new ArrayList<>(6);

        if (config.showCoordinates) {
            BlockPos position = player.blockPosition();
            lines.add(String.format(Locale.ROOT, "XYZ  %d  %d  %d",
                    position.getX(), position.getY(), position.getZ()));
        }
        if (config.showDirection) {
            lines.add("Face  " + facingLabel(player));
        }
        if (config.showFps) {
            lines.add("FPS  " + client.getFps());
        }
        if (config.showPing) {
            lines.add("Ping  " + pingLabel(client, player));
        }
        if (config.showTime) {
            lines.add("Heure  " + timeLabel(client));
        }
        if (config.showServer) {
            lines.add("Serveur  " + serverLabel(client));
        }
        return lines;
    }

    /**
     * Direction regardee, avec l'axe correspondant.
     *
     * <p>L'axe compte autant que le point cardinal : c'est lui qui dit dans quel sens la
     * coordonnee va evoluer quand on avance.</p>
     */
    private String facingLabel(LocalPlayer player) {
        return switch (player.getDirection()) {
            case NORTH -> "Nord  (-Z)";
            case SOUTH -> "Sud  (+Z)";
            case WEST -> "Ouest  (-X)";
            case EAST -> "Est  (+X)";
            default -> "-";
        };
    }

    /** Latence rapportee par le serveur, ou un tiret en solo. */
    private String pingLabel(Minecraft client, LocalPlayer player) {
        if (client.getConnection() == null || client.isLocalServer()) {
            return "-";
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(player.getUUID());
        return info == null ? "-" : info.getLatency() + " ms";
    }

    /**
     * Heure du jeu, ramenee au format d'une horloge.
     *
     * <p>Le jour de Minecraft compte 24 000 ticks et commence a six heures du matin :
     * c'est ce decalage qui rend l'heure affichee comparable a celle du soleil.</p>
     */
    private String timeLabel(Minecraft client) {
        long dayTime = client.level.getDefaultClockTime() % 24_000L;
        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = (dayTime % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private String serverLabel(Minecraft client) {
        if (client.isLocalServer()) {
            return "Solo";
        }
        var server = client.getCurrentServer();
        if (server == null) {
            return "-";
        }
        return server.name == null || server.name.isBlank() ? server.ip : server.name;
    }

    /* ------------------------------------------------------------------ */
    /* Dessin                                                              */
    /* ------------------------------------------------------------------ */

    /** Pose le panneau dans le coin choisi, sans jamais deborder de l'ecran. */
    private void draw(GuiGraphicsExtractor extractor, Font font, List<String> lines) {
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int width = textWidth + PADDING * 2 + ACCENT_WIDTH;
        int height = lines.size() * LINE_HEIGHT + PADDING * 2 - 2;

        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        int margin = config.margin;

        boolean right = config.corner == HudConfig.Corner.TOP_RIGHT
                || config.corner == HudConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = config.corner == HudConfig.Corner.BOTTOM_LEFT
                || config.corner == HudConfig.Corner.BOTTOM_RIGHT;

        int x = right ? screenWidth - width - margin : margin;
        int y = bottom ? screenHeight - height - margin : margin;

        extractor.fill(x, y, x + width, y + height, config.backgroundColor);
        // Liseré d'accent : il rattache le panneau a l'identite de MiniCube et donne un
        // repere visuel constant quel que soit le coin choisi.
        extractor.fill(x, y, x + ACCENT_WIDTH, y + height, config.accentColor);

        int textX = x + ACCENT_WIDTH + PADDING;
        int textY = y + PADDING;
        for (String line : lines) {
            extractor.text(font, line, textX, textY, config.textColor);
            textY += LINE_HEIGHT;
        }
    }
}
