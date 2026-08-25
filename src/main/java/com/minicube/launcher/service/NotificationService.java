package com.minicube.launcher.service;

import com.minicube.launcher.model.Notification;
import com.minicube.launcher.util.Log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Diffusion des notifications vers l'interface.
 *
 * <p>Les services metier publient ici ; la couche graphique s'abonne et affiche les
 * bandeaux. Cette indirection evite que les services connaissent JavaFX.</p>
 */
public class NotificationService {

    private static final int HISTORY_SIZE = 50;

    private final Deque<Notification> history = new ArrayDeque<>();
    private final List<Consumer<Notification>> listeners = new CopyOnWriteArrayList<>();
    private final ConfigService config;

    public NotificationService(ConfigService config) {
        this.config = config;
    }

    /**
     * Publie une notification.
     *
     * <p>Le message est toujours journalise, meme lorsque l'affichage des notifications
     * est desactive dans les parametres : la trace reste disponible dans l'onglet
     * Journal.</p>
     */
    public void publish(Notification notification) {
        switch (notification.type()) {
            case ERROR -> Log.error(notification.title() + " - " + notification.message());
            case WARNING -> Log.warn(notification.title() + " - " + notification.message());
            default -> Log.info(notification.title() + " - " + notification.message());
        }
        synchronized (history) {
            history.addLast(notification);
            while (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
        }
        if (!config.settings().isNotificationsEnabled()) {
            return;
        }
        for (Consumer<Notification> listener : listeners) {
            try {
                listener.accept(notification);
            } catch (Exception e) {
                Log.debug("Un afficheur de notification a echoue : " + e.getMessage());
            }
        }
    }

    public void info(String title, String message) {
        publish(Notification.info(title, message));
    }

    public void success(String title, String message) {
        publish(Notification.success(title, message));
    }

    public void warning(String title, String message) {
        publish(Notification.warning(title, message));
    }

    public void error(String title, String message) {
        publish(Notification.error(title, message));
    }

    /** Historique des notifications, de la plus ancienne a la plus recente. */
    public List<Notification> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    /** Abonne un afficheur (bandeaux de l'interface). */
    public void addListener(Consumer<Notification> listener) {
        listeners.add(listener);
    }
}
