package com.minicube.launcher.ui.dialog;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.service.GameFileService;
import com.minicube.launcher.service.LoaderService.Loader;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Installation d'une version de Minecraft, vanilla ou moddee.
 *
 * <p>Sans cette fenetre, le launcher ne savait que lancer des versions deja installees
 * par un autre outil. Elle sait desormais poser aussi <b>Fabric, Quilt, NeoForge et
 * Forge</b> : le joueur choisit son chargeur, sa version, et n'a plus a aller chercher
 * l'installeur officiel ailleurs.</p>
 *
 * <p>Le catalogue affiche depend du chargeur retenu : chacun ne couvre qu'une partie des
 * versions du jeu, et montrer les autres ne menerait qu'a des echecs.</p>
 */
public class VersionInstallDialog {

    private final LauncherContext context;
    private final Stage stage = new Stage();

    private final ChoiceBox<Loader> loaderChoice = new ChoiceBox<>();
    private final ListView<String> list = new ListView<>();
    private final TextField search = new TextField();
    private final CheckBox showSnapshots = new CheckBox(I18n.tr("install.snapshots"));
    private final ComboBox<String> loaderVersion = new ComboBox<>();
    private final Label loaderVersionLabel = new Label(I18n.tr("install.loaderVersion"));
    private final Button installButton = Ui.primaryButton(I18n.tr("install.action"),
            Icons.DOWNLOAD);
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = Ui.hint(I18n.tr("install.loading"));
    /** Titre de la fenetre : il suit le chargeur retenu. */
    private final Label title = new Label(I18n.tr("install.heading"));

    /** Versions du chargeur courant, dans l'ordre d'affichage. */
    private List<String> gameVersions = new ArrayList<>();
    /** Type et date des versions officielles, pour enrichir l'affichage en vanilla. */
    private final Map<String, GameFileService.AvailableVersion> official = new HashMap<>();
    private boolean installed;
    /** Identifiant reellement installe, pour le selectionner au retour. */
    private String installedId = "";

    public VersionInstallDialog(LauncherContext context, Window owner) {
        this.context = context;

        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Constants.APP_NAME + " - " + I18n.tr("install.title"));

        Scene scene = new Scene(buildContent(), 620, 660);
        ThemeManager.apply(scene, context.config().settings().getTheme());
        stage.setScene(scene);

        loadCatalogue();
    }

    /**
     * Affiche la fenetre et attend sa fermeture.
     *
     * @return true si une version a effectivement ete installee
     */
    public boolean showAndWait() {
        stage.showAndWait();
        return installed;
    }

    /** Version posee par la derniere installation reussie. */
    public String installedVersionId() {
        return installedId;
    }

    /* ------------------------------------------------------------------ */
    /* Construction                                                        */
    /* ------------------------------------------------------------------ */

    private VBox buildContent() {
        title.getStyleClass().add("page-title");

        loaderChoice.getItems().setAll(Loader.values());
        loaderChoice.setValue(Loader.VANILLA);
        loaderChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(Loader loader) {
                return loader == null ? "" : loader.label();
            }

            @Override
            public Loader fromString(String label) {
                return Loader.VANILLA;
            }
        });
        loaderChoice.valueProperty().addListener(
                (observable, before, after) -> loadCatalogue());

        search.setPromptText(I18n.tr("install.search"));
        search.textProperty().addListener((observable, old, value) -> applyFilter());
        showSnapshots.selectedProperty().addListener(
                (observable, old, value) -> loadCatalogue());

        list.setCellFactory(view -> versionCell());
        list.getSelectionModel().selectedItemProperty().addListener(
                (observable, old, value) -> onGameVersionSelected(value));
        VBox.setVgrow(list, Priority.ALWAYS);

        loaderVersion.setPrefWidth(220);
        loaderVersion.valueProperty().addListener(
                (observable, old, value) -> updateInstallButton());

        installButton.setDisable(true);
        installButton.setOnAction(event -> install());

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        Button close = Ui.secondaryButton(I18n.tr("install.close"), null);
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(12, close, Ui.growSpacer(), installButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox loaderRow = new HBox(12, new Label(I18n.tr("install.loader")), loaderChoice,
                Ui.growSpacer(), loaderVersionLabel, loaderVersion);
        loaderRow.setAlignment(Pos.CENTER_LEFT);

        HBox filters = new HBox(14, search, showSnapshots);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        showLoaderVersion(false);

        VBox content = new VBox(16, title, Ui.hint(I18n.tr("install.subtitle")),
                loaderRow, filters, list, status, progress, actions);
        content.getStyleClass().addAll("page", "dialog-root");
        content.setPadding(new Insets(24));
        return content;
    }

    /** Cellule montrant l'identifiant et, en vanilla, le type et la date. */
    private ListCell<String> versionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String version, boolean empty) {
                super.updateItem(version, empty);
                if (empty || version == null) {
                    setText(null);
                    return;
                }
                GameFileService.AvailableVersion details = official.get(version);
                if (details == null) {
                    setText(version);
                    return;
                }
                String date = details.releaseTime().length() >= 10
                        ? details.releaseTime().substring(0, 10) : "";
                setText(version + "    " + details.type() + "    " + date);
            }
        };
    }

    private void showLoaderVersion(boolean visible) {
        loaderVersion.setVisible(visible);
        loaderVersion.setManaged(visible);
        loaderVersionLabel.setVisible(visible);
        loaderVersionLabel.setManaged(visible);
    }

    /* ------------------------------------------------------------------ */
    /* Catalogues                                                          */
    /* ------------------------------------------------------------------ */

    /** Recharge la liste des versions pour le chargeur retenu. */
    private void loadCatalogue() {
        Loader loader = loaderChoice.getValue();
        boolean vanilla = loader == Loader.VANILLA;
        title.setText(vanilla
                ? I18n.tr("install.heading")
                : I18n.tr("install.heading.loader", loader.label()));
        showLoaderVersion(!vanilla);
        loaderVersion.getItems().clear();
        list.getItems().clear();
        status.setText(I18n.tr("install.loading"));
        installButton.setDisable(true);

        Fx.async(() -> {
            if (vanilla) {
                List<GameFileService.AvailableVersion> versions =
                        context.gameFiles().fetchAvailableVersions();
                official.clear();
                versions.forEach(version -> official.put(version.id(), version));
                return versions.stream().map(GameFileService.AvailableVersion::id).toList();
            }
            return context.loaders().gameVersions(loader, !showSnapshots.isSelected());
        }, versions -> {
            gameVersions = versions;
            status.setText(I18n.tr("install.available", versions.size()));
            applyFilter();
        }, error -> status.setText(error.getMessage()));
    }

    /** N'affiche que les versions correspondant a la recherche et au type choisi. */
    private void applyFilter() {
        String needle = search.getText() == null
                ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        boolean snapshots = showSnapshots.isSelected();
        boolean vanilla = loaderChoice.getValue() == Loader.VANILLA;

        List<String> visible = gameVersions.stream()
                // Hors vanilla, le catalogue du chargeur est deja filtre a la source.
                .filter(version -> !vanilla || snapshots
                        || official.get(version) == null
                        || "release".equals(official.get(version).type()))
                .filter(version -> needle.isEmpty()
                        || version.toLowerCase(Locale.ROOT).contains(needle))
                .limit(300)
                .toList();
        list.getItems().setAll(visible);
    }

    /** Charge les versions du chargeur disponibles pour la version de jeu retenue. */
    private void onGameVersionSelected(String gameVersion) {
        Loader loader = loaderChoice.getValue();
        if (loader == Loader.VANILLA || gameVersion == null) {
            updateInstallButton();
            return;
        }
        loaderVersion.getItems().clear();
        installButton.setDisable(true);
        status.setText(I18n.tr("install.loaderSearch", loader.label()));

        Fx.async(() -> context.loaders().loaderVersions(loader, gameVersion), versions -> {
            loaderVersion.getItems().setAll(versions);
            if (!versions.isEmpty()) {
                // La plus recente en tete : c'est celle que l'on veut neuf fois sur dix.
                loaderVersion.setValue(versions.get(0));
                status.setText(I18n.tr("install.loaderFound", versions.size(),
                        loader.label()));
            } else {
                status.setText(I18n.tr("install.loaderNone", loader.label(), gameVersion));
            }
            updateInstallButton();
        }, error -> {
            status.setText(error.getMessage());
            updateInstallButton();
        });
    }

    private void updateInstallButton() {
        boolean vanilla = loaderChoice.getValue() == Loader.VANILLA;
        boolean hasGame = list.getSelectionModel().getSelectedItem() != null;
        installButton.setDisable(!hasGame
                || (!vanilla && loaderVersion.getValue() == null));
    }

    /* ------------------------------------------------------------------ */
    /* Installation                                                        */
    /* ------------------------------------------------------------------ */

    /** Telecharge la version selectionnee, son chargeur et tout ce dont ils dependent. */
    private void install() {
        String gameVersion = list.getSelectionModel().getSelectedItem();
        Loader loader = loaderChoice.getValue();
        if (gameVersion == null) {
            return;
        }
        String chosenLoader = loaderVersion.getValue();
        installButton.setDisable(true);
        list.setDisable(true);
        loaderChoice.setDisable(true);
        loaderVersion.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);

        Fx.async(() -> context.loaders().install(loader, gameVersion, chosenLoader,
                value -> Fx.ui(() -> {
                    progress.setProgress(value.isIndeterminate()
                            ? ProgressIndicator.INDETERMINATE_PROGRESS : value.value());
                    status.setText(value.message()
                            + (value.detail().isBlank() ? "" : "  -  " + value.detail()));
                })), versionId -> {
            installed = true;
            installedId = versionId;
            context.notifications().success(I18n.tr("install.title"),
                    I18n.tr("install.done", versionId));
            stage.close();
        }, error -> {
            list.setDisable(false);
            loaderChoice.setDisable(false);
            loaderVersion.setDisable(false);
            installButton.setDisable(false);
            progress.setVisible(false);
            progress.setManaged(false);
            status.setText(error.getMessage());
            context.notifications().error(I18n.tr("install.title"), error.getMessage());
        });
    }
}
