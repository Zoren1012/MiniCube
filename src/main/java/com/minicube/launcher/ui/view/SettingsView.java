package com.minicube.launcher.ui.view;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.service.JavaRuntimeService;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Onglet Parametres : memoire, runtime Java, apparence, comportement, sauvegarde
 * distante et maintenance.
 */
public class SettingsView {

    private final VBox root;

    private final Slider ramSlider = new Slider(1024, 16384, Constants.DEFAULT_RAM_MB);
    private final Label ramValue = new Label();
    private final Label ramHint = Ui.hint("");

    private final ComboBox<JavaRuntimeService.JavaInstallation> javaSelector = new ComboBox<>();
    private final Button detectJavaButton = Ui.secondaryButton(I18n.tr("settings.java.detect"),
            Icons.REFRESH);
    private final TextField customJavaPath = new TextField();
    private final Button browseJavaButton = Ui.iconButton(Icons.FOLDER,
            I18n.tr("settings.java.browse"));
    private final TextField extraJvmArgs = new TextField();

    private final ChoiceBox<String> themeChoice = new ChoiceBox<>();
    private final ChoiceBox<String> languageChoice = new ChoiceBox<>();
    private final TextField backgroundPath = new TextField();
    private final Button browseBackgroundButton = Ui.iconButton(Icons.FOLDER,
            I18n.tr("settings.background.browse"));
    private final Button clearBackgroundButton = Ui.iconButton(Icons.CLOSE,
            I18n.tr("settings.background.clear"));

    private final CheckBox keepLauncherOpen = new CheckBox();
    private final CheckBox notificationsEnabled = new CheckBox();
    private final CheckBox debugMode = new CheckBox();
    private final CheckBox autoUpdate = new CheckBox();
    private final CheckBox verifyFiles = new CheckBox();
    private final CheckBox autoInstallMods = new CheckBox();

    private final TextField gameDirectory = new TextField();
    private final Button changeGameDirButton = Ui.secondaryButton(I18n.tr("settings.game.change"),
            Icons.FOLDER);
    private final Button openGameDirButton = Ui.iconButton(Icons.FOLDER,
            I18n.tr("action.openFolder"));

    private final TextField msClientId = new TextField();

    private final CheckBox cloudSyncEnabled = new CheckBox();
    private final TextField cloudSyncUrl = new TextField();
    private final PasswordField cloudSyncToken = new PasswordField();
    private final Button cloudPushButton = Ui.secondaryButton(I18n.tr("settings.cloud.push"),
            Icons.DOWNLOAD);
    private final Button cloudPullButton = Ui.secondaryButton(I18n.tr("settings.cloud.pull"),
            Icons.REFRESH);

    private final Button checkUpdatesButton = Ui.secondaryButton(I18n.tr("settings.checkUpdates"),
            Icons.DOWNLOAD);
    private final Button verifyIntegrityButton =
            Ui.secondaryButton(I18n.tr("settings.verifyFiles"), Icons.CHECK);
    private final Button openLogsButton = Ui.secondaryButton(I18n.tr("settings.openLogs"),
            Icons.JOURNAL);
    private final Button resetButton = Ui.dangerButton(I18n.tr("settings.reset"), Icons.WARNING);
    private final Button saveButton = Ui.primaryButton(I18n.tr("settings.save"), Icons.CHECK);

    public SettingsView() {
        root = Ui.page(I18n.tr("settings.title"), I18n.tr("settings.subtitle"),
                memoryCard(), javaCard(), appearanceCard(), behaviourCard(),
                installationCard(), accountCard(), cloudCard(), maintenanceCard(),
                actionsRow());
    }

    private VBox memoryCard() {
        ramSlider.setPrefWidth(320);
        ramSlider.setMajorTickUnit(1024);
        ramSlider.setBlockIncrement(512);
        ramSlider.setSnapToTicks(true);
        ramSlider.setMinorTickCount(1);
        ramValue.getStyleClass().add("slider-value");
        ramValue.setMinWidth(90);
        ramValue.setAlignment(Pos.CENTER_RIGHT);

        HBox control = new HBox(12, ramSlider, ramValue);
        control.setAlignment(Pos.CENTER_LEFT);

        return Ui.card(I18n.tr("settings.memory"),
                Ui.settingRow(I18n.tr("settings.ram"), I18n.tr("settings.ram.hint"), control),
                ramHint);
    }

    private VBox javaCard() {
        javaSelector.setPrefWidth(380);
        javaSelector.setPromptText(I18n.tr("settings.java.auto"));
        customJavaPath.setPromptText(I18n.tr("settings.java.pathHint"));
        HBox.setHgrow(customJavaPath, Priority.ALWAYS);
        extraJvmArgs.setPromptText("-XX:+UseZGC -Dsun.java2d.opengl=true");
        extraJvmArgs.setPrefWidth(430);

        HBox selectorRow = new HBox(12, javaSelector, detectJavaButton);
        selectorRow.setAlignment(Pos.CENTER_LEFT);

        HBox pathRow = new HBox(10, customJavaPath, browseJavaButton);
        pathRow.setAlignment(Pos.CENTER_LEFT);
        pathRow.setPrefWidth(430);

        return Ui.card(I18n.tr("settings.javaRuntime"),
                Ui.settingRow(I18n.tr("settings.java.detected"),
                        I18n.tr("settings.java.detected.hint"), selectorRow),
                Ui.settingRow(I18n.tr("settings.java.custom"), null, pathRow),
                Ui.settingRow(I18n.tr("settings.jvmArgs"), I18n.tr("settings.jvmArgs.hint"),
                        extraJvmArgs));
    }

    private VBox appearanceCard() {
        themeChoice.getItems().addAll(I18n.tr("settings.theme.dark"),
                I18n.tr("settings.theme.light"));
        I18n.SUPPORTED.values().forEach(languageChoice.getItems()::add);

        backgroundPath.setPromptText(I18n.tr("settings.background.hint"));
        HBox.setHgrow(backgroundPath, Priority.ALWAYS);
        HBox backgroundRow = new HBox(10, backgroundPath, browseBackgroundButton,
                clearBackgroundButton);
        backgroundRow.setAlignment(Pos.CENTER_LEFT);
        backgroundRow.setPrefWidth(420);

        return Ui.card(I18n.tr("settings.appearance"),
                Ui.settingRow(I18n.tr("settings.theme"), null, themeChoice),
                Ui.settingRow(I18n.tr("settings.language"), I18n.tr("settings.language.hint"),
                        languageChoice),
                Ui.settingRow(I18n.tr("settings.background"), null, backgroundRow));
    }

    private VBox behaviourCard() {
        return Ui.card(I18n.tr("settings.behaviour"),
                Ui.settingRow(I18n.tr("settings.keepOpen"), I18n.tr("settings.keepOpen.hint"),
                        keepLauncherOpen),
                Ui.settingRow(I18n.tr("settings.notifications"), null, notificationsEnabled),
                Ui.settingRow(I18n.tr("settings.verifyBeforeLaunch"),
                        I18n.tr("settings.verifyBeforeLaunch.hint"), verifyFiles),
                Ui.settingRow(I18n.tr("settings.autoInstallMods"),
                        I18n.tr("settings.autoInstallMods.hint"), autoInstallMods),
                Ui.settingRow(I18n.tr("settings.autoUpdate"), I18n.tr("settings.autoUpdate.hint"),
                        autoUpdate),
                Ui.settingRow(I18n.tr("settings.debug"), I18n.tr("settings.debug.hint"),
                        debugMode));
    }

    private VBox installationCard() {
        gameDirectory.setEditable(false);
        HBox.setHgrow(gameDirectory, Priority.ALWAYS);
        HBox row = new HBox(10, gameDirectory, changeGameDirButton, openGameDirButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(520);

        return Ui.card(I18n.tr("settings.installation"),
                Ui.settingRow(I18n.tr("settings.gameDir"), I18n.tr("settings.gameDir.hint"), row));
    }

    private VBox accountCard() {
        msClientId.setPromptText("00000000-0000-0000-0000-000000000000");
        msClientId.setPrefWidth(360);
        return Ui.card(I18n.tr("settings.microsoft"),
                Ui.settingRow(I18n.tr("settings.clientId"), I18n.tr("settings.clientId.hint"),
                        msClientId));
    }

    private VBox cloudCard() {
        cloudSyncUrl.setPromptText("https://exemple.fr/api/launcher/settings");
        cloudSyncUrl.setPrefWidth(360);
        cloudSyncToken.setPromptText(I18n.tr("settings.cloud.tokenHint"));
        cloudSyncToken.setPrefWidth(360);

        HBox actions = new HBox(12, cloudPushButton, cloudPullButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        return Ui.card(I18n.tr("settings.cloud"),
                Ui.hint(I18n.tr("settings.cloud.hint")),
                Ui.settingRow(I18n.tr("settings.cloud.enable"), null, cloudSyncEnabled),
                Ui.settingRow(I18n.tr("settings.cloud.url"), null, cloudSyncUrl),
                Ui.settingRow(I18n.tr("settings.cloud.token"), null, cloudSyncToken),
                actions);
    }

    private VBox maintenanceCard() {
        HBox actions = new HBox(12, checkUpdatesButton, verifyIntegrityButton, openLogsButton,
                resetButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        Label version = Ui.hint(Constants.APP_NAME + " " + Constants.APP_VERSION);
        return Ui.card(I18n.tr("settings.maintenance"), actions, version);
    }

    private Node actionsRow() {
        HBox row = new HBox(12, saveButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public Region root() {
        return root;
    }

    public Slider ramSlider() {
        return ramSlider;
    }

    public Label ramValue() {
        return ramValue;
    }

    public Label ramHint() {
        return ramHint;
    }

    public ComboBox<JavaRuntimeService.JavaInstallation> javaSelector() {
        return javaSelector;
    }

    public Button detectJavaButton() {
        return detectJavaButton;
    }

    public TextField customJavaPath() {
        return customJavaPath;
    }

    public Button browseJavaButton() {
        return browseJavaButton;
    }

    public TextField extraJvmArgs() {
        return extraJvmArgs;
    }

    public ChoiceBox<String> themeChoice() {
        return themeChoice;
    }

    public ChoiceBox<String> languageChoice() {
        return languageChoice;
    }

    public TextField backgroundPath() {
        return backgroundPath;
    }

    public Button browseBackgroundButton() {
        return browseBackgroundButton;
    }

    public Button clearBackgroundButton() {
        return clearBackgroundButton;
    }

    public CheckBox keepLauncherOpen() {
        return keepLauncherOpen;
    }

    public CheckBox notificationsEnabled() {
        return notificationsEnabled;
    }

    public CheckBox debugMode() {
        return debugMode;
    }

    public CheckBox autoUpdate() {
        return autoUpdate;
    }

    public CheckBox verifyFiles() {
        return verifyFiles;
    }

    public CheckBox autoInstallMods() {
        return autoInstallMods;
    }

    public TextField gameDirectory() {
        return gameDirectory;
    }

    public Button changeGameDirButton() {
        return changeGameDirButton;
    }

    public Button openGameDirButton() {
        return openGameDirButton;
    }

    public TextField msClientId() {
        return msClientId;
    }

    public CheckBox cloudSyncEnabled() {
        return cloudSyncEnabled;
    }

    public TextField cloudSyncUrl() {
        return cloudSyncUrl;
    }

    public PasswordField cloudSyncToken() {
        return cloudSyncToken;
    }

    public Button cloudPushButton() {
        return cloudPushButton;
    }

    public Button cloudPullButton() {
        return cloudPullButton;
    }

    public Button checkUpdatesButton() {
        return checkUpdatesButton;
    }

    public Button verifyIntegrityButton() {
        return verifyIntegrityButton;
    }

    public Button openLogsButton() {
        return openLogsButton;
    }

    public Button resetButton() {
        return resetButton;
    }

    public Button saveButton() {
        return saveButton;
    }
}
