package com.minicube.launcher.model;

/**
 * Reglages graphiques du jeu pilotes par le launcher.
 *
 * <p>Ces valeurs sont ecrites dans {@code .minecraft/options.txt} avant chaque lancement
 * (voir {@code OptionsService}), ce qui evite d'avoir a les regler dans le jeu.</p>
 */
public class GraphicsSettings {

    /** Profils rapides applicables en un clic depuis l'onglet Graphismes. */
    public enum Preset {
        PERFORMANCE("Performance"),
        BALANCED("Equilibre"),
        QUALITY("Qualite"),
        CUSTOM("Personnalise");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Distance de rendu en chunks (2 a 32). */
    private int renderDistance = 12;
    /** Distance de simulation en chunks (5 a 32), disponible depuis la 1.18. */
    private int simulationDistance = 10;
    /** Limite d'images par seconde ; 260 correspond a "illimite" dans le jeu. */
    private int maxFps = 120;
    private boolean vsync = false;
    private boolean fullscreen = false;
    /** 0 = rapide, 1 = detaille, 2 = fabuleux. */
    private int graphicsMode = 1;
    /** 0 = toutes, 1 = reduites, 2 = minimales. */
    private int particles = 0;
    private boolean entityShadows = true;
    /** Lumiere douce (ambient occlusion). */
    private boolean smoothLighting = true;
    /** 0 = automatique. */
    private int guiScale = 0;
    private int fov = 70;
    /** Luminosite du jeu, de 0.0 (sombre) a 1.0 (lumineux). */
    private double brightness = 0.5;
    private int windowWidth = 1280;
    private int windowHeight = 720;
    /** Applique une resolution personnalisee au lancement (hors plein ecran). */
    private boolean customResolution = false;
    private Preset preset = Preset.BALANCED;

    /** Applique un profil predefini et renvoie l'objet pour permettre le chainage. */
    public GraphicsSettings applyPreset(Preset target) {
        this.preset = target;
        switch (target) {
            case PERFORMANCE -> {
                renderDistance = 6;
                simulationDistance = 6;
                maxFps = 260;
                vsync = false;
                graphicsMode = 0;
                particles = 2;
                entityShadows = false;
                smoothLighting = false;
            }
            case BALANCED -> {
                renderDistance = 12;
                simulationDistance = 10;
                maxFps = 120;
                vsync = false;
                graphicsMode = 1;
                particles = 0;
                entityShadows = true;
                smoothLighting = true;
            }
            case QUALITY -> {
                renderDistance = 20;
                simulationDistance = 14;
                maxFps = 260;
                vsync = true;
                graphicsMode = 2;
                particles = 0;
                entityShadows = true;
                smoothLighting = true;
            }
            default -> {
                // CUSTOM : aucune valeur imposee, l'utilisateur garde la main.
            }
        }
        return this;
    }

    public int getRenderDistance() {
        return clamp(renderDistance, 2, 32);
    }

    public void setRenderDistance(int renderDistance) {
        this.renderDistance = clamp(renderDistance, 2, 32);
    }

    public int getSimulationDistance() {
        return clamp(simulationDistance, 5, 32);
    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = clamp(simulationDistance, 5, 32);
    }

    public int getMaxFps() {
        return clamp(maxFps, 10, 260);
    }

    public void setMaxFps(int maxFps) {
        this.maxFps = clamp(maxFps, 10, 260);
    }

    /** Vrai lorsque la limite correspond au mode "illimite" du jeu. */
    public boolean isUnlimitedFps() {
        return getMaxFps() >= 260;
    }

    public boolean isVsync() {
        return vsync;
    }

    public void setVsync(boolean vsync) {
        this.vsync = vsync;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public int getGraphicsMode() {
        return clamp(graphicsMode, 0, 2);
    }

    public void setGraphicsMode(int graphicsMode) {
        this.graphicsMode = clamp(graphicsMode, 0, 2);
    }

    public int getParticles() {
        return clamp(particles, 0, 2);
    }

    public void setParticles(int particles) {
        this.particles = clamp(particles, 0, 2);
    }

    public boolean isEntityShadows() {
        return entityShadows;
    }

    public void setEntityShadows(boolean entityShadows) {
        this.entityShadows = entityShadows;
    }

    public boolean isSmoothLighting() {
        return smoothLighting;
    }

    public void setSmoothLighting(boolean smoothLighting) {
        this.smoothLighting = smoothLighting;
    }

    public int getGuiScale() {
        return clamp(guiScale, 0, 4);
    }

    public void setGuiScale(int guiScale) {
        this.guiScale = clamp(guiScale, 0, 4);
    }

    public int getFov() {
        return clamp(fov, 30, 110);
    }

    public void setFov(int fov) {
        this.fov = clamp(fov, 30, 110);
    }

    public double getBrightness() {
        return Math.max(0d, Math.min(1d, brightness));
    }

    public void setBrightness(double brightness) {
        this.brightness = Math.max(0d, Math.min(1d, brightness));
    }

    public int getWindowWidth() {
        return Math.max(640, windowWidth);
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = Math.max(640, windowWidth);
    }

    public int getWindowHeight() {
        return Math.max(480, windowHeight);
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = Math.max(480, windowHeight);
    }

    public boolean isCustomResolution() {
        return customResolution;
    }

    public void setCustomResolution(boolean customResolution) {
        this.customResolution = customResolution;
    }

    public Preset getPreset() {
        return preset == null ? Preset.CUSTOM : preset;
    }

    public void setPreset(Preset preset) {
        this.preset = preset;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
