package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.GraphicsSettings;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Analyse la machine et la configuration, puis propose des corrections.
 *
 * <p>Chaque constat est accompagne de ce qu'il faut faire, et le plus souvent d'un
 * reglage a appliquer d'un clic. Un diagnostic qui se contente de nommer un probleme
 * sans dire comment le resoudre ne sert a rien.</p>
 *
 * <p>Le service ne modifie jamais la configuration de lui-meme : il retourne des
 * propositions, et c'est l'interface qui demande confirmation.</p>
 */
public class OptimizationService {

    /** Gravite d'un constat, du simple renseignement au probleme qui empechera de jouer. */
    public enum Level {
        /** Tout va bien sur ce point. */
        GOOD,
        /** Ameliorable, sans consequence immediate. */
        ADVICE,
        /** Genera la partie ou l'empechera. */
        PROBLEM
    }

    /**
     * Un constat.
     *
     * @param level       gravite
     * @param title       ce qui a ete constate
     * @param detail      pourquoi cela compte, en une phrase
     * @param actionLabel libelle du bouton, ou vide s'il n'y a rien a appliquer
     * @param action      correction a appliquer sur la configuration, ou null
     */
    public record Finding(Level level, String title, String detail,
                          String actionLabel, Runnable action) {

        public boolean hasAction() {
            return action != null && actionLabel != null && !actionLabel.isBlank();
        }
    }

    private final ConfigService config;
    private final JavaRuntimeService javaRuntime;
    private final LauncherPaths paths;

    public OptimizationService(ConfigService config, JavaRuntimeService javaRuntime,
                               LauncherPaths paths) {
        this.config = config;
        this.javaRuntime = javaRuntime;
        this.paths = paths;
    }

    /**
     * Passe en revue la machine et la configuration.
     *
     * <p>Methode bloquante : elle interroge le systeme de fichiers et, sous Windows, la
     * liste des cartes graphiques. A appeler depuis un fil de fond.</p>
     *
     * @param versionId version selectionnee, pour verifier le Java exige
     */
    public List<Finding> analyse(String versionId) {
        List<Finding> findings = new ArrayList<>();
        checkMemory(findings);
        checkJava(findings, versionId);
        checkGraphics(findings);
        checkShaders(findings);
        checkDiskSpace(findings);
        checkDedicatedGpu(findings);
        return findings;
    }

    /* ------------------------------------------------------------------ */
    /* Memoire                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * La memoire allouee au jeu.
     *
     * <p>Deux erreurs opposees sont frequentes : trop peu, et le jeu s'interrompt pour
     * liberer de la memoire ; beaucoup trop, et les pauses du ramasse-miettes
     * s'allongent au lieu de disparaitre, tout en affamant le systeme.</p>
     */
    private void checkMemory(List<Finding> findings) {
        LauncherSettings settings = config.settings();
        int allocated = settings.getRamMb();
        int system = OsUtil.totalSystemRamMb();
        int recommended = LauncherSettings.recommendedRam();

        if (system <= 0) {
            return;
        }
        if (allocated > system - 2048) {
            findings.add(new Finding(Level.PROBLEM,
                    "Trop de memoire allouee au jeu",
                    "Vous reservez " + gigabytes(allocated) + " sur " + gigabytes(system)
                            + " : il n'en reste pas assez pour Windows, qui se mettra a "
                            + "echanger sur le disque. Le jeu sera plus lent, pas plus rapide.",
                    "Ramener a " + gigabytes(recommended),
                    () -> settings.setRamMb(recommended)));
            return;
        }
        if (allocated < 2048) {
            findings.add(new Finding(Level.PROBLEM,
                    "Memoire insuffisante",
                    "Sous 2 Go, Minecraft moderne s'interrompt regulierement pour liberer "
                            + "de la memoire, et les mods aggravent le phenomene.",
                    "Passer a " + gigabytes(recommended),
                    () -> settings.setRamMb(recommended)));
            return;
        }
        if (allocated > 10240) {
            findings.add(new Finding(Level.ADVICE,
                    "Memoire allouee tres elevee",
                    "Au-dela de 8 a 10 Go, le gain est nul pour la plupart des packs, et "
                            + "les pauses du ramasse-miettes s'allongent.",
                    "Ramener a " + gigabytes(recommended),
                    () -> settings.setRamMb(recommended)));
            return;
        }
        findings.add(new Finding(Level.GOOD, "Memoire bien dimensionnee",
                gigabytes(allocated) + " alloues sur " + gigabytes(system) + " installes.",
                "", null));
    }

    /* ------------------------------------------------------------------ */
    /* Java                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Le runtime Java.
     *
     * <p>C'est la premiere cause de partie qui refuse de demarrer : chaque version de
     * Minecraft exige une version de Java precise, et celle du systeme est rarement la
     * bonne.</p>
     */
    private void checkJava(List<Finding> findings, String versionId) {
        List<JavaRuntimeService.JavaInstallation> installed =
                javaRuntime.detectInstallations(false);
        if (installed.isEmpty()) {
            findings.add(new Finding(Level.PROBLEM, "Aucun Java detecte",
                    "Sans runtime Java, aucune version de Minecraft ne peut demarrer. "
                            + "Installez Temurin 21, puis relancez la detection.",
                    "", null));
            return;
        }
        int required = requiredJavaFor(versionId);
        if (required <= 0) {
            findings.add(new Finding(Level.GOOD, "Java detecte",
                    installed.size() + " runtime(s) disponible(s).", "", null));
            return;
        }
        JavaRuntimeService.JavaInstallation match = installed.stream()
                .filter(java -> java.major() == required)
                .findFirst()
                .orElse(null);
        if (match == null) {
            findings.add(new Finding(Level.PROBLEM,
                    "Java " + required + " absent",
                    "La version selectionnee exige Java " + required
                            + ". Aucun runtime de cette version n'a ete trouve : la partie "
                            + "s'arretera au demarrage.",
                    "", null));
            return;
        }
        String configured = config.settings().getJavaPath();
        if (!configured.isBlank() && !configured.equals(match.executable().toString())) {
            findings.add(new Finding(Level.ADVICE,
                    "Un Java plus adapte est disponible",
                    "Vous imposez un chemin Java precis, alors que " + match.displayName()
                            + " correspond a ce qu'exige la version selectionnee.",
                    "Utiliser " + match.displayName(),
                    () -> config.settings().setJavaPath(match.executable().toString())));
            return;
        }
        findings.add(new Finding(Level.GOOD, "Java adapte a la version",
                match.displayName() + ", conforme a ce qu'exige Minecraft.", "", null));
    }

    /** Version majeure de Java exigee, deduite du numero de version du jeu. */
    private int requiredJavaFor(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return 0;
        }
        try {
            var json = new GameFileService(paths).readVersionJson(versionId);
            int required = javaRuntime.requiredMajorVersion(json);
            return required > 0 ? required : 0;
        } catch (Exception e) {
            Log.debug("Java exige indeterminable pour " + versionId + " : " + e.getMessage());
            return 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Graphismes                                                          */
    /* ------------------------------------------------------------------ */

    /** La distance de rendu, principal levier de performance du jeu. */
    private void checkGraphics(List<Finding> findings) {
        GraphicsSettings graphics = config.settings().getGraphics();
        int distance = graphics.getRenderDistance();
        int allocated = config.settings().getRamMb();

        // Un chunk charge coute de la memoire : au-dela de 16, il en faut nettement plus.
        if (distance > 16 && allocated < 6144) {
            findings.add(new Finding(Level.ADVICE,
                    "Distance de rendu elevee pour la memoire allouee",
                    distance + " chunks avec " + gigabytes(allocated)
                            + " : les chargements de terrain provoqueront des a-coups.",
                    "Ramener a 12 chunks",
                    () -> graphics.setRenderDistance(12)));
            return;
        }
        if (distance > 24) {
            findings.add(new Finding(Level.ADVICE,
                    "Distance de rendu tres elevee",
                    distance + " chunks est un reglage de capture d'ecran, pas de jeu : "
                            + "le cout croit plus vite que la distance.",
                    "Ramener a 16 chunks",
                    () -> graphics.setRenderDistance(16)));
            return;
        }
        findings.add(new Finding(Level.GOOD, "Reglages graphiques coherents",
                distance + " chunks de distance de rendu.", "", null));
    }

    /**
     * Les shaders sans le composant qui sait les lire.
     *
     * <p>Un pack de shaders installe ne fait rien tout seul : il faut Iris ou OptiFine.
     * Le cas est frequent et deroutant, puisque tout parait en place.</p>
     */
    private void checkShaders(List<Finding> findings) {
        if (!config.settings().isShadersEnabled()) {
            return;
        }
        Path mods = paths.gameDir().resolve("mods");
        boolean hasIris = false;
        if (Files.isDirectory(mods)) {
            try (var files = Files.list(mods)) {
                hasIris = files.anyMatch(file -> {
                    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.contains("iris") || name.contains("oculus")
                            || name.contains("optifine");
                });
            } catch (Exception e) {
                Log.debug("Dossier de mods illisible : " + e.getMessage());
            }
        }
        if (!hasIris) {
            findings.add(new Finding(Level.PROBLEM,
                    "Shaders actives, mais rien pour les lire",
                    "Un pack de shaders ne fait rien seul : il faut Iris (pour Fabric) ou "
                            + "OptiFine dans le dossier mods. En l'etat, le pack sera ignore "
                            + "sans message d'erreur.",
                    "", null));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Disque                                                              */
    /* ------------------------------------------------------------------ */

    private void checkDiskSpace(List<Finding> findings) {
        try {
            FileStore store = Files.getFileStore(paths.gameDir());
            long freeMb = store.getUsableSpace() / 1_048_576;
            if (freeMb < 2048) {
                findings.add(new Finding(Level.PROBLEM, "Espace disque critique",
                        gigabytes((int) freeMb) + " disponibles sur le disque du jeu. "
                                + "Un telechargement de version ou un monde qui s'agrandit "
                                + "echouera.", "", null));
            } else if (freeMb < 8192) {
                findings.add(new Finding(Level.ADVICE, "Espace disque limite",
                        gigabytes((int) freeMb) + " disponibles : de quoi jouer, mais peu "
                                + "pour installer un pack supplementaire.", "", null));
            } else {
                findings.add(new Finding(Level.GOOD, "Espace disque suffisant",
                        gigabytes((int) freeMb) + " disponibles.", "", null));
            }
        } catch (Exception e) {
            Log.debug("Espace disque indeterminable : " + e.getMessage());
        }
    }

    /* ------------------------------------------------------------------ */
    /* Carte graphique                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Cherche une carte graphique dediee.
     *
     * <p>Sur un portable equipe des deux, Windows lance souvent Java sur la puce
     * integree : le jeu rame sans raison apparente, et personne ne pense a verifier.
     * La liste est lue via l'inventaire du systeme, sans rien installer.</p>
     */
    private void checkDedicatedGpu(List<Finding> findings) {
        if (!OsUtil.isWindows()) {
            return;
        }
        List<String> adapters = listVideoControllers();
        if (adapters.isEmpty()) {
            return;
        }
        boolean dedicated = adapters.stream().anyMatch(this::isDedicated);
        boolean integrated = adapters.stream().anyMatch(name -> !isDedicated(name));

        if (dedicated && integrated) {
            findings.add(new Finding(Level.ADVICE,
                    "Deux cartes graphiques presentes",
                    "Cette machine a une puce integree et une carte dediee. Windows lance "
                            + "souvent Java sur l'integree : dans les parametres graphiques "
                            + "de Windows, forcez javaw.exe sur les hautes performances.",
                    "", null));
        } else if (!dedicated) {
            findings.add(new Finding(Level.ADVICE,
                    "Carte graphique integree",
                    "Aucune carte dediee detectee (" + adapters.get(0) + "). Visez des "
                            + "reglages modestes : 8 a 12 chunks, graphismes rapides.",
                    "", null));
        } else {
            findings.add(new Finding(Level.GOOD, "Carte graphique dediee",
                    adapters.stream().filter(this::isDedicated).findFirst().orElse(""),
                    "", null));
        }
    }

    private boolean isDedicated(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("nvidia") || lower.contains("geforce") || lower.contains("rtx")
                || lower.contains("gtx") || lower.contains("radeon") || lower.contains("rx ")
                || lower.contains("arc ");
    }

    /** Lit la liste des cartes graphiques declarees par Windows. */
    private List<String> listVideoControllers() {
        List<String> names = new ArrayList<>();
        try {
            Path powershell = Path.of(System.getenv("SystemRoot") == null
                    ? "C:\\Windows" : System.getenv("SystemRoot"),
                    "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (!Files.isRegularFile(powershell)) {
                return names;
            }
            ProcessBuilder builder = new ProcessBuilder(powershell.toString(),
                    "-NoProfile", "-NonInteractive", "-Command",
                    "Get-CimInstance Win32_VideoController | "
                            + "Select-Object -ExpandProperty Name");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        names.add(line.trim());
                    }
                }
            }
            process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.debug("Cartes graphiques indeterminables : " + e.getMessage());
        }
        return names;
    }

    /* ------------------------------------------------------------------ */
    /* Mise en forme                                                       */
    /* ------------------------------------------------------------------ */

    private String gigabytes(int megabytes) {
        return megabytes >= 1024
                ? String.format(Locale.ROOT, "%.1f Go", megabytes / 1024d)
                : megabytes + " Mo";
    }
}
