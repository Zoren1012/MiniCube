package com.minicube.launcher.ui.view;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.model.InstalledVersion;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.component.CubeLogo;
import com.minicube.launcher.ui.component.GlassBackground;
import com.minicube.launcher.ui.component.PlayerHead;
import com.minicube.launcher.ui.component.ToastLayer;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ossature de la fenetre : fond vivant, barre laterale de verre, zone de contenu,
 * barre de lancement et couche de notifications.
 *
 * <p>Cette classe ne contient aucune logique metier : elle construit les composants et
 * les expose au {@code ShellController}, qui les branche sur les services.</p>
 */
public class ShellView {

    /** Onglets de la barre laterale, dans l'ordre d'affichage. */
    public enum Tab {
        HOME("nav.home", Icons.HOME),
        SKIN("nav.skin", Icons.PERSON),
        SERVERS("nav.servers", Icons.SERVER),
        GRAPHICS("nav.graphics", Icons.MONITOR),
        SHADERS("nav.shaders", Icons.SPARKLE),
        MODS("nav.mods", Icons.PUZZLE),
        DISCORD("nav.discord", Icons.CHAT),
        SUPPORT("nav.support", Icons.HEART),
        STYLE("nav.style", Icons.PALETTE),
        PERFORMANCE("nav.performance", Icons.GAUGE),
        UPDATES("nav.updates", Icons.DOWNLOAD),
        SETTINGS("nav.settings", Icons.SETTINGS),
        LOGS("nav.logs", Icons.JOURNAL);

        private final String labelKey;
        private final String icon;

        Tab(String labelKey, String icon) {
            this.labelKey = labelKey;
            this.icon = icon;
        }

        public String labelKey() {
            return labelKey;
        }

        public String icon() {
            return icon;
        }
    }

    private final StackPane rootStack = new StackPane();
    private final GlassBackground background = new GlassBackground();
    private final BorderPane layout = new BorderPane();
    private final StackPane contentArea = new StackPane();
    private final ToastLayer toastLayer = new ToastLayer();
    private final Map<Tab, ToggleButton> navButtons = new LinkedHashMap<>();

    private final VBox navigation = new VBox(4);
    private final Region navIndicator = new Region();
    private final CubeLogo logo = new CubeLogo(32);

    private final PlayerHead playerHead = new PlayerHead(36);
    private final Label accountName = new Label("-");
    private final Label accountType = new Label("-");
    private final Button accountButton = Ui.iconButton(Icons.LOGOUT, I18n.tr("account.switch"));

    private final ComboBox<InstalledVersion> versionSelector = new ComboBox<>();
    private final Button playButton = new Button(I18n.tr("action.play"));
    private final Button installButton = Ui.iconButton(Icons.PLUS, I18n.tr("install.title"));
    private final ComboBox<com.minicube.launcher.model.GameProfile> profileSelector =
            new ComboBox<>();
    private final Button manageProfilesButton =
            Ui.iconButton(Icons.SETTINGS, I18n.tr("profiles.manage"));
    private final Button folderButton = Ui.iconButton(Icons.FOLDER, I18n.tr("action.openFolder"));
    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("");

    private Tab currentTab = Tab.HOME;
    private boolean indicatorPlaced;

    public ShellView() {
        layout.getStyleClass().add("shell");
        layout.setLeft(buildSidebar());
        layout.setCenter(buildCenter());

        // Le fond vient en premier : tout le reste est translucide et repose dessus.
        rootStack.getChildren().addAll(background, layout, toastLayer);
        rootStack.getStyleClass().add("root-stack");
    }

    /* ------------------------------------------------------------------ */
    /* Construction                                                        */
    /* ------------------------------------------------------------------ */

    private VBox buildSidebar() {
        Label brand = new Label(Constants.APP_NAME);
        brand.getStyleClass().add("brand");

        Label brandVersion = new Label("v" + Constants.APP_VERSION);
        brandVersion.getStyleClass().add("brand-version");

        VBox brandTexts = new VBox(0, brand, brandVersion);
        brandTexts.setAlignment(Pos.CENTER_LEFT);

        HBox brandBox = new HBox(12, logo, brandTexts);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(4, 0, 22, 4));

        ToggleGroup group = new ToggleGroup();
        for (Tab tab : Tab.values()) {
            ToggleButton button = new ToggleButton(I18n.tr(tab.labelKey()));
            button.setGraphic(Icons.of(tab.icon(), 18));
            button.setToggleGroup(group);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            navButtons.put(tab, button);
            navigation.getChildren().add(button);
        }
        // Empeche de deselectionner l'onglet courant en recliquant dessus.
        group.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null && previous != null) {
                group.selectToggle(previous);
            }
        });

        navIndicator.getStyleClass().add("nav-indicator");
        navIndicator.setMouseTransparent(true);

        Pane indicatorLayer = new Pane(navIndicator);
        indicatorLayer.setMouseTransparent(true);

        StackPane navStack = new StackPane(indicatorLayer, navigation);
        navStack.setAlignment(Pos.TOP_LEFT);

        // La pastille ne peut se placer qu'une fois les boutons mesures : on attend
        // donc la premiere mise en page reelle.
        navigation.heightProperty().addListener((observable, old, height) -> {
            if (!indicatorPlaced && height.doubleValue() > 0) {
                moveIndicator(currentTab, false);
            }
        });

        VBox sidebar = new VBox(brandBox, navStack, Ui.verticalSpacer(), buildAccountCard());
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(22, 16, 18, 16));
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(228);
        return sidebar;
    }

    /**
     * Deplace la pastille de selection sous l'onglet demande.
     *
     * @param tab     onglet vise
     * @param animate false pour un placement immediat, au premier affichage
     */
    public void moveIndicator(Tab tab, boolean animate) {
        ToggleButton button = navButtons.get(tab);
        if (button == null || button.getHeight() <= 0) {
            return;
        }
        currentTab = tab;
        indicatorPlaced = true;

        navIndicator.setPrefWidth(button.getWidth());
        navIndicator.setPrefHeight(button.getHeight());
        double target = button.getBoundsInParent().getMinY();

        if (animate) {
            Fx.springTo(navIndicator, target, 420);
        } else {
            navIndicator.setTranslateY(target);
        }
    }

    private HBox buildAccountCard() {
        accountName.getStyleClass().add("account-name");
        accountType.getStyleClass().add("account-type");

        VBox texts = new VBox(1, accountName, accountType);
        texts.setAlignment(Pos.CENTER_LEFT);

        // Le libelle occupe l espace restant : sans cela, l espaceur souple absorberait
        // toute la largeur et les textes seraient tronques des le premier caractere.
        HBox card = new HBox(12, playerHead, texts, accountButton);
        HBox.setHgrow(texts, Priority.ALWAYS);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("account-card");
        card.setPadding(new Insets(11, 12, 11, 11));
        return card;
    }

    private VBox buildCenter() {
        contentArea.getStyleClass().add("content-area");
        VBox.setVgrow(contentArea, Priority.ALWAYS);

        VBox center = new VBox(contentArea, buildLaunchBar());
        center.getStyleClass().add("center-pane");
        return center;
    }

    /** Barre inferieure permanente : version, bouton Jouer et progression. */
    private VBox buildLaunchBar() {
        versionSelector.setPrefWidth(280);
        versionSelector.getStyleClass().add("version-selector");
        versionSelector.setPromptText(I18n.tr("launch.selectVersion"));
        versionSelector.setButtonCell(versionCell());
        versionSelector.setCellFactory(list -> versionCell());

        playButton.setGraphic(Icons.of(Icons.PLAY, 20));
        playButton.getStyleClass().addAll("button-base", "play-button");
        playButton.setPrefHeight(50);
        playButton.setMinWidth(190);
        Fx.hoverLift(playButton, 2);

        progressBar.getStyleClass().add("launch-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(6);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        statusLabel.getStyleClass().add("launch-status");

        profileSelector.setPrefWidth(190);
        profileSelector.getStyleClass().add("version-selector");
        profileSelector.setPromptText(I18n.tr("profiles.select"));

        HBox controls = new HBox(14, profileSelector, manageProfilesButton,
                versionSelector, installButton, folderButton, Ui.growSpacer(),
                statusLabel, playButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(16, 24, 16, 24));

        VBox bar = new VBox(progressBar, controls);
        bar.getStyleClass().add("launch-bar");
        return bar;
    }

    /** Cellule affichant une version avec son chargeur de mods. */
    private ListCell<InstalledVersion> versionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(InstalledVersion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.displayName());
                setGraphic(item.complete() ? null : Icons.of(Icons.WARNING, 14));
            }
        };
    }

    /* ------------------------------------------------------------------ */
    /* Accesseurs utilises par le controleur                               */
    /* ------------------------------------------------------------------ */

    /** Racine a placer dans la scene. */
    public StackPane root() {
        return rootStack;
    }

    public BorderPane layout() {
        return layout;
    }

    public GlassBackground background() {
        return background;
    }

    public CubeLogo logo() {
        return logo;
    }

    public StackPane contentArea() {
        return contentArea;
    }

    public ToastLayer toastLayer() {
        return toastLayer;
    }

    public Map<Tab, ToggleButton> navButtons() {
        return navButtons;
    }

    public PlayerHead playerHead() {
        return playerHead;
    }

    public Label accountName() {
        return accountName;
    }

    public Label accountType() {
        return accountType;
    }

    public Button accountButton() {
        return accountButton;
    }

    public ComboBox<InstalledVersion> versionSelector() {
        return versionSelector;
    }

    public Button playButton() {
        return playButton;
    }

    public Button folderButton() {
        return folderButton;
    }

    public Button installButton() {
        return installButton;
    }

    public ComboBox<com.minicube.launcher.model.GameProfile> profileSelector() {
        return profileSelector;
    }

    public Button manageProfilesButton() {
        return manageProfilesButton;
    }

    public ProgressBar progressBar() {
        return progressBar;
    }

    public Label statusLabel() {
        return statusLabel;
    }

    /** Affiche ou masque la barre de progression du lancement. */
    public void setProgressVisible(boolean visible) {
        progressBar.setVisible(visible);
        progressBar.setManaged(visible);
    }
}
