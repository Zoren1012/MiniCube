package com.minicube.launcher.model;

import java.time.LocalDateTime;

/**
 * Notification affichee sous forme de bandeau temporaire dans le coin de la fenetre.
 *
 * @param title   titre court
 * @param message corps du message
 * @param type    nature de la notification, qui determine la couleur et l'icone
 * @param time    horodatage de creation
 */
public record Notification(String title, String message, Notification.Type type,
                           LocalDateTime time) {

    /** Nature d'une notification. */
    public enum Type { INFO, SUCCESS, WARNING, ERROR }

    public static Notification info(String title, String message) {
        return new Notification(title, message, Type.INFO, LocalDateTime.now());
    }

    public static Notification success(String title, String message) {
        return new Notification(title, message, Type.SUCCESS, LocalDateTime.now());
    }

    public static Notification warning(String title, String message) {
        return new Notification(title, message, Type.WARNING, LocalDateTime.now());
    }

    public static Notification error(String title, String message) {
        return new Notification(title, message, Type.ERROR, LocalDateTime.now());
    }

    /** Duree d'affichage conseillee : les erreurs restent visibles plus longtemps. */
    public int displayMillis() {
        return switch (type) {
            case ERROR -> 9000;
            case WARNING -> 7000;
            default -> 4500;
        };
    }
}
