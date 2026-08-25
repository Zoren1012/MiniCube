package com.minicube.launcher.util;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Ponts entre le thread JavaFX et les traitements longs, et animations reutilisables.
 *
 * <p>Regle du projet : aucun appel reseau ou disque n'est effectue sur le thread
 * d'interface. Les controleurs passent systematiquement par {@link #async}.</p>
 */
public final class Fx {

    /** Fabrique de threads demons : la JVM peut s'arreter sans attendre les taches. */
    private static final ThreadFactory FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "minicube-worker-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    };

    private static final ExecutorService POOL = Executors.newCachedThreadPool(FACTORY);

    private Fx() {
    }

    public static ExecutorService pool() {
        return POOL;
    }

    /** Execute une action sur le thread JavaFX (immediatement si on y est deja). */
    public static void ui(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Execute un traitement en arriere-plan puis delivre le resultat sur le thread JavaFX.
     *
     * @param work      traitement potentiellement bloquant
     * @param onSuccess appele avec le resultat, sur le thread JavaFX
     * @param onError   appele avec l'exception, sur le thread JavaFX (peut etre null)
     */
    public static <T> void async(Callable<T> work, Consumer<T> onSuccess,
                                 Consumer<Throwable> onError) {
        POOL.submit(() -> {
            try {
                T result = work.call();
                ui(() -> onSuccess.accept(result));
            } catch (Throwable error) {
                Log.error("Echec d'une tache de fond : " + error.getMessage(),
                        error instanceof Exception ? (Exception) error : new Exception(error));
                if (onError != null) {
                    ui(() -> onError.accept(error));
                }
            }
        });
    }

    /** Variante sans valeur de retour. */
    public static void async(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        async(() -> {
            work.run();
            return null;
        }, ignored -> {
            if (onSuccess != null) {
                onSuccess.run();
            }
        }, onError);
    }

    /* ------------------------------------------------------------------ */
    /* Animations                                                          */
    /* ------------------------------------------------------------------ */

    /** Fondu d'apparition. */
    public static void fadeIn(Node node, int millis) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

    /** Fondu de disparition suivi d'une action optionnelle. */
    public static void fadeOut(Node node, int millis, Runnable onFinished) {
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);
        if (onFinished != null) {
            fade.setOnFinished(event -> onFinished.run());
        }
        fade.play();
    }

    /** Apparition combinee : fondu plus glissement vertical, utilisee au changement d'onglet. */
    public static void slideInUp(Node node, int millis, double distance) {
        node.setOpacity(0);
        node.setTranslateY(distance);
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(millis), node);
        slide.setFromY(distance);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.SPLINE(0.16, 1, 0.3, 1));
        fade.play();
        slide.play();
    }

    /** Petit effet de pression au clic sur un bouton. */
    public static void pulse(Node node) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(110), node);
        scale.setFromX(1);
        scale.setFromY(1);
        scale.setToX(0.96);
        scale.setToY(0.96);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }


    /* ------------------------------------------------------------------ */
    /* Courbes de mouvement                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Deceleration douce, sans rebond : la courbe demarre vite puis s'installe.
     * C'est le mouvement par defaut de l'interface.
     */
    public static final Interpolator SMOOTH = Interpolator.SPLINE(0.16, 1, 0.3, 1);

    /**
     * Ressort leger : la valeur depasse brievement sa cible avant d'y revenir.
     *
     * <p>Ce depassement ne peut pas s'obtenir avec {@code Interpolator.SPLINE} : JavaFX
     * impose des points de controle compris entre 0 et 1, ce qui exclut toute courbe
     * sortant de l'intervalle. La courbe est donc ecrite ici, sous la forme classique
     * dite "back out", dont le seul depassement reste discret.</p>
     */
    public static final Interpolator SPRING = new Interpolator() {

        /** Amplitude du depassement ; au-dela de 2 l'effet devient caricatural. */
        private static final double OVERSHOOT = 1.18;

        @Override
        protected double curve(double t) {
            double p = t - 1;
            return 1 + (OVERSHOOT + 1) * p * p * p + OVERSHOOT * p * p;
        }
    };

    /* ------------------------------------------------------------------ */
    /* Effets composes                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Entree d'une page : fondu, montee et leger agrandissement joues ensemble.
     *
     * <p>Le contenu semble avancer vers l'utilisateur plutot que simplement
     * apparaitre, ce qui rend le changement d'onglet lisible sans etre lent.</p>
     */
    public static void enterPage(Node node) {
        node.setOpacity(0);
        node.setTranslateY(18);
        node.setScaleX(0.985);
        node.setScaleY(0.985);

        FadeTransition fade = new FadeTransition(Duration.millis(280), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(SMOOTH);

        TranslateTransition rise = new TranslateTransition(Duration.millis(380), node);
        rise.setFromY(18);
        rise.setToY(0);
        rise.setInterpolator(SMOOTH);

        ScaleTransition grow = new ScaleTransition(Duration.millis(380), node);
        grow.setFromX(0.985);
        grow.setFromY(0.985);
        grow.setToX(1);
        grow.setToY(1);
        grow.setInterpolator(SMOOTH);

        new ParallelTransition(fade, rise, grow).play();
    }

    /**
     * Rend un element sensible au survol : il se souleve legerement et grandit.
     *
     * <p>JavaFX ne connait pas les transitions CSS ; ce comportement doit donc etre
     * anime depuis le code.</p>
     *
     * @param node element a rendre reactif
     * @param lift hauteur du soulevement, en pixels
     */
    public static void hoverLift(Node node, double lift) {
        node.setOnMouseEntered(event -> {
            TranslateTransition up = new TranslateTransition(Duration.millis(180), node);
            up.setToY(-lift);
            up.setInterpolator(SMOOTH);
            ScaleTransition grow = new ScaleTransition(Duration.millis(180), node);
            grow.setToX(1.015);
            grow.setToY(1.015);
            grow.setInterpolator(SMOOTH);
            new ParallelTransition(up, grow).play();
        });
        node.setOnMouseExited(event -> {
            TranslateTransition down = new TranslateTransition(Duration.millis(220), node);
            down.setToY(0);
            down.setInterpolator(SMOOTH);
            ScaleTransition shrink = new ScaleTransition(Duration.millis(220), node);
            shrink.setToX(1);
            shrink.setToY(1);
            shrink.setInterpolator(SMOOTH);
            new ParallelTransition(down, shrink).play();
        });
    }

    /**
     * Deplace un element vers une ordonnee, avec la courbe a ressort.
     * Utilise par l'indicateur qui suit l'onglet selectionne.
     */
    public static void springTo(Node node, double targetY, int millis) {
        TranslateTransition move = new TranslateTransition(Duration.millis(millis), node);
        move.setToY(targetY);
        move.setInterpolator(SPRING);
        move.play();
    }
    /** Arrete le pool de threads (appele a la fermeture du launcher). */
    public static void shutdown() {
        POOL.shutdownNow();
    }
}
