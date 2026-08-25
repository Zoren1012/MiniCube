package com.minicube.launcher.ui.component;

import javafx.animation.AnimationTimer;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

/**
 * Previsualisation 3D du personnage.
 *
 * <p>Le modele est construit a la main : chaque membre est une boite dont les six faces
 * sont mappees sur la texture selon la disposition officielle des skins Minecraft. Les
 * boites {@code Box} de JavaFX ne conviennent pas, leur mappage de texture etant fixe.</p>
 *
 * <p>La texture est agrandie par duplication de pixels avant d'etre appliquee : le filtre
 * bilineaire de JavaFX rendrait sinon le skin flou, alors que l'esthetique attendue est
 * franchement pixelisee.</p>
 */
public class SkinViewer3D extends StackPane {

    /** Facteur d'agrandissement de la texture pour conserver un rendu net. */
    private static final int TEXTURE_SCALE = 8;
    private static final double DRAG_SENSITIVITY = 0.45;
    private static final double AUTO_ROTATE_SPEED = 0.32;

    private final Group modelRoot = new Group();
    private final Rotate rotateX = new Rotate(-8, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(22, Rotate.Y_AXIS);
    private final AnimationTimer autoRotateTimer;

    private Image skinImage;
    private Image capeImage;
    private boolean slimModel;
    private boolean autoRotate = true;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public SkinViewer3D() {
        Group world = new Group(modelRoot);
        modelRoot.getTransforms().addAll(rotateY, rotateX, new Translate(0, -2, 0));

        AmbientLight ambient = new AmbientLight(Color.gray(0.86));
        PointLight key = new PointLight(Color.gray(0.35));
        key.getTransforms().add(new Translate(-50, -70, -90));
        world.getChildren().addAll(ambient, key);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setFieldOfView(38);
        camera.setNearClip(0.1);
        camera.setFarClip(1000);
        camera.getTransforms().add(new Translate(0, 0, -72));

        SubScene subScene = new SubScene(world, 260, 380, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(camera);
        subScene.setFill(Color.TRANSPARENT);
        subScene.widthProperty().bind(widthProperty());
        subScene.heightProperty().bind(heightProperty());

        getChildren().add(subScene);
        getStyleClass().add("skin-viewer");
        setMinSize(200, 300);
        setPrefSize(280, 400);

        installMouseControls();

        autoRotateTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (autoRotate && !dragging) {
                    rotateY.setAngle(rotateY.getAngle() + AUTO_ROTATE_SPEED);
                }
            }
        };
        autoRotateTimer.start();

        setSkin(null, false);
    }

    /** Rotation a la souris : glisser horizontalement tourne, verticalement incline. */
    private void installMouseControls() {
        setOnMousePressed(event -> {
            dragging = true;
            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });
        setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - lastMouseX;
            double deltaY = event.getSceneY() - lastMouseY;
            rotateY.setAngle(rotateY.getAngle() + deltaX * DRAG_SENSITIVITY);
            double tilt = rotateX.getAngle() - deltaY * DRAG_SENSITIVITY;
            rotateX.setAngle(Math.max(-32, Math.min(32, tilt)));
            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        });
        setOnMouseReleased(event -> dragging = false);
        setOnScroll(event -> {
            double scale = modelRoot.getScaleX() + (event.getDeltaY() > 0 ? 0.08 : -0.08);
            double clamped = Math.max(0.6, Math.min(2.2, scale));
            modelRoot.setScaleX(clamped);
            modelRoot.setScaleY(clamped);
            modelRoot.setScaleZ(clamped);
        });
    }

    /* ------------------------------------------------------------------ */
    /* API publique                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Change la texture affichee et reconstruit le modele.
     *
     * @param skin texture 64x64 ou 64x32 ; null pour utiliser le personnage par defaut
     * @param slim true pour le modele a bras fins (Alex)
     */
    public final void setSkin(Image skin, boolean slim) {
        this.skinImage = (skin == null || skin.isError()) ? DefaultSkin.image() : skin;
        this.slimModel = slim;
        rebuild();
    }

    /**
     * Change la cape affichee derriere le personnage.
     *
     * @param cape texture de cape, ou null pour n'afficher aucune cape
     */
    public void setCape(Image cape) {
        this.capeImage = (cape != null && cape.isError()) ? null : cape;
        rebuild();
    }

    /** Active ou suspend la rotation automatique. */
    public void setAutoRotate(boolean enabled) {
        this.autoRotate = enabled;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    /** Remet le modele dans sa position initiale. */
    public void resetView() {
        rotateX.setAngle(-8);
        rotateY.setAngle(22);
        modelRoot.setScaleX(1);
        modelRoot.setScaleY(1);
        modelRoot.setScaleZ(1);
    }

    /** Libere le minuteur d'animation lorsque la vue n'est plus affichee. */
    public void dispose() {
        autoRotateTimer.stop();
    }

    /* ------------------------------------------------------------------ */
    /* Construction du modele                                              */
    /* ------------------------------------------------------------------ */

    /** Reconstruit l'ensemble des membres a partir des textures courantes. */
    private void rebuild() {
        modelRoot.getChildren().clear();

        Image texture = upscale(skinImage, TEXTURE_SCALE);
        boolean legacy = skinImage.getHeight() <= 32;
        int texW = 64;
        int texH = legacy ? 32 : 64;
        double arm = slimModel ? 3 : 4;
        double armOffset = 4 + arm / 2d;

        PhongMaterial material = pixelMaterial(texture);

        // Tete, avec sa surcouche (cheveux, couvre-chef).
        addPart(material, 8, 8, 8, 0, 0, 0, texW, texH, 0, -10, 0);
        addPart(material, 8, 8, 8, 0.5, 32, 0, texW, texH, 0, -10, 0);

        // Buste.
        addPart(material, 8, 12, 4, 0, 16, 16, texW, texH, 0, 0, 0);

        // Bras : sur une texture 64x32, le bras gauche reprend celle du bras droit.
        addPart(material, arm, 12, 4, 0, 40, 16, texW, texH, -armOffset, 0, 0);
        addPart(material, arm, 12, 4, 0, legacy ? 40 : 32, legacy ? 16 : 48, texW, texH,
                armOffset, 0, 0);

        // Jambes.
        addPart(material, 4, 12, 4, 0, 0, 16, texW, texH, -2, 12, 0);
        addPart(material, 4, 12, 4, 0, legacy ? 0 : 16, legacy ? 16 : 48, texW, texH, 2, 12, 0);

        // Surcouches disponibles uniquement sur les textures 64x64.
        if (!legacy) {
            addPart(material, 8, 12, 4, 0.25, 16, 32, texW, texH, 0, 0, 0);
            addPart(material, arm, 12, 4, 0.25, 40, 32, texW, texH, -armOffset, 0, 0);
            addPart(material, arm, 12, 4, 0.25, 48, 48, texW, texH, armOffset, 0, 0);
            addPart(material, 4, 12, 4, 0.25, 0, 32, texW, texH, -2, 12, 0);
            addPart(material, 4, 12, 4, 0.25, 0, 48, texW, texH, 2, 12, 0);
        }

        if (capeImage != null) {
            addCape();
        }
    }

    /** Ajoute la cape dans le dos du personnage. */
    private void addCape() {
        PhongMaterial capeMaterial = pixelMaterial(upscale(capeImage, TEXTURE_SCALE));
        int capeTexW = (int) Math.max(64, capeImage.getWidth());
        int capeTexH = (int) Math.max(32, capeImage.getHeight());

        MeshView cape = new MeshView(boxMesh(10, 16, 1, 0, 0, 0, capeTexW, capeTexH));
        cape.setMaterial(capeMaterial);
        cape.setCullFace(CullFace.NONE);
        cape.setDrawMode(DrawMode.FILL);
        // Demi-tour pour que le motif exterieur regarde vers l'arriere du personnage.
        cape.getTransforms().addAll(new Translate(0, 1, 3.2), new Rotate(180, Rotate.Y_AXIS));
        modelRoot.getChildren().add(cape);
    }

    /**
     * Ajoute un membre au modele.
     *
     * @param width   largeur du membre, en pixels du jeu
     * @param height  hauteur du membre
     * @param depth   profondeur du membre
     * @param inflate epaisseur ajoutee sur chaque face pour les surcouches
     * @param u       abscisse du bloc dans la texture
     * @param v       ordonnee du bloc dans la texture
     * @param offsetX decalage horizontal du centre du membre
     */
    private void addPart(PhongMaterial material, double width, double height, double depth,
                         double inflate, int u, int v, int textureWidth, int textureHeight,
                         double offsetX, double offsetY, double offsetZ) {
        MeshView view = new MeshView(boxMesh(width, height, depth, inflate, u, v,
                textureWidth, textureHeight));
        view.setMaterial(material);
        // Les surcouches comportent des zones transparentes : aucune face n'est ecartee.
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(DrawMode.FILL);
        view.getTransforms().add(new Translate(offsetX, offsetY, offsetZ));
        modelRoot.getChildren().add(view);
    }

    /**
     * Construit le maillage d'une boite dont les six faces suivent la disposition
     * officielle des textures Minecraft.
     *
     * <p>Pour un bloc place en (u, v) dans la texture, les panneaux se lisent ainsi :
     * dessus et dessous sur la rangee superieure, puis cote droit, face, cote gauche et
     * arriere sur la rangee principale.</p>
     */
    private TriangleMesh boxMesh(double width, double height, double depth, double inflate,
                                 int u, int v, int textureWidth, int textureHeight) {
        float halfWidth = (float) (width + inflate * 2) / 2f;
        float halfHeight = (float) (height + inflate * 2) / 2f;
        float halfDepth = (float) (depth + inflate * 2) / 2f;

        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(
                -halfWidth, -halfHeight, -halfDepth,   // 0
                halfWidth, -halfHeight, -halfDepth,    // 1
                -halfWidth, halfHeight, -halfDepth,    // 2
                halfWidth, halfHeight, -halfDepth,     // 3
                -halfWidth, -halfHeight, halfDepth,    // 4
                halfWidth, -halfHeight, halfDepth,     // 5
                -halfWidth, halfHeight, halfDepth,     // 6
                halfWidth, halfHeight, halfDepth);     // 7

        double w = width;
        double h = height;
        double d = depth;
        // Rectangles de texture : droite, face, gauche, arriere, dessus, dessous.
        addFaceTexCoords(mesh, u, v + d, d, h, textureWidth, textureHeight);
        addFaceTexCoords(mesh, u + d, v + d, w, h, textureWidth, textureHeight);
        addFaceTexCoords(mesh, u + d + w, v + d, d, h, textureWidth, textureHeight);
        addFaceTexCoords(mesh, u + d + w + d, v + d, w, h, textureWidth, textureHeight);
        addFaceTexCoords(mesh, u + d, v, w, d, textureWidth, textureHeight);
        addFaceTexCoords(mesh, u + d + w, v, w, d, textureWidth, textureHeight);

        // Chaque face reutilise ses quatre coins : 0 haut-gauche, 1 haut-droit,
        // 2 bas-gauche, 3 bas-droit.
        addQuad(mesh, 4, 0, 6, 2, 0);   // cote droit  (x negatif)
        addQuad(mesh, 0, 1, 2, 3, 1);   // face avant  (z negatif)
        addQuad(mesh, 1, 5, 3, 7, 2);   // cote gauche (x positif)
        addQuad(mesh, 5, 4, 7, 6, 3);   // arriere     (z positif)
        addQuad(mesh, 4, 5, 0, 1, 4);   // dessus      (y negatif)
        addQuad(mesh, 2, 3, 6, 7, 5);   // dessous     (y positif)
        return mesh;
    }

    /** Ajoute les quatre coordonnees de texture d'un panneau rectangulaire. */
    private void addFaceTexCoords(TriangleMesh mesh, double x, double y, double width,
                                  double height, int textureWidth, int textureHeight) {
        float left = (float) (x / textureWidth);
        float right = (float) ((x + width) / textureWidth);
        float top = (float) (y / textureHeight);
        float bottom = (float) ((y + height) / textureHeight);
        mesh.getTexCoords().addAll(left, top, right, top, left, bottom, right, bottom);
    }

    /**
     * Ajoute les deux triangles d'une face.
     *
     * @param faceIndex position du panneau dans la table des coordonnees de texture
     */
    private void addQuad(TriangleMesh mesh, int topLeft, int topRight, int bottomLeft,
                         int bottomRight, int faceIndex) {
        int base = faceIndex * 4;
        mesh.getFaces().addAll(
                topLeft, base, bottomLeft, base + 2, topRight, base + 1,
                topRight, base + 1, bottomLeft, base + 2, bottomRight, base + 3);
    }

    /** Materiau sans reflet, pour un rendu fidele aux textures du jeu. */
    private PhongMaterial pixelMaterial(Image texture) {
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseMap(texture);
        material.setSpecularColor(Color.TRANSPARENT);
        material.setSpecularPower(1);
        return material;
    }

    /**
     * Agrandit une texture par duplication de pixels.
     *
     * <p>JavaFX applique un filtrage bilineaire aux textures : sans cet agrandissement
     * au plus proche voisin, un skin de 64 pixels de cote apparaitrait flou.</p>
     */
    private Image upscale(Image source, int factor) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        PixelReader reader = source.getPixelReader();
        if (width <= 0 || height <= 0 || reader == null) {
            return source;
        }
        int targetWidth = width * factor;
        int targetHeight = height * factor;

        // Lecture et ecriture par tableaux d'entiers plutot que pixel par pixel : la
        // version precedente creait un objet Color par pixel source et appelait
        // setColor pour chacun des pixels agrandis, soit plus de 260 000 appels et
        // autant d'allocations a chaque changement de skin.
        int[] sourcePixels = new int[width * height];
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(),
                sourcePixels, 0, width);

        int[] targetPixels = new int[targetWidth * targetHeight];
        for (int y = 0; y < height; y++) {
            int rowStart = y * factor * targetWidth;
            for (int x = 0; x < width; x++) {
                int argb = sourcePixels[y * width + x];
                int columnStart = x * factor;
                // Premiere ligne du bloc agrandi, remplie a la main.
                for (int dx = 0; dx < factor; dx++) {
                    targetPixels[rowStart + columnStart + dx] = argb;
                }
            }
            // Les lignes suivantes du bloc sont des copies de la premiere.
            for (int dy = 1; dy < factor; dy++) {
                System.arraycopy(targetPixels, rowStart, targetPixels,
                        rowStart + dy * targetWidth, targetWidth);
            }
        }

        WritableImage target = new WritableImage(targetWidth, targetHeight);
        target.getPixelWriter().setPixels(0, 0, targetWidth, targetHeight,
                PixelFormat.getIntArgbInstance(), targetPixels, 0, targetWidth);
        return target;
    }
}
