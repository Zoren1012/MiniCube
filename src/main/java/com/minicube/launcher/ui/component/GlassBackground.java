package com.minicube.launcher.ui.component;

import com.minicube.launcher.ui.Styles;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.util.Log;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.CacheHint;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Fond vivant sur lequel repose tout l'effet de verre.
 *
 * <p>Trois halos, tous dans la meme famille de violets et de bleus, derivent lentement
 * les uns sur les autres. Une seule famille de teintes : des couleurs opposees se
 * salissent mutuellement la ou elles se recouvrent, et le contenu pose dessus perd sa
 * lisibilite. Les panneaux etant translucides, ce sont ces couleurs qui transparaissent
 * au travers et donnent au verre sa profondeur : sans un fond qui bouge, une surface
 * translucide ressemble a un simple gris.</p>
 *
 * <p>Chaque halo est un cercle rempli d'un degrade radial qui s'eteint vers la
 * transparence. Ce choix evite un flou gaussien plein ecran, bien plus couteux, tout en
 * donnant un degrade parfaitement lisse.</p>
 */
public class GlassBackground extends Pane {

    /** Forme d un halo : sa taille relative, sa position de repos et sa derive. */
    private record Blob(double radiusRatio, double centerXRatio, double centerYRatio,
                        double driftX, double driftY, int seconds) {
    }

    /**
     * Geometrie des trois halos : taille, position de repos, derive et duree.
     *
     * <p>Elle ne depend pas du style — seules les couleurs en dependent, et elles
     * viennent du catalogue. Un style ajoute herite donc du meme mouvement, deja
     * regle pour que les trois cycles ne retombent jamais en phase.</p>
     */
    private static final List<Blob> SHAPES = List.of(
            new Blob(0.78, 0.06, 0.10, 70, 50, 23),
            new Blob(0.70, 0.94, 0.20, -80, 60, 29),
            new Blob(0.62, 0.55, 1.02, -60, -40, 35));

    private final Pane halos = new Pane();
    private final List<Timeline> animations = new ArrayList<>();
    private boolean dark = true;
    /** Teintes des halos, celles du style courant. */
    private List<Color> colors = Styles.of(ThemeManager.DARK).halos();
    /** Faux pour un style mat, sans verre ni halo. */
    private boolean halosEnabled = true;
    /** Faux quand la fenetre est reduite : inutile d'animer ce que personne ne voit. */
    private boolean visibleToUser = true;

    public GlassBackground() {
        // Le fond ne doit jamais intercepter un clic destine a l'interface.
        setMouseTransparent(true);
        halos.setMouseTransparent(true);
        getStyleClass().add("glass-background");

        halos.setCache(true);
        halos.setCacheHint(CacheHint.SPEED);
        getChildren().add(halos);

        widthProperty().addListener((observable, old, value) -> rebuild());
        heightProperty().addListener((observable, old, value) -> rebuild());
        watchWindow();
    }

    /**
     * Suspend l'animation lorsque la fenetre est reduite ou fermee.
     *
     * <p>Le cas n'est pas theorique : par defaut le launcher reste ouvert pendant la
     * partie. Sans cela, trois degrades continueraient d'etre recalcules a la cadence de
     * l'ecran, avec le processeur graphique dont le jeu a besoin.</p>
     */
    private void watchWindow() {
        sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene == null) {
                setVisibleToUser(false);
                return;
            }
            scene.windowProperty().addListener((watched, oldWindow, window) -> {
                if (window instanceof Stage stage) {
                    bindTo(stage);
                }
            });
            if (scene.getWindow() instanceof Stage stage) {
                bindTo(stage);
            }
        });
    }

    private void bindTo(Stage stage) {
        stage.iconifiedProperty().addListener(
                (observable, before, iconified) -> refreshVisibility(stage));
        stage.showingProperty().addListener(
                (observable, before, showing) -> refreshVisibility(stage));
        refreshVisibility(stage);
    }

    private void refreshVisibility(Stage stage) {
        setVisibleToUser(stage.isShowing() && !stage.isIconified());
    }

    private void setVisibleToUser(boolean visible) {
        if (this.visibleToUser == visible) {
            return;
        }
        this.visibleToUser = visible;
        if (visible) {
            animations.forEach(Timeline::play);
            Log.debug("Fond anime repris");
        } else {
            animations.forEach(Timeline::pause);
            Log.debug("Fond anime suspendu : fenetre non visible");
        }
    }

    /**
     * Adapte le fond au theme courant.
     *
     * @param theme identifiant du style, tel qu il figure au catalogue
     */
    public void setTheme(String theme) {
        Styles.Style style = Styles.of(theme);
        // Un style mat est opaque de bout en bout : des halos derivant derriere des
        // panneaux opaques ne se verraient pas, et couteraient pour rien.
        boolean wantHalos = !style.matte();
        if (this.dark != style.dark() || this.halosEnabled != wantHalos
                || !this.colors.equals(style.halos())) {
            this.dark = style.dark();
            this.halosEnabled = wantHalos;
            this.colors = style.halos();
            rebuild();
        }
    }

    /** Recree les halos apres un redimensionnement ou un changement de theme. */
    private void rebuild() {
        double width = getWidth();
        double height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        stopAnimations();
        halos.getChildren().clear();
        if (!halosEnabled) {
            return;
        }

        double reference = Math.max(width, height);
        // Le theme clair recoit des halos plus discrets : sur fond pale, la meme
        // intensite donnerait des taches de couleur au lieu d'une lumiere diffuse.
        double peak = dark ? 0.46 : 0.26;

        for (int index = 0; index < SHAPES.size() && index < colors.size(); index++) {
            Blob blob = SHAPES.get(index);
            Color tint = colors.get(index);
            double radius = reference * blob.radiusRatio();
            Circle circle = new Circle(radius);
            circle.setCenterX(width * blob.centerXRatio());
            circle.setCenterY(height * blob.centerYRatio());
            circle.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, tint.deriveColor(0, 1, 1, peak)),
                    new Stop(0.55, tint.deriveColor(0, 1, 1, peak * 0.35)),
                    new Stop(1, Color.TRANSPARENT)));
            halos.getChildren().add(circle);
            animations.add(drift(circle, blob));
        }
    }

    /** Fait deriver un halo indefiniment, sans jamais repasser par le meme etat. */
    private Timeline drift(Circle circle, Blob blob) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(circle.translateXProperty(), 0, Interpolator.EASE_BOTH),
                        new KeyValue(circle.translateYProperty(), 0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(blob.seconds()),
                        new KeyValue(circle.translateXProperty(), blob.driftX(),
                                Interpolator.EASE_BOTH),
                        new KeyValue(circle.translateYProperty(), blob.driftY(),
                                Interpolator.EASE_BOTH)));
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Timeline.INDEFINITE);
        // Un halo recree alors que la fenetre est reduite ne doit pas repartir.
        if (visibleToUser) {
            timeline.play();
        }
        return timeline;
    }

    private void stopAnimations() {
        animations.forEach(Timeline::stop);
        animations.clear();
    }

    /** Arrete les animations lorsque la vue est remplacee. */
    public void dispose() {
        stopAnimations();
    }
}
