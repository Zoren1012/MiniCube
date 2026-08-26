package com.minicube.launcher.ui.view;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Onglet Discord : rejoindre la communaute du projet.
 *
 * <p>L'adresse n'est pas un reglage mais une constante du launcher : elle s'affiche telle
 * quelle, sans champ de saisie. Personne ne peut la remplacer depuis l'interface.</p>
 */
public class CommunityView {

    private final VBox root;
    private final Button joinButton =
            Ui.primaryButton(I18n.tr("discord.join"), Icons.CHAT);
    private final Button copyButton =
            Ui.secondaryButton(I18n.tr("discord.copy"), Icons.CHECK);

    public CommunityView() {
        Label invite = new Label(Constants.DISCORD_INVITE_URL);
        invite.getStyleClass().add("hero-title");
        invite.setWrapText(true);

        VBox hero = new VBox(8, Ui.hint(I18n.tr("discord.hero")), invite);
        hero.getStyleClass().add("hero");

        HBox actions = new HBox(12, joinButton, copyButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox joinCard = Ui.card(I18n.tr("discord.card"),
                Ui.hint(I18n.tr("discord.card.hint")), actions);

        VBox whyCard = Ui.card(I18n.tr("discord.why"),
                bullet(I18n.tr("discord.why.help")),
                bullet(I18n.tr("discord.why.news")),
                bullet(I18n.tr("discord.why.events")));

        root = Ui.page(I18n.tr("discord.title"), I18n.tr("discord.subtitle"),
                hero, joinCard, whyCard);
    }

    /** Une raison de rejoindre, precedee d'une puce. */
    private HBox bullet(String text) {
        Label dot = new Label();
        dot.getStyleClass().addAll("severity-dot", "severity-good");

        Label label = Ui.hint(text);
        label.setWrapText(true);
        label.setMaxWidth(680);

        HBox row = new HBox(12, dot, label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("setting-row");
        return row;
    }

    public VBox root() {
        return root;
    }

    public Button joinButton() {
        return joinButton;
    }

    public Button copyButton() {
        return copyButton;
    }
}
