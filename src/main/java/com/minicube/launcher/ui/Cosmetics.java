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
     */
    public record Pack(String id, String style, String accent) {
    }

    /**
     * Les habillages, dans l'ordre d'affichage.
     *
     * <p>Les quatre premiers reprennent la couleur native de leur style ; les suivants
     * la detournent, pour montrer qu'un style n'impose pas sa teinte.</p>
     */
    private static final List<Pack> PACKS = List.of(
            new Pack("amethyste", "dark", "#7C5CFF"),
            new Pack("nether", "nether", "#FF7A3D"),
            new Pack("abysse", "abysse", "#2FD3C4"),
            new Pack("foret", "foret", "#7DC95E"),
            new Pack("sakura", "sakura", "#D9548A"),
            new Pack("bloc", "minecraft", "#3C8527"),
            new Pack("aurore", "dark", "#46E0A8"),
            new Pack("braise", "nether", "#FFC24D"),
            new Pack("givre", "abysse", "#7FB0FF"),
            new Pack("papier", "light", "#B8760D"));

    private Cosmetics() {
    }

    public static List<Pack> all() {
        return PACKS;
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
