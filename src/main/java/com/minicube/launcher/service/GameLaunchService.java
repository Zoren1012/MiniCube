package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.model.GraphicsSettings;
import com.minicube.launcher.model.LauncherSettings;
import com.minicube.launcher.model.Progress;
import com.minicube.launcher.model.ServerEntry;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.OsUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Construction de la ligne de commande du jeu et supervision du processus.
 *
 * <p>Le service reproduit le comportement du launcher officiel : substitution des
 * variables {@code ${...}} des descripteurs de version, evaluation des regles
 * conditionnelles, gestion du format moderne ({@code arguments}) comme du format
 * historique ({@code minecraftArguments}).</p>
 */
public class GameLaunchService {

    /** Observateurs de la sortie du jeu, pour les mesures et les diagnostics. */
    private final java.util.List<Consumer<String>> outputListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Parametres d'un lancement.
     *
     * @param versionId identifiant de la version a lancer
     * @param account   compte a utiliser
     * @param server    serveur a rejoindre directement, ou null pour le menu principal
     */
    public record LaunchRequest(String versionId, Account account, ServerEntry server) {
    }

    private final LauncherPaths paths;
    private final ConfigService config;
    private final GameFileService fileService;
    private final JavaRuntimeService javaService;
    private final OptionsService optionsService;

    private Process currentProcess;

    public GameLaunchService(LauncherPaths paths, ConfigService config,
                             GameFileService fileService, JavaRuntimeService javaService,
                             OptionsService optionsService) {
        this.paths = paths;
        this.config = config;
        this.fileService = fileService;
        this.javaService = javaService;
        this.optionsService = optionsService;
    }

    /** Vrai si une partie est en cours. */
    /**
     * Ajoute un observateur de la sortie du jeu.
     *
     * <p>Utilise pour reperer le moment ou Minecraft atteint son menu principal. Les
     * observateurs sont appeles depuis le fil de lecture, jamais depuis l'interface.</p>
     */
    public void addOutputListener(Consumer<String> listener) {
        outputListeners.add(listener);
    }

    public boolean isRunning() {
        return currentProcess != null && currentProcess.isAlive();
    }

    /** Termine la partie en cours (bouton "Arreter le jeu"). */
    public void stopGame() {
        if (isRunning()) {
            Log.info("Arret du jeu demande par l'utilisateur");
            currentProcess.destroy();
        }
    }

    /**
     * Prepare puis demarre le jeu.
     *
     * <p>Doit etre appelee depuis un thread de fond : la methode telecharge les fichiers
     * manquants et bloque jusqu'au demarrage du processus.</p>
     *
     * @param request    parametres du lancement
     * @param onProgress rappel de progression
     * @param onExit     rappel invoque avec le code de sortie lorsque le jeu se ferme
     * @throws IOException si la preparation ou le demarrage echoue
     */
    public Process launch(LaunchRequest request, Consumer<Progress> onProgress, IntConsumer onExit)
            throws IOException {
        LauncherSettings settings = config.settings();

        onProgress.accept(Progress.indeterminate("Preparation de la version "
                + request.versionId() + "..."));
        GameFileService.ResolvedVersion version = fileService.resolve(request.versionId());

        fileService.ensureGameFiles(version, settings.isVerifyFilesBeforeLaunch(), onProgress);

        onProgress.accept(Progress.indeterminate("Application des reglages graphiques..."));
        optionsService.applyGraphicsSettings(settings.getGraphics(), settings.isShadersEnabled(),
                settings.getActiveShaderPack());

        onProgress.accept(Progress.indeterminate("Construction de la ligne de commande..."));
        Path javaExecutable = javaService.resolveJavaExecutable(settings, version.json());
        List<String> command = buildCommand(version, request, settings, javaExecutable);

        Log.info("Lancement de Minecraft " + request.versionId() + " avec "
                + javaExecutable);
        Log.debug("Commande : " + String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(paths.gameDir().toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        currentProcess = process;
        pipeOutput(process, onExit);

        onProgress.accept(Progress.done("Minecraft est en cours de demarrage."));
        return process;
    }

    /** Redirige la sortie du jeu vers le journal du launcher. */
    private void pipeOutput(Process process, IntConsumer onExit) {
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    Log.game(line);
                    for (Consumer<String> listener : outputListeners) {
                        listener.accept(line);
                    }
                }
            } catch (IOException e) {
                Log.debug("Fin de la lecture de la sortie du jeu : " + e.getMessage());
            }
            int exitCode = -1;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Log.info("Le jeu s'est termine avec le code " + exitCode);
            if (onExit != null) {
                onExit.accept(exitCode);
            }
        }, "minecraft-output");
        reader.setDaemon(true);
        reader.start();
    }

    /* ------------------------------------------------------------------ */
    /* Construction de la commande                                         */
    /* ------------------------------------------------------------------ */

    /** Assemble la commande complete : java, arguments JVM, classe principale, arguments jeu. */
    private List<String> buildCommand(GameFileService.ResolvedVersion version,
                                      LaunchRequest request, LauncherSettings settings,
                                      Path javaExecutable) {
        GraphicsSettings graphics = settings.getGraphics();
        Map<String, Boolean> features = new HashMap<>();
        features.put("is_demo_user", false);
        features.put("has_custom_resolution", graphics.isCustomResolution());
        boolean joinServer = request.server() != null;
        features.put("has_quick_plays_support", joinServer);
        features.put("is_quick_play_multiplayer", joinServer);
        features.put("is_quick_play_singleplayer", false);
        features.put("is_quick_play_realms", false);

        Map<String, String> variables = buildVariables(version, request, settings);

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());

        // Memoire et reglages JVM du launcher, appliques avant ceux du descripteur.
        command.add("-Xmx" + settings.getRamMb() + "M");
        command.add("-Xms" + Math.min(settings.getRamMb(), 512) + "M");
        command.addAll(defaultJvmArguments());

        if (OsUtil.isMac()) {
            command.add("-XstartOnFirstThread");
        }

        for (String extra : splitArguments(settings.getExtraJvmArgs())) {
            command.add(extra);
        }

        List<String> versionJvmArgs = collectArguments(version.json(), "jvm", features, variables);
        if (versionJvmArgs.isEmpty()) {
            // Format historique : le descripteur ne fournit pas de bloc jvm.
            versionJvmArgs = List.of(
                    "-Djava.library.path=" + version.nativesDir(),
                    "-cp", variables.get("classpath"));
        }
        command.addAll(versionJvmArgs);

        command.add(version.mainClass());

        command.addAll(collectGameArguments(version.json(), features, variables));

        if (graphics.isFullscreen()) {
            command.add("--fullscreen");
        } else if (graphics.isCustomResolution()) {
            command.add("--width");
            command.add(String.valueOf(graphics.getWindowWidth()));
            command.add("--height");
            command.add(String.valueOf(graphics.getWindowHeight()));
        }

        if (joinServer) {
            command.addAll(serverArguments(version.json(), request.server()));
        }
        return command;
    }

    /**
     * Arguments de connexion directe a un serveur.
     *
     * <p>Depuis la 1.20, {@code --server}/{@code --port} ont ete remplaces par
     * {@code --quickPlayMultiplayer}. Le format supporte est deduit du descripteur.</p>
     */
    private List<String> serverArguments(JsonObject versionJson, ServerEntry server) {
        if (supportsQuickPlay(versionJson)) {
            return List.of("--quickPlayMultiplayer",
                    server.getAddress() + ":" + server.getPort());
        }
        return List.of("--server", server.getAddress(), "--port", String.valueOf(server.getPort()));
    }

    /** Detecte la prise en charge de quickPlay en inspectant les regles du descripteur. */
    private boolean supportsQuickPlay(JsonObject versionJson) {
        JsonObject arguments = Json.object(versionJson, "arguments");
        if (arguments == null) {
            return false;
        }
        return Json.array(arguments, "game").toString().contains("quick_play_multiplayer");
    }

    /** Arguments JVM par defaut : collecteur G1 et reglages evitant les micro-freezes. */
    private List<String> defaultJvmArguments() {
        List<String> args = new ArrayList<>();
        args.add("-XX:+UnlockExperimentalVMOptions");
        args.add("-XX:+UseG1GC");
        args.add("-XX:G1NewSizePercent=20");
        args.add("-XX:G1ReservePercent=20");
        args.add("-XX:MaxGCPauseMillis=50");
        args.add("-XX:G1HeapRegionSize=32M");
        args.add("-Dfile.encoding=UTF-8");
        args.add("-Djava.net.preferIPv4Stack=true");
        return args;
    }

    /* ------------------------------------------------------------------ */
    /* Variables de substitution                                           */
    /* ------------------------------------------------------------------ */

    /** Table des variables ${...} attendues par les descripteurs de version. */
    private Map<String, String> buildVariables(GameFileService.ResolvedVersion version,
                                               LaunchRequest request,
                                               LauncherSettings settings) {
        Account account = request.account();
        GraphicsSettings graphics = settings.getGraphics();
        String assetIndexId = version.assetIndexId();

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("auth_player_name", account.getUsername());
        variables.put("version_name", version.id());
        variables.put("game_directory", paths.gameDir().toString());
        variables.put("assets_root", paths.assetsDir().toString());
        variables.put("assets_index_name", assetIndexId);
        variables.put("auth_uuid", account.getUuid());
        variables.put("auth_access_token", account.getAccessToken());
        variables.put("auth_session", "token:" + account.getAccessToken() + ":"
                + account.getUuid());
        variables.put("clientid", "");
        variables.put("auth_xuid", "");
        variables.put("user_type", account.isOffline() ? "legacy" : "msa");
        variables.put("version_type", Json.string(version.json(), "type", "release"));
        variables.put("user_properties", "{}");
        variables.put("natives_directory", version.nativesDir().toString());
        variables.put("launcher_name", Constants.APP_NAME.replace(" ", ""));
        variables.put("launcher_version", Constants.APP_VERSION);
        variables.put("classpath", buildClasspath(version));
        variables.put("classpath_separator", OsUtil.classpathSeparator());
        variables.put("library_directory", paths.librariesDir().toString());
        variables.put("game_assets", paths.assetsVirtualDir(assetIndexId).toString());
        variables.put("resolution_width", String.valueOf(graphics.getWindowWidth()));
        variables.put("resolution_height", String.valueOf(graphics.getWindowHeight()));
        if (request.server() != null) {
            variables.put("quickPlayMultiplayer",
                    request.server().getAddress() + ":" + request.server().getPort());
        }
        return variables;
    }

    /** Classpath du jeu : toutes les bibliotheques retenues, puis le jar client. */
    private String buildClasspath(GameFileService.ResolvedVersion version) {
        List<String> entries = new ArrayList<>();
        for (GameFileService.LibraryFile library : version.libraries()) {
            if (Files.isRegularFile(library.target())) {
                entries.add(library.target().toString());
            }
        }
        if (Files.isRegularFile(version.clientJar())) {
            entries.add(version.clientJar().toString());
        }
        return String.join(OsUtil.classpathSeparator(), entries);
    }

    /**
     * Extrait les arguments d'une section du descripteur en appliquant les regles et la
     * substitution des variables.
     *
     * @param section game ou jvm
     */
    private List<String> collectArguments(JsonObject versionJson, String section,
                                          Map<String, Boolean> features,
                                          Map<String, String> variables) {
        List<String> result = new ArrayList<>();
        JsonObject arguments = Json.object(versionJson, "arguments");
        if (arguments == null) {
            return result;
        }
        for (JsonElement element : Json.array(arguments, section)) {
            if (element.isJsonPrimitive()) {
                result.add(substitute(element.getAsString(), variables));
                continue;
            }
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject conditional = element.getAsJsonObject();
            if (!GameFileService.rulesAllow(Json.array(conditional, "rules"), features)) {
                continue;
            }
            JsonElement value = conditional.get("value");
            if (value == null) {
                continue;
            }
            if (value.isJsonArray()) {
                for (JsonElement item : value.getAsJsonArray()) {
                    result.add(substitute(item.getAsString(), variables));
                }
            } else {
                result.add(substitute(value.getAsString(), variables));
            }
        }
        return result;
    }

    /**
     * Arguments du jeu, en gerant les deux formats de descripteur :
     * {@code arguments.game} (1.13 et suivantes) ou {@code minecraftArguments} (anterieures).
     */
    private List<String> collectGameArguments(JsonObject versionJson,
                                              Map<String, Boolean> features,
                                              Map<String, String> variables) {
        List<String> modern = collectArguments(versionJson, "game", features, variables);
        if (!modern.isEmpty()) {
            return modern;
        }
        String legacy = Json.string(versionJson, "minecraftArguments", "");
        if (legacy.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : legacy.split(" ")) {
            if (!token.isBlank()) {
                result.add(substitute(token, variables));
            }
        }
        return result;
    }

    /** Remplace toutes les occurrences de ${cle} par leur valeur. */
    private String substitute(String input, Map<String, String> variables) {
        if (input == null || !input.contains("${")) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Decoupe une chaine d'arguments en respectant les valeurs entre guillemets.
     *
     * <p>Exemple : {@code -Xss2m -Dchemin="C:/Program Files/jeu"} produit deux arguments,
     * le second conservant son espace. Le decoupage est fait a la main plutot qu'avec une
     * expression reguliere pour rester lisible et sans echappement.</p>
     *
     * @param raw chaine saisie dans les parametres, eventuellement vide
     * @return la liste des arguments, guillemets retires
     */
    static List<String> splitArguments(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < raw.length(); i++) {
            char character = raw.charAt(i);
            if (character == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && Character.isWhitespace(character)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}
