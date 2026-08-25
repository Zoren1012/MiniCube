package com.minicube.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Fabrique de composants d'interface reutilises par toutes les vues.
 *
 * <p>Centraliser ces constructions garantit une apparence homogene et evite de repeter
 * les memes reglages de marges et de classes CSS dans chaque onglet.</p>
 */
public final class Ui {

    private Ui() {
    }

    /* ------------------------------------------------------------------ */
    /* Structure                                                           */
    /* ------------------------------------------------------------------ */

    /** Conteneur principal d'un onglet : titre, sous-titre et contenu defilant. */
    public static VBox page(String title, String subtitle, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");

        VBox header = new VBox(2, heading);
        if (subtitle != null && !subtitle.isBlank()) {
            Label sub = new Label(subtitle);
            sub.getStyleClass().add("page-subtitle");
            sub.setWrapText(true);
            header.getChildren().add(sub);
        }

        VBox box = new VBox(18);
        box.getStyleClass().add("page");
        box.getChildren().add(header);
        box.getChildren().addAll(content);
        return box;
    }

    /** Enveloppe un contenu dans une zone defilante sans bordure. */
    public static ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("content-scroll");
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return pane;
    }

    /** Carte : bloc encadre regroupant des reglages ou des informations. */
    public static VBox card(String title, Node... content) {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");
        if (title != null && !title.isBlank()) {
            Label label = new Label(title);
            label.getStyleClass().add("card-title");
            card.getChildren().add(label);
        }
        card.getChildren().addAll(content);
        return card;
    }

    /** Ligne de reglage : libelle, explication et controle aligne a droite. */
    public static HBox settingRow(String label, String description, Node control) {
        Label name = new Label(label);
        name.getStyleClass().add("setting-label");

        VBox texts = new VBox(2, name);
        if (description != null && !description.isBlank()) {
            Label hint = new Label(description);
            hint.getStyleClass().add("setting-hint");
            hint.setWrapText(true);
            hint.setMaxWidth(460);
            texts.getChildren().add(hint);
        }
        HBox row = new HBox(16, texts, growSpacer(), control);
        row.getStyleClass().add("setting-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.SOMETIMES);
        return row;
    }

    /** Espace flexible qui pousse les elements suivants vers la droite. */
    public static Region growSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** Espace vertical flexible. */
    public static Region verticalSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** Separateur horizontal discret. */
    public static Region divider() {
        Region line = new Region();
        line.getStyleClass().add("divider");
        line.setMinHeight(1);
        line.setMaxHeight(1);
        return line;
    }

    /* ------------------------------------------------------------------ */
    /* Boutons et textes                                                   */
    /* ------------------------------------------------------------------ */

    /** Bouton principal, mis en avant par la couleur d'accent. */
    public static Button primaryButton(String text, String iconSvg) {
        Button button = baseButton(text, iconSvg);
        button.getStyleClass().add("button-primary");
        return button;
    }

    /** Bouton secondaire, contour discret. */
    public static Button secondaryButton(String text, String iconSvg) {
        Button button = baseButton(text, iconSvg);
        button.getStyleClass().add("button-secondary");
        return button;
    }

    /** Bouton d'action destructrice (suppression). */
    public static Button dangerButton(String text, String iconSvg) {
        Button button = baseButton(text, iconSvg);
        button.getStyleClass().add("button-danger");
        return button;
    }

    /** Bouton reduit a une icone, avec infobulle. */
    public static Button iconButton(String iconSvg, String tooltip) {
        Button button = new Button();
        button.setGraphic(Icons.of(iconSvg, 18));
        button.getStyleClass().addAll("button-icon");
        if (tooltip != null && !tooltip.isBlank()) {
            Tooltip hint = new Tooltip(tooltip);
            hint.setShowDelay(Duration.millis(350));
            button.setTooltip(hint);
        }
        return button;
    }

    private static Button baseButton(String text, String iconSvg) {
        Button button = new Button(text);
        if (iconSvg != null) {
            button.setGraphic(Icons.of(iconSvg, 17));
        }
        button.getStyleClass().add("button-base");
        button.setPadding(new Insets(9, 18, 9, 16));
        return button;
    }

    /** Texte secondaire, plus petit et grise. */
    public static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("setting-hint");
        label.setWrapText(true);
        return label;
    }

    /** Etiquette coloree (categorie d'actualite, statut de serveur). */
    public static Label badge(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", styleClass);
        return label;
    }

    /** Message affiche a la place d'une liste vide. */
    public static VBox emptyState(String message, String iconSvg) {
        Node icon = Icons.of(iconSvg, 42);
        icon.getStyleClass().add("empty-icon");
        Label label = new Label(message);
        label.getStyleClass().add("empty-label");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);

        VBox box = new VBox(12, icon, label);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        box.setPadding(new Insets(40, 20, 40, 20));
        return box;
    }
}
