package com.minicube.launcher.service;

import com.minicube.launcher.model.PlayerStats;

import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Defis a accomplir pour gagner des pieces.
 *
 * <h2>Pourquoi des defis en plus du temps de jeu</h2>
 *
 * <p>Payer a la minute recompense l'attente : laisser le jeu ouvert rapporte autant que
 * d'y jouer. Les defis recompensent ce qu'on fait — revenir, essayer un chargeur de mods,
 * varier. C'est la difference entre une progression et un compteur.</p>
 *
 * <h2>Comment ils sont evalues</h2>
 *
 * <p>Un defi n'est pas un evenement qu'on aurait pu manquer : c'est une <b>condition
 * relue a chaque fois</b> sur les compteurs du joueur. Un defi ajoute plus tard se
 * declenche donc immediatement s'il etait deja rempli, et aucun ne peut etre perdu parce
 * que le launcher s'est ferme au mauvais moment.</p>
 */
public final class Challenges {

    /**
     * Un defi.
     *
     * @param id       identifiant stable, utilise pour la traduction et l'enregistrement
     * @param reward   pieces versees une seule fois
     * @param target   valeur a atteindre
     * @param measure  ce qui est mesure dans l'instantane du joueur
     */
    public record Challenge(String id, int reward, long target,
                            ToLongFunction<PlayerStats> measure) {

        /** Avancement, borne a la cible : un defi ne se depasse pas. */
        public long progress(PlayerStats stats) {
            return Math.min(target, Math.max(0, measure.applyAsLong(stats)));
        }

        public boolean isComplete(PlayerStats stats) {
            return measure.applyAsLong(stats) >= target;
        }

        /** Avancement entre 0 et 1, pour la barre de progression. */
        public double ratio(PlayerStats stats) {
            return target <= 0 ? 1 : progress(stats) / (double) target;
        }
    }

    private static final List<Challenge> ALL = List.of(
            // Le premier est volontairement trivial : il montre que les defis paient
            // vraiment, avant de demander quoi que ce soit.
            new Challenge("first", 100, 1, PlayerStats::sessions),
            new Challenge("regular", 250, 10, PlayerStats::sessions),
            new Challenge("marathon", 200, 60, PlayerStats::playMinutes),
            new Challenge("veteran", 500, 600, PlayerStats::playMinutes),
            new Challenge("modded", 250, 1, PlayerStats::moddedSessions),
            new Challenge("tinkerer", 400, 3, PlayerStats::distinctLoaders),
            new Challenge("collector", 400, 5, PlayerStats::ownedLooks));

    private Challenges() {
    }

    public static List<Challenge> all() {
        return ALL;
    }

    /**
     * Recompense d'un defi, zero pour un identifiant inconnu.
     *
     * <p>Un defi retire du catalogue ne doit pas faire disparaitre les pieces de qui
     * l'avait accompli — mais il ne doit pas non plus empecher le launcher de demarrer.
     * Zero est le compromis : le solde baisse, le launcher vit.</p>
     */
    public static int rewardOf(String id) {
        return ALL.stream()
                .filter(challenge -> challenge.id().equals(id))
                .mapToInt(Challenge::reward)
                .findFirst()
                .orElse(0);
    }
}
