package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.ui.view.StyleView;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * Controleur de l'onglet Style.
 *
 * <p>Chaque changement s'applique immediatement et s'enregistre : un reglage d'apparence
 * se juge a l'oeil, pas apres avoir cherche un bouton Enregistrer.</p>
 */
public class StyleController {

    private final LauncherContext context;
    private final StyleView view = new StyleView();
    private final Window owner;
    private final Runnable onThemeChanged;

    public StyleController(LauncherContext context, Window owner, Runnable onThemeChanged) {
        this.context = context;
        this.owner = owner;
        this.onThemeChanged = onThemeChanged;

        view.setOnThemePicked(this::applyPickedTheme);
        view.accentPicker().setOnAction(event -> applyAccent(view.accentHex()));
        view.resetAccentButton().setOnAction(event -> applyAccent(""));
        view.browseBackgroundButton().setOnAction(event -> browseBackground());
        view.clearBackgroundButton().setOnAction(event -> applyBackground(""));

        loadFromSettings();
    }

    public Node root() {
        return view.root();
    }

    private void loadFromSettings() {
        LauncherSettings settings = context.config().settings();
        view.markActiveTheme(settings.getTheme());
        String accent = settings.getAccentColor();
        view.setAccentHex(accent.isBlank() ? "#7C5CFF" : accent);
        view.backgroundPath().setText(settings.getBackgroundImage());
    }

    /* ------------------------------------------------------------------ */
    /* Theme                                                               */
    /* ------------------------------------------------------------------ */

    private void applyPickedTheme() {
        String theme = view.pendingTheme();
        if (theme.equals(context.config().settings().getTheme())) {
            return;
        }
        context.config().settings().setTheme(theme);
        context.config().save();
        view.markActiveTheme(theme);
        onThemeChanged.run();
        context.notifications().info(I18n.tr("style.title"),
                I18n.tr("style.theme.applied", I18n.tr("style.theme." + theme)));
    }

    /* ------------------------------------------------------------------ */
    /* Accent                                                              */
    /* ------------------------------------------------------------------ */

    private void applyAccent(String hex) {
        context.config().settings().setAccentColor(hex);
        context.config().save();
        onThemeChanged.run();
        if (hex.isBlank()) {
            view.setAccentHex("#7C5CFF");
        }
    }

    /* ------------------------------------------------------------------ */
    /* Fond                                                                */
    /* ------------------------------------------------------------------ */

    private void browseBackground() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.tr("settings.background.browse"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                I18n.tr("settings.background.filter"), "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(owner);
        if (file != null) {
            applyBackground(file.getAbsolutePath());
        }
    }

    private void applyBackground(String path) {
        context.config().settings().setBackgroundImage(path);
        context.config().save();
        view.backgroundPath().setText(path);
        onThemeChanged.run();
    }
}
