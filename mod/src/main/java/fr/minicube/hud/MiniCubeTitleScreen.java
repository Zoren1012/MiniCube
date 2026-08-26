package fr.minicube.hud;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Habillage du menu principal aux couleurs de la communaute.
 *
 * <p>Deux ajouts, tous deux desactivables : une signature discrete en bas a gauche, et un
 * bouton qui connecte directement au serveur du projet. Rien n'est retire du menu du
 * jeu : les boutons d'origine restent a leur place, la ou les joueurs les cherchent.</p>
 */
public final class MiniCubeTitleScreen {

    /** Largeur du bouton de connexion, alignee sur celle des boutons du jeu. */
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;

    private MiniCubeTitleScreen() {
    }

    /** Branche l'habillage sur l'ouverture de chaque ecran. */
    public static void register(HudConfig config) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof TitleScreen)) {
                return;
            }
            boolean button = config.menuJoinButton && !config.serverAddress.isBlank();
            if (button) {
                addJoinButton(client, screen, config, width, height);
            }
            MiniCubeHudClient.LOGGER.info("Menu personnalise : signature {}, bouton {}",
                    config.menuBranding ? "oui" : "non", button ? "oui" : "non");
            if (config.menuBranding) {
                ScreenEvents.afterRender(screen).register(
                        (rendered, context, mouseX, mouseY, delta) ->
                                drawBranding(context, config, height));
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Bouton de connexion                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Ajoute le bouton sous ceux du jeu.
     *
     * <p>Il est place en bas de l'ecran plutot qu'au milieu de la pile : inserer un
     * bouton entre ceux du jeu deplacerait tous les autres, et un joueur habitue
     * cliquerait a cote.</p>
     */
    private static void addJoinButton(MinecraftClient client, Screen screen,
                                      HudConfig config, int width, int height) {
        Text label = Text.literal("Rejoindre " + serverLabel(config));

        ButtonWidget button = ButtonWidget.builder(label, pressed -> connect(client, screen, config))
                .dimensions((width - BUTTON_WIDTH) / 2, height - 30, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();

        Screens.getButtons(screen).add(button);
    }

    /** Nom lisible du serveur, ou son adresse a defaut. */
    private static String serverLabel(HudConfig config) {
        return config.serverName == null || config.serverName.isBlank()
                ? config.serverAddress
                : config.serverName;
    }

    /**
     * Lance la connexion.
     *
     * <p>Passe par le meme chemin que le menu multijoueur du jeu : l'ecran de connexion
     * s'affiche, avec ses messages d'erreur habituels si le serveur est injoignable.</p>
     */
    private static void connect(MinecraftClient client, Screen parent, HudConfig config) {
        ServerInfo info = new ServerInfo(serverLabel(config), config.serverAddress,
                ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(parent, client, ServerAddress.parse(config.serverAddress),
                info, false, null);
    }

    /* ------------------------------------------------------------------ */
    /* Signature                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Dessine la signature en bas a gauche.
     *
     * <p>Volontairement petite et posee dans un coin : le menu appartient au jeu, pas au
     * launcher. Elle rappelle l'identite sans encombrer.</p>
     */
    private static void drawBranding(DrawContext context, HudConfig config, int height) {
        var font = MinecraftClient.getInstance().textRenderer;

        String title = "MiniCube";
        String subtitle = config.communityName == null || config.communityName.isBlank()
                ? config.serverAddress
                : config.communityName;

        int textWidth = Math.max(font.getWidth(title), font.getWidth(subtitle));
        int panelWidth = textWidth + 16;
        int panelHeight = subtitle.isBlank() ? 20 : 30;

        int x = 4;
        int y = height - panelHeight - 4;

        context.fill(x, y, x + panelWidth, y + panelHeight, config.backgroundColor);
        context.fill(x, y, x + 2, y + panelHeight, config.accentColor);

        context.drawText(font, title, x + 8, y + 6, config.accentColor, true);
        if (!subtitle.isBlank()) {
            context.drawText(font, subtitle, x + 8, y + 17, config.textColor, true);
        }
    }
}
