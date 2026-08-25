package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Onglet Performances : mesures en direct et diagnostic de la machine.
 *
 * <p>Les mesures sont presentees en vignettes de meme forme que celles de l'accueil : un
 * chiffre lisible de loin, sa legende en petites capitales.</p>
 */
public class PerformanceView {

    private final VBox root;

    /** Vignettes de mesure, retrouvees par leur cle pour la mise a jour. */
    private final Map<String, Label> values = new LinkedHashMap<>();

    private final VBox findingsBox = new VBox(10);
    private final Button analyseButton = Ui.primaryButton(I18n.tr("perf.analyse"), Icons.WAND);
    private final Button applyAllButton =
            Ui.secondaryButton(I18n.tr("perf.applyAll"), Icons.CHECK);
    private final Label findingsStatus = Ui.hint(I18n.tr("perf.analyse.hint"));

    private final VBox serversBox = new VBox(8);

    public PerformanceView() {
        root = Ui.page(I18n.tr("perf.title"), I18n.tr("perf.subtitle"),
                metricsCard(), analysisCard(), serversCard());
    }

    public VBox root() {
        return root;
    }

    /* ------------------------------------------------------------------ */
    /* Mesures                                                             */
    /* ------------------------------------------------------------------ */

    private VBox metricsCard() {
        // FlowPane plutot qu'une grille figee : les vignettes se reorganisent selon la
        // largeur disponible au lieu de deborder.
        FlowPane grid = new FlowPane(14, 14);
        grid.getChildren().addAll(
                metric("fps", I18n.tr("perf.fps"), Icons.GAUGE),
                metric("cpu", I18n.tr("perf.cpu"), Icons.MONITOR),
                metric("ram", I18n.tr("perf.ram"), Icons.SERVER),
                metric("system", I18n.tr("perf.systemRam"), Icons.SERVER),
                metric("java", I18n.tr("perf.java"), Icons.SETTINGS),
                metric("startup", I18n.tr("perf.gameStartup"), Icons.PLAY));

        return Ui.card(I18n.tr("perf.live"), Ui.hint(I18n.tr("perf.live.hint")), grid);
    }

    /** Vignette : icone, legende en capitales, valeur en gros. */
    private VBox metric(String key, String caption, String icon) {
        Label value = new Label("-");
        // Police un cran sous celle de l'accueil : ces vignettes portent des valeurs
        // plus longues, comme "18,1 / 31,6 Go", qui seraient tronquees autrement.
        value.getStyleClass().addAll("stat-value", "metric-value");
        values.put(key, value);

        Label name = new Label(caption.toUpperCase(Locale.ROOT));
        name.getStyleClass().add("stat-label");

        HBox header = new HBox(8, Icons.of(icon, 15), name);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, header, value);
        card.getStyleClass().addAll("card", "stat-card");
        card.setMinWidth(248);
        card.setPrefWidth(248);
        return card;
    }

    /** Met a jour une vignette. */
    public void setMetric(String key, String value) {
        Label label = values.get(key);
        if (label != null) {
            label.setText(value);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Analyse                                                             */
    /* ------------------------------------------------------------------ */

    private VBox analysisCard() {
        HBox actions = new HBox(12, analyseButton, applyAllButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        applyAllButton.setDisable(true);

        return Ui.card(I18n.tr("perf.analysis"), findingsStatus, actions, findingsBox);
    }

    /* ------------------------------------------------------------------ */
    /* Serveurs                                                            */
    /* ------------------------------------------------------------------ */

    private VBox serversCard() {
        VBox card = Ui.card(I18n.tr("perf.servers"),
                Ui.hint(I18n.tr("perf.servers.hint")), serversBox);
        VBox.setVgrow(card, Priority.NEVER);
        return card;
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public VBox findingsBox() {
        return findingsBox;
    }

    public Button analyseButton() {
        return analyseButton;
    }

    public Button applyAllButton() {
        return applyAllButton;
    }

    public Label findingsStatus() {
        return findingsStatus;
    }

    public VBox serversBox() {
        return serversBox;
    }
}
