package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.component.ThemedAnimation;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Onglet Boutique et soutien, en attendant son contenu.
 *
 * <p>Une page vide serait prise pour une panne. Elle annonce donc franchement ce qui est
 * en cours, et l'animation — dessinee dans le langage du theme actif — montre que le
 * launcher fonctionne.</p>
 */
public class SupportView {

    private final VBox root;
    private final ThemedAnimation animation = new ThemedAnimation();

    public SupportView() {
        Label title = new Label(I18n.tr("support.building"));
        title.getStyleClass().add("hero-title");
        title.setWrapText(true);

        Label detail = Ui.hint(I18n.tr("support.building.hint"));
        detail.setWrapText(true);
        detail.setMaxWidth(620);

        VBox texts = new VBox(10, title, detail, animation);
        texts.setAlignment(Pos.TOP_LEFT);

        VBox hero = new VBox(texts);
        hero.getStyleClass().add("hero");

        VBox plannedCard = Ui.card(I18n.tr("support.planned"),
                Ui.hint(I18n.tr("support.planned.hint")),
                planned(I18n.tr("support.planned.cosmetics")),
                planned(I18n.tr("support.planned.ranks")),
                planned(I18n.tr("support.planned.donate")));

        root = Ui.page(I18n.tr("support.title"), I18n.tr("support.subtitle"),
                hero, plannedCard);
    }

    /** Une entree a venir, signalee comme telle plutot que promise. */
    private HBox planned(String text) {
        Label dot = new Label();
        dot.getStyleClass().addAll("severity-dot", "severity-advice");

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

    /** Animation a rafraichir au changement de theme. */
    public ThemedAnimation animation() {
        return animation;
    }
}
