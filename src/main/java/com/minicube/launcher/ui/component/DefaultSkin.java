package com.minicube.launcher.ui.component;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Skin de repli genere par le code.
 *
 * <p>Aucune texture n'est distribuee avec le launcher : le personnage par defaut est
 * dessine a la volee dans une image 64 par 64 respectant la disposition officielle des
 * faces. Cela evite d'embarquer un fichier soumis a droits tout en offrant un apercu
 * credible pour les comptes hors-ligne.</p>
 */
public final class DefaultSkin {

    private static final Color SKIN = Color.web("#C89B70");
    private static final Color SKIN_DARK = Color.web("#B08355");
    private static final Color HAIR = Color.web("#3A2618");
    private static final Color SHIRT = Color.web("#22A8A8");
    private static final Color SHIRT_DARK = Color.web("#1B8C8C");
    private static final Color PANTS = Color.web("#3B44AA");
    private static final Color PANTS_DARK = Color.web("#2F3789");
    private static final Color SHOES = Color.web("#4A4A55");
    private static final Color EYE_WHITE = Color.web("#F2F2F2");
    private static final Color EYE = Color.web("#2F4FA8");
    private static final Color MOUTH = Color.web("#7A4B32");

    private static Image cached;

    private DefaultSkin() {
    }

    /** Image 64 par 64 du personnage par defaut, calculee une seule fois. */
    public static Image image() {
        if (cached == null) {
            cached = build();
        }
        return cached;
    }

    private static Image build() {
        WritableImage image = new WritableImage(64, 64);
        PixelWriter writer = image.getPixelWriter();

        // Toute la zone non utilisee reste transparente.
        fill(writer, 0, 0, 64, 64, Color.TRANSPARENT);

        drawHead(writer);
        drawBody(writer);
        drawArm(writer, 40, 16);
        drawArm(writer, 32, 48);
        drawLeg(writer, 0, 16);
        drawLeg(writer, 16, 48);
        return image;
    }

    /** Tete : cheveux sur le dessus et l'arriere, visage sur la face avant. */
    private static void drawHead(PixelWriter writer) {
        fill(writer, 8, 0, 8, 8, HAIR);      // dessus
        fill(writer, 16, 0, 8, 8, SKIN_DARK); // dessous
        fill(writer, 0, 8, 8, 8, SKIN);       // cote droit
        fill(writer, 8, 8, 8, 8, SKIN);       // face
        fill(writer, 16, 8, 8, 8, SKIN);      // cote gauche
        fill(writer, 24, 8, 8, 8, HAIR);      // arriere

        // Frange sur les trois faces visibles.
        fill(writer, 8, 8, 8, 2, HAIR);
        fill(writer, 0, 8, 8, 2, HAIR);
        fill(writer, 16, 8, 8, 2, HAIR);

        // Yeux et bouche sur la face avant.
        fill(writer, 10, 11, 2, 1, EYE_WHITE);
        fill(writer, 11, 11, 1, 1, EYE);
        fill(writer, 12, 11, 2, 1, EYE_WHITE);
        fill(writer, 12, 11, 1, 1, EYE);
        fill(writer, 11, 13, 3, 1, MOUTH);
    }

    /** Buste : tee-shirt sur toutes les faces. */
    private static void drawBody(PixelWriter writer) {
        fill(writer, 20, 16, 8, 4, SHIRT_DARK); // dessus
        fill(writer, 28, 16, 8, 4, SHIRT_DARK); // dessous
        fill(writer, 16, 20, 4, 12, SHIRT_DARK); // cote droit
        fill(writer, 20, 20, 8, 12, SHIRT);      // face
        fill(writer, 28, 20, 4, 12, SHIRT_DARK); // cote gauche
        fill(writer, 32, 20, 8, 12, SHIRT);      // arriere
    }

    /**
     * Bras : manche courte puis peau.
     *
     * @param u abscisse du bloc dans la texture
     * @param v ordonnee du bloc dans la texture
     */
    private static void drawArm(PixelWriter writer, int u, int v) {
        fill(writer, u + 4, v, 4, 4, SHIRT_DARK);
        fill(writer, u + 8, v, 4, 4, SKIN_DARK);
        fill(writer, u, v + 4, 4, 12, SKIN);
        fill(writer, u + 4, v + 4, 4, 12, SKIN);
        fill(writer, u + 8, v + 4, 4, 12, SKIN);
        fill(writer, u + 12, v + 4, 4, 12, SKIN);
        // Manche : les quatre premieres rangees du bras.
        fill(writer, u, v + 4, 16, 4, SHIRT);
    }

    /** Jambe : pantalon puis chaussure. */
    private static void drawLeg(PixelWriter writer, int u, int v) {
        fill(writer, u + 4, v, 4, 4, PANTS_DARK);
        fill(writer, u + 8, v, 4, 4, SHOES);
        fill(writer, u, v + 4, 16, 12, PANTS);
        fill(writer, u, v + 4, 4, 12, PANTS_DARK);
        fill(writer, u + 8, v + 4, 4, 12, PANTS_DARK);
        // Chaussures : les deux dernieres rangees.
        fill(writer, u, v + 14, 16, 2, SHOES);
    }

    private static void fill(PixelWriter writer, int x, int y, int width, int height, Color color) {
        for (int i = x; i < x + width; i++) {
            for (int j = y; j < y + height; j++) {
                if (i >= 0 && i < 64 && j >= 0 && j < 64) {
                    writer.setColor(i, j, color);
                }
            }
        }
    }
}
