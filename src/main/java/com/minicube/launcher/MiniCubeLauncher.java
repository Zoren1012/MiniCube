package com.minicube.launcher;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.component.DefaultSkin;
import com.minicube.launcher.ui.component.PlayerHead;
import com.minicube.launcher.ui.component.SplashScreen;
import com.minicube.launcher.ui.controller.ShellController;
import com.minicube.launcher.ui.dialog.SetupWizard;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Application JavaFX du launcher.
 *
 * <p>Sequence de demarrage : journalisation, chargement de la configuration, assistant
 * de premier demarrage si necessaire, construction de l'interface, puis reconnexion
 * silencieuse du compte enregistre.</p>
 *
 * <p>Le point d'entree du jar est {@link Bootstrap} : lancer directement cette classe
 * exigerait que JavaFX soit sur le chemin de modules.</p>
 */
public class MiniCubeLauncher extends Application {

    private LauncherContext context;
    private ShellController shell;
    private Stage primaryStage;
    private SplashScreen splash;
    private Scene scene;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        Log.init(LauncherPaths.logsDir());
        Log.info("=== " + Constants.APP_NAME + " " + Constants.APP_VERSION + " ===");
        Log.info("Systeme : " + System.getProperty("os.name") + " (" + System.getProperty("os.arch")
                + "), Java " + System.getProperty("java.version"));
        installExceptionHandler();

        // L'ecran de demarrage apparait avant tout travail : il montre les etapes au
        // lieu de laisser une fenetre vide pendant que le launcher s'initialise.
        splash = new SplashScreen();
        splash.show();
        splash.setStatus(I18n.tr("splash.starting"));

        // L'initialisation est repoussee d'un battement pour que l'ecran ait le temps
        // d'etre peint : sans cela, il resterait blanc jusqu'a la fin des traitements.
        Platform.runLater(() -> initialise(stage));
    }

    /**
     * Deroule l'initialisation, en tenant l'ecran de demarrage informe.
     *
     * <p>Les etapes s'enchainent sur le thread JavaFX, chacune laissant l'interface se
     * rafraichir avant la suivante. Elles sont courtes : la lecture de la configuration
     * et l'inventaire des dossiers, rien qui touche au reseau.</p>
     */
    private void initialise(Stage stage) {
        splash.setStatus(I18n.tr("splash.config"));
        context = new LauncherContext();
        Log.setDebugEnabled(context.config().settings().isDebugMode());
        I18n.setLanguage(context.config().settings().getLanguage());

        splash.setStatus(I18n.tr("splash.folders"));
        context.prepareDirectories();

        stage.setTitle(Constants.APP_NAME);
        stage.setMinWidth(1000);
        stage.setMinHeight(660);
        stage.getIcons().add(PlayerHead.extractHead(DefaultSkin.image()));
        stage.setOnCloseRequest(event -> Log.info("Fermeture du launcher"));

        if (!context.config().settings().isFirstRunCompleted()) {
            // L'assistant est modal : l'ecran de demarrage doit s'effacer avant, sinon
            // il resterait au premier plan par-dessus lui.
            splash.close(() -> runSetupThenShow(stage));
            return;
        }

        splash.setStatus(I18n.tr("splash.interface"));
        buildUserInterface();
        splash.close(() -> revealMainWindow(stage));
    }

    /** Premier demarrage : assistant, puis fenetre principale. */
    private void runSetupThenShow(Stage stage) {
        SetupWizard wizard = new SetupWizard(context, null);
        if (!wizard.showAndWait()) {
            Log.info("Assistant annule : arret du launcher");
            Platform.exit();
            return;
        }
        buildUserInterface();
        revealMainWindow(stage);
    }

    /**
     * Fait apparaitre la fenetre principale en fondu.
     *
     * <p>Elle est montree a opacite nulle puis revelee : afficher d'un coup une fenetre
     * de mille pixels apres un ecran de demarrage en fondu produirait une rupture.</p>
     */
    private void revealMainWindow(Stage stage) {
        stage.setOpacity(0);
        stage.show();

        Timeline reveal = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(stage.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(320),
                        new KeyValue(stage.opacityProperty(), 1, Fx.SMOOTH)));
        reveal.play();

        if (shell != null) {
            Fx.enterPage(shell.root());
        }

        // Renouvellement silencieux de la session : ne doit pas retarder l'affichage.
        Fx.async(() -> {
            context.accounts().refreshActiveQuietly();
            return Boolean.TRUE;
        }, ignored -> { }, error -> Log.debug("Reconnexion automatique ignoree"));
    }

    /**
     * Construit ou reconstruit l'interface.
     *
     * <p>La reconstruction complete est utilisee au changement de langue : tous les
     * libelles etant lus a la construction, c'est le moyen le plus sur de tout traduire
     * sans redemarrer l'application.</p>
     */
    private void buildUserInterface() {
        if (shell != null) {
            shell.dispose();
        }
        shell = new ShellController(context, primaryStage, this::rebuildUserInterface);

        if (scene == null) {
            scene = new Scene(shell.root(), Constants.DEFAULT_WINDOW_WIDTH,
                    Constants.DEFAULT_WINDOW_HEIGHT);
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(shell.root());
        }
        ThemeManager.apply(scene, context.config().settings().getTheme());
        shell.applyTheme();
    }

    /** Recharge entierement l'interface (changement de langue). */
    private void rebuildUserInterface() {
        Log.info("Reconstruction de l'interface (langue : " + I18n.currentLanguage() + ")");
        buildUserInterface();
    }

    /** Journalise toute exception non rattrapee plutot que de la perdre en console. */
    private void installExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                Log.error("Exception non rattrapee dans " + thread.getName(),
                        throwable instanceof Exception exception
                                ? exception : new Exception(throwable)));
    }

    @Override
    public void stop() {
        if (shell != null) {
            shell.dispose();
        }
        if (context != null) {
            // Le serveur de la page de compte ne doit pas survivre au launcher.
            context.profileServer().stop();
        }
        Fx.shutdown();
        Log.info("Launcher arrete");
        Log.close();
    }

    /** Utilise par le plugin JavaFX en developpement ({@code mvn javafx:run}). */
    public static void main(String[] args) {
        launch(args);
    }
}
