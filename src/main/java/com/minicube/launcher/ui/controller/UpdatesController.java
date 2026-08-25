package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.service.UpdateService;
import com.minicube.launcher.ui.Confirm;
import com.minicube.launcher.ui.view.UpdatesView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Safety;
import javafx.scene.Node;
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
    }

    public Node root() {
        return view.root();
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
