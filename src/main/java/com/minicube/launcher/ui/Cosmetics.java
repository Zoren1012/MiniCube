package com.minicube.launcher.ui;

import java.util.List;

/**
 * Catalogue des habillages proposes dans la Boutique.
 *
 * <p>Un habillage n'est pas un style : c'est un <b>look nomme</b>, soit un style associe
 * a une couleur d'accent. Deux habillages peuvent donc partager le meme style et ne pas
 * se ressembler — c'est tout l'interet, et c'est ce qui les distingue de l'onglet Style,
 * ou l'on regle le theme et la couleur separement.</p>
 *
 * <p>Tout est gratuit et le restera pour ce qui est deja au catalogue. Rien ici ne touche
 * au jeu : un habillage repeint le launcher, pas la partie.</p>
 */
public final class Cosmetics {

    /**
     * Un habillage du launcher.
     *
     * @param id     identifiant stable, utilise pour la traduction du nom
     * @param style  style applique
     * @param accent couleur d'accent au format {@code #RRGGBB}
     * @param price  prix en pieces ; zero pour un habillage offert d'emblee
     */
    public record Pack(String id, String style, String accent, int price) {

        /** Vrai si l'habillage est disponible sans rien depenser. */
        public boolean free() {
            return price <= 0;
        }
    }

    /**
     * Les habillages, dans l'ordre d'affichage.
     *
     * <p>Les trois premiers sont offerts : une boutique dont rien n'est accessible au
     * premier lancement ne se distingue pas d'une boutique vide. Les suivants coutent
     * d'autant plus qu'ils s'eloignent de l'habillage d'origine.</p>
     *
     * <p>Plusieurs habillages partagent un meme style avec une couleur differente : un
     * style n'impose pas sa teinte, et c'est ce qui separe un habillage d'un theme.</p>
     */
    private static final List<Pack> PACKS = List.of(
            new Pack("amethyste", "dark", "#7C5CFF", 0),
            new Pack("papier", "light", "#B8760D", 0),
            new Pack("bloc", "minecraft", "#3C8527", 0),
            new Pack("aurore", "dark", "#46E0A8", 150),
            new Pack("foret", "foret", "#7DC95E", 300),
            new Pack("abysse", "abysse", "#2FD3C4", 400),
            new Pack("givre", "abysse", "#7FB0FF", 450),
            new Pack("nether", "nether", "#FF7A3D", 600),
            new Pack("braise", "nether", "#FFC24D", 650),
            new Pack("sakura", "sakura", "#D9548A", 800));

    private Cosmetics() {
    }

    public static List<Pack> all() {
        return PACKS;
    }

    /**
     * Prix d'un habillage, zero pour un identifiant inconnu.
     *
     * <p>C'est la fonction que la boutique consulte pour chiffrer une collection. Un
     * identifiant inconnu vaut zero plutot que de lever une erreur : un article retire
     * du catalogue ne doit pas empecher le launcher de demarrer, ni faire disparaitre
     * les pieces de qui l'avait achete.</p>
     */
    public static int priceOf(String id) {
        return PACKS.stream()
                .filter(pack -> pack.id().equals(id))
                .mapToInt(Pack::price)
                .findFirst()
                .orElse(0);
    }

    /**
     * Vrai si les reglages courants correspondent exactement a cet habillage.
     *
     * <p>Une couleur d'accent vide signifie « celle du style » : l'habillage dont
     * l'accent est justement celui du style est alors le bon.</p>
     */
    public static boolean isActive(Pack pack, String theme, String accent) {
        if (!pack.style().equals(theme)) {
            return false;
        }
        String effective = accent == null || accent.isBlank()
                ? Styles.of(theme).accent()
                : accent;
        return pack.accent().equalsIgnoreCase(effective);
    }
}
