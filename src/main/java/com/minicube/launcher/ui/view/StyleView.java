package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;


/**
 * Onglet Style : apparence du launcher.
 *
 * <p>Les habillages — style et couleur ensemble — vivent dans la Boutique. Cet onglet
 * ne garde que ce qui ne s achete pas : la couleur d accent et l image de fond. Les
 * proposer aux deux endroits reviendrait a donner gratuitement ce que la Boutique
 * demande de debloquer.</p>
 */
public class StyleView {

    private final VBox root;

    private final ColorPicker accentPicker = new ColorPicker();
    private final Button resetAccentButton =
            Ui.secondaryButton(I18n.tr("style.accent.reset"), Icons.REFRESH);

    private final TextField backgroundPath = new TextField();
    private final Button browseBackgroundButton =
            Ui.secondaryButton(I18n.tr("settings.background.browse"), Icons.FOLDER);
    private final Button clearBackgroundButton =
            Ui.iconButton(Icons.CLOSE, I18n.tr("settings.background.clear"));

    private final Button openShopButton =
            Ui.secondaryButton(I18n.tr("style.looks.open"), Icons.HEART);

    public StyleView() {
        root = Ui.page(I18n.tr("style.title"), I18n.tr("style.subtitle"),
                looksCard(), accentCard(), backgroundCard());
    }

    public VBox root() {
        return root;
    }

    /* ------------------------------------------------------------------ */
    /* Habillages                                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Renvoie vers la Boutique.
     *
     * <p>Sans cette carte, un onglet nomme Style qui ne propose plus de style se lirait
     * comme une regression. Elle dit ou les habillages sont passes, et y emmene.</p>
     */
    private VBox looksCard() {
        HBox row = new HBox(12, openShopButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return Ui.card(I18n.tr("style.looks"), Ui.hint(I18n.tr("style.looks.hint")), row);
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

    public Button openShopButton() {
        return openShopButton;
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
