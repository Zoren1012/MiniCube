package com.minicube.launcher;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.component.DefaultSkin;
import com.minicube.launcher.ui.component.PlayerHead;
import com.minicube.launcher.ui.controller.ShellController;
import com.minicube.launcher.ui.dialog.SetupWizard;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
    private Scene scene;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        Log.init(LauncherPaths.logsDir());
        Log.info("=== " + Constants.APP_NAME + " " + Constants.APP_VERSION + " ===");
        Log.info("Systeme : " + System.getProperty("os.name") + " (" + System.getProperty("os.arch")
                + "), Java " + System.getProperty("java.version"));
        installExceptionHandler();

        context = new LauncherContext();
        Log.setDebugEnabled(context.config().settings().isDebugMode());
        I18n.setLanguage(context.config().settings().getLanguage());
        context.prepareDirectories();

        stage.setTitle(Constants.APP_NAME);
        stage.setMinWidth(1000);
        stage.setMinHeight(660);
        stage.getIcons().add(PlayerHead.extractHead(DefaultSkin.image()));
        stage.setOnCloseRequest(event -> Log.info("Fermeture du launcher"));

        if (!context.config().settings().isFirstRunCompleted()) {
            SetupWizard wizard = new SetupWizard(context, null);
            if (!wizard.showAndWait()) {
                Log.info("Assistant annule : arret du launcher");
                Platform.exit();
                return;
            }
        }

        buildUserInterface();
        stage.show();

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
        Fx.shutdown();
        Log.info("Launcher arrete");
        Log.close();
    }

    /** Utilise par le plugin JavaFX en developpement ({@code mvn javafx:run}). */
    public static void main(String[] args) {
        launch(args);
    }
}
