package com.minicube.launcher.model;

/** Nature d'un compte enregistre dans le launcher. */
public enum AccountType {

    /** Compte Microsoft authentifie : jeu multijoueur en ligne et changement de skin possibles. */
    MICROSOFT("Microsoft"),

    /** Compte local sans authentification : serveurs hors-ligne et solo uniquement. */
    OFFLINE("Hors-ligne");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
