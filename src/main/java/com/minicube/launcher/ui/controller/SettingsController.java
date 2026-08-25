package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.service.JavaRuntimeService;
import com.minicube.launcher.service.MinecraftInstallService;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.view.SettingsView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Controleur de l'onglet Parametres.
 *
 * <p>Les rappels fournis au constructeur permettent au controleur principal de reagir
 * aux changements qui depassent l'onglet : theme, langue et dossier de jeu.</p>
 */
public class SettingsController {

    private final LauncherContext context;
    private final SettingsView view = new SettingsView();
    private final Window owner;
    private final Supplier<String> selectedVersion;
    private final Runnable onThemeChanged;
    private final Runnable onLanguageChanged;
    private final Runnable onGameDirectoryChanged;

    private boolean updating;

    public SettingsController(LauncherContext context, Window owner,
                              Supplier<String> selectedVersion, Runnable onThemeChanged,
                              Runnable onLanguageChanged, Runnable onGameDirectoryChanged) {
        this.context = context;
        this.owner = owner;
        this.selectedVersion = selectedVersion;
        this.onThemeChanged = onThemeChanged;
        this.onLanguageChanged = onLanguageChanged;
        this.onGameDirectoryChanged = onGameDirectoryChanged;

        wireActions();
        loadFromSettings();
        detectJavaRuntimes(false);
    }

    public Node root() {
        return view.root();
    }

    private void wireActions() {
        view.ramSlider().valueProperty().addListener((obs, old, value) ->
                view.ramValue().setText(formatRam(value.intValue())));
        view.detectJavaButton().setOnAction(event -> detectJavaRuntimes(true));
        view.javaSelector().valueProperty().addListener((obs, old, installation) -> {
            if (!updating && installation != null) {
                view.customJavaPath().setText(installation.executable().toString());
            }
        });
        view.browseJavaButton().setOnAction(event -> browseJava());
        view.browseBackgroundButton().setOnAction(event -> browseBackground());
        view.clearBackgroundButton().setOnAction(event -> {
            view.backgroundPath().clear();
            save();
        });
        view.changeGameDirButton().setOnAction(event -> changeGameDirectory());
        view.openGameDirButton().setOnAction(event ->
                OsUtil.openFolder(context.paths().gameDir()));
        view.openLogsButton().setOnAction(event ->
                OsUtil.openFolder(com.minicube.launcher.core.LauncherPaths.logsDir()));
        view.cloudPushButton().setOnAction(event -> cloudPush());
        view.cloudPullButton().setOnAction(event -> cloudPull());
        view.checkUpdatesButton().setOnAction(event -> checkUpdates());
        view.verifyIntegrityButton().setOnAction(event -> verifyIntegrity());
        view.resetButton().setOnAction(event -> resetSettings());
        view.saveButton().setOnAction(event -> save());
    }

    private String formatRam(int megabytes) {
        return megabytes >= 1024
                ? String.format("%.1f Go", megabytes / 1024d)
                : megabytes + " Mo";
    }

    /* ------------------------------------------------------------------ */
    /* Chargement                                                          */
    /* ------------------------------------------------------------------ */

    /** Recopie la configuration dans les controles. */
    public final void loadFromSettings() {
        LauncherSettings settings = context.config().settings();
        updating = true;
        try {
            int systemRam = OsUtil.totalSystemRamMb();
            if (systemRam > 2048) {
                view.ramSlider().setMax(Math.min(32768, systemRam));
                view.ramHint().setText(I18n.tr("settings.ram.system",
                        formatRam(systemRam), formatRam(LauncherSettings.recommendedRam())));
            }
            view.ramSlider().setValue(settings.getRamMb());
            view.ramValue().setText(formatRam(settings.getRamMb()));
            view.customJavaPath().setText(settings.getJavaPath());
            view.extraJvmArgs().setText(settings.getExtraJvmArgs());
            view.themeChoice().getSelectionModel().select(settings.isDarkTheme() ? 0 : 1);
            view.languageChoice().getSelectionModel().select(
                    languageIndex(settings.getLanguage()));
            view.backgroundPath().setText(settings.getBackgroundImage());
            view.keepLauncherOpen().setSelected(settings.isKeepLauncherOpen());
            view.notificationsEnabled().setSelected(settings.isNotificationsEnabled());
            view.debugMode().setSelected(settings.isDebugMode());
            view.autoUpdate().setSelected(settings.isAutoUpdateLauncher());
            view.verifyFiles().setSelected(settings.isVerifyFilesBeforeLaunch());
            view.autoInstallMods().setSelected(settings.isAutoInstallRequiredMods());
            view.gameDirectory().setText(settings.getGameDirectory());
            view.msClientId().setText(settings.getMsClientId());
            view.cloudSyncEnabled().setSelected(settings.isCloudSyncEnabled());
            view.cloudSyncUrl().setText(settings.getCloudSyncUrl());
            view.cloudSyncToken().setText(settings.getCloudSyncToken());
        } finally {
            updating = false;
        }
    }

    private int languageIndex(String code) {
        int index = 0;
        for (String key : I18n.SUPPORTED.keySet()) {
            if (key.equals(code)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    private String languageCode(int index) {
        List<String> codes = List.copyOf(I18n.SUPPORTED.keySet());
        return index >= 0 && index < codes.size() ? codes.get(index) : "fr";
    }

    /* ------------------------------------------------------------------ */
    /* Enregistrement                                                      */
    /* ------------------------------------------------------------------ */

    /** Ecrit les controles dans la configuration et applique les effets immediats. */
    private void save() {
        LauncherSettings settings = context.config().settings();
        String previousTheme = settings.getTheme();
        String previousLanguage = settings.getLanguage();
        String previousBackground = settings.getBackgroundImage();

        settings.setRamMb((int) view.ramSlider().getValue());
        settings.setJavaPath(view.customJavaPath().getText().trim());
        settings.setExtraJvmArgs(view.extraJvmArgs().getText().trim());
        settings.setTheme(view.themeChoice().getSelectionModel().getSelectedIndex() == 1
                ? ThemeManager.LIGHT : ThemeManager.DARK);
        settings.setLanguage(languageCode(
                view.languageChoice().getSelectionModel().getSelectedIndex()));
        settings.setBackgroundImage(view.backgroundPath().getText().trim());
        settings.setKeepLauncherOpen(view.keepLauncherOpen().isSelected());
        settings.setNotificationsEnabled(view.notificationsEnabled().isSelected());
        settings.setDebugMode(view.debugMode().isSelected());
        settings.setAutoUpdateLauncher(view.autoUpdate().isSelected());
        settings.setVerifyFilesBeforeLaunch(view.verifyFiles().isSelected());
        settings.setAutoInstallRequiredMods(view.autoInstallMods().isSelected());
        settings.setMsClientId(view.msClientId().getText().trim());
        settings.setCloudSyncEnabled(view.cloudSyncEnabled().isSelected());
        settings.setCloudSyncUrl(view.cloudSyncUrl().getText().trim());
        settings.setCloudSyncToken(view.cloudSyncToken().getText().trim());

        Log.setDebugEnabled(settings.isDebugMode());
        context.config().save();

        if (!previousTheme.equals(settings.getTheme())
                || !previousBackground.equals(settings.getBackgroundImage())) {
            onThemeChanged.run();
        }
        if (!previousLanguage.equals(settings.getLanguage())) {
            I18n.setLanguage(settings.getLanguage());
            onLanguageChanged.run();
        }
        context.notifications().success(I18n.tr("settings.title"), I18n.tr("settings.saved"));
    }

    /* ------------------------------------------------------------------ */
    /* Java                                                                */
    /* ------------------------------------------------------------------ */

    /** Recherche les runtimes Java installes sur la machine. */
    private void detectJavaRuntimes(boolean notify) {
        view.detectJavaButton().setDisable(true);
        Fx.async(() -> context.javaRuntime().detectInstallations(true), installations -> {
            view.detectJavaButton().setDisable(false);
            updating = true;
            view.javaSelector().getItems().setAll(installations);
            String configured = context.config().settings().getJavaPath();
            for (JavaRuntimeService.JavaInstallation installation : installations) {
                if (installation.executable().toString().equals(configured)) {
                    view.javaSelector().getSelectionModel().select(installation);
                }
            }
            updating = false;
            if (notify) {
                context.notifications().info(I18n.tr("settings.title"),
                        I18n.tr("settings.java.found", installations.size()));
            }
        }, error -> {
            view.detectJavaButton().setDisable(false);
            context.notifications().error(I18n.tr("settings.title"), error.getMessage());
        });
    }

    private void browseJava() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("settings.java.browse"));
        java.io.File file = chooser.showOpenDialog(owner);
        if (file != null) {
            view.customJavaPath().setText(file.getAbsolutePath());
        }
    }

    private void browseBackground() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("settings.background.browse"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = chooser.showOpenDialog(owner);
        if (file != null) {
            view.backgroundPath().setText(file.getAbsolutePath());
            save();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Dossier de jeu                                                      */
    /* ------------------------------------------------------------------ */

    /** Change le dossier .minecraft apres validation. */
    private void changeGameDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.tr("settings.gameDir.choose"));
        java.io.File directory = chooser.showDialog(owner);
        if (directory == null) {
            return;
        }
        Path path = directory.toPath();
        MinecraftInstallService.ValidationResult result = context.install().validate(path);
        if (!result.valid()) {
            context.notifications().error(I18n.tr("settings.title"), result.message());
            return;
        }
        context.config().settings().setGameDirectory(path.toString());
        context.config().save();
        context.rebindGameDirectory();
        context.prepareDirectories();
        view.gameDirectory().setText(path.toString());
        context.notifications().success(I18n.tr("settings.title"), result.message());
        onGameDirectoryChanged.run();
    }

    /* ------------------------------------------------------------------ */
    /* Sauvegarde cloud                                                    */
    /* ------------------------------------------------------------------ */

    private void cloudPush() {
        save();
        view.cloudPushButton().setDisable(true);
        Fx.async(() -> {
            context.cloudSync().push();
            return Boolean.TRUE;
        }, ignored -> {
            view.cloudPushButton().setDisable(false);
            context.notifications().success(I18n.tr("settings.cloud"),
                    I18n.tr("settings.cloud.pushed"));
        }, error -> {
            view.cloudPushButton().setDisable(false);
            context.notifications().error(I18n.tr("settings.cloud"), error.getMessage());
        });
    }

    private void cloudPull() {
        view.cloudPullButton().setDisable(true);
        Fx.async(() -> context.cloudSync().pull(), restored -> {
            view.cloudPullButton().setDisable(false);
            if (Boolean.TRUE.equals(restored)) {
                loadFromSettings();
                onThemeChanged.run();
                context.notifications().success(I18n.tr("settings.cloud"),
                        I18n.tr("settings.cloud.pulled"));
            } else {
                context.notifications().info(I18n.tr("settings.cloud"),
                        I18n.tr("settings.cloud.empty"));
            }
        }, error -> {
            view.cloudPullButton().setDisable(false);
            context.notifications().error(I18n.tr("settings.cloud"), error.getMessage());
        });
    }

    /* ------------------------------------------------------------------ */
    /* Maintenance                                                         */
    /* ------------------------------------------------------------------ */

    private void checkUpdates() {
        view.checkUpdatesButton().setDisable(true);
        Fx.async(() -> context.updates().checkForUpdate(), update -> {
            view.checkUpdatesButton().setDisable(false);
            if (update.isEmpty()) {
                context.notifications().info(I18n.tr("settings.title"),
                        I18n.tr("settings.upToDate"));
                return;
            }
            context.notifications().success(I18n.tr("settings.title"),
                    I18n.tr("settings.updateAvailable", update.get().version()));
        }, error -> {
            view.checkUpdatesButton().setDisable(false);
            context.notifications().error(I18n.tr("settings.title"), error.getMessage());
        });
    }

    /** Verifie l'integrite des fichiers de la version selectionnee. */
    private void verifyIntegrity() {
        String version = selectedVersion.get();
        if (version == null || version.isBlank()) {
            context.notifications().warning(I18n.tr("settings.title"),
                    I18n.tr("launch.noVersion"));
            return;
        }
        view.verifyIntegrityButton().setDisable(true);
        Fx.async(() -> {
            var resolved = context.gameFiles().resolve(version);
            return context.gameFiles().checkIntegrity(resolved, progress -> { });
        }, report -> {
            view.verifyIntegrityButton().setDisable(false);
            if (report.isHealthy()) {
                context.notifications().success(I18n.tr("settings.title"), report.summary());
            } else {
                context.notifications().warning(I18n.tr("settings.title"), report.summary());
            }
        }, error -> {
            view.verifyIntegrityButton().setDisable(false);
            context.notifications().error(I18n.tr("settings.title"), error.getMessage());
        });
    }

    /** Remet les parametres a leurs valeurs par defaut, apres confirmation. */
    private void resetSettings() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.tr("settings.reset.confirm"), ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(I18n.tr("settings.reset"));
        confirm.initOwner(owner);
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) {
                return;
            }
            context.config().resetToDefaults();
            loadFromSettings();
            onThemeChanged.run();
            onLanguageChanged.run();
            context.notifications().info(I18n.tr("settings.title"), I18n.tr("settings.wasReset"));
        });
    }
}
