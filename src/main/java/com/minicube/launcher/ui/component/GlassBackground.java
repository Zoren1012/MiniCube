package com.minicube.launcher.ui.component;

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
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Fond vivant sur lequel repose tout l'effet de verre.
 *
 * <p>Des halos colores derivent lentement les uns sur les autres. Les panneaux de
 * l'interface etant translucides, ce sont ces couleurs qui transparaissent au travers
 * et donnent au verre sa profondeur : sans un fond qui bouge, une surface translucide
 * ressemble a un simple gris.</p>
 *
 * <p>Chaque halo est un cercle rempli d'un degrade radial qui s'eteint vers la
 * transparence. Ce choix evite un flou gaussien plein ecran, bien plus couteux, tout en
 * donnant un degrade parfaitement lisse.</p>
 */
public class GlassBackground extends Pane {

    /** Un halo : sa couleur, sa taille relative et sa position de repos. */
    private record Blob(Color color, double radiusRatio, double centerXRatio,
                        double centerYRatio, double driftX, double driftY, int seconds) {
    }

    private static final List<Blob> DARK_BLOBS = List.of(
            new Blob(Color.web("#7C5CFF"), 0.52, 0.16, 0.18, 90, 70, 17),
            new Blob(Color.web("#2E8BFF"), 0.46, 0.82, 0.12, -110, 90, 21),
            new Blob(Color.web("#16C7B4"), 0.40, 0.68, 0.86, -80, -70, 25),
            new Blob(Color.web("#FF4FA3"), 0.34, 0.10, 0.92, 120, -60, 19),
            new Blob(Color.web("#5E3BFF"), 0.30, 0.48, 0.48, 70, -90, 29));

    private static final List<Blob> LIGHT_BLOBS = List.of(
            new Blob(Color.web("#8E76FF"), 0.52, 0.16, 0.18, 90, 70, 17),
            new Blob(Color.web("#5BA8FF"), 0.46, 0.82, 0.12, -110, 90, 21),
            new Blob(Color.web("#4BD8CA"), 0.40, 0.68, 0.86, -80, -70, 25),
            new Blob(Color.web("#FF83BE"), 0.34, 0.10, 0.92, 120, -60, 19),
            new Blob(Color.web("#9C88FF"), 0.30, 0.48, 0.48, 70, -90, 29));

    private final Pane halos = new Pane();
    private final List<Timeline> animations = new ArrayList<>();
    private boolean dark = true;

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
    }

    /**
     * Adapte la palette au theme courant.
     *
     * @param darkTheme true pour le theme sombre
     */
    public void setDark(boolean darkTheme) {
        if (this.dark != darkTheme) {
            this.dark = darkTheme;
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

        double reference = Math.max(width, height);
        // Le theme clair recoit des halos plus discrets : sur fond pale, la meme
        // intensite donnerait des taches de couleur au lieu d'une lumiere diffuse.
        double peak = dark ? 0.55 : 0.34;

        for (Blob blob : (dark ? DARK_BLOBS : LIGHT_BLOBS)) {
            double radius = reference * blob.radiusRatio();
            Circle circle = new Circle(radius);
            circle.setCenterX(width * blob.centerXRatio());
            circle.setCenterY(height * blob.centerYRatio());
            circle.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, blob.color().deriveColor(0, 1, 1, peak)),
                    new Stop(0.55, blob.color().deriveColor(0, 1, 1, peak * 0.35)),
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
        timeline.play();
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
