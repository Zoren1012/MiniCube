package com.minicube.launcher.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/**
 * Bibliotheque d'icones vectorielles.
 *
 * <p>Chaque constante est un trace SVG dessine dans un carre de 24 par 24. Les icones
 * sont des noeuds JavaFX colores par la feuille de style (classe {@code icon}), ce qui
 * leur permet de suivre automatiquement le theme clair ou sombre.</p>
 */
public final class Icons {

    private Icons() {
    }

    public static final String HOME =
            "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";

    public static final String PERSON =
            "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 "
            + "4v2h16v-2c0-2.66-5.33-4-8-4z";

    public static final String SERVER =
            "M20 13H4c-.55 0-1 .45-1 1v6c0 .55.45 1 1 1h16c.55 0 1-.45 1-1v-6c0-.55-.45-1-1-1zM7 "
            + "19c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zM20 3H4c-.55 0-1 .45-1 1v6c0 .55.45 "
            + "1 1 1h16c.55 0 1-.45 1-1V4c0-.55-.45-1-1-1zM7 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 "
            + "2-.9 2-2 2z";

    public static final String MONITOR =
            "M21 2H3c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h7v2H8v2h8v-2h-2v-2h7c1.1 0 2-.9 "
            + "2-2V4c0-1.1-.9-2-2-2zm0 14H3V4h18v12z";

    public static final String SPARKLE =
            "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 "
            + "9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 "
            + "23l1.25-2.75L23 19l-2.75-1.25L19 15z";

    public static final String PUZZLE =
            "M20.5 11H19V7c0-1.1-.9-2-2-2h-4V3.5C13 2.12 11.88 1 10.5 1S8 2.12 8 3.5V5H4c-1.1 "
            + "0-1.99.9-1.99 2v3.8H3.5c1.49 0 2.7 1.21 2.7 2.7s-1.21 2.7-2.7 2.7H2V20c0 1.1.9 2 "
            + "2 2h3.8v-1.5c0-1.49 1.21-2.7 2.7-2.7s2.7 1.21 2.7 2.7V22H17c1.1 0 2-.9 "
            + "2-2v-4h1.5c1.38 0 2.5-1.12 2.5-2.5S21.88 11 20.5 11z";

    public static final String SETTINGS =
            "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41."
            + "12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36"
            + "-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13."
            + "57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 "
            + "1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 "
            + "3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48."
            + "41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08."
            + "47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1."
            + "62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";

    public static final String JOURNAL =
            "M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM7 "
            + "7h10v2H7V7zm0 4h10v2H7v-2zm0 4h7v2H7v-2z";

    public static final String PLAY = "M8 5v14l11-7z";

    public static final String STOP = "M6 6h12v12H6z";

    public static final String BELL =
            "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-."
            + "67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z";

    public static final String FOLDER =
            "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2"
            + "h-8l-2-2z";

    public static final String REFRESH =
            "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-8 8s3.57 8 8 8c3.73 0 6.84-2."
            + "55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14."
            + "69 4.22 1.78L13 11h7V4l-2.35 2.35z";

    public static final String CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";

    public static final String CLOSE =
            "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 "
            + "19 17.59 13.41 12z";

    public static final String DOWNLOAD = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";

    public static final String TRASH =
            "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z";

    public static final String WARNING = "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";

    public static final String INFO =
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm"
            + "0-8h-2V7h2v2z";

    public static final String ERROR =
            "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 "
            + "17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 "
            + "12 17 15.59z";

    public static final String PLUS = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";

    public static final String LOGOUT =
            "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 "
            + "2v14c0 1.1.9 2 2 2h8v-2H4V5z";

    /** Bulle de discussion : la communaute du projet. */
    public static final String CHAT =
            "M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h4v4l4-4h8c1.1 0 2-.9 2-2V4c0-1.1"
            + "-.9-2-2-2zM8 12H6v-2h2v2zm5 0h-2v-2h2v2zm5 0h-2v-2h2v2z";

    /** Piece : la monnaie de la Boutique. */
    public static final String COIN =
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm.88 15.76"
            + "V19h-1.75v-1.29c-1.13-.24-2.08-.97-2.15-2.21h1.28c.07.68.53 1.21 1.75 1.21"
            + " 1.31 0 1.6-.65 1.6-1.06 0-.55-.29-1.07-1.77-1.42-1.65-.4-2.78-1.08-2.78"
            + "-2.44 0-1.14.92-1.89 2.07-2.14V8h1.75v1.26c1.24.3 1.86 1.24 1.9 2.26h-1.28"
            + "c-.03-.72-.41-1.21-1.42-1.21-.96 0-1.54.43-1.54 1.05 0 .54.42.9 1.77 1.24"
            + " 1.35.35 2.78.93 2.78 2.63-.01 1.21-.92 1.87-2.21 2.09z";

    /** Coeur : soutien au projet. */
    public static final String HEART =
            "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41"
            + ".81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86"
            + "-8.55 11.54L12 21.35z";

    /** Palette : personnalisation de l apparence. */
    public static final String PALETTE =
            "M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1"
            + "-.23-.27-.38-.62-.38-1 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03"
            + "-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5"
            + " 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-"
            + ".83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 "
            + "0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z";

    /** Courbe de mesure, pour le tableau de bord des performances. */
    public static final String GAUGE =
            "M3.5 18.5l6-6 4 4L22 6.92 20.59 5.5l-7.09 8-4-4L2 17l1.5 1.5z";

    /** Baguette : reglage automatique propose par le launcher. */
    public static final String WAND =
            "M7.5 5.6L10 7L8.6 4.5L10 2L7.5 3.4L5 2l1.4 2.5L5 7l2.5-1.4zm12 9.8L17 14l1.4 2.5"
            + "L17 19l2.5-1.4L22 19l-1.4-2.5L22 14l-2.5 1.4zM22 2l-2.5 1.4L17 2l1.4 2.5L17 7"
            + "l2.5-1.4L22 7l-1.4-2.5L22 2zm-7.63 5.29a.996.996 0 0 0-1.41 0L1.29 18.96c-.39"
            + ".39-.39 1.02 0 1.41l2.34 2.34c.39.39 1.02.39 1.41 0L16.7 11.05c.39-.39.39-1.02"
            + " 0-1.41l-2.33-2.35zm-1.03 5.49l-2.12-2.12 2.44-2.44 2.12 2.12-2.44 2.44z";

    /**
     * Construit un noeud affichable a partir d'un trace.
     *
     * @param svg  trace SVG (constante de cette classe)
     * @param size taille souhaitee en pixels
     */
    public static Node of(String svg, double size) {
        SVGPath path = new SVGPath();
        path.setContent(svg);
        path.getStyleClass().add("icon");
        double scale = size / 24d;
        path.setScaleX(scale);
        path.setScaleY(scale);
        // Le groupe adopte les dimensions visuelles reelles apres mise a l'echelle.
        Group group = new Group(path);
        group.setManaged(true);
        return group;
    }

    /** Icone de 18 pixels, taille utilisee dans les boutons et la navigation. */
    public static Node of(String svg) {
        return of(svg, 18);
    }
}
