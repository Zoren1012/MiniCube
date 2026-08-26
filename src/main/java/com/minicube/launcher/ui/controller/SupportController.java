package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.ui.Cosmetics;
import com.minicube.launcher.ui.view.SupportView;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;

/**
 * Controleur de l'onglet Boutique.
 *
 * <p>Un habillage pose deux reglages d'un coup — le style et la couleur d'accent — puis
 * s'applique aussitot. C'est la difference avec l'onglet Style, ou les deux se reglent
 * separement : ici on choisit un resultat, pas des ingredients.</p>
 */
public class SupportController {

    private final LauncherContext context;
    private final SupportView view = new SupportView();
    private final Runnable onThemeChanged;

    public SupportController(LauncherContext context, Runnable onThemeChanged) {
        this.context = context;
        this.onThemeChanged = onThemeChanged;

        view.setOnPackPicked(this::apply);
        refresh();
    }

    public Node root() {
        return view.root();
    }

    public SupportView view() {
        return view;
    }

    /** Remet la marque de selection en accord avec les reglages courants. */
    public void refresh() {
        LauncherSettings settings = context.config().settings();
        view.markActive(settings.getTheme(), settings.getAccentColor());
    }

    /**
     * Applique un habillage.
     *
     * <p>La couleur est ecrite en clair dans les reglages plutot que d'etre deduite du
     * style : l'utilisateur peut ensuite la retoucher dans l'onglet Style sans que son
     * choix soit ecrase au prochain demarrage.</p>
     */
    private void apply(Cosmetics.Pack pack) {
        LauncherSettings settings = context.config().settings();
        if (Cosmetics.isActive(pack, settings.getTheme(), settings.getAccentColor())) {
            return;
        }
        settings.setTheme(pack.style());
        settings.setAccentColor(pack.accent());
        context.config().save();

        onThemeChanged.run();
        refresh();
        context.notifications().success(I18n.tr("support.title"),
                I18n.tr("support.applied", I18n.tr("support.pack." + pack.id())));
    }
}
