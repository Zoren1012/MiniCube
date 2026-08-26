package com.minicube.launcher.ui.controller;

import com.minicube.launcher.core.LauncherContext;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.service.Challenges;
import com.minicube.launcher.service.ShopService;
import com.minicube.launcher.ui.Cosmetics;
import com.minicube.launcher.ui.view.SupportView;
import com.minicube.launcher.util.I18n;
import javafx.scene.Node;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controleur de l'onglet Boutique.
 *
 * <p>Un clic sur une carte fait la seule chose sensee a ce moment-la : appliquer
 * l'habillage si on le possede, l'acheter sinon. Deux boutons par carte — « Acheter »
 * puis « Appliquer » — auraient double le nombre de clics pour rien.</p>
 *
 * <p>Appliquer un habillage pose deux reglages d'un coup, le style et la couleur
 * d'accent. C'est la difference avec l'onglet Style, ou les deux se reglent separement :
 * ici on choisit un resultat, pas des ingredients.</p>
 */
public class SupportController {

    private final LauncherContext context;
    private final SupportView view = new SupportView();
    private final Runnable onThemeChanged;
    private final Runnable onChallengesChecked;
    /** Garde une reference : le service ne retient que des Runnable. */
    private final Runnable shopListener = this::refresh;

    public SupportController(LauncherContext context, Runnable onThemeChanged,
                             Runnable onChallengesChecked) {
        this.context = context;
        this.onThemeChanged = onThemeChanged;
        this.onChallengesChecked = onChallengesChecked;

        view.setOnPackPicked(this::pick);
        // Les pieces arrivent a la fin d'une partie, pendant que l'onglet est peut-etre
        // deja ouvert : sans abonnement, le solde afficherait une valeur perimee.
        context.shop().addChangeListener(shopListener);
        refresh();
    }

    public Node root() {
        return view.root();
    }

    public SupportView view() {
        return view;
    }

    /** Detache l'abonnement quand la page est remplacee. */
    public void dispose() {
        context.shop().removeChangeListener(shopListener);
        view.animation().dispose();
    }

    /* ------------------------------------------------------------------ */
    /* Affichage                                                           */
    /* ------------------------------------------------------------------ */

    /** Remet le solde et l'etat de chaque carte en accord avec le service. */
    public void refresh() {
        ShopService shop = context.shop();
        LauncherSettings settings = context.config().settings();
        view.setBalance(shop.balance(), shop.playMinutes(), shop.sessions());

        Map<String, SupportView.State> states = new LinkedHashMap<>();
        for (Cosmetics.Pack pack : Cosmetics.all()) {
            states.put(pack.id(), stateOf(pack, shop, settings));
        }
        view.refreshPacks(states);
        view.refreshChallenges(shop.stats(), claimedIds());
    }

    /** Defis deja payes, tels que la vue les affiche. */
    private java.util.Set<String> claimedIds() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (Challenges.Challenge challenge : Challenges.all()) {
            if (context.shop().isClaimed(challenge.id())) {
                ids.add(challenge.id());
            }
        }
        return ids;
    }

    private SupportView.State stateOf(Cosmetics.Pack pack, ShopService shop,
                                      LauncherSettings settings) {
        if (Cosmetics.isActive(pack, settings.getTheme(), settings.getAccentColor())) {
            return SupportView.State.EQUIPPED;
        }
        if (shop.owns(pack.id())) {
            return SupportView.State.OWNED;
        }
        return pack.price() <= shop.balance()
                ? SupportView.State.BUYABLE : SupportView.State.LOCKED;
    }

    /* ------------------------------------------------------------------ */
    /* Achat et application                                                */
    /* ------------------------------------------------------------------ */

    /** Achete l'habillage s'il n'est pas encore acquis, puis l'applique. */
    private void pick(Cosmetics.Pack pack) {
        if (!context.shop().owns(pack.id()) && !purchase(pack)) {
            return;
        }
        apply(pack);
    }

    /**
     * Tente l'achat.
     *
     * @return vrai si l'habillage est desormais acquis
     */
    private boolean purchase(Cosmetics.Pack pack) {
        String name = I18n.tr("support.pack." + pack.id());
        ShopService.Purchase result = context.shop().buy(pack.id());
        switch (result) {
            case DONE -> {
                context.notifications().success(I18n.tr("support.title"),
                        I18n.tr("support.bought", name, pack.price()));
                // Un achat fait grossir la collection : le defi du collectionneur peut
                // tomber a cet instant precis.
                onChallengesChecked.run();
                return true;
            }
            case ALREADY_OWNED -> {
                return true;
            }
            // Dire ce qui manque vaut mieux qu'un refus sec : le joueur sait alors
            // combien de temps de jeu le separe de l'habillage.
            case TOO_EXPENSIVE -> {
                int missing = pack.price() - context.shop().balance();
                context.notifications().warning(I18n.tr("support.title"),
                        I18n.tr("support.tooExpensive", name, missing));
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * Applique un habillage.
     *
     * <p>La couleur est ecrite en clair dans les reglages plutot que d'etre deduite du
     * style : l'utilisateur peut ensuite la retoucher dans l'onglet Style sans que son
     * choix soit ecrase au prochain demarrage.</p>
     */
    private void apply(Cosmetics.Pack pack) {
        LauncherSettings settings = context.config().settings();
        if (Cosmetics.isActive(pack, settings.getTheme(), settings.getAccentColor())) {
            return;
        }
        settings.setTheme(pack.style());
        settings.setAccentColor(pack.accent());
        context.config().save();

        onThemeChanged.run();
        refresh();
        context.notifications().success(I18n.tr("support.title"),
                I18n.tr("support.applied", I18n.tr("support.pack." + pack.id())));
    }
}
