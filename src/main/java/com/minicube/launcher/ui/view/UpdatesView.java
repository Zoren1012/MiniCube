package com.minicube.launcher.ui.view;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Onglet Mise a jour : etat de la version installee et installation d'une nouvelle.
 */
public class UpdatesView {

    private final VBox root;

    private final Label currentVersion = new Label(Constants.APP_VERSION);
    private final Label installKind = Ui.hint("");
    private final Label sourceLabel = Ui.hint("");

    private final Label statusTitle = new Label(I18n.tr("updates.unknown"));
    private final Label statusDetail = Ui.hint(I18n.tr("updates.neverChecked"));
    private final Button checkButton =
            Ui.primaryButton(I18n.tr("updates.check"), Icons.REFRESH);
    private final Button installButton =
            Ui.primaryButton(I18n.tr("updates.install"), Icons.DOWNLOAD);
    private final Button releasePageButton =
            Ui.secondaryButton(I18n.tr("updates.openPage"), null);

    private final VBox changelogCard;
    private final TextArea changelog = new TextArea();

    /** Historique des versions publiees, avec leurs nouveautes. */
    private final VBox historyCard;
    private final VBox historyBox = new VBox(12);
    private final ProgressBar progress = new ProgressBar(0);

    public UpdatesView() {
        currentVersion.getStyleClass().add("hero-title");
        statusTitle.getStyleClass().add("card-title");

        VBox versionBox = new VBox(2, Ui.hint(I18n.tr("updates.installedVersion")),
                currentVersion, installKind);

        HBox header = new HBox(20, versionBox, Ui.growSpacer(), checkButton);
        header.setAlignment(Pos.CENTER_LEFT);

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        installButton.setVisible(false);
        installButton.setManaged(false);
        releasePageButton.setVisible(false);
        releasePageButton.setManaged(false);

        HBox actions = new HBox(12, installButton, releasePageButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox statusCard = Ui.card(null, header, Ui.divider(),
                statusTitle, statusDetail, progress, actions, sourceLabel);

        changelog.setEditable(false);
        changelog.setWrapText(true);
        changelog.getStyleClass().add("console");
        changelog.setPrefHeight(260);
        VBox.setVgrow(changelog, Priority.ALWAYS);

        changelogCard = Ui.card(I18n.tr("updates.changelog"), changelog);
        changelogCard.setVisible(false);
        changelogCard.setManaged(false);

        historyCard = Ui.card(I18n.tr("updates.history"),
                Ui.hint(I18n.tr("updates.history.hint")), historyBox);

        root = Ui.page(I18n.tr("updates.title"), I18n.tr("updates.subtitle"),
                statusCard, changelogCard, historyCard);
    }

    /**
     * Remplace le contenu de l'historique.
     *
     * <p>Chaque version y figure avec ses nouveautes : ce qui a change vaut d'etre lu
     * avant de mettre a jour, et pas seulement quand une nouvelle version existe.</p>
     */
    public VBox historyBox() {
        return historyBox;
    }

    /**
     * Affiche l'etat courant.
     *
     * @param title  message principal
     * @param detail complement
     * @param style  classe de couleur : status-online ou status-offline, ou null
     */
    public void setStatus(String title, String detail, String style) {
        statusTitle.setText(title);
        statusDetail.setText(detail);
        statusTitle.getStyleClass().removeAll("status-online", "status-offline");
        if (style != null) {
            statusTitle.getStyleClass().add(style);
        }
    }

    /** Montre ou cache le bloc de nouveautes. */
    public void setChangelog(String text) {
        boolean visible = text != null && !text.isBlank();
        changelog.setText(visible ? text : "");
        changelogCard.setVisible(visible);
        changelogCard.setManaged(visible);
    }

    /** Montre ou cache les actions d'installation. */
    public void setUpdateAvailable(boolean available) {
        installButton.setVisible(available);
        installButton.setManaged(available);
        releasePageButton.setVisible(available);
        releasePageButton.setManaged(available);
    }

    public void setProgressVisible(boolean visible) {
        progress.setVisible(visible);
        progress.setManaged(visible);
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public Region root() {
        return root;
    }

    public Label installKind() {
        return installKind;
    }

    public Label sourceLabel() {
        return sourceLabel;
    }

    public Button checkButton() {
        return checkButton;
    }

    public Button installButton() {
        return installButton;
    }

    public Button releasePageButton() {
        return releasePageButton;
    }

    public ProgressBar progress() {
        return progress;
    }
}
