package com.minicube.launcher.ui.component;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;

/**
 * Tete du joueur affichee dans la barre laterale et l'onglet Skin.
 *
 * <p>L'image est d'abord recherchee sur un service de rendu public, ce qui donne une
 * tete avec sa seconde couche. En cas d'echec ou pour un compte hors-ligne, la tete est
 * extraite localement de la texture du skin, sans aucun appel reseau.</p>
 */
public class PlayerHead extends StackPane {

    private final ImageView imageView = new ImageView();

    /**
     * @param size cote de la vignette en pixels
     */
    public PlayerHead(double size) {
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        // Conserve l'aspect pixelise du jeu plutot que de lisser l'agrandissement.
        imageView.setSmooth(false);

        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getStyleClass().add("player-head");
        getChildren().add(imageView);

        setImage(null);
    }

    /**
     * Definit l'image affichee.
     *
     * @param head image deja prete, ou null pour utiliser la tete du skin par defaut
     */
    public void setImage(Image head) {
        if (head == null || head.isError()) {
            imageView.setImage(extractHead(DefaultSkin.image()));
        } else {
            imageView.setImage(head);
        }
    }

    /**
     * Charge une tete depuis une URL, en arriere-plan.
     *
     * <p>Le repli sur la texture locale est automatique si le telechargement echoue,
     * ce qui evite un trou dans l'interface en cas de coupure reseau.</p>
     *
     * @param url      adresse du rendu de tete
     * @param fallback texture de skin utilisee en cas d'echec (peut etre null)
     */
    public void loadFromUrl(String url, Image fallback) {
        if (url == null || url.isBlank()) {
            setImage(fallback == null ? null : extractHead(fallback));
            return;
        }
        Image remote = new Image(url, true);
        remote.errorProperty().addListener((observable, wasError, isError) -> {
            if (Boolean.TRUE.equals(isError)) {
                setImage(fallback == null ? null : extractHead(fallback));
            }
        });
        remote.progressProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.doubleValue() >= 1 && !remote.isError()) {
                imageView.setImage(remote);
            }
        });
        if (!remote.isError() && remote.getProgress() >= 1) {
            imageView.setImage(remote);
        } else {
            setImage(fallback == null ? null : extractHead(fallback));
        }
    }

    /**
     * Extrait la face avant de la tete d'une texture de skin, surcouche comprise.
     *
     * @param skin texture 64x64 ou 64x32
     * @return une image de 8 par 8 pixels
     */
    public static Image extractHead(Image skin) {
        PixelReader reader = skin.getPixelReader();
        if (reader == null) {
            return skin;
        }
        WritableImage head = new WritableImage(8, 8);
        var writer = head.getPixelWriter();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                writer.setColor(x, y, reader.getColor(8 + x, 8 + y));
            }
        }
        // La seconde couche (cheveux, casque) est superposee lorsqu'elle est opaque.
        if (skin.getWidth() >= 64 && skin.getHeight() >= 64) {
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    var overlay = reader.getColor(40 + x, 8 + y);
                    if (overlay.getOpacity() > 0.35) {
                        writer.setColor(x, y, overlay);
                    }
                }
            }
        }
        return head;
    }
}
