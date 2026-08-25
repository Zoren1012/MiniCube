package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.model.ServerStatus;
import com.minicube.launcher.ui.view.ServersView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controleur de l'onglet Serveurs : chargement de la liste, mesure des latences en
 * parallele et connexion directe.
 */
public class ServersController {

    private final LauncherContext context;
    private final ServersView view = new ServersView();
    private final Consumer<ServerEntry> joinHandler;

    public ServersController(LauncherContext context, Consumer<ServerEntry> joinHandler) {
        this.context = context;
        this.joinHandler = joinHandler;

        view.refreshButton().setOnAction(event -> refresh());
        view.addButton().setOnAction(event -> addServer());
        refresh();
    }

    public Node root() {
        return view.root();
    }

    /**
     * Recharge la liste puis interroge chaque serveur.
     *
     * <p>Les cartes sont affichees immediatement avec une latence inconnue, puis chaque
     * mesure met a jour l'affichage des son arrivee : l'onglet reste utilisable meme si
     * un serveur met plusieurs secondes a repondre.</p>
     */
    public final void refresh() {
        view.showLoading();
        Fx.async(() -> {
            List<ServerEntry> all = context.serverList().loadAll();
            Set<String> customKeys = new LinkedHashSet<>();
            for (ServerEntry custom : context.serverList().loadCustomServers()) {
                customKeys.add(key(custom));
            }
            return new Object[]{all, customKeys};
        }, result -> {
            @SuppressWarnings("unchecked")
            List<ServerEntry> servers = (List<ServerEntry>) result[0];
            @SuppressWarnings("unchecked")
            Set<String> customKeys = (Set<String>) result[1];
            render(servers, customKeys);
        }, error -> view.showEmpty());
    }

    private void render(List<ServerEntry> servers, Set<String> customKeys) {
        if (servers.isEmpty()) {
            view.showEmpty();
            return;
        }
        // Premier rendu sans latence, puis une mesure par serveur.
        List<ServerStatus> statuses = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) {
            statuses.add(null);
        }
        redraw(servers, statuses, customKeys);

        for (int index = 0; index < servers.size(); index++) {
            final int position = index;
            final ServerEntry server = servers.get(index);
            Fx.async(() -> context.serverPing().ping(server),
                    status -> {
                        statuses.set(position, status);
                        redraw(servers, statuses, customKeys);
                    },
                    error -> {
                        statuses.set(position, ServerStatus.offline(error.getMessage()));
                        redraw(servers, statuses, customKeys);
                    });
        }
    }

    private void redraw(List<ServerEntry> servers, List<ServerStatus> statuses,
                        Set<String> customKeys) {
        view.clearServers();
        for (int i = 0; i < servers.size(); i++) {
            ServerEntry server = servers.get(i);
            view.addServerCard(server, statuses.get(i), customKeys.contains(key(server)),
                    this::join, this::removeServer);
        }
    }

    private String key(ServerEntry server) {
        return server.getAddress().toLowerCase() + ":" + server.getPort();
    }

    /** Delegue le lancement du jeu avec connexion directe au controleur principal. */
    private void join(ServerEntry server) {
        joinHandler.accept(server);
    }

    /** Ajoute un serveur saisi manuellement, en acceptant la notation hote:port. */
    private void addServer() {
        String name = view.nameField().getText();
        String address = view.addressField().getText();
        if (name == null || name.isBlank() || address == null || address.isBlank()) {
            context.notifications().warning(I18n.tr("servers.title"),
                    I18n.tr("servers.missingFields"));
            return;
        }
        String host = address.trim();
        int port = 25565;
        int separator = host.lastIndexOf(':');
        if (separator > 0) {
            try {
                port = Integer.parseInt(host.substring(separator + 1).trim());
                host = host.substring(0, separator).trim();
            } catch (NumberFormatException e) {
                context.notifications().warning(I18n.tr("servers.title"),
                        I18n.tr("servers.invalidPort"));
                return;
            }
        }
        ServerEntry entry = new ServerEntry(name.trim(), host, port);
        context.serverList().addCustomServer(entry);
        view.nameField().clear();
        view.addressField().clear();
        context.notifications().success(I18n.tr("servers.title"),
                I18n.tr("servers.added", entry.getName()));
        refresh();
    }

    private void removeServer(ServerEntry server) {
        context.serverList().removeCustomServer(server);
        context.notifications().info(I18n.tr("servers.title"),
                I18n.tr("servers.removed", server.getName()));
        refresh();
    }
}
