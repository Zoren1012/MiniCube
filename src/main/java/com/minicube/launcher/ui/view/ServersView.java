package com.minicube.launcher.ui.view;

import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.model.ServerStatus;
import com.minicube.launcher.ui.Icons;
import com.minicube.launcher.ui.Ui;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Onglet Serveurs : liste des serveurs avec leur latence, leur frequentation et un
 * bouton de connexion directe.
 */
public class ServersView {

    private final VBox root;
    private final VBox serverContainer = new VBox(12);

    private final Button refreshButton = Ui.secondaryButton(I18n.tr("action.refresh"),
            Icons.REFRESH);
    private final TextField nameField = new TextField();
    private final TextField addressField = new TextField();
    private final Button addButton = Ui.primaryButton(I18n.tr("servers.add"), Icons.PLUS);

    public ServersView() {
        nameField.setPromptText(I18n.tr("servers.nameHint"));
        nameField.setPrefWidth(200);
        addressField.setPromptText(I18n.tr("servers.addressHint"));
        addressField.setPrefWidth(280);

        HBox addRow = new HBox(12, nameField, addressField, addButton);
        addRow.setAlignment(Pos.CENTER_LEFT);

        VBox addCard = Ui.card(I18n.tr("servers.addTitle"),
                Ui.hint(I18n.tr("servers.addHint")), addRow);

        Label listTitle = new Label(I18n.tr("servers.list"));
        listTitle.getStyleClass().add("card-title");
        HBox listHeader = new HBox(12, listTitle, Ui.growSpacer(), refreshButton);
        listHeader.setAlignment(Pos.CENTER_LEFT);

        VBox listCard = Ui.card(null, listHeader, serverContainer);
        VBox.setVgrow(listCard, Priority.ALWAYS);

        root = Ui.page(I18n.tr("servers.title"), I18n.tr("servers.subtitle"),
                listCard, addCard);
    }

    /** Affiche l'etat de chargement pendant la mesure des latences. */
    public void showLoading() {
        serverContainer.getChildren().setAll(
                Ui.emptyState(I18n.tr("servers.loading"), Icons.REFRESH));
    }

    /** Affiche le message d'absence de serveur. */
    public void showEmpty() {
        serverContainer.getChildren().setAll(
                Ui.emptyState(I18n.tr("servers.empty"), Icons.SERVER));
    }

    public void clearServers() {
        serverContainer.getChildren().clear();
    }

    /**
     * Ajoute une carte de serveur a la liste.
     *
     * @param server   serveur decrit
     * @param status   etat mesure, ou null si la mesure est en cours
     * @param custom   true pour un serveur ajoute par l'utilisateur (supprimable)
     * @param onJoin   action du bouton Rejoindre
     * @param onRemove action du bouton Supprimer
     */
    public void addServerCard(ServerEntry server, ServerStatus status, boolean custom,
                              Consumer<ServerEntry> onJoin, Consumer<ServerEntry> onRemove) {
        Label name = new Label(server.getName());
        name.getStyleClass().add("server-name");

        Label address = new Label(server.fullAddress());
        address.getStyleClass().add("setting-hint");

        VBox identity = new VBox(2, name, address);

        HBox badges = new HBox(8);
        badges.setAlignment(Pos.CENTER_LEFT);
        if (server.isOfficial()) {
            badges.getChildren().add(Ui.badge(I18n.tr("servers.official"), "badge-accent"));
        }
        if (!server.getRequiredVersion().isBlank()) {
            badges.getChildren().add(Ui.badge(server.getRequiredVersion(), "badge-neutral"));
        }

        Label players = new Label(status == null ? "..." : status.playersLabel());
        players.getStyleClass().add("server-metric");
        Label ping = new Label(status == null ? "..." : status.pingLabel());
        ping.getStyleClass().addAll("server-metric", pingStyle(status));

        VBox playersBox = metric(I18n.tr("servers.players"), players);
        VBox pingBox = metric(I18n.tr("servers.ping"), ping);

        Button join = Ui.primaryButton(I18n.tr("servers.join"), Icons.PLAY);
        join.setOnAction(event -> onJoin.accept(server));
        join.setDisable(status != null && !status.online());

        HBox actions = new HBox(10, join);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (custom) {
            Button remove = Ui.iconButton(Icons.TRASH, I18n.tr("action.delete"));
            remove.setOnAction(event -> onRemove.accept(server));
            actions.getChildren().add(remove);
        }

        HBox card = new HBox(18, identity, badges, Ui.growSpacer(), playersBox, pingBox, actions);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("server-card");
        Fx.hoverLift(card, 3);
        card.setPadding(new Insets(14, 18, 14, 18));

        if (status != null && !status.online() && status.error() != null) {
            Label error = Ui.hint(status.error());
            VBox wrapper = new VBox(6, card, error);
            serverContainer.getChildren().add(wrapper);
        } else {
            serverContainer.getChildren().add(card);
        }
    }

    private VBox metric(String title, Label value) {
        Label label = new Label(title);
        label.getStyleClass().add("stat-label");
        VBox box = new VBox(2, value, label);
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(84);
        return box;
    }

    private String pingStyle(ServerStatus status) {
        if (status == null) {
            return "ping-unknown";
        }
        return switch (status.pingQuality()) {
            case 0 -> "ping-good";
            case 1 -> "ping-medium";
            case 2 -> "ping-bad";
            default -> "ping-unknown";
        };
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public Node root() {
        return root;
    }

    public Button refreshButton() {
        return refreshButton;
    }

    public Button addButton() {
        return addButton;
    }

    public TextField nameField() {
        return nameField;
    }

    public TextField addressField() {
        return addressField;
    }
}
