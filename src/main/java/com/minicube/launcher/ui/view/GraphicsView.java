package com.minicube.launcher.ui.view;

import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Onglet Graphismes : profils rapides et reglage fin des options de rendu.
 *
 * <p>Les valeurs sont ecrites dans {@code options.txt} avant chaque lancement.</p>
 */
public class GraphicsView {

    private final VBox root;

    private final Button performancePreset =
            Ui.secondaryButton(I18n.tr("graphics.preset.perf"), null);
    private final Button balancedPreset =
            Ui.secondaryButton(I18n.tr("graphics.preset.balanced"), null);
    private final Button qualityPreset =
            Ui.secondaryButton(I18n.tr("graphics.preset.quality"), null);

    private final Slider renderDistance = new Slider(2, 32, 12);
    private final Label renderDistanceValue = new Label();
    private final Slider simulationDistance = new Slider(5, 32, 10);
    private final Label simulationDistanceValue = new Label();
    private final Slider maxFps = new Slider(10, 260, 120);
    private final Label maxFpsValue = new Label();
    private final Slider fov = new Slider(30, 110, 70);
    private final Label fovValue = new Label();
    private final Slider brightness = new Slider(0, 1, 0.5);
    private final Label brightnessValue = new Label();

    private final CheckBox vsync = new CheckBox();
    private final CheckBox fullscreen = new CheckBox();
    private final CheckBox entityShadows = new CheckBox();
    private final CheckBox smoothLighting = new CheckBox();
    private final CheckBox customResolution = new CheckBox();

    private final ChoiceBox<String> graphicsMode = new ChoiceBox<>();
    private final ChoiceBox<String> particles = new ChoiceBox<>();
    private final ChoiceBox<String> guiScale = new ChoiceBox<>();

    private final Spinner<Integer> windowWidth = new Spinner<>(640, 7680, 1280, 10);
    private final Spinner<Integer> windowHeight = new Spinner<>(480, 4320, 720, 10);

    private final Button applyButton = Ui.primaryButton(I18n.tr("graphics.apply"), Icons.CHECK);
    private final Button importButton =
            Ui.secondaryButton(I18n.tr("graphics.import"), Icons.DOWNLOAD);

    public GraphicsView() {
        graphicsMode.getItems().addAll(I18n.tr("graphics.mode.fast"),
                I18n.tr("graphics.mode.fancy"), I18n.tr("graphics.mode.fabulous"));
        particles.getItems().addAll(I18n.tr("graphics.particles.all"),
                I18n.tr("graphics.particles.decreased"), I18n.tr("graphics.particles.minimal"));
        guiScale.getItems().addAll(I18n.tr("graphics.gui.auto"), "1", "2", "3", "4");

        windowWidth.setEditable(true);
        windowWidth.setPrefWidth(110);
        windowHeight.setEditable(true);
        windowHeight.setPrefWidth(110);

        HBox presets = new HBox(12, performancePreset, balancedPreset, qualityPreset);
        presets.setAlignment(Pos.CENTER_LEFT);

        VBox presetCard = Ui.card(I18n.tr("graphics.presets"),
                Ui.hint(I18n.tr("graphics.presets.hint")), presets);

        VBox renderingCard = Ui.card(I18n.tr("graphics.rendering"),
                sliderRow("graphics.renderDistance", "graphics.renderDistance.hint",
                        renderDistance, renderDistanceValue),
                sliderRow("graphics.simulationDistance", "graphics.simulationDistance.hint",
                        simulationDistance, simulationDistanceValue),
                Ui.settingRow(I18n.tr("graphics.mode"), I18n.tr("graphics.mode.hint"),
                        graphicsMode),
                Ui.settingRow(I18n.tr("graphics.particles"), null, particles),
                Ui.settingRow(I18n.tr("graphics.smoothLighting"),
                        I18n.tr("graphics.smoothLighting.hint"), smoothLighting),
                Ui.settingRow(I18n.tr("graphics.entityShadows"), null, entityShadows));

        VBox performanceCard = Ui.card(I18n.tr("graphics.performance"),
                sliderRow("graphics.maxFps", "graphics.maxFps.hint", maxFps, maxFpsValue),
                Ui.settingRow(I18n.tr("graphics.vsync"), I18n.tr("graphics.vsync.hint"), vsync));

        VBox displayCard = Ui.card(I18n.tr("graphics.display"),
                Ui.settingRow(I18n.tr("graphics.fullscreen"),
                        I18n.tr("graphics.fullscreen.hint"), fullscreen),
                Ui.settingRow(I18n.tr("graphics.customResolution"),
                        I18n.tr("graphics.customResolution.hint"), customResolution),
                Ui.settingRow(I18n.tr("graphics.resolution"), null,
                        new HBox(10, windowWidth, new Label("x"), windowHeight)),
                Ui.settingRow(I18n.tr("graphics.gui"), null, guiScale),
                sliderRow("graphics.fov", null, fov, fovValue),
                sliderRow("graphics.brightness", null, brightness, brightnessValue));

        HBox actions = new HBox(12, applyButton, importButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        root = Ui.page(I18n.tr("graphics.title"), I18n.tr("graphics.subtitle"),
                presetCard, renderingCard, performanceCard, displayCard, actions);
    }

    /** Ligne comprenant un curseur et la valeur courante affichee a droite. */
    private Node sliderRow(String labelKey, String hintKey, Slider slider, Label value) {
        slider.setPrefWidth(240);
        slider.setBlockIncrement(1);
        value.getStyleClass().add("slider-value");
        value.setMinWidth(72);
        value.setAlignment(Pos.CENTER_RIGHT);

        HBox control = new HBox(12, slider, value);
        control.setAlignment(Pos.CENTER_LEFT);
        return Ui.settingRow(I18n.tr(labelKey), hintKey == null ? null : I18n.tr(hintKey),
                control);
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public Node root() {
        return root;
    }

    public Button performancePreset() {
        return performancePreset;
    }

    public Button balancedPreset() {
        return balancedPreset;
    }

    public Button qualityPreset() {
        return qualityPreset;
    }

    public Slider renderDistance() {
        return renderDistance;
    }

    public Label renderDistanceValue() {
        return renderDistanceValue;
    }

    public Slider simulationDistance() {
        return simulationDistance;
    }

    public Label simulationDistanceValue() {
        return simulationDistanceValue;
    }

    public Slider maxFps() {
        return maxFps;
    }

    public Label maxFpsValue() {
        return maxFpsValue;
    }

    public Slider fov() {
        return fov;
    }

    public Label fovValue() {
        return fovValue;
    }

    public Slider brightness() {
        return brightness;
    }

    public Label brightnessValue() {
        return brightnessValue;
    }

    public CheckBox vsync() {
        return vsync;
    }

    public CheckBox fullscreen() {
        return fullscreen;
    }

    public CheckBox entityShadows() {
        return entityShadows;
    }

    public CheckBox smoothLighting() {
        return smoothLighting;
    }

    public CheckBox customResolution() {
        return customResolution;
    }

    public ChoiceBox<String> graphicsMode() {
        return graphicsMode;
    }

    public ChoiceBox<String> particles() {
        return particles;
    }

    public ChoiceBox<String> guiScale() {
        return guiScale;
    }

    public Spinner<Integer> windowWidth() {
        return windowWidth;
    }

    public Spinner<Integer> windowHeight() {
        return windowHeight;
    }

    public Button applyButton() {
        return applyButton;
    }

    public Button importButton() {
        return importButton;
    }
}
