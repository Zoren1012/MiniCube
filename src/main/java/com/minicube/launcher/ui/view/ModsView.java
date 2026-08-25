package com.minicube.launcher.ui.view;

import com.minicube.launcher.model.ModEntry;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Onglet Mods : inventaire des mods installes, activation et installation automatique
 * des mods requis par le projet.
 */
public class ModsView {

    private final VBox root;
    private final VBox modContainer = new VBox(10);

    private final TextField searchField = new TextField();
    private final Button refreshButton = Ui.secondaryButton(I18n.tr("action.refresh"),
            Icons.REFRESH);
    private final Button installButton = Ui.primaryButton(I18n.tr("mods.install"), Icons.FOLDER);
    private final Button installRequiredButton =
            Ui.secondaryButton(I18n.tr("mods.installRequired"), Icons.DOWNLOAD);
    private final Button openFolderButton = Ui.iconButton(Icons.FOLDER,
            I18n.tr("mods.openFolder"));
    private final Label summaryLabel = Ui.hint("");

    public ModsView() {
        searchField.setPromptText(I18n.tr("mods.search"));
        searchField.setPrefWidth(260);

        HBox toolbar = new HBox(12, installButton, installRequiredButton, Ui.growSpacer(),
                searchField, refreshButton, openFolderButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox toolbarCard = Ui.card(null, toolbar, summaryLabel);

        VBox listCard = Ui.card(I18n.tr("mods.installed"), modContainer);
        VBox.setVgrow(listCard, Priority.ALWAYS);

        root = Ui.page(I18n.tr("mods.title"), I18n.tr("mods.subtitle"), toolbarCard, listCard);
    }

    public void clearMods() {
        modContainer.getChildren().clear();
    }

    public void showEmpty() {
        modContainer.getChildren().setAll(Ui.emptyState(I18n.tr("mods.empty"), Icons.PUZZLE));
    }

    /**
     * Ajoute une ligne de mod.
     *
     * @param mod      mod decrit
     * @param onToggle appele avec le mod et son nouvel etat
     * @param onDelete appele pour supprimer le mod
     */
    public void addModRow(ModEntry mod, BiConsumer<ModEntry, Boolean> onToggle,
                          Consumer<ModEntry> onDelete) {
        CheckBox toggle = new CheckBox();
        toggle.setSelected(mod.isEnabled());
        toggle.setDisable(mod.isRequired());
        toggle.selectedProperty().addListener((obs, old, enabled) -> onToggle.accept(mod, enabled));

        Label name = new Label(mod.getName());
        name.getStyleClass().add("mod-name");

        HBox titleRow = new HBox(8, name);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (!mod.getVersion().isBlank()) {
            titleRow.getChildren().add(Ui.badge(mod.getVersion(), "badge-neutral"));
        }
        titleRow.getChildren().add(Ui.badge(mod.getLoader(), "badge-soft"));
        if (mod.isRequired()) {
            titleRow.getChildren().add(Ui.badge(I18n.tr("mods.required"), "badge-accent"));
        }
        if (mod.hasUpdate()) {
            titleRow.getChildren().add(
                    Ui.badge(I18n.tr("mods.update", mod.getAvailableUpdate()), "badge-warn"));
        }

        Label details = Ui.hint(mod.getDescription().isBlank()
                ? mod.getFileName() + "  -  " + mod.fileSizeLabel()
                : mod.getDescription());
        details.setMaxWidth(620);

        VBox texts = new VBox(3, titleRow, details);
        HBox.setHgrow(texts, Priority.ALWAYS);

        Button delete = Ui.iconButton(Icons.TRASH, I18n.tr("action.delete"));
        delete.setDisable(mod.isRequired());
        delete.setOnAction(event -> onDelete.accept(mod));

        HBox row = new HBox(14, toggle, texts, Ui.growSpacer(), delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mod-row");
        if (!mod.isEnabled()) {
            row.getStyleClass().add("mod-row-disabled");
        }
        row.setPadding(new Insets(12, 16, 12, 14));
        modContainer.getChildren().add(row);
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public Region root() {
        return root;
    }

    public TextField searchField() {
        return searchField;
    }

    public Button refreshButton() {
        return refreshButton;
    }

    public Button installButton() {
        return installButton;
    }

    public Button installRequiredButton() {
        return installRequiredButton;
    }

    public Button openFolderButton() {
        return openFolderButton;
    }

    public Label summaryLabel() {
        return summaryLabel;
    }
}
