package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.ui.Styles;
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
    private final Runnable onOpenShop;

    public StyleController(LauncherContext context, Window owner, Runnable onThemeChanged,
                           Runnable onOpenShop) {
        this.context = context;
        this.owner = owner;
        this.onThemeChanged = onThemeChanged;
        this.onOpenShop = onOpenShop;

        view.openShopButton().setOnAction(event -> onOpenShop.run());
        view.accentPicker().setOnAction(event -> applyAccent(view.accentHex()));
        view.resetAccentButton().setOnAction(event -> applyAccent(""));
        view.browseBackgroundButton().setOnAction(event -> browseBackground());
        view.clearBackgroundButton().setOnAction(event -> applyBackground(""));

        refresh();
    }

    public Node root() {
        return view.root();
    }

    /**
     * Remet l onglet en accord avec les reglages.
     *
     * <p>Appele aussi depuis l exterieur : un habillage applique dans la Boutique change
     * le theme et la couleur, et cet onglet afficherait sinon l ancien choix.</p>
     */
    public void refresh() {
        LauncherSettings settings = context.config().settings();
        String accent = settings.getAccentColor();
        // Une couleur vide signifie "celle du style" : afficher un violet fixe mentirait
        // des que le style courant n est pas le sombre.
        view.setAccentHex(accent.isBlank() ? styleAccent() : accent);
        view.backgroundPath().setText(settings.getBackgroundImage());
    }

    /** Couleur d accent native du style courant. */
    private String styleAccent() {
        return Styles.of(context.config().settings().getTheme()).accent();
    }

    /* ------------------------------------------------------------------ */
    /* Accent                                                              */
    /* ------------------------------------------------------------------ */

    private void applyAccent(String hex) {
        context.config().settings().setAccentColor(hex);
        context.config().save();
        onThemeChanged.run();
        if (hex.isBlank()) {
            view.setAccentHex(styleAccent());
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
