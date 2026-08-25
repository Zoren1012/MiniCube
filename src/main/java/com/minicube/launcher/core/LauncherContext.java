package com.minicube.launcher.core;

import com.minicube.launcher.service.AccountService;
import com.minicube.launcher.service.CloudSyncService;
import com.minicube.launcher.service.ConfigService;
import com.minicube.launcher.service.GameFileService;
import com.minicube.launcher.service.GameLaunchService;
import com.minicube.launcher.service.JavaRuntimeService;
import com.minicube.launcher.service.MicrosoftAuthService;
import com.minicube.launcher.service.MinecraftInstallService;
import com.minicube.launcher.service.ModService;
import com.minicube.launcher.service.NeuralVoiceService;
import com.minicube.launcher.service.NewsService;
import com.minicube.launcher.service.NotificationService;
import com.minicube.launcher.service.ProfileService;
import com.minicube.launcher.service.ProfileWebServer;
import com.minicube.launcher.service.OptionsService;
import com.minicube.launcher.service.ServerListService;
import com.minicube.launcher.service.ServerPingService;
import com.minicube.launcher.service.ShaderService;
import com.minicube.launcher.service.SkinService;
import com.minicube.launcher.service.UpdateService;
import com.minicube.launcher.service.VoiceService;
import com.minicube.launcher.util.Log;

import java.nio.file.Path;

/**
 * Point d'assemblage de l'application : cree les services une seule fois et les met a
 * disposition des controleurs.
 *
 * <p>Deux familles de services cohabitent :</p>
 * <ul>
 *   <li>les services <b>globaux</b> (configuration, comptes, actualites, mises a jour),
 *       crees une fois pour toutes ;</li>
 *   <li>les services <b>lies au dossier de jeu</b> (fichiers, mods, shaders, lancement),
 *       recrees par {@link #rebindGameDirectory()} lorsque l'utilisateur change
 *       d'installation.</li>
 * </ul>
 */
public class LauncherContext {

    private final ConfigService config;
    private final NotificationService notifications;
    private final MicrosoftAuthService auth;
    private final AccountService accounts;
    private final MinecraftInstallService install;
    private final ServerPingService serverPing;
    private final ServerListService serverList;
    private final NewsService news;
    private final UpdateService updates;
    private final CloudSyncService cloudSync;
    private final SkinService skins;
    private final ProfileService profiles;
    private final ProfileWebServer profileServer;
    private final VoiceService voice;
    private final NeuralVoiceService neuralVoice;

    private LauncherPaths paths;
    private JavaRuntimeService javaRuntime;
    private OptionsService options;
    private GameFileService gameFiles;
    private ShaderService shaders;
    private ModService mods;
    private GameLaunchService gameLauncher;

    public LauncherContext() {
        this.config = new ConfigService();
        this.notifications = new NotificationService(config);
        this.auth = new MicrosoftAuthService(config);
        this.accounts = new AccountService(config, auth);
        this.install = new MinecraftInstallService();
        this.serverPing = new ServerPingService();
        this.serverList = new ServerListService(config);
        this.news = new NewsService(config);
        this.updates = new UpdateService(config);
        this.cloudSync = new CloudSyncService(config);
        this.skins = new SkinService(auth);
        this.profiles = new ProfileService();
        this.profileServer = new ProfileWebServer(profiles);
        this.neuralVoice = new NeuralVoiceService();
        this.voice = new VoiceService(config.settings(), LauncherPaths.cacheDir(), neuralVoice);

        rebindGameDirectory();
    }

    /**
     * Reconstruit les services dependants du dossier de jeu.
     * A appeler apres tout changement de {@code gameDirectory}.
     */
    public final void rebindGameDirectory() {
        String directory = config.settings().getGameDirectory();
        Path gameDir = directory.isBlank()
                ? com.minicube.launcher.util.OsUtil.defaultMinecraftDir()
                : Path.of(directory);

        this.paths = new LauncherPaths(gameDir);
        this.javaRuntime = new JavaRuntimeService(paths);
        this.options = new OptionsService(paths);
        this.gameFiles = new GameFileService(paths);
        this.shaders = new ShaderService(paths, config, options);
        this.mods = new ModService(paths, config);
        this.gameLauncher = new GameLaunchService(paths, config, gameFiles, javaRuntime, options);

        Log.info("Dossier de jeu actif : " + gameDir);
    }

    /** Cree les dossiers necessaires au launcher et au jeu. */
    public void prepareDirectories() {
        paths.ensureDirectories();
    }

    /* --- Accesseurs ---------------------------------------------------- */

    public ConfigService config() {
        return config;
    }

    public NotificationService notifications() {
        return notifications;
    }

    public MicrosoftAuthService auth() {
        return auth;
    }

    public AccountService accounts() {
        return accounts;
    }

    public MinecraftInstallService install() {
        return install;
    }

    public ServerPingService serverPing() {
        return serverPing;
    }

    public ServerListService serverList() {
        return serverList;
    }

    public NewsService news() {
        return news;
    }

    public UpdateService updates() {
        return updates;
    }

    public CloudSyncService cloudSync() {
        return cloudSync;
    }

    public SkinService skins() {
        return skins;
    }

    public ProfileService profiles() {
        return profiles;
    }

    /** Serveur local servant la page de compte. */
    public ProfileWebServer profileServer() {
        return profileServer;
    }

    /** Accueil vocal du demarrage. */
    public VoiceService voice() {
        return voice;
    }

    /** Moteur de synthese neuronale local. */
    public NeuralVoiceService neuralVoice() {
        return neuralVoice;
    }

    /**
     * Nom sous lequel saluer le joueur.
     *
     * <p>Le pseudo du compte MiniCube prime : c est celui que le joueur s est choisi.
     * A defaut, celui du compte de jeu actif ; sans aucun des deux, la chaine est vide
     * et l accueil reste generique.</p>
     */
    public String playerDisplayName() {
        if (profiles.profile().exists()) {
            return profiles.profile().getUsername();
        }
        return accounts.active().map(com.minicube.launcher.model.Account::getUsername)
                .orElse("");
    }

    public LauncherPaths paths() {
        return paths;
    }

    public JavaRuntimeService javaRuntime() {
        return javaRuntime;
    }

    public OptionsService options() {
        return options;
    }

    public GameFileService gameFiles() {
        return gameFiles;
    }

    public ShaderService shaders() {
        return shaders;
    }

    public ModService mods() {
        return mods;
    }

    public GameLaunchService gameLauncher() {
        return gameLauncher;
    }
}
