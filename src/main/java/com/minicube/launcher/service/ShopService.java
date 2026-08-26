package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.PlayerStats;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.Safety;
import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Economie de la Boutique : les pieces gagnees en jouant et les articles debloques.
 *
 * <h2>Pourquoi des pieces et pas de l'argent</h2>
 *
 * <p>Une boutique payante demande un encaissement, donc un serveur, un prestataire et la
 * responsabilite qui va avec. Rien de tout cela n'existe ici, et un bouton "Acheter" qui
 * ne debite rien serait un mensonge. Les pieces se gagnent donc en jouant : la boutique
 * fonctionne entierement, hors ligne, sans qu'un centime change de main.</p>
 *
 * <h2>Le solde est calcule, pas stocke</h2>
 *
 * <p>Seuls le temps de jeu, les defis accomplis et la liste des articles possedes sont
 * enregistres. Le solde est la difference entre ce que tout cela a rapporte et le prix de
 * ce qui est possede.
 * Un compteur de solde ecrit a part pourrait diverger de cette liste — apres une panne au
 * mauvais moment, ou quand le prix d'un article change d'une version a l'autre. Ici, la
 * question ne se pose pas : il n'y a qu'une seule source.</p>
 */
public class ShopService {

    /** Nom du fichier ou vivent les pieces et les articles possedes. */
    private static final String FILE_NAME = "shop.json";

    /** Pieces offertes a la premiere ouverture, pour que la boutique ne soit pas morte. */
    public static final int WELCOME_COINS = 250;
    /** Pieces gagnees par minute de jeu. */
    public static final int COINS_PER_MINUTE = 5;
    /** Pieces gagnees a chaque partie lancee, quelle qu'en soit la duree. */
    public static final int COINS_PER_SESSION = 25;

    /**
     * Duree maximale comptabilisee pour une partie, en minutes.
     *
     * <p>Une session laissee ouverte toute la nuit rapporterait autrement de quoi tout
     * acheter d'un coup, ce qui viderait la progression de son sens.</p>
     */
    public static final long MAX_MINUTES_PER_SESSION = 240;

    /** Etat serialise. */
    private static class Store {
        long playMinutes;
        int sessions;
        int moddedSessions;
        List<String> owned = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        List<String> claimed = new ArrayList<>();
    }

    private final Set<String> owned = new LinkedHashSet<>();
    /** Chargeurs deja utilises, un defi comptant leur variete. */
    private final Set<String> loaders = new LinkedHashSet<>();
    /** Defis deja payes : leur recompense ne se verse qu une fois. */
    private final Set<String> claimed = new LinkedHashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final ToIntFunction<String> priceOf;
    private long playMinutes;
    private int sessions;
    private int moddedSessions;

    /**
     * @param priceOf prix d'un article, 0 pour un article offert ou inconnu ; le
     *                catalogue vit dans l'interface, le service n'a pas a le connaitre
     */
    public ShopService(ToIntFunction<String> priceOf) {
        this.priceOf = priceOf;
        load();
    }

    /* ------------------------------------------------------------------ */
    /* Chargement et enregistrement                                        */
    /* ------------------------------------------------------------------ */

    private Path file() {
        return LauncherPaths.launcherDir().resolve(FILE_NAME);
    }

    private void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Store store = Json.read(path, new TypeToken<Store>() { }.getType());
            playMinutes = Math.max(0, store.playMinutes);
            sessions = Math.max(0, store.sessions);
            moddedSessions = Math.max(0, store.moddedSessions);
            owned.clear();
            loaders.clear();
            claimed.clear();
            if (store.owned != null) {
                owned.addAll(store.owned);
            }
            if (store.loaders != null) {
                loaders.addAll(store.loaders);
            }
            if (store.claimed != null) {
                claimed.addAll(store.claimed);
            }
            Log.info("Boutique : " + owned.size() + " article(s), " + balance() + " piece(s)");
        } catch (Exception e) {
            // Un fichier illisible ne doit pas empecher le launcher de demarrer : la
            // boutique repart a neuf, et le temps de jeu deja ecoule est perdu. C'est
            // regrettable, ce n'est pas grave.
            Log.warn("Boutique illisible, remise a neuf : " + e.getMessage());
            owned.clear();
            loaders.clear();
            claimed.clear();
            playMinutes = 0;
            sessions = 0;
            moddedSessions = 0;
        }
    }

    public void save() {
        try {
            Store store = new Store();
            store.playMinutes = playMinutes;
            store.sessions = sessions;
            store.moddedSessions = moddedSessions;
            store.owned = new ArrayList<>(owned);
            store.loaders = new ArrayList<>(loaders);
            store.claimed = new ArrayList<>(claimed);
            Path path = file();
            Files.createDirectories(path.getParent());
            Json.write(path, store);
            Safety.restrictToOwner(path);
        } catch (Exception e) {
            Log.warn("Boutique non enregistree : " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Gains                                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Comptabilise une partie terminee.
     *
     * @param millis duree de la partie
     * @param loader chargeur de la version jouee : Vanilla, Fabric, Forge...
     * @return pieces gagnees, zero si la partie a ete trop courte pour compter
     */
    public int recordSession(long millis, String loader) {
        if (millis <= 0) {
            return 0;
        }
        long minutes = Math.min(MAX_MINUTES_PER_SESSION, millis / 60_000);
        int before = totalEarned();
        playMinutes += minutes;
        sessions++;
        if (loader != null && !loader.isBlank()) {
            loaders.add(loader);
            // OptiFine n'ajoute pas de mods : il ne compte pas comme une partie moddee.
            if (!"Vanilla".equalsIgnoreCase(loader) && !"OptiFine".equalsIgnoreCase(loader)) {
                moddedSessions++;
            }
        }
        save();
        int gained = totalEarned() - before;
        notifyListeners();
        Log.info("Boutique : " + gained + " piece(s) gagnee(s) en " + minutes + " min");
        return gained;
    }

    /* ------------------------------------------------------------------ */
    /* Defis                                                               */
    /* ------------------------------------------------------------------ */

    /** Instantane des compteurs, tel que les defis le lisent. */
    public PlayerStats stats() {
        return new PlayerStats(sessions, playMinutes, loaders.size(), moddedSessions,
                owned.size());
    }

    /**
     * Verse les defis nouvellement accomplis.
     *
     * <p>Un defi est une condition relue, pas un evenement : appeler cette methode deux
     * fois ne paie pas deux fois, et un defi rempli pendant que le launcher etait ferme
     * est verse au demarrage suivant.</p>
     *
     * @return identifiants des defis payes a cet appel, vide si aucun
     */
    public synchronized List<String> claimCompletedChallenges() {
        PlayerStats stats = stats();
        List<String> justClaimed = new ArrayList<>();
        for (Challenges.Challenge challenge : Challenges.all()) {
            if (!claimed.contains(challenge.id()) && challenge.isComplete(stats)) {
                claimed.add(challenge.id());
                justClaimed.add(challenge.id());
            }
        }
        if (justClaimed.isEmpty()) {
            return List.of();
        }
        save();
        notifyListeners();
        Log.info("Boutique : defi(s) accompli(s) " + justClaimed);
        return justClaimed;
    }

    /** Vrai si la recompense de ce defi a deja ete versee. */
    public boolean isClaimed(String id) {
        return claimed.contains(id);
    }

    /** Tout ce que le joueur a gagne depuis le debut, prime de bienvenue comprise. */
    public int totalEarned() {
        int rewards = claimed.stream().mapToInt(Challenges::rewardOf).sum();
        return WELCOME_COINS
                + (int) Math.min(Integer.MAX_VALUE, playMinutes * COINS_PER_MINUTE)
                + sessions * COINS_PER_SESSION
                + rewards;
    }

    /** Somme des prix des articles possedes. */
    public int totalSpent() {
        return owned.stream().mapToInt(priceOf::applyAsInt).sum();
    }

    /** Pieces disponibles. Jamais negatif, meme si un prix augmente apres coup. */
    public int balance() {
        return Math.max(0, totalEarned() - totalSpent());
    }

    public long playMinutes() {
        return playMinutes;
    }

    public int sessions() {
        return sessions;
    }

    /* ------------------------------------------------------------------ */
    /* Achats                                                              */
    /* ------------------------------------------------------------------ */

    /** Resultat d'un achat, pour que l'interface dise pourquoi il n'a pas eu lieu. */
    public enum Purchase {
        /** Article debloque. */
        DONE,
        /** Deja possede : rien n'a ete debite. */
        ALREADY_OWNED,
        /** Solde insuffisant. */
        TOO_EXPENSIVE
    }

    /** Vrai si l'article est possede, ou s'il est offert. */
    public boolean owns(String id) {
        return priceOf.applyAsInt(id) <= 0 || owned.contains(id);
    }

    /**
     * Achete un article.
     *
     * <p>L'enregistrement precede la notification : si l'ecriture echoue, l'article n'est
     * pas annonce comme acquis.</p>
     */
    public synchronized Purchase buy(String id) {
        if (owns(id)) {
            return Purchase.ALREADY_OWNED;
        }
        int price = priceOf.applyAsInt(id);
        if (price > balance()) {
            return Purchase.TOO_EXPENSIVE;
        }
        owned.add(id);
        save();
        notifyListeners();
        Log.info("Boutique : " + id + " achete pour " + price + " piece(s)");
        return Purchase.DONE;
    }

    /** Articles possedes, hors articles offerts. */
    public Set<String> owned() {
        return Set.copyOf(owned);
    }

    /* ------------------------------------------------------------------ */
    /* Abonnement                                                          */
    /* ------------------------------------------------------------------ */

    /** Abonne un composant aux changements de solde ou de collection. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        // Copie : un abonne peut se desabonner en reagissant.
        for (Runnable listener : List.copyOf(listeners)) {
            try {
                listener.run();
            } catch (Exception e) {
                Log.warn("Abonne de la boutique en erreur : " + e.getMessage());
            }
        }
    }
}
