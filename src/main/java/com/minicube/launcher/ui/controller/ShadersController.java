package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.ShaderPack;
import com.minicube.launcher.ui.Confirm;
import com.minicube.launcher.ui.view.ShadersView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.util.List;

/**
 * Controleur de l'onglet Shaders : installation, activation et suppression des packs.
 */
public class ShadersController {

    private final LauncherContext context;
    private final ShadersView view = new ShadersView();
    private final Window owner;

    private boolean updating;

    public ShadersController(LauncherContext context, Window owner) {
        this.context = context;
        this.owner = owner;

        view.shadersEnabled().setSelected(context.config().settings().isShadersEnabled());
        view.shadersEnabled().selectedProperty().addListener((obs, old, enabled) -> {
            if (!updating) {
                context.shaders().setShadersEnabled(enabled);
                context.notifications().info(I18n.tr("shaders.title"),
                        enabled ? I18n.tr("shaders.enabled") : I18n.tr("shaders.disabled"));
            }
        });
        view.refreshButton().setOnAction(event -> refresh());
        view.installFileButton().setOnAction(event -> installFromFile());
        view.installUrlButton().setOnAction(event -> installFromUrl());

        refresh();
    }

    public Node root() {
        return view.root();
    }

    /** Relit le dossier shaderpacks et reconstruit la grille. */
    public final void refresh() {
        updating = true;
        view.shadersEnabled().setSelected(context.config().settings().isShadersEnabled());
        updating = false;

        Fx.async(() -> context.shaders().scan(), this::render,
                error -> view.showEmpty());
    }

    private void render(List<ShaderPack> packs) {
        view.clearPacks();
        if (packs.isEmpty()) {
            view.showEmpty();
            return;
        }
        for (ShaderPack pack : packs) {
            view.addPackCard(pack, this::activate, this::delete);
        }
    }

    private void activate(ShaderPack pack) {
        context.shaders().activate(pack);
        context.notifications().success(I18n.tr("shaders.title"),
                I18n.tr("shaders.activated", pack.getName()));
        refresh();
    }

    private void delete(ShaderPack pack) {
        if (!Confirm.destructive(owner, I18n.tr("shaders.title"),
                I18n.tr("shaders.deleteConfirm", pack.getName()), I18n.tr("action.delete"))) {
            return;
        }
        Fx.async(() -> {
            context.shaders().delete(pack);
            return Boolean.TRUE;
        }, ignored -> {
            context.notifications().info(I18n.tr("shaders.title"),
                    I18n.tr("shaders.deleted", pack.getName()));
            refresh();
        }, error -> context.notifications().error(I18n.tr("shaders.title"),
                error.getMessage()));
    }

    /** Installe un pack depuis une archive choisie sur le disque. */
    private void installFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("shaders.choose"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archives ZIP", "*.zip"));
        java.io.File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        Fx.async(() -> context.shaders().install(file.toPath()), pack -> {
            context.notifications().success(I18n.tr("shaders.title"),
                    I18n.tr("shaders.installedPack", pack.getName()));
            refresh();
        }, error -> context.notifications().error(I18n.tr("shaders.title"),
                error.getMessage()));
    }

    /** Telecharge et installe un pack depuis une adresse. */
    private void installFromUrl() {
        String url = view.urlField().getText();
        if (url == null || url.isBlank()) {
            context.notifications().warning(I18n.tr("shaders.title"),
                    I18n.tr("shaders.urlMissing"));
            return;
        }
        view.installUrlButton().setDisable(true);
        Fx.async(() -> context.shaders().installFromUrl(url.trim(), null), pack -> {
            view.installUrlButton().setDisable(false);
            view.urlField().clear();
            context.notifications().success(I18n.tr("shaders.title"),
                    I18n.tr("shaders.installedPack", pack.getName()));
            refresh();
        }, error -> {
            view.installUrlButton().setDisable(false);
            context.notifications().error(I18n.tr("shaders.title"), error.getMessage());
        });
    }
}
