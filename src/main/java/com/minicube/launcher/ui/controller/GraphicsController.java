package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.GraphicsSettings;
import com.minicube.launcher.ui.view.GraphicsView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;

/**
 * Controleur de l'onglet Graphismes : synchronise les curseurs avec le modele et ecrit
 * les reglages dans {@code options.txt}.
 */
public class GraphicsController {

    private final LauncherContext context;
    private final GraphicsView view = new GraphicsView();

    /** Empeche les allers-retours lorsque le code met a jour les controles. */
    private boolean updating;

    public GraphicsController(LauncherContext context) {
        this.context = context;

        bindValueLabels();
        bindPresets();

        view.applyButton().setOnAction(event -> apply());
        view.importButton().setOnAction(event -> importFromGame());

        loadFromSettings();
    }

    public Node root() {
        return view.root();
    }

    /* ------------------------------------------------------------------ */
    /* Liaisons                                                            */
    /* ------------------------------------------------------------------ */

    /** Met a jour les etiquettes de valeur et bascule en profil personnalise. */
    private void bindValueLabels() {
        view.renderDistance().valueProperty().addListener((obs, old, value) -> {
            view.renderDistanceValue().setText(value.intValue() + " "
                    + I18n.tr("graphics.chunks"));
            markCustom();
        });
        view.simulationDistance().valueProperty().addListener((obs, old, value) -> {
            view.simulationDistanceValue().setText(value.intValue() + " "
                    + I18n.tr("graphics.chunks"));
            markCustom();
        });
        view.maxFps().valueProperty().addListener((obs, old, value) -> {
            int fps = value.intValue();
            view.maxFpsValue().setText(fps >= 260 ? I18n.tr("graphics.unlimited")
                    : fps + " FPS");
            markCustom();
        });
        view.fov().valueProperty().addListener((obs, old, value) ->
                view.fovValue().setText(value.intValue() + " deg"));
        view.brightness().valueProperty().addListener((obs, old, value) ->
                view.brightnessValue().setText(Math.round(value.doubleValue() * 100) + " %"));
    }

    private void bindPresets() {
        view.performancePreset().setOnAction(event ->
                applyPreset(GraphicsSettings.Preset.PERFORMANCE));
        view.balancedPreset().setOnAction(event ->
                applyPreset(GraphicsSettings.Preset.BALANCED));
        view.qualityPreset().setOnAction(event ->
                applyPreset(GraphicsSettings.Preset.QUALITY));
    }

    private void applyPreset(GraphicsSettings.Preset preset) {
        context.config().settings().getGraphics().applyPreset(preset);
        loadFromSettings();
        apply();
        context.notifications().info(I18n.tr("graphics.title"),
                I18n.tr("graphics.presetApplied", preset.label()));
    }

    private void markCustom() {
        if (!updating) {
            context.config().settings().getGraphics()
                    .setPreset(GraphicsSettings.Preset.CUSTOM);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Modele vers vue                                                     */
    /* ------------------------------------------------------------------ */

    /** Recopie les valeurs de la configuration dans les controles. */
    public final void loadFromSettings() {
        GraphicsSettings graphics = context.config().settings().getGraphics();
        updating = true;
        try {
            view.renderDistance().setValue(graphics.getRenderDistance());
            view.simulationDistance().setValue(graphics.getSimulationDistance());
            view.maxFps().setValue(graphics.getMaxFps());
            view.fov().setValue(graphics.getFov());
            view.brightness().setValue(graphics.getBrightness());
            view.vsync().setSelected(graphics.isVsync());
            view.fullscreen().setSelected(graphics.isFullscreen());
            view.entityShadows().setSelected(graphics.isEntityShadows());
            view.smoothLighting().setSelected(graphics.isSmoothLighting());
            view.customResolution().setSelected(graphics.isCustomResolution());
            view.graphicsMode().getSelectionModel().select(graphics.getGraphicsMode());
            view.particles().getSelectionModel().select(graphics.getParticles());
            view.guiScale().getSelectionModel().select(graphics.getGuiScale());
            view.windowWidth().getValueFactory().setValue(graphics.getWindowWidth());
            view.windowHeight().getValueFactory().setValue(graphics.getWindowHeight());
        } finally {
            updating = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Vue vers modele                                                     */
    /* ------------------------------------------------------------------ */

    /** Enregistre les reglages et les ecrit immediatement dans options.txt. */
    private void apply() {
        GraphicsSettings graphics = context.config().settings().getGraphics();
        graphics.setRenderDistance((int) view.renderDistance().getValue());
        graphics.setSimulationDistance((int) view.simulationDistance().getValue());
        graphics.setMaxFps((int) view.maxFps().getValue());
        graphics.setFov((int) view.fov().getValue());
        graphics.setBrightness(view.brightness().getValue());
        graphics.setVsync(view.vsync().isSelected());
        graphics.setFullscreen(view.fullscreen().isSelected());
        graphics.setEntityShadows(view.entityShadows().isSelected());
        graphics.setSmoothLighting(view.smoothLighting().isSelected());
        graphics.setCustomResolution(view.customResolution().isSelected());
        graphics.setGraphicsMode(Math.max(0,
                view.graphicsMode().getSelectionModel().getSelectedIndex()));
        graphics.setParticles(Math.max(0,
                view.particles().getSelectionModel().getSelectedIndex()));
        graphics.setGuiScale(Math.max(0,
                view.guiScale().getSelectionModel().getSelectedIndex()));
        graphics.setWindowWidth(view.windowWidth().getValue());
        graphics.setWindowHeight(view.windowHeight().getValue());

        context.config().save();

        Fx.async(() -> {
            context.options().applyGraphicsSettings(graphics,
                    context.config().settings().isShadersEnabled(),
                    context.config().settings().getActiveShaderPack());
            return Boolean.TRUE;
        }, ignored -> context.notifications().success(I18n.tr("graphics.title"),
                I18n.tr("graphics.saved")),
                error -> context.notifications().error(I18n.tr("graphics.title"),
                        error.getMessage()));
    }

    /** Relit options.txt pour refleter les reglages faits dans le jeu. */
    private void importFromGame() {
        Fx.async(() -> {
            context.options().importInto(context.config().settings().getGraphics());
            return Boolean.TRUE;
        }, ignored -> {
            loadFromSettings();
            context.config().save();
            context.notifications().info(I18n.tr("graphics.title"),
                    I18n.tr("graphics.imported"));
        }, error -> context.notifications().error(I18n.tr("graphics.title"),
                error.getMessage()));
    }
}
