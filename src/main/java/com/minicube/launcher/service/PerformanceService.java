package com.minicube.launcher.service;

import com.minicube.launcher.util.Log;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mesures de fonctionnement du launcher et du jeu.
 *
 * <p>Ce service ne connait pas JavaFX : la cadence d'affichage lui est <b>transmise</b>
 * par l'interface, qui seule peut la compter. Tout le reste est lu dans les compteurs de
 * la machine virtuelle et du systeme.</p>
 *
 * <h2>Ce que veut dire "temps de demarrage de Minecraft"</h2>
 *
 * <p>Le chiffre annonce n'est pas le temps mis pour lancer le processus, qui ne dirait
 * rien : c'est le delai entre l'appui sur Jouer et le moment ou le jeu <b>signale qu'il
 * est pret</b> dans sa propre sortie. C'est ce que le joueur ressent comme le temps de
 * chargement.</p>
 */
public class PerformanceService {

    /**
     * Lignes ecrites par Minecraft lorsqu'il atteint son menu principal.
     *
     * <p>Le moteur sonore est initialise juste avant l'affichage du menu, et ce message
     * n'a pas change depuis des annees. Les autres servent de repli selon les versions
     * et les chargeurs de mods.</p>
     */
    private static final String[] READY_MARKERS = {
            "OpenAL initialized",
            "Sound engine started",
            "Created: 1024x512 textures-atlas",
            "Backend library: LWJGL"
    };

    /** Instantane des mesures, tel qu'affiche par le tableau de bord. */
    public record Snapshot(double launcherCpu, double systemCpu,
                           long heapUsedMb, long heapMaxMb,
                           long systemUsedMb, long systemTotalMb,
                           double framesPerSecond, int threads,
                           String javaVersion, String javaVendor,
                           OptionalLong gameStartupMillis) {

        public String launcherCpuLabel() {
            return percent(launcherCpu);
        }

        public String systemCpuLabel() {
            return percent(systemCpu);
        }

        private static String percent(double value) {
            return value < 0 ? "-" : String.format(Locale.ROOT, "%.1f %%", value * 100);
        }

        public String heapLabel() {
            return heapUsedMb + " Mo";
        }

        public String systemMemoryLabel() {
            return systemTotalMb <= 0
                    ? "-"
                    : String.format(Locale.ROOT, "%.1f / %.1f Go",
                    systemUsedMb / 1024d, systemTotalMb / 1024d);
        }

        public String framesLabel() {
            return framesPerSecond <= 0
                    ? "-"
                    : String.format(Locale.ROOT, "%.0f img/s", framesPerSecond);
        }

        /** Delai entre l'appui sur Jouer et le menu principal du jeu. */
        public String gameStartupLabel() {
            if (gameStartupMillis.isEmpty()) {
                return "-";
            }
            double seconds = gameStartupMillis.getAsLong() / 1000d;
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
    }

    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

    private volatile double framesPerSecond;
    private final AtomicLong launchRequestedAt = new AtomicLong();
    private final AtomicLong gameStartupMillis = new AtomicLong(-1);

    /**
     * Enregistre la cadence mesuree par l'interface.
     *
     * @param value images par seconde, ou zero si la mesure n'a pas encore abouti
     */
    public void reportFrameRate(double value) {
        this.framesPerSecond = value;
    }

    /** A appeler au moment ou le joueur demande le lancement. */
    public void markLaunchRequested() {
        launchRequestedAt.set(System.currentTimeMillis());
        gameStartupMillis.set(-1);
    }

    /**
     * Examine une ligne de la sortie du jeu pour y reperer le signal de fin de chargement.
     *
     * <p>Seule la premiere correspondance compte : les versions moddees repetent
     * certains de ces messages.</p>
     */
    public void inspectGameOutput(String line) {
        if (line == null || launchRequestedAt.get() == 0 || gameStartupMillis.get() >= 0) {
            return;
        }
        for (String marker : READY_MARKERS) {
            if (line.contains(marker)) {
                long elapsed = System.currentTimeMillis() - launchRequestedAt.get();
                gameStartupMillis.set(elapsed);
                Log.info("Minecraft pret en " + elapsed / 1000d + " s");
                return;
            }
        }
    }

    /** Duree du dernier chargement de Minecraft, si une partie a ete lancee. */
    public OptionalLong lastGameStartupMillis() {
        long value = gameStartupMillis.get();
        return value < 0 ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /* ------------------------------------------------------------------ */
    /* Releve en fond                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * Dernier releve, publie par le fil de mesure.
     *
     * <p>Le detail compte : sur Windows, le <b>premier</b> appel a
     * {@code getProcessCpuLoad()} coute pres de 600 ms, et chacun des suivants une
     * vingtaine de millisecondes. Interroger ces compteurs depuis le fil de l'interface
     * ferait perdre une image par seconde, et retarderait de plus d'une demi-seconde la
     * premiere ouverture de l'onglet. Le releve se fait donc a l'ecart, et l'interface
     * ne lit qu'une valeur deja calculee.</p>
     */
    private volatile Snapshot latest = emptySnapshot();

    private Thread sampler;
    private volatile boolean sampling;

    /** Demarre le releve periodique. Sans effet s'il tourne deja. */
    public synchronized void startSampling() {
        if (sampling) {
            return;
        }
        sampling = true;
        sampler = new Thread(() -> {
            while (sampling) {
                latest = measure();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "minicube-mesures");
        sampler.setDaemon(true);
        sampler.start();
    }

    /** Arrete le releve : rien ne doit tourner pour un onglet ferme. */
    public synchronized void stopSampling() {
        sampling = false;
        if (sampler != null) {
            sampler.interrupt();
            sampler = null;
        }
    }

    /**
     * Dernier etat connu.
     *
     * <p>Retour immediat : la valeur a ete calculee sur le fil de mesure. L'interface
     * peut l'appeler aussi souvent qu'elle veut.</p>
     */
    public Snapshot sample() {
        return latest;
    }

    private Snapshot emptySnapshot() {
        return new Snapshot(-1, -1, 0, 0, 0, 0, 0, 0,
                System.getProperty("java.version", "?"),
                System.getProperty("java.vendor", "?"),
                OptionalLong.empty());
    }

    /** Interroge reellement les compteurs. A n'appeler que depuis le fil de mesure. */
    private Snapshot measure() {
        double launcherCpu = -1;
        double systemCpu = -1;
        long systemTotal = 0;
        long systemUsed = 0;

        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            launcherCpu = sun.getProcessCpuLoad();
            systemCpu = sun.getCpuLoad();
            systemTotal = sun.getTotalMemorySize() / 1_048_576;
            systemUsed = systemTotal - sun.getFreeMemorySize() / 1_048_576;
        }

        var heap = memory.getHeapMemoryUsage();
        return new Snapshot(launcherCpu, systemCpu,
                heap.getUsed() / 1_048_576, heap.getMax() / 1_048_576,
                systemUsed, systemTotal,
                framesPerSecond, Thread.activeCount(),
                System.getProperty("java.version", "?"),
                System.getProperty("java.vendor", "?"),
                lastGameStartupMillis());
    }
}
