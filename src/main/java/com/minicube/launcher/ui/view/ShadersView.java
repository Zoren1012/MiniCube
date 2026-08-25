package com.minicube.launcher.ui.view;

import com.minicube.launcher.model.ShaderPack;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * Onglet Shaders : activation globale, installation de packs et apercus.
 */
public class ShadersView {

    private final VBox root;
    private final FlowPane packContainer = new FlowPane(16, 16);

    private final CheckBox shadersEnabled = new CheckBox();
    private final Button refreshButton = Ui.secondaryButton(I18n.tr("action.refresh"),
            Icons.REFRESH);
    private final Button installFileButton = Ui.primaryButton(I18n.tr("shaders.installFile"),
            Icons.FOLDER);
    private final TextField urlField = new TextField();
    private final Button installUrlButton = Ui.secondaryButton(I18n.tr("shaders.installUrl"),
            Icons.DOWNLOAD);

    public ShadersView() {
        urlField.setPromptText(I18n.tr("shaders.urlHint"));
        HBox.setHgrow(urlField, Priority.ALWAYS);

        VBox toggleCard = Ui.card(I18n.tr("shaders.activation"),
                Ui.settingRow(I18n.tr("shaders.enable"), I18n.tr("shaders.enable.hint"),
                        shadersEnabled),
                Ui.hint(I18n.tr("shaders.requirement")));

        HBox installRow = new HBox(12, installFileButton, urlField, installUrlButton);
        installRow.setAlignment(Pos.CENTER_LEFT);

        VBox installCard = Ui.card(I18n.tr("shaders.install"),
                Ui.hint(I18n.tr("shaders.install.hint")), installRow);

        Label listTitle = new Label(I18n.tr("shaders.installed"));
        listTitle.getStyleClass().add("card-title");
        HBox listHeader = new HBox(12, listTitle, Ui.growSpacer(), refreshButton);
        listHeader.setAlignment(Pos.CENTER_LEFT);

        packContainer.setPrefWrapLength(900);
        VBox listCard = Ui.card(null, listHeader, packContainer);
        VBox.setVgrow(listCard, Priority.ALWAYS);

        root = Ui.page(I18n.tr("shaders.title"), I18n.tr("shaders.subtitle"),
                toggleCard, installCard, listCard);
    }

    public void clearPacks() {
        packContainer.getChildren().clear();
    }

    public void showEmpty() {
        packContainer.getChildren().setAll(
                Ui.emptyState(I18n.tr("shaders.empty"), Icons.SPARKLE));
    }

    /**
     * Ajoute la vignette d'un pack.
     *
     * @param pack      pack a afficher
     * @param onActivate action du bouton d'activation
     * @param onDelete   action du bouton de suppression
     */
    public void addPackCard(ShaderPack pack, Consumer<ShaderPack> onActivate,
                            Consumer<ShaderPack> onDelete) {
        StackPane preview = new StackPane();
        preview.getStyleClass().add("shader-preview");
        preview.setPrefSize(240, 132);
        preview.setMinSize(240, 132);

        if (pack.getPreviewImage() != null && Files.isRegularFile(pack.getPreviewImage())) {
            ImageView image = new ImageView(
                    new Image(pack.getPreviewImage().toUri().toString(), 240, 132, false, true));
            image.setFitWidth(240);
            image.setFitHeight(132);
            preview.getChildren().add(image);
        } else {
            Node icon = Icons.of(Icons.SPARKLE, 34);
            icon.getStyleClass().add("empty-icon");
            preview.getChildren().add(icon);
        }

        Label name = new Label(pack.getName());
        name.getStyleClass().add("shader-name");
        name.setWrapText(true);
        name.setMaxWidth(230);

        Label size = Ui.hint(pack.fileSizeLabel()
                + (pack.isDirectory() ? "  -  " + I18n.tr("shaders.folder") : ""));

        Button activate = pack.isActive()
                ? Ui.secondaryButton(I18n.tr("shaders.active"), Icons.CHECK)
                : Ui.primaryButton(I18n.tr("shaders.activate"), null);
        activate.setDisable(pack.isActive());
        activate.setOnAction(event -> onActivate.accept(pack));

        Button delete = Ui.iconButton(Icons.TRASH, I18n.tr("action.delete"));
        delete.setOnAction(event -> onDelete.accept(pack));

        HBox actions = new HBox(8, activate, Ui.growSpacer(), delete);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, preview, name, size, actions);
        card.getStyleClass().add("shader-card");
        Fx.hoverLift(card, 4);
        if (pack.isActive()) {
            card.getStyleClass().add("shader-card-active");
        }
        card.setPadding(new Insets(12));
        card.setPrefWidth(264);
        packContainer.getChildren().add(card);
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public Region root() {
        return root;
    }

    public CheckBox shadersEnabled() {
        return shadersEnabled;
    }

    public Button refreshButton() {
        return refreshButton;
    }

    public Button installFileButton() {
        return installFileButton;
    }

    public TextField urlField() {
        return urlField;
    }

    public Button installUrlButton() {
        return installUrlButton;
    }
}
