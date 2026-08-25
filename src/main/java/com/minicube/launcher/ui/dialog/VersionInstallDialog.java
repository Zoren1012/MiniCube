package com.minicube.launcher.ui.dialog;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.service.GameFileService;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Installation d'une version officielle depuis le catalogue Mojang.
 *
 * <p>Sans cette fenetre, le launcher ne savait que lancer des versions deja installees
 * par un autre outil : sur une machine neuve, il n'y avait rien a lancer. Le
 * telechargement lui-meme existait deja dans {@code GameFileService}, il ne lui
 * manquait qu'une porte d'entree.</p>
 */
public class VersionInstallDialog {

    private final LauncherContext context;
    private final Stage stage = new Stage();

    private final ListView<GameFileService.AvailableVersion> list = new ListView<>();
    private final TextField search = new TextField();
    private final CheckBox showSnapshots = new CheckBox(I18n.tr("install.snapshots"));
    private final Button installButton = Ui.primaryButton(I18n.tr("install.action"),
            Icons.DOWNLOAD);
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = Ui.hint(I18n.tr("install.loading"));

    private List<GameFileService.AvailableVersion> catalogue = new ArrayList<>();
    private boolean installed;

    public VersionInstallDialog(LauncherContext context, Window owner) {
        this.context = context;

        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Constants.APP_NAME + " - " + I18n.tr("install.title"));

        Scene scene = new Scene(buildContent(), 560, 620);
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

    private VBox buildContent() {
        Label title = new Label(I18n.tr("install.heading"));
        title.getStyleClass().add("page-title");

        search.setPromptText(I18n.tr("install.search"));
        search.textProperty().addListener((observable, old, value) -> applyFilter());
        showSnapshots.selectedProperty().addListener((observable, old, value) -> applyFilter());

        list.setCellFactory(view -> versionCell());
        list.getSelectionModel().selectedItemProperty().addListener(
                (observable, old, value) -> installButton.setDisable(value == null));
        VBox.setVgrow(list, Priority.ALWAYS);

        installButton.setDisable(true);
        installButton.setOnAction(event -> install());

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        Button close = Ui.secondaryButton(I18n.tr("install.close"), null);
        close.setOnAction(event -> stage.close());

        HBox actions = new HBox(12, close, Ui.growSpacer(), installButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox filters = new HBox(14, search, showSnapshots);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox content = new VBox(16, title, Ui.hint(I18n.tr("install.subtitle")),
                filters, list, status, progress, actions);
        content.getStyleClass().addAll("page", "dialog-root");
        content.setPadding(new Insets(24));
        return content;
    }

    /** Cellule montrant l'identifiant, le type et la date de publication. */
    private ListCell<GameFileService.AvailableVersion> versionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(GameFileService.AvailableVersion item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String date = item.releaseTime().length() >= 10
                        ? item.releaseTime().substring(0, 10) : "";
                setText(item.id() + "    " + item.type() + "    " + date);
            }
        };
    }

    /** Recupere le catalogue officiel en tache de fond. */
    private void loadCatalogue() {
        Fx.async(() -> context.gameFiles().fetchAvailableVersions(), versions -> {
            catalogue = versions;
            status.setText(I18n.tr("install.available", versions.size()));
            applyFilter();
        }, error -> status.setText(error.getMessage()));
    }

    /** N'affiche que les versions correspondant a la recherche et au type choisi. */
    private void applyFilter() {
        String needle = search.getText() == null
                ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        boolean snapshots = showSnapshots.isSelected();

        List<GameFileService.AvailableVersion> visible = catalogue.stream()
                .filter(version -> snapshots || "release".equals(version.type()))
                .filter(version -> needle.isEmpty()
                        || version.id().toLowerCase(Locale.ROOT).contains(needle))
                .limit(300)
                .toList();
        list.getItems().setAll(visible);
    }

    /** Telecharge la version selectionnee et tout ce dont elle depend. */
    private void install() {
        GameFileService.AvailableVersion target = list.getSelectionModel().getSelectedItem();
        if (target == null) {
            return;
        }
        installButton.setDisable(true);
        list.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);

        Fx.async(() -> {
            context.gameFiles().installVersion(target.id(), value -> Fx.ui(() -> {
                progress.setProgress(value.isIndeterminate()
                        ? javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS
                        : value.value());
                status.setText(value.message()
                        + (value.detail().isBlank() ? "" : "  -  " + value.detail()));
            }));
            return Boolean.TRUE;
        }, done -> {
            installed = true;
            context.notifications().success(I18n.tr("install.title"),
                    I18n.tr("install.done", target.id()));
            stage.close();
        }, error -> {
            list.setDisable(false);
            installButton.setDisable(false);
            progress.setVisible(false);
            progress.setManaged(false);
            status.setText(error.getMessage());
            context.notifications().error(I18n.tr("install.title"), error.getMessage());
        });
    }
}
