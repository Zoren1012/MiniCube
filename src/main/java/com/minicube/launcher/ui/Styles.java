package com.minicube.launcher.ui;

import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue des styles du launcher.
 *
 * <p>Un style, c'est une feuille de couleurs ({@code /css/<id>.css}) et ce que les
 * composants dessines a la main ne peuvent pas lire dedans : la teinte des halos du
 * fond, celle de l'animation, et les trois couleurs de la vignette d'apercu.</p>
 *
 * <p>Ces informations vivaient auparavant dans trois {@code switch} separes — le fond,
 * la vignette, l'animation. Ajouter un style obligeait a les retrouver tous les trois, et
 * en oublier un donnait un theme a moitie applique. Elles sont ici, en un seul endroit :
 * ajouter un style, c'est ajouter une entree et une feuille de style.</p>
 */
public final class Styles {

    /**
     * Un style et tout ce qui ne tient pas dans sa feuille de couleurs.
     *
     * @param id       identifiant, qui est aussi le nom du fichier CSS
     * @param dark     vrai si le fond est sombre : les composants dessines s'y adaptent
     * @param matte    vrai pour un style entierement opaque, sans verre ni halo
     * @param accent   couleur d'accent du style, telle que definie dans sa feuille
     * @param halos    trois teintes pour les halos du fond, du plus vif au plus sourd
     * @param backdrop fond de la vignette d'apercu
     * @param panel    panneau lateral de la vignette
     * @param surface  surface de contenu de la vignette
     */
    public record Style(String id, boolean dark, boolean matte, String accent,
                        List<Color> halos, String backdrop, String panel, String surface) {
    }

    /** Styles proposes, dans l'ordre d'affichage. */
    private static final Map<String, Style> CATALOGUE = new LinkedHashMap<>();

    private static void add(Style style) {
        CATALOGUE.put(style.id(), style);
    }

    static {
        // Le style d'origine : presque noir, pour que les halos s'expriment.
        add(new Style("dark", true, false, "#7C5CFF",
                List.of(Color.web("#6D4AFF"), Color.web("#2E6BFF"), Color.web("#4A2FD0")),
                "#0A0B12", "#141726", "#1D2033"));

        // Le meme, retourne : sur fond pale les halos sont volontairement plus doux.
        add(new Style("light", false, false, "#6247E0",
                List.of(Color.web("#9C86FF"), Color.web("#7FB0FF"), Color.web("#8E7BFF")),
                "#F2F4FA", "#FFFFFF", "#E8ECF6"));

        // Le launcher officiel : angles droits, surfaces opaques, vert de la pancarte.
        add(new Style("minecraft", true, true, "#3C8527",
                List.of(), "#1E1E1E", "#313233", "#48494A"));

        // Braise et obsidienne. L'orange est celui de la lave vue de loin, pas celui
        // d'une alerte : sature, mais pose sur un fond assez sombre pour ne pas crier.
        add(new Style("nether", true, false, "#FF7A3D",
                List.of(Color.web("#FF5722"), Color.web("#C2185B"), Color.web("#7B1E12")),
                "#100708", "#1E1012", "#2A181B"));

        // Fosse oceanique : le bleu-vert froid d'une lumiere qui traverse l'eau.
        add(new Style("abysse", true, false, "#2FD3C4",
                List.of(Color.web("#00B8D4"), Color.web("#1565C0"), Color.web("#00695C")),
                "#040F14", "#0C1D25", "#123039"));

        // Sous-bois. Le vert reste desature : un vert vif sur grande surface fatigue.
        add(new Style("foret", true, false, "#7DC95E",
                List.of(Color.web("#4CAF50"), Color.web("#8BC34A"), Color.web("#2E5E32")),
                "#070D08", "#111B12", "#1B2A1D"));

        // Le seul style clair en dehors du theme clair : rose poudre sur ivoire.
        add(new Style("sakura", false, false, "#D9548A",
                List.of(Color.web("#F48FB1"), Color.web("#CE93D8"), Color.web("#F8BBD0")),
                "#FBF4F6", "#FFFFFF", "#F3E3EA"));
    }

    private Styles() {
    }

    /** Identifiants de tous les styles, dans l'ordre d'affichage. */
    public static List<String> ids() {
        return List.copyOf(CATALOGUE.keySet());
    }

    /**
     * Style demande, ou le style sombre si l'identifiant est inconnu.
     *
     * <p>Un identifiant inconnu arrive apres une mise a jour qui retire un style, ou
     * quand quelqu'un edite le fichier de reglages a la main. Mieux vaut une fenetre
     * sombre qu'une fenetre sans couleurs.</p>
     */
    public static Style of(String id) {
        Style style = CATALOGUE.get(id);
        return style != null ? style : CATALOGUE.get("dark");
    }

    /** Vrai si le style existe reellement au catalogue. */
    public static boolean exists(String id) {
        return CATALOGUE.containsKey(id);
    }
}
