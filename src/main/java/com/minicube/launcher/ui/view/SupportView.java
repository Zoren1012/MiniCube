package com.minicube.launcher.ui.view;

import com.minicube.launcher.model.PlayerStats;
import com.minicube.launcher.service.Challenges;
import com.minicube.launcher.ui.Cosmetics;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Styles;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.ui.component.ThemedAnimation;
import com.minicube.launcher.util.I18n;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Onglet Boutique : le catalogue des cosmetiques du launcher.
 *
 * <p>Chaque habillage affiche son etat reel — offert, possede, ou son prix. La carte
 * n'invente rien : elle demande a la vue quel est l'etat, et la vue le tient du service.
 * Une carte qui deciderait elle-meme de ce qu'elle affiche finirait par mentir.</p>
 */
public class SupportView {

    /** Ce qu'on peut faire d'un habillage, du point de vue de l'affichage. */
    public enum State {
        /** Applique en ce moment. */
        EQUIPPED,
        /** Possede ou offert : un clic l'applique. */
        OWNED,
        /** A acheter, et le solde suffit. */
        BUYABLE,
        /** A acheter, mais le solde ne suffit pas. */
        LOCKED
    }

    private final VBox root;
    private final ThemedAnimation animation = new ThemedAnimation();
    /** Cartes des habillages, retrouvees pour rafraichir leur etat. */
    private final Map<String, PackCard> packCards = new LinkedHashMap<>();
    /** Lignes de defis, retrouvees pour rafraichir leur avancement. */
    private final Map<String, ChallengeRow> challengeRows = new LinkedHashMap<>();

    private final Label balanceLabel = new Label();
    private final Label earnedLabel = Ui.hint("");

    private Consumer<Cosmetics.Pack> onPackPicked = pack -> { };

    public SupportView() {
        root = Ui.page(I18n.tr("support.title"), I18n.tr("support.subtitle"),
                hero(), challengesCard(), packsCard(), plannedCard());
    }

    /* ------------------------------------------------------------------ */
    /* Defis                                                               */
    /* ------------------------------------------------------------------ */

    private VBox challengesCard() {
        VBox rows = new VBox(2);
        Challenges.all().forEach(challenge -> {
            ChallengeRow row = new ChallengeRow(challenge);
            challengeRows.put(challenge.id(), row);
            rows.getChildren().add(row.node);
        });
        return Ui.card(I18n.tr("challenge.title"),
                Ui.hint(I18n.tr("challenge.hint")), rows);
    }

    /**
     * Une ligne de defi : ce qu'il demande, ou l'on en est, ce qu'il rapporte.
     *
     * <p>La barre reste affichee une fois le defi accompli, pleine. La faire disparaitre
     * effacerait la trace de ce qu'on a fait, et ferait paraitre la carte plus vide a
     * mesure qu'on avance — exactement l'inverse de ce qu'on veut montrer.</p>
     */
    private final class ChallengeRow {

        private final HBox node;
        private final Label progress = Ui.hint("");
        private final Label reward;
        private final ProgressBar bar = new ProgressBar(0);

        private ChallengeRow(Challenges.Challenge challenge) {
            Label name = new Label(I18n.tr("challenge." + challenge.id()));
            name.getStyleClass().add("setting-label");

            bar.getStyleClass().add("challenge-bar");
            bar.setPrefWidth(150);
            bar.setMinWidth(150);

            reward = Ui.badge(I18n.tr("support.price", challenge.reward()), "chip-label");

            VBox texts = new VBox(4, name, progress);
            HBox.setHgrow(texts, Priority.ALWAYS);

            node = new HBox(14, texts, bar, reward);
            node.setAlignment(Pos.CENTER_LEFT);
            node.getStyleClass().add("setting-row");
        }

        private void apply(Challenges.Challenge challenge, PlayerStats stats,
                           boolean claimed) {
            bar.setProgress(challenge.ratio(stats));
            progress.setText(I18n.tr("challenge.progress",
                    challenge.progress(stats), challenge.target()));

            reward.getStyleClass().removeAll("chip-accent", "chip-label");
            reward.setText(claimed
                    ? I18n.tr("challenge.claimed")
                    : I18n.tr("support.price", challenge.reward()));
            reward.getStyleClass().add(claimed ? "chip-accent" : "chip-label");
        }
    }

    /**
     * Met les defis a jour.
     *
     * @param stats compteurs du joueur
     * @param claimed identifiants des defis deja payes
     */
    public void refreshChallenges(PlayerStats stats, Set<String> claimed) {
        Challenges.all().forEach(challenge -> {
            ChallengeRow row = challengeRows.get(challenge.id());
            if (row != null) {
                row.apply(challenge, stats, claimed.contains(challenge.id()));
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* En-tete : le solde                                                  */
    /* ------------------------------------------------------------------ */

    private VBox hero() {
        balanceLabel.getStyleClass().add("hero-title");

        HBox coins = new HBox(10, Icons.of(Icons.COIN, 26), balanceLabel);
        coins.setAlignment(Pos.CENTER_LEFT);

        Label detail = Ui.hint(I18n.tr("support.coins.hint"));
        detail.setWrapText(true);
        detail.setMaxWidth(620);

        earnedLabel.setWrapText(true);
        earnedLabel.setMaxWidth(620);

        VBox texts = new VBox(10, coins, detail, earnedLabel, animation);
        texts.setAlignment(Pos.TOP_LEFT);

        VBox hero = new VBox(texts);
        hero.getStyleClass().add("hero");
        return hero;
    }

    /**
     * Affiche le solde et d'ou il vient.
     *
     * @param balance pieces disponibles
     * @param minutes minutes de jeu comptabilisees
     * @param sessions parties comptabilisees
     */
    public void setBalance(int balance, long minutes, int sessions) {
        balanceLabel.setText(I18n.tr("support.coins", balance));
        earnedLabel.setText(I18n.tr("support.coins.earned", minutes, sessions));
    }

    /* ------------------------------------------------------------------ */
    /* Habillages                                                          */
    /* ------------------------------------------------------------------ */

    private VBox packsCard() {
        FlowPane grid = new FlowPane(16, 16);
        Cosmetics.all().forEach(pack -> {
            PackCard card = new PackCard(pack);
            packCards.put(pack.id(), card);
            grid.getChildren().add(card.node);
        });
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
    private final class PackCard {

        private final VBox node;
        private final Label badge;
        private final Region veil;

        private PackCard(Cosmetics.Pack pack) {
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

            // Voile pose sur l'apercu d'un habillage hors de portee : il reste visible,
            // mais on voit d'un coup d'oeil qu'il n'est pas a soi.
            veil = new Region();
            veil.getStyleClass().add("locked-veil");
            veil.setVisible(false);
            veil.setMouseTransparent(true);

            javafx.scene.layout.StackPane preview = new javafx.scene.layout.StackPane(mock, veil);
            preview.setAlignment(Pos.CENTER);

            Label name = new Label(I18n.tr("support.pack." + pack.id()));
            name.getStyleClass().add("setting-label");

            badge = Ui.badge("", "chip-accent");

            HBox footer = new HBox(8, name, Ui.growSpacer(), badge);
            footer.setAlignment(Pos.CENTER_LEFT);

            node = new VBox(11, preview, footer);
            node.getStyleClass().addAll("card", "theme-card");
            node.setPadding(new Insets(14));
            node.setMinWidth(190);
            node.setPrefWidth(190);
            node.setOnMouseClicked(event -> onPackPicked.accept(pack));
        }

        private void apply(Cosmetics.Pack pack, State state) {
            node.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"),
                    state == State.EQUIPPED);
            veil.setVisible(state == State.LOCKED);

            badge.getStyleClass().removeAll("chip-accent", "chip-label");
            switch (state) {
                case EQUIPPED -> {
                    badge.setText(I18n.tr("support.equipped"));
                    badge.getStyleClass().add("chip-accent");
                }
                case OWNED -> {
                    badge.setText(pack.free()
                            ? I18n.tr("support.free") : I18n.tr("support.owned"));
                    badge.getStyleClass().add("chip-label");
                }
                // Le prix reste affiche quand il est hors de portee : masquer le
                // montant obligerait a cliquer pour savoir ce qu'il manque.
                case BUYABLE, LOCKED -> {
                    badge.setText(I18n.tr("support.price", pack.price()));
                    badge.getStyleClass().add(
                            state == State.BUYABLE ? "chip-accent" : "chip-label");
                }
                default -> badge.setText("");
            }
        }
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
     * Met chaque carte a jour.
     *
     * @param states etat de chaque habillage, par identifiant
     */
    public void refreshPacks(Map<String, State> states) {
        Cosmetics.all().forEach(pack -> {
            PackCard card = packCards.get(pack.id());
            State state = states.get(pack.id());
            if (card != null && state != null) {
                card.apply(pack, state);
            }
        });
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
