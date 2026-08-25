package com.minicube.launcher.ui;

import com.minicube.launcher.util.Log;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Application des feuilles de style et du fond personnalise.
 *
 * <p>Trois feuilles cohabitent : {@code base.css} definit la structure commune,
 * {@code dark.css} et {@code light.css} ne redefinissent que les couleurs. Changer de
 * theme revient donc a echanger une seule feuille, sans reconstruire l'interface.</p>
 */
public final class ThemeManager {

    public static final String DARK = "dark";
    public static final String LIGHT = "light";

    private ThemeManager() {
    }

    /**
     * Applique un theme a une scene.
     *
     * @param scene scene cible
     * @param theme {@code dark} ou {@code light}
     */
    public static void apply(Scene scene, String theme) {
        scene.getStylesheets().clear();
        addStylesheet(scene, "/css/base.css");
        addStylesheet(scene, LIGHT.equals(theme) ? "/css/light.css" : "/css/dark.css");
        Log.debug("Theme applique : " + theme);
    }

    private static void addStylesheet(Scene scene, String resource) {
        URL url = ThemeManager.class.getResource(resource);
        if (url == null) {
            Log.warn("Feuille de style introuvable : " + resource);
            return;
        }
        scene.getStylesheets().add(url.toExternalForm());
    }

    /**
     * Applique une image de fond personnalisee a une region.
     *
     * <p>Si le chemin est vide ou le fichier absent, la region retrouve le degrade par
     * defaut defini dans la feuille de style.</p>
     *
     * @param region    region a habiller (generalement la racine de la fenetre)
     * @param imagePath chemin absolu de l'image, ou chaine vide
     */
    public static void applyBackground(Region region, String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            region.setStyle("");
            return;
        }
        Path file = Path.of(imagePath);
        if (!Files.isRegularFile(file)) {
            Log.warn("Image de fond introuvable : " + imagePath);
            region.setStyle("");
            return;
        }
        String uri = file.toUri().toString();
        region.setStyle("-fx-background-image: url('" + uri + "');"
                + "-fx-background-size: cover;"
                + "-fx-background-position: center center;");
        Log.debug("Fond personnalise applique : " + imagePath);
    }
}
