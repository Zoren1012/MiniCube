package com.minicube.launcher.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Journalisation du launcher : console, fichier logs/launcher.log et tampon memoire
 * consultable depuis l'onglet "Journal".
 *
 * <p>Toutes les methodes sont sures vis-a-vis des threads : le service de telechargement
 * ecrit depuis un pool alors que l'interface lit depuis le thread JavaFX.</p>
 */
public final class Log {

    /** Niveaux de gravite, du plus bavard au plus critique. */
    public enum Level { DEBUG, INFO, WARN, ERROR, GAME }

    /** Une ligne de journal horodatee. */
    public record Entry(LocalDateTime time, Level level, String message) {

        /** Rendu texte utilise dans le fichier et dans l'onglet Journal. */
        public String format() {
            return "[" + TIME_FORMAT.format(time) + "] [" + level + "] " + message;
        }
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int MAX_BUFFERED_LINES = 5000;
    /** Nombre de lignes ecrites avant un vidage automatique du tampon fichier. */
    private static final int FLUSH_EVERY = 40;
    /** Delai maximal avant qu une ligne en tampon ne rejoigne le fichier. */
    private static final long FLUSH_DELAY_MS = 700;

    private static final Deque<Entry> BUFFER = new ArrayDeque<>();
    private static final List<Consumer<Entry>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object FILE_LOCK = new Object();

    private static Path logFile;
    private static java.io.BufferedWriter writer;
    /** Lignes en attente avant vidage du tampon. */
    private static int pendingLines;
    private static long lastFlush;
    private static Thread flusher;
    private static boolean debugEnabled = false;

    private Log() {
    }

    /**
     * Prepare le fichier de log de la session et purge les archives trop anciennes.
     *
     * @param logsDir repertoire ou stocker les journaux
     */
    public static void init(Path logsDir) {
        synchronized (FILE_LOCK) {
            try {
                Files.createDirectories(logsDir);
                logFile = logsDir.resolve("launcher.log");
                if (Files.exists(logFile) && Files.size(logFile) > 0) {
                    Path archive = logsDir.resolve(
                            "launcher-" + FILE_FORMAT.format(LocalDateTime.now()) + ".log");
                    Files.move(logFile, archive);
                }
                // Ouverture en creation avec troncature : ni createFile, qui echoue si
                // le fichier existe deja, ni APPEND, qui melangerait deux sessions. Un
                // journal vide laisse par une session precedente est simplement reutilise.
                writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                lastFlush = System.currentTimeMillis();
                startFlusher();
                pruneOldLogs(logsDir);
            } catch (IOException e) {
                logFile = null;
                writer = null;
                System.err.println("[Log] Journalisation fichier desactivee : " + e.getMessage());
            }
        }
    }

    /** Conserve au maximum les 10 archives les plus recentes. */
    private static void pruneOldLogs(Path logsDir) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(logsDir)) {
            List<Path> archives = stream
                    .filter(p -> p.getFileName().toString().startsWith("launcher-"))
                    .sorted()
                    .toList();
            for (int i = 0; i < archives.size() - 10; i++) {
                Files.deleteIfExists(archives.get(i));
            }
        }
    }

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static void debug(String message) {
        if (debugEnabled) {
            log(Level.DEBUG, message);
        }
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    /** Ligne provenant de la sortie standard du jeu. */
    public static void game(String message) {
        log(Level.GAME, message);
    }

    public static void error(String message, Throwable throwable) {
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        log(Level.ERROR, message + System.lineSeparator() + trace);
    }

    private static void log(Level level, String message) {
        // Le masquage est central : aucun appelant ne peut oublier de le faire, et une
        // ligne du jeu contenant un jeton est assainie au meme titre que les notres.
        Entry entry = new Entry(LocalDateTime.now(), level, Safety.redact(message));
        String line = entry.format();

        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }

        synchronized (BUFFER) {
            BUFFER.addLast(entry);
            while (BUFFER.size() > MAX_BUFFERED_LINES) {
                BUFFER.removeFirst();
            }
        }
        writeToFile(line, level);

        for (Consumer<Entry> listener : LISTENERS) {
            try {
                listener.accept(entry);
            } catch (Exception ignored) {
                // Un afficheur defaillant ne doit jamais casser la journalisation.
            }
        }
    }

    /**
     * Ecrit une ligne dans le fichier de journal.
     *
     * <p>Le flux reste ouvert entre deux lignes. La version precedente rouvrait le
     * fichier a chaque appel, ce qui etait sans consequence pour les messages du
     * launcher mais couteux pour la sortie du jeu : Minecraft produit plusieurs milliers
     * de lignes au demarrage, soit autant d'ouvertures et de fermetures.</p>
     *
     * <p>Le tampon est vide immediatement pour un avertissement ou une erreur, afin
     * qu'un plantage ne laisse jamais le diagnostic dans un tampon perdu.</p>
     */
    private static void writeToFile(String line, Level level) {
        synchronized (FILE_LOCK) {
            if (writer == null) {
                return;
            }
            try {
                writer.write(line);
                writer.newLine();
                pendingLines++;
                long now = System.currentTimeMillis();
                // Trois raisons de vider le tampon. La condition de temps est la plus
                // importante : sans elle, les quelques lignes du demarrage resteraient
                // invisibles dans le fichier, et seraient perdues si le launcher etait
                // tue avant d'en avoir ecrit quarante.
                boolean urgent = level == Level.WARN || level == Level.ERROR;
                if (urgent || pendingLines >= FLUSH_EVERY || now - lastFlush >= FLUSH_DELAY_MS) {
                    writer.flush();
                    pendingLines = 0;
                    lastFlush = now;
                }
            } catch (IOException ignored) {
                // Disque plein ou fichier verrouille : on continue sans journal fichier.
            }
        }
    }

    /**
     * Vide periodiquement le tampon.
     *
     * <p>Un vidage declenche par l ecriture ne suffit pas : apres les quelques lignes
     * du demarrage, plus rien n est journalise pendant que l utilisateur regarde
     * l interface, et ces lignes resteraient invisibles dans le fichier. Un fil demon
     * les libere au bout d une seconde.</p>
     */
    private static void startFlusher() {
        if (flusher != null) {
            return;
        }
        flusher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(FLUSH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (FILE_LOCK) {
                    if (writer == null) {
                        return;
                    }
                    if (pendingLines > 0) {
                        try {
                            writer.flush();
                            pendingLines = 0;
                            lastFlush = System.currentTimeMillis();
                        } catch (IOException ignored) {
                            return;
                        }
                    }
                }
            }
        }, "minicube-log-flusher");
        flusher.setDaemon(true);
        flusher.start();
    }

    /**
     * Ferme proprement le journal.
     * Appelee a l arret du launcher, elle garantit que rien ne reste en tampon.
     */
    public static void close() {
        synchronized (FILE_LOCK) {
            if (writer == null) {
                return;
            }
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
                // Rien a faire de plus a ce stade.
            }
            writer = null;
        }
    }

    /** Copie immuable du tampon memoire, du plus ancien au plus recent. */
    public static List<Entry> snapshot() {
        synchronized (BUFFER) {
            return new ArrayList<>(BUFFER);
        }
    }

    public static void clearBuffer() {
        synchronized (BUFFER) {
            BUFFER.clear();
        }
    }

    /** Abonne un afficheur (onglet Journal) aux nouvelles lignes. */
    public static void addListener(Consumer<Entry> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<Entry> listener) {
        LISTENERS.remove(listener);
    }

    /** Chemin du fichier de log courant, ou null si la journalisation fichier est desactivee. */
    public static Path logFile() {
        return logFile;
    }
}
