package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Onglet Journal : consultation en direct des messages du launcher et du jeu.
 */
public class LogsView {

    private final VBox root;
    private final TextArea console = new TextArea();

    private final Button clearButton = Ui.secondaryButton(I18n.tr("logs.clear"), Icons.TRASH);
    private final Button copyButton = Ui.secondaryButton(I18n.tr("logs.copy"), null);
    private final Button openFolderButton = Ui.secondaryButton(I18n.tr("logs.openFolder"),
            Icons.FOLDER);
    private final CheckBox autoScroll = new CheckBox(I18n.tr("logs.autoScroll"));
    private final CheckBox showDebug = new CheckBox(I18n.tr("logs.showDebug"));

    public LogsView() {
        console.setEditable(false);
        console.setWrapText(false);
        console.getStyleClass().add("console");
        VBox.setVgrow(console, Priority.ALWAYS);
        autoScroll.setSelected(true);

        HBox toolbar = new HBox(12, clearButton, copyButton, openFolderButton,
                Ui.growSpacer(), showDebug, autoScroll);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox card = Ui.card(null, toolbar, console);
        VBox.setVgrow(card, Priority.ALWAYS);

        root = Ui.page(I18n.tr("logs.title"), I18n.tr("logs.subtitle"), card);
    }

    /** Ajoute une ligne au bas de la console. */
    public void appendLine(String line) {
        console.appendText(line + System.lineSeparator());
        if (autoScroll.isSelected()) {
            console.positionCaret(console.getLength());
        }
    }

    /** Remplace integralement le contenu affiche. */
    public void setContent(String content) {
        console.setText(content);
        if (autoScroll.isSelected()) {
            console.positionCaret(console.getLength());
        }
    }

    public String content() {
        return console.getText();
    }

    public Region root() {
        return root;
    }

    public Button clearButton() {
        return clearButton;
    }

    public Button copyButton() {
        return copyButton;
    }

    public Button openFolderButton() {
        return openFolderButton;
    }

    public CheckBox showDebug() {
        return showDebug;
    }
}
