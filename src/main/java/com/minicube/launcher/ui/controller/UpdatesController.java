package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.service.UpdateService;
import com.minicube.launcher.ui.Confirm;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.view.UpdatesView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Safety;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;

/**
 * Controleur de l'onglet Mise a jour.
 *
 * <p>La verification n'est jamais automatique depuis cet onglet : elle part d'un clic.
 * L'installation demande une confirmation explicite, car elle ferme le launcher.</p>
 */
public class UpdatesController {

    private final LauncherContext context;
    private final UpdatesView view = new UpdatesView();
    private final Window owner;

    private UpdateService.UpdateInfo pending;

    public UpdatesController(LauncherContext context, Window owner) {
        this.context = context;
        this.owner = owner;

        view.checkButton().setOnAction(event -> check());
        view.installButton().setOnAction(event -> install());
        view.releasePageButton().setOnAction(event -> {
            if (pending != null) {
                Safety.openWebLink(pending.releaseUrl());
            }
        });

        describeInstall();
        describeSource();
        loadHistory();
    }

    public Node root() {
        return view.root();
    }

    /* ------------------------------------------------------------------ */
    /* Historique des versions                                             */
    /* ------------------------------------------------------------------ */

    /** Nombre de versions rapportees : de quoi couvrir plusieurs mois sans noyer la page. */
    private static final int HISTORY_SIZE = 15;

    /**
     * Charge l'historique des publications.
     *
     * <p>Independant de la verification de mise a jour : ce qui a change se lit meme
     * quand on est deja a jour, ne serait-ce que pour retrouver quand telle chose est
     * arrivee.</p>
     */
    private void loadHistory() {
        view.historyBox().getChildren().setAll(Ui.hint(I18n.tr("updates.history.loading")));

        Fx.async(() -> context.updates().fetchReleaseHistory(HISTORY_SIZE), notes -> {
            view.historyBox().getChildren().clear();
            if (notes.isEmpty()) {
                view.historyBox().getChildren().add(
                        Ui.hint(I18n.tr("updates.history.none")));
                return;
            }
            notes.forEach(note -> view.historyBox().getChildren().add(historyEntry(note)));
        }, error -> view.historyBox().getChildren().setAll(
                Ui.hint(I18n.tr("updates.history.failed", error.getMessage()))));
    }

    /**
     * Une version de l'historique.
     *
     * <p>La version installee et celles plus recentes sont signalees : sans ce reperage,
     * une liste de numeros ne dit pas ou l'on se situe.</p>
     */
    private Node historyEntry(UpdateService.ReleaseNote note) {
        Label version = new Label("MiniCube " + note.version());
        version.getStyleClass().add("setting-label");

        HBox header = new HBox(10, version);
        header.setAlignment(Pos.CENTER_LEFT);

        if (note.installed()) {
            header.getChildren().add(badge(I18n.tr("updates.history.installed"),
                    "status-online"));
        } else if (note.newer()) {
            header.getChildren().add(badge(I18n.tr("updates.history.newer"), "chip-accent"));
        }
        if (!note.dateLabel().isEmpty()) {
            header.getChildren().addAll(Ui.growSpacer(), Ui.hint(note.dateLabel()));
        }

        VBox entry = new VBox(6, header);

        String notes = note.plainNotes();
        if (!notes.isBlank()) {
            Label body = Ui.hint(notes);
            body.setWrapText(true);
            body.setMaxWidth(760);
            entry.getChildren().add(body);
        }
        if (!note.url().isBlank()) {
            Button page = Ui.secondaryButton(I18n.tr("updates.openPage"), null);
            page.setOnAction(event -> Safety.openWebLink(note.url()));
            HBox row = new HBox(page);
            row.setAlignment(Pos.CENTER_LEFT);
            entry.getChildren().add(row);
        }
        entry.getStyleClass().add("setting-row");
        return entry;
    }

    private Label badge(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().addAll("chip", "chip-label", style);
        return label;
    }

    /** Rappelle comment MiniCube a ete installe : cela determine ce qui sera telecharge. */
    private void describeInstall() {
        view.installKind().setText(UpdateService.isPackagedInstall()
                ? I18n.tr("updates.kind.installed")
                : I18n.tr("updates.kind.portable"));
    }

    /** Indique d'ou viennent les mises a jour, pour que ce ne soit pas une boite noire. */
    private void describeSource() {
        String repository = context.config().settings().getGithubRepo();
        String url = context.config().settings().getUpdateUrl();
        if (!repository.isBlank()) {
            view.sourceLabel().setText(I18n.tr("updates.source.github", repository));
        } else if (!url.isBlank()) {
            view.sourceLabel().setText(I18n.tr("updates.source.url", url));
        } else {
            view.sourceLabel().setText(I18n.tr("updates.source.none"));
        }
    }

    /** Interroge la source configuree. */
    public void check() {
        describeSource();
        view.checkButton().setDisable(true);
        view.setUpdateAvailable(false);
        view.setChangelog(null);
        view.setStatus(I18n.tr("updates.checking"), "", null);

        Fx.async(() -> context.updates().check(), result -> {
            view.checkButton().setDisable(false);
            applyResult(result);
        }, error -> {
            view.checkButton().setDisable(false);
            view.setStatus(I18n.tr("updates.failed"), error.getMessage(), "status-offline");
        });
    }

    /**
     * Traduit le resultat en message.
     *
     * <p>Chaque etat a son propre libelle. Confondre "vous etes a jour" avec "je n'ai
     * pas pu verifier" laisserait croire a l'utilisateur qu'il possede la derniere
     * version alors que personne n'a pu le lui confirmer.</p>
     */
    private void applyResult(UpdateService.CheckResult result) {
        pending = null;

        switch (result.status()) {
            case AVAILABLE -> {
                pending = result.update();
                view.setStatus(I18n.tr("updates.available", pending.version()),
                        I18n.tr("updates.available.detail", pending.assetName(),
                                formatDate(pending.publishedAt())),
                        null);
                view.setChangelog(pending.changelog());
                // Une nouvelle version vient d etre trouvee : l historique la contient
                // desormais, il doit etre relu.
                loadHistory();
                view.setUpdateAvailable(true);
            }
            case UP_TO_DATE -> view.setStatus(I18n.tr("updates.upToDate"),
                    result.detail().isBlank()
                            ? I18n.tr("updates.upToDate.detail", Constants.APP_VERSION)
                            : result.detail(),
                    "status-online");

            case NO_RELEASE -> view.setStatus(I18n.tr("updates.noRelease"),
                    result.detail(), null);

            case NOT_FOUND -> view.setStatus(I18n.tr("updates.notFound"),
                    result.detail(), "status-offline");

            case NOT_CONFIGURED -> view.setStatus(I18n.tr("updates.notConfigured"),
                    I18n.tr("updates.notConfigured.detail"), null);

            case REJECTED -> view.setStatus(I18n.tr("updates.rejected"),
                    result.detail(), "status-offline");

            default -> view.setStatus(I18n.tr("updates.failed"), result.detail(),
                    "status-offline");
        }
    }

    /** La date de GitHub arrive au format ISO ; seul le jour interesse ici. */
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.length() < 10) {
            return "";
        }
        return isoDate.substring(0, 10);
    }

    /** Telecharge puis installe, apres confirmation. */
    private void install() {
        if (pending == null) {
            return;
        }
        String question = UpdateService.isPackagedInstall()
                ? I18n.tr("updates.confirm.installed", pending.version())
                : I18n.tr("updates.confirm.portable", pending.version());

        if (!Confirm.destructive(owner, I18n.tr("updates.title"), question,
                I18n.tr("updates.install"))) {
            return;
        }
        view.installButton().setDisable(true);
        view.checkButton().setDisable(true);
        view.setProgressVisible(true);

        Fx.async(() -> context.updates().download(pending, progress -> Fx.ui(() -> {
            view.progress().setProgress(progress.isIndeterminate()
                    ? javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS
                    : progress.value());
            view.setStatus(progress.message(), progress.detail(), null);
        })), downloaded -> finish(downloaded), error -> {
            view.installButton().setDisable(false);
            view.checkButton().setDisable(false);
            view.setProgressVisible(false);
            view.setStatus(I18n.tr("updates.failed"), error.getMessage(), "status-offline");
            context.notifications().error(I18n.tr("updates.title"), error.getMessage());
        });
    }

    /** Lance l'installation ; le launcher se ferme pour laisser la place. */
    private void finish(Path downloaded) {
        view.setProgressVisible(false);
        view.setStatus(I18n.tr("updates.ready"), I18n.tr("updates.ready.detail"), "status-online");
        try {
            context.updates().install(downloaded);
        } catch (Exception e) {
            view.installButton().setDisable(false);
            view.checkButton().setDisable(false);
            view.setStatus(I18n.tr("updates.failed"), e.getMessage(), "status-offline");
            context.notifications().error(I18n.tr("updates.title"), e.getMessage());
        }
    }
}
