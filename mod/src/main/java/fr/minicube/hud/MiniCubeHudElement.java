package fr.minicube.hud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le panneau d'informations dessine par-dessus le jeu.
 *
 * <p>Appele a chaque image : rien de couteux ne doit s'y trouver, ni lecture de fichier,
 * ni appel reseau. Toutes les valeurs affichees sont deja connues du client.</p>
 */
public class MiniCubeHudElement implements HudElement {

    /** Hauteur d'une ligne de texte, police du jeu comprise. */
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 6;
    /** Largeur du lisere d'accent colle au bord gauche du panneau. */
    private static final int ACCENT_WIDTH = 2;

    private final HudConfig config;
    private final KeyBinding toggleKey;
    private final KeyBinding cornerKey;

    public MiniCubeHudElement(HudConfig config, KeyBinding toggleKey, KeyBinding cornerKey) {
        this.config = config;
        this.toggleKey = toggleKey;
        this.cornerKey = cornerKey;
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        // Les touches sont relevees ici plutot que dans un evenement de tick : cela evite
        // de dependre d'un module supplementaire, et cette methode est de toute facon
        // appelee a chaque image.
        pollKeys();

        if (!config.enabled) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        // Pas de joueur : on est dans un menu, il n'y a rien a afficher. Le masquage de
        // l'interface par F1 est gere en amont : un element enregistre dans le HUD
        // disparait avec lui.
        if (player == null || client.world == null) {
            return;
        }

        List<String> lines = collectLines(client, player);
        if (!lines.isEmpty()) {
            draw(context, client.textRenderer, lines);
        }
    }

    /**
     * Applique les raccourcis.
     *
     * <p>{@code wasPressed} ne rend vrai qu'une fois par pression : maintenir la touche ne
     * declenche donc pas l'action en boucle, meme appele soixante fois par seconde.</p>
     */
    private void pollKeys() {
        if (toggleKey.wasPressed()) {
            config.enabled = !config.enabled;
            config.save();
        }
        if (cornerKey.wasPressed()) {
            config.corner = config.corner.next();
            config.save();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Contenu                                                             */
    /* ------------------------------------------------------------------ */

    /** Compose les lignes a afficher, dans l'ordre, selon ce qui est active. */
    private List<String> collectLines(MinecraftClient client, ClientPlayerEntity player) {
        List<String> lines = new ArrayList<>(6);

        if (config.showCoordinates) {
            BlockPos position = player.getBlockPos();
            lines.add(String.format(Locale.ROOT, "XYZ  %d  %d  %d",
                    position.getX(), position.getY(), position.getZ()));
        }
        if (config.showDirection) {
            lines.add("Face  " + facingLabel(player));
        }
        if (config.showFps) {
            lines.add("FPS  " + client.getCurrentFps());
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
    private String facingLabel(ClientPlayerEntity player) {
        return switch (player.getHorizontalFacing()) {
            case NORTH -> "Nord  (-Z)";
            case SOUTH -> "Sud  (+Z)";
            case WEST -> "Ouest  (-X)";
            case EAST -> "Est  (+X)";
            default -> "-";
        };
    }

    /** Latence rapportee par le serveur, ou un tiret en solo. */
    private String pingLabel(MinecraftClient client, ClientPlayerEntity player) {
        if (client.getNetworkHandler() == null || client.isInSingleplayer()) {
            return "-";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return entry == null ? "-" : entry.getLatency() + " ms";
    }

    /**
     * Heure du jeu, ramenee au format d'une horloge.
     *
     * <p>Le jour de Minecraft compte 24 000 ticks et commence a six heures du matin :
     * c'est ce decalage qui rend l'heure affichee comparable a celle du soleil.</p>
     */
    private String timeLabel(MinecraftClient client) {
        long dayTime = client.world.getTimeOfDay() % 24_000L;
        long hours = (dayTime / 1000L + 6L) % 24L;
        long minutes = (dayTime % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private String serverLabel(MinecraftClient client) {
        if (client.isInSingleplayer()) {
            return "Solo";
        }
        var server = client.getCurrentServerEntry();
        if (server == null) {
            return "-";
        }
        return server.name == null || server.name.isBlank() ? server.address : server.name;
    }

    /* ------------------------------------------------------------------ */
    /* Dessin                                                              */
    /* ------------------------------------------------------------------ */

    /** Pose le panneau dans le coin choisi, sans jamais deborder de l'ecran. */
    private void draw(DrawContext context, TextRenderer font, List<String> lines) {
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, font.getWidth(line));
        }
        int width = textWidth + PADDING * 2 + ACCENT_WIDTH;
        int height = lines.size() * LINE_HEIGHT + PADDING * 2 - 2;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int margin = config.margin;

        boolean right = config.corner == HudConfig.Corner.TOP_RIGHT
                || config.corner == HudConfig.Corner.BOTTOM_RIGHT;
        boolean bottom = config.corner == HudConfig.Corner.BOTTOM_LEFT
                || config.corner == HudConfig.Corner.BOTTOM_RIGHT;

        int x = right ? screenWidth - width - margin : margin;
        int y = bottom ? screenHeight - height - margin : margin;

        context.fill(x, y, x + width, y + height, config.backgroundColor);
        // Lisere d'accent : il rattache le panneau a l'identite de MiniCube et donne un
        // repere visuel constant quel que soit le coin choisi.
        context.fill(x, y, x + ACCENT_WIDTH, y + height, config.accentColor);

        int textX = x + ACCENT_WIDTH + PADDING;
        int textY = y + PADDING;
        for (String line : lines) {
            context.drawText(font, line, textX, textY, config.textColor, true);
            textY += LINE_HEIGHT;
        }
    }
}
