package fr.minicube.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MiniCube HUD : un panneau d'informations discret, aux couleurs du launcher.
 *
 * <p>Le mod est <b>entierement client</b> : il ne touche ni au monde, ni aux entites, ni
 * au reseau. Il n'apporte donc aucun avantage en partie et peut etre utilise sur un
 * serveur sans rien demander a personne.</p>
 *
 * <p>Deux touches suffisent a le piloter, et tout le reste se regle dans
 * {@code config/minicube-hud.json}.</p>
 */
public class MiniCubeHudClient implements ClientModInitializer {

    public static final String MOD_ID = "minicube-hud";
    public static final Logger LOGGER = LoggerFactory.getLogger("MiniCube HUD");

    private static HudConfig config;

    private KeyMapping toggleKey;
    private KeyMapping cornerKey;

    @Override
    public void onInitializeClient() {
        config = HudConfig.load();

        registerKeys();
        // addLast : le panneau se dessine par-dessus les elements du jeu, mais avant les
        // ecrans, qui ne doivent jamais etre recouverts.
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "info-panel"),
                new MiniCubeHudElement(config));

        LOGGER.info("MiniCube HUD pret");
    }

    /* ------------------------------------------------------------------ */
    /* Touches                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Enregistre les raccourcis.
     *
     * <p>F6 et F7 sont libres dans Minecraft. Ils restent modifiables dans les commandes
     * du jeu, la ou le joueur cherche naturellement.</p>
     */
    private void registerKeys() {
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KeyMapping.Category.MISC));

        cornerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + MOD_ID + ".corner",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    /**
     * Traite les touches une fois par tick.
     *
     * <p>{@code consumeClick} ne rend vrai qu'une fois par pression : maintenir la touche
     * ne declenche donc pas l'action en boucle.</p>
     */
    private void onTick(Minecraft client) {
        if (toggleKey.consumeClick()) {
            config.enabled = !config.enabled;
            config.save();
        }
        if (cornerKey.consumeClick()) {
            config.corner = config.corner.next();
            config.save();
        }
    }

}
