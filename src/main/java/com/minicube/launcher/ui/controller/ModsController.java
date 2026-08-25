package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.ModEntry;
import com.minicube.launcher.ui.Confirm;
import com.minicube.launcher.ui.view.ModsView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.OsUtil;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Controleur de l'onglet Mods : inventaire, activation, suppression et installation
 * automatique des mods requis.
 */
public class ModsController {

    private final LauncherContext context;
    private final ModsView view = new ModsView();
    private final Window owner;
    /** Fournit l'identifiant de la version selectionnee dans la barre de lancement. */
    private final Supplier<String> selectedVersion;

    private List<ModEntry> allMods = new ArrayList<>();

    public ModsController(LauncherContext context, Window owner,
                          Supplier<String> selectedVersion) {
        this.context = context;
        this.owner = owner;
        this.selectedVersion = selectedVersion;

        view.refreshButton().setOnAction(event -> refresh());
        view.installButton().setOnAction(event -> installFromFile());
        view.installRequiredButton().setOnAction(event -> installRequired());
        view.openFolderButton().setOnAction(event ->
                OsUtil.openFolder(context.paths().modsDir()));
        view.searchField().textProperty().addListener((obs, old, text) -> render(text));

        refresh();
    }

    public Node root() {
        return view.root();
    }

    /** Relit les dossiers de mods et confronte le resultat au manifeste du projet. */
    public final void refresh() {
        Fx.async(() -> {
            List<ModEntry> mods = context.mods().scan();
            context.mods().annotateWithManifest(mods, selectedVersion.get());
            return mods;
        }, mods -> {
            allMods = mods;
            render(view.searchField().getText());
            updateSummary();
        }, error -> {
            allMods = new ArrayList<>();
            view.showEmpty();
        });
    }

    /** Affiche les mods correspondant au filtre de recherche. */
    private void render(String filter) {
        view.clearMods();
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);

        List<ModEntry> visible = allMods.stream()
                .filter(mod -> needle.isEmpty()
                        || mod.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || mod.getFileName().toLowerCase(Locale.ROOT).contains(needle))
                .toList();

        if (visible.isEmpty()) {
            view.showEmpty();
            return;
        }
        for (ModEntry mod : visible) {
            view.addModRow(mod, this::toggle, this::delete);
        }
    }

    private void updateSummary() {
        long enabled = allMods.stream().filter(ModEntry::isEnabled).count();
        long updates = context.mods().countUpdates(allMods);
        String summary = I18n.tr("mods.summary", allMods.size(), enabled);
        if (updates > 0) {
            summary = summary + "  -  " + I18n.tr("mods.updatesAvailable", updates);
        }
        view.summaryLabel().setText(summary);
    }

    private void toggle(ModEntry mod, boolean enabled) {
        Fx.async(() -> {
            context.mods().setEnabled(mod, enabled);
            return Boolean.TRUE;
        }, ignored -> {
            updateSummary();
            refresh();
        }, error -> {
            context.notifications().error(I18n.tr("mods.title"), error.getMessage());
            refresh();
        });
    }

    private void delete(ModEntry mod) {
        if (!Confirm.destructive(owner, I18n.tr("mods.title"),
                I18n.tr("mods.deleteConfirm", mod.getName()), I18n.tr("action.delete"))) {
            return;
        }
        Fx.async(() -> {
            context.mods().delete(mod);
            return Boolean.TRUE;
        }, ignored -> {
            context.notifications().info(I18n.tr("mods.title"),
                    I18n.tr("mods.deleted", mod.getName()));
            refresh();
        }, error -> context.notifications().error(I18n.tr("mods.title"), error.getMessage()));
    }

    /** Copie un ou plusieurs fichiers .jar dans le dossier des mods. */
    private void installFromFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("mods.choose"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Mods Minecraft", "*.jar"));
        List<java.io.File> files = chooser.showOpenMultipleDialog(owner);
        if (files == null || files.isEmpty()) {
            return;
        }
        Fx.async(() -> {
            int installed = 0;
            for (java.io.File file : files) {
                context.mods().install(file.toPath());
                installed++;
            }
            return installed;
        }, count -> {
            context.notifications().success(I18n.tr("mods.title"),
                    I18n.tr("mods.installedCount", count));
            refresh();
        }, error -> context.notifications().error(I18n.tr("mods.title"), error.getMessage()));
    }

    /** Telecharge les mods declares obligatoires par le manifeste du projet. */
    private void installRequired() {
        view.installRequiredButton().setDisable(true);
        String version = selectedVersion.get();

        Fx.async(() -> context.mods().installRequiredMods(version,
                progress -> Fx.ui(() -> view.summaryLabel().setText(
                        progress.message() + " - " + progress.detail()))),
                count -> {
                    view.installRequiredButton().setDisable(false);
                    if (count == 0) {
                        context.notifications().info(I18n.tr("mods.title"),
                                I18n.tr("mods.allUpToDate"));
                    } else {
                        context.notifications().success(I18n.tr("mods.title"),
                                I18n.tr("mods.installedCount", count));
                    }
                    refresh();
                }, error -> {
                    view.installRequiredButton().setDisable(false);
                    context.notifications().error(I18n.tr("mods.title"), error.getMessage());
                });
    }
}
