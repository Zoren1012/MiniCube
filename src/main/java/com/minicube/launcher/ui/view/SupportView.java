package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Cosmetics;
import com.minicube.launcher.ui.Styles;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.component.ThemedAnimation;
import com.minicube.launcher.util.I18n;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Onglet Boutique : le catalogue des cosmetiques du launcher.
 *
 * <p>La boutique payante n'existe pas encore, et la page le dit. Ce qui existe deja —
 * les habillages — est offert et s'applique d'un clic : une boutique vide se lit comme
 * une panne, une boutique qui donne quelque chose se lit comme une boutique.</p>
 */
public class SupportView {

    private final VBox root;
    private final ThemedAnimation animation = new ThemedAnimation();
    /** Cartes des habillages, retrouvees pour marquer celle qui est appliquee. */
    private final Map<String, VBox> packCards = new LinkedHashMap<>();

    private Consumer<Cosmetics.Pack> onPackPicked = pack -> { };

    public SupportView() {
        root = Ui.page(I18n.tr("support.title"), I18n.tr("support.subtitle"),
                hero(), packsCard(), plannedCard());
    }

    /* ------------------------------------------------------------------ */
    /* En-tete                                                             */
    /* ------------------------------------------------------------------ */

    private VBox hero() {
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
        return hero;
    }

    /* ------------------------------------------------------------------ */
    /* Habillages                                                          */
    /* ------------------------------------------------------------------ */

    private VBox packsCard() {
        FlowPane grid = new FlowPane(16, 16);
        Cosmetics.all().forEach(pack -> grid.getChildren().add(packCard(pack)));
        return Ui.card(I18n.tr("support.packs"),
                Ui.hint(I18n.tr("support.packs.hint")), grid);
    }

    /**
     * Vignette d'un habillage.
     *
     * <p>Elle est peinte avec les couleurs du style qu'elle represente et l'accent de
     * l'habillage, pas avec le theme applique a l'ecran : sinon toutes les vignettes se
     * ressembleraient, et le catalogue ne servirait a rien.</p>
     */
    private VBox packCard(Cosmetics.Pack pack) {
        Styles.Style style = Styles.of(pack.style());
        int radius = style.matte() ? 0 : 8;

        Region sidebar = block(30, 82, style.surface(), radius);
        VBox lines = new VBox(6, block(84, 9, style.surface(), radius),
                block(64, 9, style.surface(), radius),
                block(52, 18, pack.accent(), radius));
        lines.setAlignment(Pos.TOP_LEFT);

        HBox mock = new HBox(8, sidebar, lines);
        mock.setPadding(new Insets(9));
        mock.setStyle("-fx-background-color: " + style.panel() + ";"
                + "-fx-background-radius: " + radius + ";");

        Label name = new Label(I18n.tr("support.pack." + pack.id()));
        name.getStyleClass().add("setting-label");

        Label price = Ui.badge(I18n.tr("support.free"), "chip-accent");

        HBox footer = new HBox(8, name, Ui.growSpacer(), price);
        footer.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(11, mock, footer);
        card.getStyleClass().addAll("card", "theme-card");
        card.setPadding(new Insets(14));
        card.setMinWidth(190);
        card.setPrefWidth(190);
        card.setOnMouseClicked(event -> onPackPicked.accept(pack));

        packCards.put(pack.id(), card);
        return card;
    }

    private Region block(double width, double height, String color, int radius) {
        Region region = new Region();
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        region.setStyle("-fx-background-color: " + color + ";"
                + "-fx-background-radius: " + radius + ";");
        return region;
    }

    /**
     * Marque l'habillage correspondant aux reglages courants, ou aucun.
     *
     * <p>Aucun n'est marque quand la couleur d'accent a ete choisie a la main dans
     * l'onglet Style : c'est exact, et c'est preferable a designer un habillage voisin
     * que l'utilisateur n'a pas choisi.</p>
     */
    public void markActive(String theme, String accent) {
        String active = Cosmetics.all().stream()
                .filter(pack -> Cosmetics.isActive(pack, theme, accent))
                .findFirst()
                .map(Cosmetics.Pack::id)
                .orElse("");
        // Meme pseudo-classe que les vignettes de l'onglet Style : la marque de
        // selection est deja dessinee dans base.css, il n'y a rien a y ajouter.
        packCards.forEach((id, card) -> card.pseudoClassStateChanged(
                PseudoClass.getPseudoClass("selected"), id.equals(active)));
    }

    public void setOnPackPicked(Consumer<Cosmetics.Pack> handler) {
        this.onPackPicked = handler;
    }

    /* ------------------------------------------------------------------ */
    /* A venir                                                             */
    /* ------------------------------------------------------------------ */

    private VBox plannedCard() {
        return Ui.card(I18n.tr("support.planned"),
                Ui.hint(I18n.tr("support.planned.hint")),
                planned(I18n.tr("support.planned.capes")),
                planned(I18n.tr("support.planned.ranks")),
                planned(I18n.tr("support.planned.donate")));
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
