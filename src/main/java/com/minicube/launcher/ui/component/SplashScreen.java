package com.minicube.launcher.ui.component;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.util.Fx;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Ecran affiche pendant l'initialisation.
 *
 * <p>Il ne fait pas patienter pour le plaisir : les etapes reelles du demarrage y
 * defilent — lecture de la configuration, chargement des comptes, reperage du dossier
 * de jeu. Si l'une d'elles prend du temps, l'utilisateur sait laquelle.</p>
 *
 * <p>L'animation d'entree dure environ sept dixiemes de seconde. Le lancement etant
 * generalement plus rapide, l'ecran reste visible le temps qu'elle se termine, faute de
 * quoi il n'apparaitrait que le temps d'un clignotement.</p>
 */
public class SplashScreen {

    /** Duree en dessous de laquelle l'animation n'aurait pas le temps d'exister. */
    private static final int MINIMUM_MILLIS = 950;

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final CubeLogo logo = new CubeLogo(76);
    private final Label title = new Label(Constants.APP_NAME);
    private final Label version = new Label("v" + Constants.APP_VERSION);
    private final Label status = new Label("");
    private final Region progressTrack = new Region();
    private final Region progressBead = new Region();

    private final long shownAt = System.currentTimeMillis();
    private Timeline beadAnimation;

    public SplashScreen() {
        title.getStyleClass().add("splash-title");
        version.getStyleClass().add("splash-version");
        status.getStyleClass().add("splash-status");

        VBox texts = new VBox(1, title, version);
        texts.setAlignment(Pos.CENTER);

        progressTrack.getStyleClass().add("splash-track");
        progressTrack.setPrefSize(220, 3);
        progressTrack.setMaxWidth(220);
        progressBead.getStyleClass().add("splash-bead");
        progressBead.setPrefSize(70, 3);
        progressBead.setMaxSize(70, 3);

        StackPane progress = new StackPane(progressTrack, progressBead);
        progress.setAlignment(Pos.CENTER_LEFT);
        progress.setMaxWidth(220);
        progress.setPadding(new Insets(0, 0, 0, 0));

        VBox content = new VBox(18, logo, texts, progress, status);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("splash");
        content.setPadding(new Insets(38, 46, 30, 46));

        StackPane root = new StackPane(content);
        root.setStyle("-fx-background-color: transparent;");
        // La zone d'ombre doit rester dans la fenetre, sinon elle est rognee.
        root.setPadding(new Insets(28));

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#000000", 0.55));
        shadow.setRadius(38);
        shadow.setOffsetY(12);
        content.setEffect(shadow);

        Scene scene = new Scene(root, 396, 330);
        scene.setFill(Color.TRANSPARENT);
        com.minicube.launcher.ui.ThemeManager.apply(scene, "dark");

        stage.setScene(scene);
        stage.setAlwaysOnTop(true);
        stage.centerOnScreen();
        stage.setOpacity(0);
    }

    /** Affiche l'ecran et lance l'animation d'entree. */
    public void show() {
        stage.show();
        stage.centerOnScreen();

        FadeTransition appear = new FadeTransition(Duration.millis(180), stage.getScene()
                .getRoot());
        stage.setOpacity(1);
        appear.setFromValue(0);
        appear.setToValue(1);
        appear.play();

        // Le cube arrive en depassant legerement sa taille, ce qui lui donne du poids.
        logo.setScaleX(0.55);
        logo.setScaleY(0.55);
        ScaleTransition grow = new ScaleTransition(Duration.millis(620), logo);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(Fx.SPRING);
        grow.play();

        slideUp(title, 220, 120);
        slideUp(version, 220, 190);
        slideUp(status, 220, 260);

        startBead();
    }

    /** Fait monter un element en fondu, apres un delai. */
    private void slideUp(javafx.scene.Node node, int millis, int delayMillis) {
        node.setOpacity(0);
        node.setTranslateY(10);

        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMillis));
        fade.setInterpolator(Fx.SMOOTH);

        TranslateTransition rise = new TranslateTransition(Duration.millis(millis), node);
        rise.setToY(0);
        rise.setDelay(Duration.millis(delayMillis));
        rise.setInterpolator(Fx.SMOOTH);

        fade.play();
        rise.play();
    }

    /**
     * Anime la bille qui parcourt la barre.
     *
     * <p>Une barre indeterminee plutot qu'une progression chiffree : les etapes de
     * demarrage n'ont pas de duree previsible, et une jauge qui saute de 20 a 90 pour
     * cent n'informe personne.</p>
     */
    private void startBead() {
        beadAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progressBead.translateXProperty(), 0,
                                Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1100),
                        new KeyValue(progressBead.translateXProperty(), 150,
                                Interpolator.EASE_BOTH)));
        beadAnimation.setAutoReverse(true);
        beadAnimation.setCycleCount(Timeline.INDEFINITE);
        beadAnimation.play();
    }

    /**
     * Met a jour l'etape affichee.
     * Peut etre appelee depuis n'importe quel thread.
     */
    public void setStatus(String message) {
        Fx.ui(() -> status.setText(message));
    }

    /**
     * Ferme l'ecran en fondu, puis execute la suite.
     *
     * <p>La fermeture attend que l'animation d'entree ait eu le temps de se derouler :
     * un ecran qui disparait avant d'etre apparu donne l'impression d'un defaut
     * d'affichage.</p>
     *
     * @param onClosed action executee une fois l'ecran retire
     */
    public void close(Runnable onClosed) {
        long elapsed = System.currentTimeMillis() - shownAt;
        long wait = Math.max(0, MINIMUM_MILLIS - elapsed);

        PauseTransition delay = new PauseTransition(Duration.millis(wait));
        delay.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(Duration.millis(260),
                    stage.getScene().getRoot());
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setInterpolator(Fx.SMOOTH);
            fade.setOnFinished(done -> {
                if (beadAnimation != null) {
                    beadAnimation.stop();
                }
                logo.dispose();
                stage.close();
                onClosed.run();
            });
            fade.play();
        });
        delay.play();
    }
}
