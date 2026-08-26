package fr.minicube.hud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
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

    @Override
    public void onInitializeClient() {
        HudConfig config = HudConfig.load();
        // L adresse du serveur vient du launcher : sans cela, le bouton du menu resterait
        // invisible chez tout le monde.
        config.fillFromLauncher();

        // F6 et F7 sont libres dans Minecraft. Ils restent modifiables dans les commandes
        // du jeu, la ou le joueur cherche naturellement.
        KeyBinding toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KeyBinding.Category.MISC));

        KeyBinding cornerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key." + MOD_ID + ".corner",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KeyBinding.Category.MISC));

        // addLast : le panneau se dessine par-dessus les elements du jeu, mais avant les
        // ecrans, qui ne doivent jamais etre recouverts.
        HudElementRegistry.addLast(
                Identifier.of(MOD_ID, "info-panel"),
                new MiniCubeHudElement(config, toggleKey, cornerKey));

        MiniCubeTitleScreen.register(config);

        LOGGER.info("MiniCube HUD pret");
    }
}
