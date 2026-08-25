package com.minicube.launcher.ui.view;

import com.minicube.launcher.model.NewsItem;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import com.minicube.launcher.util.Safety;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Onglet Accueil : bandeau de bienvenue, statistiques de connexion et actualites.
 */
public class HomeView {

    private final VBox root;
    private final VBox newsContainer = new VBox(12);
    private final Label welcomeLabel = new Label();
    private final Label statusValue = new Label("-");
    private final Label playersValue = new Label("-");
    private final Label pingValue = new Label("-");
    private final Label serverNameLabel = new Label("-");
    private final Button refreshButton = Ui.secondaryButton(I18n.tr("action.refresh"),
            Icons.REFRESH);

    public HomeView() {
        welcomeLabel.getStyleClass().add("hero-title");

        Label subtitle = new Label(I18n.tr("home.subtitle"));
        subtitle.getStyleClass().add("hero-subtitle");
        subtitle.setWrapText(true);

        VBox hero = new VBox(6, welcomeLabel, subtitle);
        hero.getStyleClass().add("hero");
        hero.setPadding(new Insets(26, 28, 26, 28));

        HBox stats = new HBox(14,
                statCard(I18n.tr("home.serverStatus"), statusValue, Icons.SERVER),
                statCard(I18n.tr("home.playersOnline"), playersValue, Icons.PERSON),
                statCard(I18n.tr("home.ping"), pingValue, Icons.REFRESH));
        stats.setFillHeight(true);

        Label newsTitle = new Label(I18n.tr("home.news"));
        newsTitle.getStyleClass().add("card-title");

        HBox newsHeader = new HBox(12, newsTitle, Ui.growSpacer(), refreshButton);
        newsHeader.setAlignment(Pos.CENTER_LEFT);

        VBox newsCard = Ui.card(null, newsHeader, newsContainer);
        VBox.setVgrow(newsCard, Priority.ALWAYS);

        root = Ui.page(I18n.tr("home.title"), null, hero, serverLine(), stats, newsCard);
    }

    /** Ligne rappelant le serveur principal surveille. */
    private HBox serverLine() {
        serverNameLabel.getStyleClass().add("setting-hint");
        HBox line = new HBox(8, Icons.of(Icons.SERVER, 15), serverNameLabel);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    /** Vignette de statistique : icone, valeur et libelle. */
    private VBox statCard(String title, Label valueLabel, String icon) {
        valueLabel.getStyleClass().add("stat-value");

        Label name = new Label(title);
        name.getStyleClass().add("stat-label");

        HBox header = new HBox(8, Icons.of(icon, 16), name);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(6, header, valueLabel);
        card.getStyleClass().addAll("card", "stat-card");
        card.setPadding(new Insets(16, 20, 16, 20));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /* ------------------------------------------------------------------ */
    /* Mise a jour du contenu                                              */
    /* ------------------------------------------------------------------ */

    /** Remplace la liste d'actualites affichee. */
    public void setNews(java.util.List<NewsItem> items) {
        newsContainer.getChildren().clear();
        if (items.isEmpty()) {
            newsContainer.getChildren().add(
                    Ui.emptyState(I18n.tr("home.noNews"), Icons.INFO));
            return;
        }
        for (NewsItem item : items) {
            newsContainer.getChildren().add(newsCard(item));
        }
    }

    private Node newsCard(NewsItem item) {
        Label category = Ui.badge(item.category(), "badge-accent");

        Label date = new Label(item.date());
        date.getStyleClass().add("news-date");

        HBox header = new HBox(10, category, Ui.growSpacer(), date);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(item.title());
        title.getStyleClass().add("news-title");
        title.setWrapText(true);

        Label content = new Label(item.content());
        content.getStyleClass().add("news-content");
        content.setWrapText(true);

        VBox card = new VBox(8, header, title, content);
        card.getStyleClass().add("news-card");
        Fx.hoverLift(card, 3);
        card.setPadding(new Insets(16, 18, 16, 18));

        if (item.hasLink()) {
            Button open = Ui.secondaryButton(I18n.tr("home.readMore"), null);
            open.setOnAction(event -> Safety.openWebLink(item.link()));
            HBox actions = new HBox(open);
            actions.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(actions);
        }
        return card;
    }

    public VBox root() {
        return root;
    }

    public Label welcomeLabel() {
        return welcomeLabel;
    }

    public Label statusValue() {
        return statusValue;
    }

    public Label playersValue() {
        return playersValue;
    }

    public Label pingValue() {
        return pingValue;
    }

    public Label serverNameLabel() {
        return serverNameLabel;
    }

    public Button refreshButton() {
        return refreshButton;
    }
}
