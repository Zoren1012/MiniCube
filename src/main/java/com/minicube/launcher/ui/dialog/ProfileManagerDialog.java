package com.minicube.launcher.ui.dialog;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.GameProfile;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.OsUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.nio.file.Path;

/**
 * Gestion des profils : creation, renommage, suppression.
 *
 * <p>La creation propose de partager le dossier {@code .minecraft} principal ou de
 * donner au profil son propre dossier. Le choix compte : un dossier propre isole les
 * mods et les mondes, un dossier partage reprend ce qui est deja installe.</p>
 */
public class ProfileManagerDialog {

    private final LauncherContext context;
    private final Stage stage = new Stage();

    private final VBox list = new VBox(10);
    private final TextField nameField = new TextField();
    private final ChoiceBox<GameProfile.Preset> presetChoice = new ChoiceBox<>();
    private final CheckBox isolated = new CheckBox(I18n.tr("profiles.isolated"));
    private final Label status = Ui.hint("");

    private boolean changed;

    public ProfileManagerDialog(LauncherContext context, Window owner) {
        this.context = context;

        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Constants.APP_NAME + " - " + I18n.tr("profiles.title"));

        Scene scene = new Scene(buildContent(), 620, 640);
        ThemeManager.apply(scene, context.config().settings().getTheme());
        stage.setScene(scene);

        refresh();
    }

    /**
     * Affiche la fenetre.
     *
     * @return true si la liste des profils a change
     */
    public boolean showAndWait() {
        stage.showAndWait();
        return changed;
    }

    /* ------------------------------------------------------------------ */
    /* Construction                                                        */
    /* ------------------------------------------------------------------ */

    private VBox buildContent() {
        Label title = new Label(I18n.tr("profiles.title"));
        title.getStyleClass().add("page-title");

        nameField.setPromptText(I18n.tr("profiles.name"));
        HBox.setHgrow(nameField, Priority.ALWAYS);

        presetChoice.getItems().setAll(GameProfile.Preset.values());
        presetChoice.setValue(GameProfile.Preset.VANILLA);
        presetChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(GameProfile.Preset preset) {
                return preset == null ? "" : preset.label();
            }

            @Override
            public GameProfile.Preset fromString(String label) {
                return GameProfile.Preset.CUSTOM;
            }
        });

        isolated.setSelected(true);

        Button create = Ui.primaryButton(I18n.tr("profiles.create"), Icons.PLUS);
        create.setOnAction(event -> createProfile());

        HBox creationRow = new HBox(12, nameField, presetChoice, create);
        creationRow.setAlignment(Pos.CENTER_LEFT);

        VBox creationCard = Ui.card(I18n.tr("profiles.new"),
                Ui.hint(I18n.tr("profiles.new.hint")), creationRow, isolated, status);

        VBox listCard = Ui.card(I18n.tr("profiles.existing"), list);
        VBox.setVgrow(listCard, Priority.ALWAYS);

        Button close = Ui.secondaryButton(I18n.tr("install.close"), null);
        close.setOnAction(event -> stage.close());
        HBox actions = new HBox(close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(16, title, creationCard,
                Ui.scroll(listCard), actions);
        content.getStyleClass().addAll("page", "dialog-root");
        content.setPadding(new Insets(24));
        VBox.setVgrow(listCard, Priority.ALWAYS);
        return content;
    }

    /* ------------------------------------------------------------------ */
    /* Liste                                                               */
    /* ------------------------------------------------------------------ */

    private void refresh() {
        list.getChildren().clear();
        String activeId = context.gameProfiles().active()
                .map(GameProfile::getId).orElse("");
        context.gameProfiles().all()
                .forEach(profile -> list.getChildren().add(row(profile, activeId)));
    }

    private Node row(GameProfile profile, String activeId) {
        Label name = new Label(profile.getName());
        name.getStyleClass().add("setting-label");

        String location = profile.isShared()
                ? I18n.tr("profiles.shared")
                : I18n.tr("profiles.own", profile.getDirectory());
        Label detail = Ui.hint(profile.getPreset().label() + "  -  "
                + profile.summaryLabel() + "  -  " + location);
        detail.setWrapText(true);
        detail.setMaxWidth(420);

        VBox texts = new VBox(3, name, detail);

        HBox row = new HBox(12, texts, Ui.growSpacer());
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");

        if (profile.getId().equals(activeId)) {
            Label badge = new Label(I18n.tr("profiles.active"));
            badge.getStyleClass().addAll("chip", "chip-label");
            row.getChildren().add(badge);
        }

        Button folder = Ui.iconButton(Icons.FOLDER, I18n.tr("action.openFolder"));
        folder.setOnAction(event -> OsUtil.openFolder(profile.isShared()
                ? OsUtil.defaultMinecraftDir()
                : Path.of(profile.getDirectory())));
        row.getChildren().add(folder);

        Button rename = Ui.iconButton(Icons.REFRESH, I18n.tr("profiles.rename"));
        rename.setOnAction(event -> renameProfile(profile));
        row.getChildren().add(rename);

        Button delete = Ui.iconButton(Icons.TRASH, I18n.tr("profiles.delete"));
        delete.setOnAction(event -> deleteProfile(profile));
        row.getChildren().add(delete);

        return row;
    }

    /* ------------------------------------------------------------------ */
    /* Actions                                                             */
    /* ------------------------------------------------------------------ */

    private void createProfile() {
        try {
            context.gameProfiles().create(nameField.getText(),
                    presetChoice.getValue(), isolated.isSelected());
            nameField.clear();
            status.setText(I18n.tr("profiles.created"));
            changed = true;
            refresh();
        } catch (RuntimeException e) {
            status.setText(e.getMessage());
        }
    }

    private void renameProfile(GameProfile profile) {
        javafx.scene.control.TextInputDialog dialog =
                new javafx.scene.control.TextInputDialog(profile.getName());
        dialog.initOwner(stage);
        dialog.setHeaderText(I18n.tr("profiles.rename"));
        dialog.setContentText(I18n.tr("profiles.name"));
        dialog.showAndWait().ifPresent(newName -> {
            try {
                context.gameProfiles().rename(profile.getId(), newName);
                changed = true;
                refresh();
            } catch (RuntimeException e) {
                status.setText(e.getMessage());
            }
        });
    }

    /**
     * Supprime un profil apres confirmation.
     *
     * <p>Le message dit explicitement que les fichiers restent : sans cela, personne
     * n'ose supprimer un profil de peur d'y perdre ses mondes.</p>
     */
    private void deleteProfile(GameProfile profile) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                I18n.tr("profiles.delete.confirm", profile.getName()),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(I18n.tr("profiles.delete"));
        confirm.initOwner(stage);
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) {
                return;
            }
            try {
                context.gameProfiles().delete(profile.getId());
                changed = true;
                refresh();
            } catch (RuntimeException e) {
                status.setText(e.getMessage());
            }
        });
    }
}
