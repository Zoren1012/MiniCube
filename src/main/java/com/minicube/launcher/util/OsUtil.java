package com.minicube.launcher.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Detection du systeme d'exploitation, de l'architecture et des chemins standards.
 */
public final class OsUtil {

    /** Familles de systemes reconnues par les regles des fichiers de version Mojang. */
    public enum Os {
        WINDOWS("windows"), OSX("osx"), LINUX("linux");

        private final String mojangName;

        Os(String mojangName) {
            this.mojangName = mojangName;
        }

        /** Nom utilise par Mojang dans les blocs {@code rules} des JSON de version. */
        public String mojangName() {
            return mojangName;
        }
    }

    private static final Os CURRENT = detect();

    private OsUtil() {
    }

    private static Os detect() {
        String name = System.getProperty("os.name", "").toLowerCase();
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.OSX;
        }
        return Os.LINUX;
    }

    public static Os current() {
        return CURRENT;
    }

    public static boolean isWindows() {
        return CURRENT == Os.WINDOWS;
    }

    public static boolean isMac() {
        return CURRENT == Os.OSX;
    }

    public static boolean isLinux() {
        return CURRENT == Os.LINUX;
    }

    /** {@code x86}, {@code x86_64} ou {@code arm64} (format attendu par les regles Mojang). */
    public static String arch() {
        String arch = System.getProperty("os.arch", "amd64").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("64")) {
            return "x86_64";
        }
        return "x86";
    }

    /** {@code 32} ou {@code 64} : valeur substituee dans {@code ${arch}} des classifiers natifs. */
    public static String archBits() {
        return System.getProperty("os.arch", "").contains("64") ? "64" : "32";
    }

    public static String osVersion() {
        return System.getProperty("os.version", "");
    }

    /** Separateur de classpath : {@code ;} sous Windows, {@code :} ailleurs. */
    public static String classpathSeparator() {
        return File.pathSeparator;
    }

    /**
     * Emplacement standard du dossier {@code .minecraft} pour le systeme courant.
     *
     * @return le chemin attendu, meme s'il n'existe pas encore
     */
    public static Path defaultMinecraftDir() {
        String home = System.getProperty("user.home", ".");
        switch (CURRENT) {
            case WINDOWS: {
                String appData = System.getenv("APPDATA");
                Path base = (appData != null && !appData.isBlank())
                        ? Paths.get(appData)
                        : Paths.get(home, "AppData", "Roaming");
                return base.resolve(".minecraft");
            }
            case OSX:
                return Paths.get(home, "Library", "Application Support", "minecraft");
            default:
                return Paths.get(home, ".minecraft");
        }
    }

    /** Repertoire de travail du launcher (configuration, logs, caches). */
    public static Path launcherDir() {
        return Paths.get(System.getProperty("user.home", "."),
                com.minicube.launcher.core.Constants.APP_DIR_NAME);
    }

    /** Memoire physique totale de la machine, en Mo (0 si indisponible). */
    public static int totalSystemRamMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                return (int) (sunBean.getTotalMemorySize() / (1024L * 1024L));
            }
        } catch (Throwable ignored) {
            // API non disponible sur cette JVM : on retombe sur la valeur par defaut.
        }
        return 0;
    }

    /** Nom de l'executable Java (avec {@code .exe} sous Windows). */
    public static String javaExecutableName() {
        return isWindows() ? "javaw.exe" : "java";
    }

    /** Executable java de la JVM qui execute le launcher. */
    public static Path currentJavaExecutable() {
        Path bin = Paths.get(System.getProperty("java.home")).resolve("bin");
        Path javaw = bin.resolve(javaExecutableName());
        if (Files.isRegularFile(javaw)) {
            return javaw;
        }
        return bin.resolve(isWindows() ? "java.exe" : "java");
    }

    /** Ouvre un dossier dans l'explorateur de fichiers du systeme. */
    public static void openFolder(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            String cmd;
            if (isWindows()) {
                cmd = "explorer";
            } else if (isMac()) {
                cmd = "open";
            } else {
                cmd = "xdg-open";
            }
            new ProcessBuilder(cmd, path.toAbsolutePath().toString()).start();
        } catch (Exception e) {
            Log.warn("Impossible d'ouvrir le dossier " + path + " : " + e.getMessage());
        }
    }

    /** Ouvre une URL dans le navigateur par defaut. */
    public static void openUrl(String url) {
        try {
            if (isWindows()) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (isMac()) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            Log.warn("Impossible d'ouvrir l'URL " + url + " : " + e.getMessage());
        }
    }
}
