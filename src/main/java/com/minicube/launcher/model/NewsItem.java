package com.minicube.launcher.model;

/**
 * Actualite affichee sur l'onglet Accueil.
 *
 * @param title    titre court
 * @param content  corps du message (texte brut)
 * @param date     date affichee, au format libre
 * @param category etiquette de categorie : Mise a jour, Evenement, Maintenance...
 * @param imageUrl illustration optionnelle
 * @param link     lien "en savoir plus" optionnel
 */
public record NewsItem(String title, String content, String date, String category,
                       String imageUrl, String link) {

    /** Actualite minimale, sans illustration ni lien. */
    public static NewsItem of(String title, String content, String date, String category) {
        return new NewsItem(title, content, date, category, "", "");
    }

    public boolean hasLink() {
        return link != null && !link.isBlank();
    }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }
}
