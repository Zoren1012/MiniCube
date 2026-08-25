package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Detection des runtimes Java installes et choix de l'executable adapte a une version
 * du jeu.
 *
 * <p>Minecraft impose une version de Java minimale qui figure dans le descripteur de
 * version ({@code javaVersion.majorVersion}) : 8 jusqu'a la 1.16, 17 a partir de la 1.18,
 * 21 a partir de la 1.20.5. Le service choisit automatiquement un runtime compatible et
 * previent l'utilisateur si aucun ne convient.</p>
 */
public class JavaRuntimeService {

    /**
     * Runtime Java detecte sur la machine.
     *
     * @param executable chemin de l'executable java
     * @param version    chaine de version complete, par exemple 21.0.4
     * @param major      version majeure, par exemple 21
     * @param vendor     editeur si connu
     */
    public record JavaInstallation(Path executable, String version, int major, String vendor) {

        /** Libelle affiche dans la liste deroulante des parametres. */
        public String displayName() {
            String label = "Java " + major + " (" + version + ")";
            return vendor.isBlank() ? label : label + " - " + vendor;
        }

        /** Affichage dans les listes deroulantes de l ecran Parametres. */
        @Override
        public String toString() {
            return displayName();
        }
    }

    private final LauncherPaths paths;
    private List<JavaInstallation> cache;

    public JavaRuntimeService(LauncherPaths paths) {
        this.paths = paths;
    }

    /**
     * Liste les runtimes Java disponibles.
     *
     * <p>Operation potentiellement lente (un processus est lance par candidat) :
     * a appeler depuis un thread de fond. Le resultat est mis en cache.</p>
     */
    public List<JavaInstallation> detectInstallations(boolean forceRefresh) {
        if (cache != null && !forceRefresh) {
            return cache;
        }
        Set<Path> candidates = new LinkedHashSet<>();

        candidates.add(OsUtil.currentJavaExecutable());

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            addIfExecutable(candidates, Path.of(javaHome));
        }
        collectMojangRuntimes(candidates);
        collectSystemRuntimes(candidates);

        List<JavaInstallation> found = new ArrayList<>();
        for (Path executable : candidates) {
            JavaInstallation installation = probe(executable);
            if (installation != null) {
                found.add(installation);
            }
        }
        found.sort((a, b) -> Integer.compare(b.major(), a.major()));
        cache = found;
        Log.info(found.size() + " runtime(s) Java detecte(s)");
        return found;
    }

    /** Runtimes telecharges par le launcher officiel dans {@code .minecraft/runtime}. */
    private void collectMojangRuntimes(Set<Path> candidates) {
        Path runtimeRoot = paths.gameDir().resolve("runtime");
        if (!Files.isDirectory(runtimeRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(runtimeRoot, 5)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals("bin"))
                    .forEach(bin -> addExecutable(candidates, bin));
        } catch (IOException e) {
            Log.debug("Exploration des runtimes Mojang impossible : " + e.getMessage());
        }
    }

    /** Emplacements d'installation habituels selon le systeme. */
    private void collectSystemRuntimes(Set<Path> candidates) {
        List<Path> roots = new ArrayList<>();
        String home = System.getProperty("user.home", ".");

        if (OsUtil.isWindows()) {
            roots.add(Path.of("C:", "Program Files", "Java"));
            roots.add(Path.of("C:", "Program Files", "Eclipse Adoptium"));
            roots.add(Path.of("C:", "Program Files", "Amazon Corretto"));
            roots.add(Path.of("C:", "Program Files", "Microsoft"));
            roots.add(Path.of("C:", "Program Files", "Zulu"));
            roots.add(Path.of("C:", "Program Files", "BellSoft"));
            roots.add(Path.of("C:", "Program Files (x86)", "Java"));
            roots.add(Path.of(home, ".jdks"));
        } else if (OsUtil.isMac()) {
            roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
            roots.add(Path.of(home, "Library", "Java", "JavaVirtualMachines"));
        } else {
            roots.add(Path.of("/usr/lib/jvm"));
            roots.add(Path.of("/usr/java"));
            roots.add(Path.of(home, ".sdkman", "candidates", "java"));
        }

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(root)) {
                stream.filter(Files::isDirectory).forEach(dir -> {
                    addIfExecutable(candidates, dir);
                    // Sous macOS, le runtime est imbrique dans Contents/Home.
                    addIfExecutable(candidates, dir.resolve("Contents").resolve("Home"));
                });
            } catch (IOException e) {
                Log.debug("Lecture de " + root + " impossible : " + e.getMessage());
            }
        }
    }

    private void addIfExecutable(Set<Path> candidates, Path javaHome) {
        addExecutable(candidates, javaHome.resolve("bin"));
    }

    private void addExecutable(Set<Path> candidates, Path binDir) {
        Path executable = binDir.resolve(OsUtil.isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(executable)) {
            candidates.add(executable);
        }
    }

    /**
     * Interroge un executable java pour connaitre sa version.
     *
     * @return les informations du runtime, ou null si l'executable ne repond pas
     */
    /**
     * Lit la version d'un runtime sans lancer de processus.
     *
     * <p>Chaque distribution Java depose un fichier {@code release} a sa racine, qui
     * contient une ligne {@code JAVA_VERSION="21.0.4"}. Le lire coute une ouverture de
     * fichier, la ou interroger l'executable coute la creation d'un processus complet :
     * sur une machine equipee de plusieurs JDK, la difference se voit a l'oeil nu dans
     * l'onglet Parametres.</p>
     *
     * @return les informations du runtime, ou null si le fichier est absent ou muet
     */
    private JavaInstallation readFromReleaseFile(Path executable) {
        Path javaHome = executable.getParent() == null ? null : executable.getParent().getParent();
        if (javaHome == null) {
            return null;
        }
        Path releaseFile = javaHome.resolve("release");
        if (!Files.isRegularFile(releaseFile)) {
            return null;
        }
        try {
            String version = "";
            String vendor = "";
            for (String line : Files.readAllLines(releaseFile, StandardCharsets.ISO_8859_1)) {
                if (line.startsWith("JAVA_VERSION=")) {
                    version = unquote(line.substring("JAVA_VERSION=".length()));
                } else if (line.startsWith("IMPLEMENTOR=")) {
                    vendor = unquote(line.substring("IMPLEMENTOR=".length()));
                }
            }
            if (version.isEmpty()) {
                return null;
            }
            return new JavaInstallation(executable, version, majorVersion(version), vendor);
        } catch (IOException e) {
            Log.debug("Fichier release illisible pour " + javaHome + " : " + e.getMessage());
            return null;
        }
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Interroge un runtime Java pour connaitre sa version.
     *
     * <p>Le fichier {@code release} est consulte en premier ; l'executable n'est lance
     * que s'il est absent, ce qui n'arrive que sur des installations tres anciennes ou
     * incompletes.</p>
     *
     * @return les informations du runtime, ou null s'il ne repond pas
     */
    public JavaInstallation probe(Path executable) {
        if (executable == null || !Files.isRegularFile(executable)) {
            return null;
        }
        JavaInstallation fromFile = readFromReleaseFile(executable);
        if (fromFile != null) {
            return fromFile;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(executable.toString(), "-version");
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String version = "";
            String vendor = "";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (version.isEmpty()) {
                        int first = line.indexOf('"');
                        int last = line.lastIndexOf('"');
                        if (first >= 0 && last > first) {
                            version = line.substring(first + 1, last);
                            vendor = detectVendor(line);
                        }
                    }
                }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            if (version.isEmpty()) {
                return null;
            }
            return new JavaInstallation(executable, version, majorVersion(version), vendor);
        } catch (IOException e) {
            Log.debug("Runtime illisible " + executable + " : " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private String detectVendor(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("openjdk")) {
            return "OpenJDK";
        }
        if (lower.contains("java(tm)") || lower.contains("hotspot")) {
            return "Oracle";
        }
        return "";
    }

    /** Extrait la version majeure : "1.8.0_412" donne 8, "21.0.4" donne 21. */
    public static int majorVersion(String version) {
        try {
            String cleaned = version.trim();
            if (cleaned.startsWith("1.")) {
                return Integer.parseInt(cleaned.substring(2, 3));
            }
            int dot = cleaned.indexOf('.');
            String head = dot > 0 ? cleaned.substring(0, dot) : cleaned;
            int dash = head.indexOf('-');
            if (dash > 0) {
                head = head.substring(0, dash);
            }
            return Integer.parseInt(head);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Version majeure de Java exigee par le descripteur (8 par defaut). */
    public int requiredMajorVersion(JsonObject versionJson) {
        JsonObject javaVersion = Json.object(versionJson, "javaVersion");
        return javaVersion == null ? 8 : Json.integer(javaVersion, "majorVersion", 8);
    }

    /**
     * Choisit l'executable Java a utiliser pour lancer une version.
     *
     * <p>Ordre de priorite : le chemin force dans les parametres, puis un runtime detecte
     * dont la version majeure correspond a celle exigee, puis le premier runtime
     * suffisamment recent, et enfin la JVM du launcher.</p>
     *
     * @param settings    parametres du launcher
     * @param versionJson descripteur de la version a lancer
     */
    public Path resolveJavaExecutable(LauncherSettings settings, JsonObject versionJson) {
        String forced = settings.getJavaPath();
        if (!forced.isBlank()) {
            Path path = Path.of(forced);
            if (Files.isRegularFile(path)) {
                return path;
            }
            Log.warn("Le chemin Java configure est introuvable : " + forced
                    + ". Detection automatique utilisee.");
        }
        int required = requiredMajorVersion(versionJson);
        List<JavaInstallation> installations = detectInstallations(false);

        for (JavaInstallation installation : installations) {
            if (installation.major() == required) {
                return installation.executable();
            }
        }
        for (JavaInstallation installation : installations) {
            if (installation.major() >= required) {
                Log.info("Aucun Java " + required + " exact, utilisation de Java "
                        + installation.major());
                return installation.executable();
            }
        }
        Log.warn("Aucun runtime Java " + required + " ou superieur n'a ete trouve. "
                + "Le jeu risque de ne pas demarrer.");
        return OsUtil.currentJavaExecutable();
    }

    /**
     * Verifie qu'un runtime compatible existe pour une version donnee.
     *
     * @return null si tout va bien, sinon un message d'avertissement a afficher
     */
    public String checkCompatibility(JsonObject versionJson) {
        int required = requiredMajorVersion(versionJson);
        boolean compatible = detectInstallations(false).stream()
                .anyMatch(installation -> installation.major() >= required);
        if (compatible) {
            return null;
        }
        return "Cette version necessite Java " + required
                + ", introuvable sur cette machine. Installez-le puis relancez la detection.";
    }
}
