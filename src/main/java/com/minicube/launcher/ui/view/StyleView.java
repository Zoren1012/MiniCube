package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Styles;
import com.minicube.launcher.ui.ThemeManager;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Onglet Style : apparence du launcher.
 *
 * <p>Le theme se choisit sur un apercu plutot que dans une liste deroulante. Trois
 * ambiances tres differentes cohabitent — le verre translucide, sa version claire, et
 * l'habillage Minecraft — et un nom seul ne dit pas laquelle on prend.</p>
 */
public class StyleView {

    private final VBox root;

    /** Vignettes d'apercu, une par theme, retrouvees pour marquer celle qui est active. */
    private final Map<String, VBox> themeCards = new LinkedHashMap<>();

    private final ColorPicker accentPicker = new ColorPicker();
    private final Button resetAccentButton =
            Ui.secondaryButton(I18n.tr("style.accent.reset"), Icons.REFRESH);

    private final TextField backgroundPath = new TextField();
    private final Button browseBackgroundButton =
            Ui.secondaryButton(I18n.tr("settings.background.browse"), Icons.FOLDER);
    private final Button clearBackgroundButton =
            Ui.iconButton(Icons.CLOSE, I18n.tr("settings.background.clear"));

    private Runnable onThemePicked = () -> { };
    private String pendingTheme = ThemeManager.DARK;

    public StyleView() {
        root = Ui.page(I18n.tr("style.title"), I18n.tr("style.subtitle"),
                themeCard(), accentCard(), backgroundCard());
    }

    public VBox root() {
        return root;
    }

    /* ------------------------------------------------------------------ */
    /* Themes                                                              */
    /* ------------------------------------------------------------------ */

    private VBox themeCard() {
        FlowPane grid = new FlowPane(16, 16);
        ThemeManager.ALL.forEach(theme -> grid.getChildren().add(themePreview(theme)));
        return Ui.card(I18n.tr("style.theme"), Ui.hint(I18n.tr("style.theme.hint")), grid);
    }

    /**
     * Vignette d'un theme : une maquette miniature de la fenetre.
     *
     * <p>Les couleurs viennent du catalogue, pas de la feuille de style appliquee :
     * l'apercu doit montrer le style <b>qu'il represente</b>, pas celui qui est a
     * l'ecran. Un style ajoute au catalogue apparait donc ici sans rien changer.</p>
     */
    private VBox themePreview(String theme) {
        Styles.Style style = Styles.of(theme);
        // Les styles mats du launcher officiel n'ont aucun arrondi : la vignette doit
        // le montrer, c'est la difference qu'on voit en premier.
        int radius = style.matte() ? 0 : 8;

        Region sidebar = block(34, 96, style.surface(), radius);
        VBox lines = new VBox(6, block(96, 10, style.surface(), radius),
                block(74, 10, style.surface(), radius),
                block(60, 20, style.accent(), radius));
        lines.setAlignment(Pos.TOP_LEFT);

        HBox mock = new HBox(8, sidebar, lines);
        mock.setPadding(new Insets(10));
        mock.setStyle("-fx-background-color: " + style.panel() + ";"
                + "-fx-background-radius: " + radius + ";");

        // L etiquette suit le theme courant, pas celui qu elle decrit : ecrite dans la
        // couleur du theme clair, elle disparaitrait sur une carte sombre.
        Label name = new Label(I18n.tr("style.theme." + theme));
        name.getStyleClass().add("setting-label");

        VBox card = new VBox(12, mock, name);
        card.getStyleClass().addAll("card", "theme-card");
        card.setPadding(new Insets(14));
        card.setMinWidth(200);
        card.setPrefWidth(200);
        card.setOnMouseClicked(event -> {
            pendingTheme = theme;
            onThemePicked.run();
        });
        themeCards.put(theme, card);
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

    /** Entoure d'un liseré la vignette du theme actif. */
    public void markActiveTheme(String theme) {
        themeCards.forEach((key, card) ->
                card.pseudoClassStateChanged(
                        javafx.css.PseudoClass.getPseudoClass("selected"),
                        key.equals(theme)));
    }

    public String pendingTheme() {
        return pendingTheme;
    }

    public void setOnThemePicked(Runnable action) {
        this.onThemePicked = action;
    }

    /* ------------------------------------------------------------------ */
    /* Accent                                                              */
    /* ------------------------------------------------------------------ */

    private VBox accentCard() {
        accentPicker.setPrefWidth(160);
        HBox row = new HBox(12, accentPicker, resetAccentButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return Ui.card(I18n.tr("style.accent"), Ui.hint(I18n.tr("style.accent.hint")), row);
    }

    /* ------------------------------------------------------------------ */
    /* Fond                                                                */
    /* ------------------------------------------------------------------ */

    private VBox backgroundCard() {
        backgroundPath.setPromptText(I18n.tr("settings.background.hint"));
        HBox.setHgrow(backgroundPath, Priority.ALWAYS);
        HBox row = new HBox(10, backgroundPath, browseBackgroundButton,
                clearBackgroundButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return Ui.card(I18n.tr("style.background"),
                Ui.hint(I18n.tr("style.background.hint")), row);
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public ColorPicker accentPicker() {
        return accentPicker;
    }

    public Button resetAccentButton() {
        return resetAccentButton;
    }

    public TextField backgroundPath() {
        return backgroundPath;
    }

    public Button browseBackgroundButton() {
        return browseBackgroundButton;
    }

    public Button clearBackgroundButton() {
        return clearBackgroundButton;
    }

    /** Couleur affichee par le selecteur, au format hexadecimal. */
    public String accentHex() {
        Color color = accentPicker.getValue();
        if (color == null) {
            return "";
        }
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    public void setAccentHex(String hex) {
        if (hex != null && hex.matches("#[0-9a-fA-F]{6}")) {
            accentPicker.setValue(Color.web(hex));
        }
    }
}
