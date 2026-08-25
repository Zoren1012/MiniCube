package com.minicube.launcher.ui.component;

import com.minicube.launcher.model.Notification;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.util.Fx;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Couche transparente superposee a l'interface, chargee d'afficher les notifications.
 *
 * <p>Les bandeaux apparaissent en haut a droite, s'empilent et disparaissent seuls.
 * La couche laisse passer les clics partout ou aucun bandeau n'est affiche.</p>
 */
public class ToastLayer extends StackPane {

    private static final int MAX_VISIBLE = 4;

    private final VBox stack = new VBox(10);

    public ToastLayer() {
        stack.setAlignment(Pos.TOP_RIGHT);
        stack.setPadding(new Insets(18));
        stack.setPickOnBounds(false);
        stack.setMaxWidth(380);

        setAlignment(Pos.TOP_RIGHT);
        // La couche couvre toute la fenetre : sans ces deux precautions, elle
        // capterait chaque clic destine a l'interface situee dessous. Un fond,
        // meme entierement transparent, suffirait a rendre la region cliquable
        // sur toute sa surface : la feuille de style ne lui en donne donc aucun.
        setPickOnBounds(false);
        setBackground(Background.EMPTY);
        getChildren().add(stack);
        getStyleClass().add("toast-layer");
    }

    /**
     * Affiche une notification.
     *
     * <p>Peut etre appelee depuis n'importe quel thread : le passage sur le thread
     * JavaFX est assure ici.</p>
     */
    public void show(Notification notification) {
        Fx.ui(() -> {
            Node toast = buildToast(notification);
            stack.getChildren().add(toast);
            while (stack.getChildren().size() > MAX_VISIBLE) {
                stack.getChildren().remove(0);
            }
            Fx.slideInUp(toast, 260, -14);

            PauseTransition delay =
                    new PauseTransition(Duration.millis(notification.displayMillis()));
            delay.setOnFinished(event ->
                    Fx.fadeOut(toast, 220, () -> stack.getChildren().remove(toast)));
            delay.play();
        });
    }

    private Node buildToast(Notification notification) {
        Label title = new Label(notification.title());
        title.getStyleClass().add("toast-title");

        Label message = new Label(notification.message());
        message.getStyleClass().add("toast-message");
        message.setWrapText(true);
        message.setMaxWidth(280);

        VBox texts = new VBox(3, title, message);

        HBox toast = new HBox(12, Icons.of(iconFor(notification.type()), 20), texts);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.getStyleClass().addAll("toast", "toast-" + notification.type().name()
                .toLowerCase());
        toast.setPadding(new Insets(14, 18, 14, 16));
        toast.setMaxWidth(360);
        // Un clic sur le bandeau le fait disparaitre immediatement.
        toast.setOnMouseClicked(event ->
                Fx.fadeOut(toast, 160, () -> stack.getChildren().remove(toast)));
        return toast;
    }

    private String iconFor(Notification.Type type) {
        return switch (type) {
            case SUCCESS -> Icons.CHECK;
            case WARNING -> Icons.WARNING;
            case ERROR -> Icons.ERROR;
            default -> Icons.INFO;
        };
    }
}
