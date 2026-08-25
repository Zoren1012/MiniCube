package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.model.ServerStatus;
import com.minicube.launcher.ui.view.HomeView;
import com.minicube.launcher.util.Fx;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;

import java.util.List;

/**
 * Controleur de l'onglet Accueil : charge les actualites et surveille l'etat du serveur
 * principal du projet.
 */
public class HomeController {

    private final LauncherContext context;
    private final HomeView view = new HomeView();

    private ServerEntry watchedServer;

    public HomeController(LauncherContext context) {
        this.context = context;
        view.refreshButton().setOnAction(event -> refresh());
        context.accounts().addChangeListener(account -> updateWelcome());
        updateWelcome();
        refresh();
    }

    public Node root() {
        return view.root();
    }

    /** Met a jour le message de bienvenue avec le pseudo du compte actif. */
    private void updateWelcome() {
        String name = context.accounts().active()
                .map(account -> account.getUsername())
                .orElse(I18n.tr("home.guest"));
        view.welcomeLabel().setText(I18n.tr("home.welcome", name));
    }

    /** Recharge les actualites et relance la mesure d'etat du serveur. */
    public void refresh() {
        loadNews();
        loadServerStatus();
    }

    private void loadNews() {
        Fx.async(() -> context.news().load(),
                items -> view.setNews(items),
                error -> view.setNews(List.of()));
    }

    /**
     * Mesure l'etat du premier serveur declare officiel, ou du premier de la liste.
     * C'est celui que l'accueil met en avant.
     */
    private void loadServerStatus() {
        view.statusValue().setText(I18n.tr("home.checking"));
        view.playersValue().setText("-");
        view.pingValue().setText("-");

        Fx.async(() -> {
            List<ServerEntry> servers = context.serverList().loadAll();
            if (servers.isEmpty()) {
                return null;
            }
            ServerEntry target = servers.stream()
                    .filter(ServerEntry::isOfficial)
                    .findFirst()
                    .orElse(servers.get(0));
            watchedServer = target;
            return new Object[]{target, context.serverPing().ping(target)};
        }, result -> {
            if (result == null) {
                view.serverNameLabel().setText(I18n.tr("home.noServer"));
                view.statusValue().setText("-");
                return;
            }
            ServerEntry server = (ServerEntry) result[0];
            ServerStatus status = (ServerStatus) result[1];
            view.serverNameLabel().setText(server.getName() + "  -  " + server.fullAddress());
            view.statusValue().setText(status.online()
                    ? I18n.tr("home.status.online") : I18n.tr("home.status.offline"));
            view.statusValue().getStyleClass().removeAll("status-online", "status-offline");
            view.statusValue().getStyleClass().add(status.online()
                    ? "status-online" : "status-offline");
            view.playersValue().setText(status.playersLabel());
            view.pingValue().setText(status.pingLabel());
        }, error -> {
            view.statusValue().setText(I18n.tr("home.status.offline"));
            view.serverNameLabel().setText(I18n.tr("home.noServer"));
        });
    }

    /** Serveur actuellement surveille par l'accueil (peut etre null). */
    public ServerEntry watchedServer() {
        return watchedServer;
    }
}
