package com.minicube.launcher.ui;

import com.minicube.launcher.util.Log;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Application des feuilles de style et du fond personnalise.
 *
 * <p>{@code base.css} definit la structure commune ; chaque style n y redefinit que
 * ses couleurs. {@code minecraft.css} fait exception et remplace aussi les formes : son
 * langage visuel — angles droits, biseaux, surfaces opaques — est incompatible avec
 * celui du verre.</p>
 *
 * <p>La liste des styles et leurs teintes vivent dans {@link Styles} ; cette classe ne
 * fait que les appliquer.</p>
 */
public final class ThemeManager {

    public static final String DARK = "dark";
    public static final String LIGHT = "light";
    public static final String MINECRAFT = "minecraft";

    /** Themes proposes, dans l ordre d affichage : celui du catalogue. */
    public static final List<String> ALL = Styles.ids();

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
        addStylesheet(scene, "/css/" + normalise(theme) + ".css");
        Log.debug("Theme applique : " + theme);
    }

    /** Ramene une valeur inconnue au theme sombre plutot que de laisser l ecran nu. */
    public static String normalise(String theme) {
        return Styles.exists(theme) ? theme : DARK;
    }

    /**
     * Vrai si le theme est fonce.
     *
     * <p>Sert au fond anime et aux composants dessines a la main, qui ne lisent pas la
     * feuille de style.</p>
     */
    public static boolean isDark(String theme) {
        return Styles.of(theme).dark();
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
     * Remplace la couleur d'accent du theme par celle choisie par l'utilisateur.
     *
     * <p>Les couleurs nommees de JavaFX se resolvent en remontant l'arbre : un style
     * pose sur la racine redefinit donc {@code -color-accent} pour toute la fenetre,
     * sans toucher aux feuilles de style. Les variantes — survol, appui, voile — sont
     * derivees de la couleur de base, pour qu'il n'y ait qu'un reglage a fournir.</p>
     *
     * @param root  racine de la scene
     * @param color couleur au format {@code #RRGGBB}, ou vide pour revenir au theme
     */
    public static void applyRootStyle(Region root, String theme, String color) {
        StringBuilder style = new StringBuilder();
        String font = fontFor(theme);
        if (!font.isEmpty()) {
            style.append("-fx-font-family: \"").append(font).append("\";");
        }
        style.append(accentStyle(color));
        root.setStyle(style.toString());
    }

    /**
     * Police du theme, choisie parmi celles reellement installees.
     *
     * <p>JavaFX ne parcourt pas une liste de familles comme le ferait un navigateur : il
     * retient la premiere et retombe sur la police par defaut si elle manque. Nommer
     * "Minecraft" en tete d'une liste ne servirait donc a rien chez qui ne l'a pas. Le
     * choix se fait ici, a l'execution.</p>
     *
     * <p>La police du jeu n'est pas redistribuable : elle est utilisee si le joueur l'a
     * installee, sans quoi une police a chasse fixe du systeme en donne l'esprit.</p>
     */
    private static String fontFor(String theme) {
        if (!Styles.of(theme).matte()) {
            return "";
        }
        List<String> preferred = List.of("Minecraft", "Monocraft", "Consolas",
                "Cascadia Mono", "Lucida Console", "Courier New");
        List<String> installed = Font.getFamilies();
        for (String family : preferred) {
            if (installed.stream().anyMatch(name -> name.equalsIgnoreCase(family))) {
                return family;
            }
        }
        return "";
    }

    /** Fragment de style redefinissant la couleur d'accent, ou chaine vide. */
    private static String accentStyle(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            return "";
        }
        String rgb = color.substring(1);
        int red = Integer.parseInt(rgb.substring(0, 2), 16);
        int green = Integer.parseInt(rgb.substring(2, 4), 16);
        int blue = Integer.parseInt(rgb.substring(4, 6), 16);

        // derive() ne sait pas produire de transparence : les voiles sont ecrits en rgba.
        String veil = "rgba(" + red + ", " + green + ", " + blue + ", 0.20)";
        // Le bandeau d en-tete est peint avec un voile plus large de la meme couleur.
        String hero = "rgba(" + red + ", " + green + ", " + blue + ", 0.32)";
        String glow = "rgba(" + red + ", " + green + ", " + blue + ", 0.50)";

        Log.debug("Couleur d'accent appliquee : " + color);
        return "-color-accent: " + color + ";"
                + "-color-accent-2: derive(" + color + ", 18%);"
                + "-color-accent-hover: derive(" + color + ", 12%);"
                + "-color-accent-pressed: derive(" + color + ", -14%);"
                + "-color-accent-bright: derive(" + color + ", 32%);"
                + "-color-accent-veil: " + veil + ";"
                + "-color-accent-glow: " + glow + ";"
                + "-color-hero-veil: " + hero + ";";
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
