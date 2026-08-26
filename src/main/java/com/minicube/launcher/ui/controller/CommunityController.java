package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.ui.view.CommunityView;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Safety;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * Controleur de l'onglet Discord.
 *
 * <p>Deux actions seulement : ouvrir l'invitation, ou la copier pour la transmettre.
 * L'adresse vient de {@link Constants} et n'est modifiable nulle part dans l'interface.</p>
 */
public class CommunityController {

    private final LauncherContext context;
    private final CommunityView view = new CommunityView();

    public CommunityController(LauncherContext context) {
        this.context = context;

        view.joinButton().setOnAction(event -> open());
        view.copyButton().setOnAction(event -> copy());
    }

    public Node root() {
        return view.root();
    }

    /**
     * Ouvre l'invitation dans le navigateur.
     *
     * <p>Passe par {@link Safety} comme tout lien sortant : seule une adresse web
     * reconnue est transmise au systeme.</p>
     */
    private void open() {
        if (Safety.openWebLink(Constants.DISCORD_INVITE_URL)) {
            context.notifications().info(I18n.tr("discord.title"),
                    I18n.tr("discord.opened"));
        } else {
            context.notifications().error(I18n.tr("discord.title"),
                    I18n.tr("discord.failed"));
        }
    }

    /** Copie l'invitation, pour la coller ailleurs sans la retaper. */
    private void copy() {
        ClipboardContent content = new ClipboardContent();
        content.putString(Constants.DISCORD_INVITE_URL);
        Clipboard.getSystemClipboard().setContent(content);
        context.notifications().success(I18n.tr("discord.title"), I18n.tr("discord.copied"));
    }
}
