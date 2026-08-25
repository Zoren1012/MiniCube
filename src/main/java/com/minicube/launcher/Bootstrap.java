package com.minicube.launcher;

import javafx.application.Application;

/**
 * Point d'entree du jar auto-portant.
 *
 * <p>Cette classe n'herite pas de {@link Application} : c'est ce qui permet a JavaFX de
 * demarrer alors que ses modules sont sur le chemin de classes plutot que sur le chemin
 * de modules. Sans cette indirection, la JVM refuserait le lancement avec le message
 * "JavaFX runtime components are missing".</p>
 */
public final class Bootstrap {

    private Bootstrap() {
    }

    public static void main(String[] args) {
        Application.launch(MiniCubeLauncher.class, args);
    }
}
