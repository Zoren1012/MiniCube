package com.minicube.launcher.model;

/**
 * Etat d'avancement rapporte par les taches longues (verification, telechargement,
 * lancement du jeu).
 *
 * @param message libelle affiche a l'utilisateur
 * @param detail  complement facultatif : nom du fichier en cours, debit...
 * @param value   avancement entre 0 et 1 ; valeur negative pour une barre indeterminee
 */
public record Progress(String message, String detail, double value) {

    /** Progression indeterminee, pour une etape dont la duree est inconnue. */
    public static Progress indeterminate(String message) {
        return new Progress(message, "", -1);
    }

    public static Progress of(String message, double value) {
        return new Progress(message, "", value);
    }

    public static Progress of(String message, String detail, double value) {
        return new Progress(message, detail, value);
    }

    /** Etape terminee. */
    public static Progress done(String message) {
        return new Progress(message, "", 1);
    }

    public boolean isIndeterminate() {
        return value < 0;
    }

    /** Avancement en pourcentage, pour l'affichage textuel. */
    public int percent() {
        return isIndeterminate() ? 0 : (int) Math.round(value * 100);
    }
}
