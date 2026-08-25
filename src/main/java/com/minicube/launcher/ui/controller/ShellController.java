package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.model.InstalledVersion;
import com.minicube.launcher.model.Notification;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.service.GameLaunchService;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.component.PlayerHead;
import com.minicube.launcher.ui.dialog.LoginDialog;
import com.minicube.launcher.ui.dialog.VersionInstallDialog;
import com.minicube.launcher.ui.view.ShellView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controleur principal : navigation entre les onglets, gestion du compte affiche et
 * orchestration du lancement du jeu.
 *
 * <p>Les onglets sont crees a la demande : le premier affichage d'un onglet construit
 * son controleur, les suivants reutilisent l'instance. Le demarrage reste ainsi rapide
 * meme si l'analyse des mods ou des shaders prend du temps.</p>
 */
public class ShellController {

    private final LauncherContext context;
    private final ShellView view = new ShellView();
    private final Stage stage;
    private final Runnable onRebuildRequested;

    private final Map<ShellView.Tab, Node> pages = new EnumMap<>(ShellView.Tab.class);

    private HomeController home;
    private SkinController skin;
    private ServersController servers;
    private GraphicsController graphics;
    private ShadersController shaders;
    private ModsController mods;
    private SettingsController settings;
    private UpdatesController updates;
    private LogsController logs;

    private boolean launching;

    public ShellController(LauncherContext context, Stage stage, Runnable onRebuildRequested) {
        this.context = context;
        this.stage = stage;
        this.onRebuildRequested = onRebuildRequested;

        wireNavigation();
        wireLaunchBar();
        wireAccount();

        context.notifications().addListener(this::onNotification);
        context.accounts().addChangeListener(account -> updateAccountCard());

        updateAccountCard();
        refreshVersions();
        showTab(ShellView.Tab.HOME);
        checkForLauncherUpdate();
    }

    /** Racine a placer dans la scene. */
    public javafx.scene.Parent root() {
        return view.root();
    }

    /** Libere les animations et les ressources des onglets deja construits. */
    public void dispose() {
        view.background().dispose();
        view.logo().dispose();
        if (skin != null) {
            skin.dispose();
        }
        if (logs != null) {
            logs.dispose();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Navigation                                                          */
    /* ------------------------------------------------------------------ */

    private void wireNavigation() {
        for (Map.Entry<ShellView.Tab, ToggleButton> entry : view.navButtons().entrySet()) {
            ShellView.Tab tab = entry.getKey();
            entry.getValue().setOnAction(event -> showTab(tab));
        }
    }

    /** Affiche un onglet, en le construisant au premier acces. */
    public void showTab(ShellView.Tab tab) {
        Node page = pages.computeIfAbsent(tab, this::createPage);
        view.contentArea().getChildren().setAll(page);
        view.navButtons().get(tab).setSelected(true);
        view.moveIndicator(tab, true);
        Fx.enterPage(page);
    }

    private Node createPage(ShellView.Tab tab) {
        switch (tab) {
            case HOME -> {
                home = new HomeController(context);
                return Ui.scroll(home.root());
            }
            case SKIN -> {
                skin = new SkinController(context, stage);
                return Ui.scroll(skin.root());
            }
            case SERVERS -> {
                servers = new ServersController(context, this::playOnServer);
                return Ui.scroll(servers.root());
            }
            case GRAPHICS -> {
                graphics = new GraphicsController(context);
                return Ui.scroll(graphics.root());
            }
            case SHADERS -> {
                shaders = new ShadersController(context, stage);
                return Ui.scroll(shaders.root());
            }
            case MODS -> {
                mods = new ModsController(context, stage, this::selectedVersionId);
                return Ui.scroll(mods.root());
            }
            case UPDATES -> {
                updates = new UpdatesController(context, stage);
                return Ui.scroll(updates.root());
            }
            case SETTINGS -> {
                settings = new SettingsController(context, stage, this::selectedVersionId,
                        this::applyTheme, onRebuildRequested, this::onGameDirectoryChanged);
                return Ui.scroll(settings.root());
            }
            default -> {
                logs = new LogsController(context);
                return logs.root();
            }
        }
    }

    /** Rafraichit les onglets dependant du dossier de jeu apres un changement. */
    private void onGameDirectoryChanged() {
        pages.remove(ShellView.Tab.MODS);
        pages.remove(ShellView.Tab.SHADERS);
        mods = null;
        shaders = null;
        refreshVersions();
        context.notifications().info(I18n.tr("settings.title"),
                I18n.tr("settings.gameDir.changed"));
    }

    /* ------------------------------------------------------------------ */
    /* Compte                                                              */
    /* ------------------------------------------------------------------ */

    private void wireAccount() {
        view.accountButton().setOnAction(event -> openLoginDialog());
    }

    /** Ouvre la fenetre de connexion et met a jour l'affichage au retour. */
    public void openLoginDialog() {
        LoginDialog dialog = new LoginDialog(context, stage);
        Optional<Account> account = dialog.showAndWait();
        account.ifPresent(value -> {
            updateAccountCard();
            if (skin != null) {
                skin.reloadFromAccount();
            }
        });
    }

    /** Met a jour la vignette, le pseudo et le type du compte actif. */
    private void updateAccountCard() {
        Account account = context.accounts().active().orElse(null);
        if (account == null) {
            view.accountName().setText(I18n.tr("account.none"));
            view.accountType().setText(I18n.tr("account.signIn"));
            view.playerHead().setImage(null);
            return;
        }
        view.accountName().setText(account.getUsername());
        view.accountType().setText(account.getType().label());
        loadAvatar(account);
    }

    /** Charge la tete du joueur, avec repli local si le service distant est injoignable. */
    private void loadAvatar(Account account) {
        PlayerHead head = view.playerHead();

        // Une texture importee sur cette machine prime sur le rendu distant : c'est
        // celle que l'utilisateur vient de choisir, et pour un compte hors-ligne c'est
        // la seule qui existe.
        if (account.hasLocalSkin()) {
            java.nio.file.Path local = java.nio.file.Path.of(account.getLocalSkinPath());
            if (java.nio.file.Files.isRegularFile(local)) {
                javafx.scene.image.Image texture =
                        new javafx.scene.image.Image(local.toUri().toString());
                if (!texture.isError()) {
                    head.setImage(PlayerHead.extractHead(texture));
                    return;
                }
            }
        }
        if (account.isOffline() && (account.getSkinUrl() == null
                || account.getSkinUrl().isBlank())) {
            head.setImage(null);
            return;
        }
        head.loadFromUrl(context.accounts().avatarUrl(account), null);
    }

    /* ------------------------------------------------------------------ */
    /* Barre de lancement                                                  */
    /* ------------------------------------------------------------------ */

    private void wireLaunchBar() {
        view.playButton().setOnAction(event -> {
            Fx.pulse(view.playButton());
            if (context.gameLauncher().isRunning()) {
                context.gameLauncher().stopGame();
            } else {
                play(null);
            }
        });
        view.folderButton().setOnAction(event ->
                OsUtil.openFolder(context.paths().gameDir()));
        view.installButton().setOnAction(event -> openVersionInstaller());
        view.versionSelector().valueProperty().addListener((obs, old, version) -> {
            if (version != null) {
                context.config().settings().setLastVersionId(version.id());
                context.config().save();
            }
        });
    }

    /** Recharge la liste des versions installees. */
    public final void refreshVersions() {
        refreshVersions("");
    }

    /**
     * Recharge la liste des versions installees.
     *
     * @param preferred version a selectionner de preference : celle qui vient d etre
     *                  installee, pour que le joueur puisse jouer sans la chercher
     */
    public final void refreshVersions(String preferred) {
        Fx.async(() -> context.install().listVersions(context.paths()), versions -> {
            view.versionSelector().getItems().setAll(versions);
            InstalledVersion selected = versions.stream()
                    .filter(version -> version.id().equals(preferred))
                    .findFirst()
                    .orElseGet(() -> context.install().pickDefaultVersion(versions,
                            context.config().settings().getLastVersionId()));
            if (selected != null) {
                view.versionSelector().getSelectionModel().select(selected);
            } else {
                view.statusLabel().setText(I18n.tr("launch.noVersionFound"));
            }
        }, error -> view.statusLabel().setText(error.getMessage()));
    }

    /**
     * Ouvre le catalogue des versions officielles.
     * La liste des versions installees est rechargee si une installation a eu lieu.
     */
    private void openVersionInstaller() {
        VersionInstallDialog dialog = new VersionInstallDialog(context, stage);
        if (dialog.showAndWait()) {
            refreshVersions(dialog.installedVersionId());
        }
    }

    /** Identifiant de la version selectionnee, ou chaine vide. */
    public String selectedVersionId() {
        InstalledVersion version = view.versionSelector().getValue();
        return version == null ? "" : version.id();
    }

    /** Lance le jeu en se connectant directement au serveur indique. */
    private void playOnServer(ServerEntry server) {
        play(server);
    }

    /* ------------------------------------------------------------------ */
    /* Lancement du jeu                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Prepare et demarre le jeu.
     *
     * @param server serveur a rejoindre directement, ou null pour le menu principal
     */
    public void play(ServerEntry server) {
        if (launching) {
            // Une partie est deja en preparation ou en cours : on le signale plutot
            // que d ignorer silencieusement le clic.
            context.notifications().info(I18n.tr("launch.title"), I18n.tr("launch.alreadyRunning"));
            return;
        }
        InstalledVersion version = view.versionSelector().getValue();
        if (version == null) {
            context.notifications().warning(I18n.tr("launch.title"),
                    I18n.tr("launch.noVersion"));
            return;
        }
        if (context.accounts().active().isEmpty()) {
            context.notifications().info(I18n.tr("launch.title"), I18n.tr("launch.needAccount"));
            openLoginDialog();
            return;
        }
        // Un serveur peut exiger une version precise : on previent sans bloquer.
        if (server != null && !server.getRequiredVersion().isBlank()
                && !version.id().contains(server.getRequiredVersion())) {
            context.notifications().warning(I18n.tr("servers.title"),
                    I18n.tr("servers.versionMismatch", server.getRequiredVersion()));
        }

        setLaunching(true);
        String versionId = version.id();
        boolean installMods = context.config().settings().isAutoInstallRequiredMods();

        Fx.async(() -> {
            Account account = context.accounts().ensureValidSession();
            if (installMods) {
                context.mods().installRequiredMods(versionId, this::publishProgress);
            }
            GameLaunchService.LaunchRequest request =
                    new GameLaunchService.LaunchRequest(versionId, account, server);
            return context.gameLauncher().launch(request, this::publishProgress,
                    exitCode -> Fx.ui(() -> onGameExit(exitCode)));
        }, process -> onGameStarted(), error -> {
            setLaunching(false);
            view.statusLabel().setText("");
            context.notifications().error(I18n.tr("launch.failed"), error.getMessage());
        });
    }

    /** Rapport de progression : toujours reporte sur le thread JavaFX. */
    private void publishProgress(Progress progress) {
        Fx.ui(() -> {
            view.progressBar().setProgress(progress.isIndeterminate()
                    ? javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS
                    : progress.value());
            String detail = progress.detail() == null || progress.detail().isBlank()
                    ? "" : "  -  " + progress.detail();
            view.statusLabel().setText(progress.message() + detail);
        });
    }

    /** Instant du demarrage de la partie, pour comptabiliser le temps de jeu. */
    private long gameStartedAt;

    private void onGameStarted() {
        gameStartedAt = System.currentTimeMillis();
        context.profiles().recordLaunch(selectedVersionId());
        view.statusLabel().setText(I18n.tr("launch.running"));
        view.setProgressVisible(false);
        view.playButton().setDisable(false);
        view.playButton().setText(I18n.tr("action.stop"));
        view.playButton().setGraphic(Icons.of(Icons.STOP, 18));
        context.notifications().success(I18n.tr("launch.title"), I18n.tr("launch.started"));

        if (!context.config().settings().isKeepLauncherOpen()) {
            stage.setIconified(true);
        }
    }

    /** Remet l'interface en etat lorsque le jeu se termine. */
    private void onGameExit(int exitCode) {
        if (gameStartedAt > 0) {
            context.profiles().recordPlayTime(System.currentTimeMillis() - gameStartedAt);
            gameStartedAt = 0;
        }
        setLaunching(false);
        view.statusLabel().setText("");
        stage.setIconified(false);

        if (exitCode == 0) {
            context.notifications().info(I18n.tr("launch.title"), I18n.tr("launch.closed"));
        } else {
            context.notifications().error(I18n.tr("launch.title"),
                    I18n.tr("launch.crashed", exitCode));
        }
    }

    private void setLaunching(boolean active) {
        launching = active;
        view.setProgressVisible(active);
        view.progressBar().setProgress(
                javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS);
        view.playButton().setDisable(active);
        view.versionSelector().setDisable(active);
        if (!active) {
            view.playButton().setText(I18n.tr("action.play"));
            view.playButton().setGraphic(Icons.of(Icons.PLAY, 20));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Apparence et mises a jour                                           */
    /* ------------------------------------------------------------------ */

    /** Reapplique le theme et le fond personnalise a la scene courante. */
    public void applyTheme() {
        if (view.root().getScene() != null) {
            ThemeManager.apply(view.root().getScene(),
                    context.config().settings().getTheme());
        }
        // Les halos du fond suivent le theme : en clair ils s'attenuent, sinon ils
        // formeraient des taches de couleur au lieu d'une lumiere diffuse.
        view.background().setDark(context.config().settings().isDarkTheme());

        // Une image de fond choisie par l'utilisateur se substitue aux halos : elle est
        // posee sur la mise en page, donc au-dessus d'eux et sous le verre.
        ThemeManager.applyBackground(view.layout(),
                context.config().settings().getBackgroundImage());
    }

    private void onNotification(Notification notification) {
        view.toastLayer().show(notification);
    }
    /**
     * Signale au demarrage qu'une version plus recente existe.
     *
     * <p>Le telechargement n'est pas propose ici : une boite de dialogue qui surgit
     * pendant que l'utilisateur veut jouer est une gene. La notification renvoie vers
     * l'onglet Mise a jour, ou l'action est deliberee.</p>
     */
    private void checkForLauncherUpdate() {
        Fx.async(() -> context.updates().checkAtStartup(), update -> {
            if (!update.isAvailable()) {
                return;
            }
            context.notifications().info(I18n.tr("updates.title"),
                    I18n.tr("updates.notification", update.update().version()));
        }, error -> Log.debug("Verification de mise a jour ignoree : " + error.getMessage()));
    }


    /** Onglets deja construits, utilise pour les rafraichir apres un changement global. */
    public List<ShellView.Tab> loadedTabs() {
        return List.copyOf(pages.keySet());
    }
}
