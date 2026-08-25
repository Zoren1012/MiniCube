package com.minicube.launcher.ui.component;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * Logo de MiniCube : un cube isometrique dessine par le code.
 *
 * <p>Trois losanges forment les faces visibles. Chacune recoit un degrade different, ce
 * qui suffit a suggerer le volume sans recourir a la 3D. Le cube flotte doucement, en
 * accord avec l'esprit du reste de l'interface.</p>
 */
public class CubeLogo extends StackPane {

    private static final String TOP_FACE = "M12 2 L21 7 L12 12 L3 7 Z";
    private static final String LEFT_FACE = "M3 7 L12 12 L12 22 L3 17 Z";
    private static final String RIGHT_FACE = "M21 7 L12 12 L12 22 L21 17 Z";

    private final Timeline floating;

    /**
     * @param size cote souhaite du logo, en pixels
     */
    public CubeLogo(double size) {
        SVGPath top = face(TOP_FACE,
                new Stop(0, Color.web("#B9A6FF")), new Stop(1, Color.web("#7C5CFF")));
        SVGPath left = face(LEFT_FACE,
                new Stop(0, Color.web("#5B45D6")), new Stop(1, Color.web("#3A2A9E")));
        SVGPath right = face(RIGHT_FACE,
                new Stop(0, Color.web("#8C74FF")), new Stop(1, Color.web("#4E36C9")));

        Group cube = new Group(left, right, top);
        double scale = size / 24d;
        cube.setScaleX(scale);
        cube.setScaleY(scale);

        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#7C5CFF", 0.55));
        glow.setRadius(size * 0.55);
        glow.setSpread(0.12);
        cube.setEffect(glow);

        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getChildren().add(cube);
        getStyleClass().add("cube-logo");

        // Leger flottement : l'amplitude reste sous le pixel et demi pour rester elegante.
        floating = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(cube.translateYProperty(), -1.5, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.seconds(2.6),
                        new KeyValue(cube.translateYProperty(), 1.5, Interpolator.EASE_BOTH)));
        floating.setAutoReverse(true);
        floating.setCycleCount(Timeline.INDEFINITE);
        floating.play();
    }

    private SVGPath face(String content, Stop from, Stop to) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, from, to));
        path.setStroke(Color.TRANSPARENT);
        return path;
    }

    /** Arrete l'animation lorsque le logo n'est plus affiche. */
    public void dispose() {
        floating.stop();
    }
}
