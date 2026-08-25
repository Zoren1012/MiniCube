package com.minicube.launcher.ui.dialog;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.service.MinecraftInstallService;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assistant affiche au premier demarrage : selection et validation du dossier
 * {@code .minecraft}.
 *
 * <p>Le dossier standard du systeme est propose d'emblee et valide automatiquement,
 * de sorte que la majorite des utilisateurs n'a qu'a confirmer.</p>
 */
public class SetupWizard {

    private final LauncherContext context;
    private final Stage stage = new Stage();

    private final TextField pathField = new TextField();
    private final Label resultLabel = new Label();
    private final VBox warningsBox = new VBox(4);
    private final Button continueButton = Ui.primaryButton(I18n.tr("setup.continue"), Icons.CHECK);

    private boolean completed;

    public SetupWizard(LauncherContext context, Window owner) {
        this.context = context;

        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.setTitle(Constants.APP_NAME + " - " + I18n.tr("setup.title"));
        stage.setResizable(false);

        Scene scene = new Scene(buildContent(), 660, 460);
        ThemeManager.apply(scene, context.config().settings().getTheme());
        stage.setScene(scene);

        // Propose le chemin deja connu, sinon l'emplacement standard du systeme.
        String existing = context.config().settings().getGameDirectory();
        Path initial = existing.isBlank() ? OsUtil.defaultMinecraftDir() : Path.of(existing);
        pathField.setText(initial.toString());
        validate(initial);
    }

    /**
     * Affiche l'assistant et attend sa fermeture.
     *
     * @return true si l'utilisateur a valide un dossier exploitable
     */
    public boolean showAndWait() {
        stage.showAndWait();
        return completed;
    }

    private VBox buildContent() {
        Label title = new Label(I18n.tr("setup.heading"));
        title.getStyleClass().add("page-title");

        Label subtitle = Ui.hint(I18n.tr("setup.subtitle"));

        pathField.setEditable(false);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browse = Ui.secondaryButton(I18n.tr("setup.browse"), Icons.FOLDER);
        browse.setOnAction(event -> chooseDirectory());

        Button useDefault = Ui.secondaryButton(I18n.tr("setup.useDefault"), null);
        useDefault.setOnAction(event -> {
            Path standard = OsUtil.defaultMinecraftDir();
            pathField.setText(standard.toString());
            validate(standard);
        });

        HBox pathRow = new HBox(10, pathField, browse);
        pathRow.setAlignment(Pos.CENTER_LEFT);

        resultLabel.setWrapText(true);
        resultLabel.getStyleClass().add("setting-label");

        VBox card = Ui.card(I18n.tr("setup.folder"),
                Ui.hint(I18n.tr("setup.folder.hint")),
                pathRow,
                useDefault,
                Ui.divider(),
                resultLabel,
                warningsBox);

        continueButton.setOnAction(event -> confirm());
        continueButton.setDisable(true);

        Button quit = Ui.secondaryButton(I18n.tr("setup.quit"), null);
        quit.setOnAction(event -> {
            completed = false;
            stage.close();
        });

        HBox actions = new HBox(12, quit, Ui.growSpacer(), continueButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(18, title, subtitle, card, Ui.verticalSpacer(), actions);
        content.getStyleClass().addAll("page", "dialog-root");
        content.setPadding(new Insets(26));
        return content;
    }

    private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.tr("setup.browse"));
        Path current = Path.of(pathField.getText());
        if (Files.isDirectory(current)) {
            chooser.setInitialDirectory(current.toFile());
        }
        java.io.File selected = chooser.showDialog(stage);
        if (selected != null) {
            pathField.setText(selected.getAbsolutePath());
            validate(selected.toPath());
        }
    }

    /** Valide le dossier en tache de fond et affiche le verdict. */
    private void validate(Path directory) {
        resultLabel.setText(I18n.tr("setup.checking"));
        warningsBox.getChildren().clear();
        continueButton.setDisable(true);

        Fx.async(() -> context.install().validate(directory), result -> {
            resultLabel.setText(result.message());
            resultLabel.getStyleClass().removeAll("status-online", "status-offline");
            resultLabel.getStyleClass().add(result.valid() ? "status-online" : "status-offline");
            for (String warning : result.warnings()) {
                warningsBox.getChildren().add(Ui.hint("- " + warning));
            }
            continueButton.setDisable(!result.valid());
        }, error -> {
            resultLabel.setText(error.getMessage());
            continueButton.setDisable(true);
        });
    }

    /** Enregistre le dossier choisi et reconstruit les services associes. */
    private void confirm() {
        String directory = pathField.getText();
        MinecraftInstallService.ValidationResult result =
                context.install().validate(Path.of(directory));
        if (!result.valid()) {
            resultLabel.setText(result.message());
            return;
        }
        context.config().settings().setGameDirectory(directory);
        context.config().settings().setFirstRunCompleted(true);
        context.config().save();
        context.rebindGameDirectory();
        context.prepareDirectories();
        // Reprend les reglages deja presents dans le jeu plutot que d'imposer les notres.
        context.options().importInto(context.config().settings().getGraphics());
        context.config().save();

        Log.info("Assistant de premier demarrage termine : " + directory);
        completed = true;
        stage.close();
    }
}
