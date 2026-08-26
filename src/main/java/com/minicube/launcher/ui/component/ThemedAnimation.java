package com.minicube.launcher.ui.component;

import com.minicube.launcher.ui.Styles;
import com.minicube.launcher.ui.ThemeManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Animation d'attente, dessinee dans le langage du theme actif.
 *
 * <p>Une meme animation sous tous les styles trahirait l habillage : le verre translucide
 * demande des formes rondes qui respirent, Minecraft des blocs carres qui sautent. Ce
 * composant en tient deux et bascule de l'une a l'autre.</p>
 *
 * <p>Comme le fond anime, elle se suspend des que la fenetre est reduite : rien ne doit
 * tourner pour un ecran que personne ne regarde.</p>
 */
public class ThemedAnimation extends HBox {

    /** Nombre d'elements anime, dans les deux styles. */
    private static final int COUNT = 5;
    private static final int BLOCK_SIZE = 14;
    private static final int ORB_RADIUS = 9;
    /** Duree d'un cycle complet ; chaque element part decale d'une fraction. */
    private static final Duration CYCLE = Duration.millis(1400);

    /**
     * Palette des blocs, reprise des couleurs de terre et d'herbe du jeu.
     *
     * <p>Ecrites en dur : ce sont celles de Minecraft, pas celles du launcher, et elles
     * ne doivent pas suivre la couleur d'accent choisie par l'utilisateur.</p>
     */
    private static final List<Color> BLOCK_COLORS = List.of(
            Color.web("#5FA83E"), Color.web("#7BB661"), Color.web("#8B6A43"),
            Color.web("#6E5334"), Color.web("#5FA83E"));

    private final List<Timeline> animations = new ArrayList<>();
    /** Fenetres deja surveillees, pour ne pas empiler les ecouteurs. */
    private final Set<Stage> watched = Collections.newSetFromMap(new WeakHashMap<>());
    private String theme = ThemeManager.DARK;
    private boolean visibleToUser = true;

    public ThemedAnimation() {
        getStyleClass().add("themed-animation");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);
        setMinHeight(52);
        setPrefHeight(52);
        setMouseTransparent(true);
        watchWindow();
        rebuild();
    }

    /** Change de style. Sans effet si le theme est deja celui-la. */
    public void setTheme(String value) {
        String normalised = ThemeManager.normalise(value);
        if (!normalised.equals(theme)) {
            theme = normalised;
            rebuild();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Construction                                                        */
    /* ------------------------------------------------------------------ */

    private void rebuild() {
        stopAnimations();
        getChildren().clear();

        boolean blocky = Styles.of(theme).matte();
        for (int index = 0; index < COUNT; index++) {
            Node node = blocky ? block(index) : orb(index);
            getChildren().add(node);
            animations.add(blocky ? hop(node, index) : breathe(node, index));
        }
    }

    /**
     * Un bloc : carre, mat, sans arrondi.
     *
     * <p>Minecraft n'a ni degrade ni coin arrondi ; un carre plein avec une arete claire
     * en haut suffit a l'evoquer.</p>
     */
    private Node block(int index) {
        Rectangle rectangle = new Rectangle(BLOCK_SIZE, BLOCK_SIZE);
        rectangle.setFill(BLOCK_COLORS.get(index % BLOCK_COLORS.size()));
        rectangle.setStroke(Color.web("#1B1B1B"));
        rectangle.setStrokeWidth(1);
        return rectangle;
    }

    /**
     * Une bulle de verre : ronde, diffuse, sans contour.
     *
     * <p>Les teintes sont celles des halos du style : l'animation et le fond parlent
     * ainsi la meme langue, et un style ajoute au catalogue s'anime dans ses propres
     * couleurs sans une ligne de plus ici.</p>
     */
    private Node orb(int index) {
        Styles.Style style = Styles.of(theme);
        List<Color> palette = style.halos();
        Color base = palette.get(index % palette.size());
        // Les bulles s'eclaircissent vers la droite : la lumiere semble venir d'un point.
        Color tint = base.interpolate(Color.web(style.accent()), index / (double) COUNT);
        if (!style.dark()) {
            // Sur fond clair, une bulle pale disparait : on fonce au lieu d'eclaircir.
            tint = tint.deriveColor(0, 1.05, 0.82, 1);
        }

        Circle circle = new Circle(ORB_RADIUS);
        circle.setFill(new RadialGradient(0, 0, 0.5, 0.42, 0.62, true, CycleMethod.NO_CYCLE,
                new Stop(0, tint.deriveColor(0, 1, 1.25, 0.95)),
                new Stop(1, tint.deriveColor(0, 1, 0.85, 0.18))));
        return circle;
    }

    /* ------------------------------------------------------------------ */
    /* Mouvements                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Le saut d'un bloc.
     *
     * <p>Interpolation lineaire, volontairement : une acceleration douce donnerait un
     * mouvement organique, etranger a un jeu fait de cubes.</p>
     */
    private Timeline hop(Node node, int index) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.translateYProperty(), 0, Interpolator.LINEAR)),
                new KeyFrame(CYCLE.divide(2),
                        new KeyValue(node.translateYProperty(), -14, Interpolator.LINEAR)),
                new KeyFrame(CYCLE,
                        new KeyValue(node.translateYProperty(), 0, Interpolator.LINEAR)));
        return start(timeline, index);
    }

    /** La respiration d'une bulle : elle enfle et s'eteint doucement. */
    private Timeline breathe(Node node, int index) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(node.scaleXProperty(), 0.6, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 0.6, Interpolator.EASE_BOTH),
                        new KeyValue(node.opacityProperty(), 0.35, Interpolator.EASE_BOTH)),
                new KeyFrame(CYCLE.divide(2),
                        new KeyValue(node.scaleXProperty(), 1.15, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 1.15, Interpolator.EASE_BOTH),
                        new KeyValue(node.opacityProperty(), 1, Interpolator.EASE_BOTH)),
                new KeyFrame(CYCLE,
                        new KeyValue(node.scaleXProperty(), 0.6, Interpolator.EASE_BOTH),
                        new KeyValue(node.scaleYProperty(), 0.6, Interpolator.EASE_BOTH),
                        new KeyValue(node.opacityProperty(), 0.35, Interpolator.EASE_BOTH)));
        return start(timeline, index);
    }

    /** Lance une animation en la decalant, pour que la vague traverse la rangee. */
    private Timeline start(Timeline timeline, int index) {
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.setDelay(CYCLE.divide(COUNT * 2.0).multiply(index));
        if (visibleToUser) {
            timeline.play();
        }
        return timeline;
    }

    /* ------------------------------------------------------------------ */
    /* Economie                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Suspend l'animation quand la fenetre est reduite, fermee, ou quand la page quitte
     * la scene.
     *
     * <p>La fenetre n'est pas toujours attachee au moment ou la vue rejoint la scene :
     * ecouter seulement {@code scene.getWindow()} laisserait l'animation tourner pour
     * toujours. La scene est donc surveillee elle aussi.</p>
     */
    private void watchWindow() {
        sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene == null) {
                setActive(false);
                return;
            }
            scene.windowProperty().addListener((watched, oldWindow, window) -> {
                if (window instanceof Stage stage) {
                    bindTo(stage);
                }
            });
            if (scene.getWindow() instanceof Stage stage) {
                bindTo(stage);
            } else {
                setActive(true);
            }
        });
    }

    /** Une fenetre reduite ou fermee ne montre rien : inutile de l'animer. */
    private void bindTo(Stage stage) {
        if (!watched.add(stage)) {
            refreshFrom(stage);
            return;
        }
        stage.iconifiedProperty().addListener(
                (observable, before, iconified) -> refreshFrom(stage));
        stage.showingProperty().addListener(
                (observable, before, showing) -> refreshFrom(stage));
        refreshFrom(stage);
    }

    private void refreshFrom(Stage stage) {
        setActive(stage.isShowing() && !stage.isIconified());
    }

    private void setActive(boolean active) {
        if (visibleToUser == active) {
            return;
        }
        visibleToUser = active;
        animations.forEach(active ? Timeline::play : Timeline::pause);
    }

    private void stopAnimations() {
        animations.forEach(Timeline::stop);
        animations.clear();
    }

    /** Libere les animations lorsque la vue est remplacee. */
    public void dispose() {
        stopAnimations();
    }

    /** Racine utilisable comme n'importe quel panneau. */
    public Pane pane() {
        return this;
    }
}
