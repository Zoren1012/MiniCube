package com.minicube.launcher.ui.view;

import com.minicube.launcher.model.Cape;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.component.SkinViewer3D;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Onglet Skin : previsualisation 3D, import d'une texture et gestion des capes.
 */
public class SkinView {

    private final VBox root;
    private final SkinViewer3D viewer = new SkinViewer3D();

    private final Button importButton = Ui.primaryButton(I18n.tr("skin.import"), Icons.FOLDER);
    private final Button applyButton = Ui.primaryButton(I18n.tr("skin.apply"), Icons.CHECK);
    private final Button refreshButton = Ui.secondaryButton(I18n.tr("action.refresh"),
            Icons.REFRESH);
    private final Button resetViewButton = Ui.secondaryButton(I18n.tr("skin.resetView"), null);
    private final CheckBox autoRotate = new CheckBox(I18n.tr("skin.autoRotate"));

    private final ToggleGroup modelGroup = new ToggleGroup();
    private final RadioButton classicModel = new RadioButton(I18n.tr("skin.model.classic"));
    private final RadioButton slimModel = new RadioButton(I18n.tr("skin.model.slim"));

    private final ComboBox<Cape> capeSelector = new ComboBox<>();
    private final Button applyCapeButton = Ui.secondaryButton(I18n.tr("skin.cape.apply"), null);
    private final Button removeCapeButton = Ui.secondaryButton(I18n.tr("skin.cape.remove"), null);

    private final Label selectedFileLabel = Ui.hint(I18n.tr("skin.noFile"));
    private final Label warningLabel = Ui.hint("");

    public SkinView() {
        classicModel.setToggleGroup(modelGroup);
        slimModel.setToggleGroup(modelGroup);
        classicModel.setSelected(true);
        autoRotate.setSelected(true);

        VBox previewCard = Ui.card(I18n.tr("skin.preview"),
                viewer,
                new HBox(12, autoRotate, Ui.growSpacer(), resetViewButton),
                Ui.hint(I18n.tr("skin.dragHint")));
        VBox.setVgrow(viewer, Priority.ALWAYS);
        previewCard.setPrefWidth(360);
        // Sans largeur minimale, la carte de droite comprime celle-ci jusqu'a tronquer
        // ses libelles ("Rotation auto...", "R..." au lieu de "Recentrer").
        previewCard.setMinWidth(340);

        VBox importCard = Ui.card(I18n.tr("skin.manage"),
                selectedFileLabel,
                new HBox(12, importButton, applyButton, refreshButton),
                Ui.divider(),
                Ui.settingRow(I18n.tr("skin.model"), I18n.tr("skin.model.hint"),
                        new HBox(14, classicModel, slimModel)),
                Ui.divider(),
                capeSection(),
                warningLabel);

        HBox columns = new HBox(18, previewCard, importCard);
        HBox.setHgrow(importCard, Priority.ALWAYS);
        columns.setAlignment(Pos.TOP_LEFT);

        root = Ui.page(I18n.tr("skin.title"), I18n.tr("skin.subtitle"), columns);
    }

    private VBox capeSection() {
        Label title = new Label(I18n.tr("skin.cape"));
        title.getStyleClass().add("setting-label");

        capeSelector.setPrefWidth(240);
        capeSelector.setPromptText(I18n.tr("skin.cape.none"));

        HBox controls = new HBox(12, capeSelector, applyCapeButton, removeCapeButton);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox section = new VBox(10, title, Ui.hint(I18n.tr("skin.cape.hint")), controls);
        section.setPadding(new Insets(4, 0, 0, 0));
        return section;
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public VBox root() {
        return root;
    }

    public SkinViewer3D viewer() {
        return viewer;
    }

    public Button importButton() {
        return importButton;
    }

    public Button applyButton() {
        return applyButton;
    }

    public Button refreshButton() {
        return refreshButton;
    }

    public Button resetViewButton() {
        return resetViewButton;
    }

    public CheckBox autoRotate() {
        return autoRotate;
    }

    public RadioButton classicModel() {
        return classicModel;
    }

    public RadioButton slimModel() {
        return slimModel;
    }

    public ComboBox<Cape> capeSelector() {
        return capeSelector;
    }

    public Button applyCapeButton() {
        return applyCapeButton;
    }

    public Button removeCapeButton() {
        return removeCapeButton;
    }

    public Label selectedFileLabel() {
        return selectedFileLabel;
    }

    public Label warningLabel() {
        return warningLabel;
    }
}
