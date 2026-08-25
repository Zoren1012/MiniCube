package com.minicube.launcher.model;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.util.OsUtil;

/**
 * Configuration complete du launcher, serialisee dans
 * {@code ~/.minicube/config.json}.
 *
 * <p>Un exemple commente est fourni dans {@code config.example.json} a la racine du
 * projet. Tout champ absent du fichier reprend la valeur par defaut declaree ici, ce qui
 * rend la configuration compatible entre versions du launcher.</p>
 */
public class LauncherSettings {

    /* --- Installation ------------------------------------------------- */

    /** Chemin absolu du dossier .minecraft choisi au premier demarrage. */
    private String gameDirectory = "";
    /** Passe a vrai une fois l'assistant de premier demarrage termine. */
    private boolean firstRunCompleted = false;
    /** Identifiant de la derniere version lancee (repris au prochain demarrage). */
    private String lastVersionId = "";
    /** UUID du compte actif. */
    private String activeAccountUuid = "";

    /* --- Java et memoire ---------------------------------------------- */

    private int ramMb = Constants.DEFAULT_RAM_MB;
    /** Executable Java a utiliser ; vide = detection automatique. */
    private String javaPath = "";
    /** Arguments JVM supplementaires, separes par des espaces. */
    private String extraJvmArgs = "";

    /* --- Interface ----------------------------------------------------- */

    /** dark ou light. */
    private String theme = "dark";
    /** Code de langue ISO 639-1 : fr, en. */
    private String language = "";
    /** Image de fond personnalisee ; vide = degrade par defaut. */
    private String backgroundImage = "";
    /** Garde la fenetre du launcher ouverte pendant la partie. */
    private boolean keepLauncherOpen = true;
    private boolean notificationsEnabled = true;

    /* --- Comportement -------------------------------------------------- */

    /** Journalisation detaillee (niveau DEBUG). */
    private boolean debugMode = false;
    private boolean autoUpdateLauncher = true;
    /** Verifie l'empreinte de chaque fichier avant de lancer le jeu. */
    private boolean verifyFilesBeforeLaunch = true;
    /** Installe automatiquement les mods declares obligatoires par le manifeste. */
    private boolean autoInstallRequiredMods = true;
    /** Prononce un mot de bienvenue au demarrage, avec le pseudo du joueur. */
    private boolean voiceGreetingEnabled = true;

    /* --- Shaders ------------------------------------------------------- */

    private boolean shadersEnabled = false;
    /** Nom de fichier du pack de shaders actif. */
    private String activeShaderPack = "";

    /* --- Endpoints du projet ------------------------------------------- */

    private String msClientId = Constants.DEFAULT_MS_CLIENT_ID;
    private String newsUrl = Constants.DEFAULT_NEWS_URL;
    private String serversUrl = Constants.DEFAULT_SERVERS_URL;
    private String modsManifestUrl = Constants.DEFAULT_MODS_MANIFEST_URL;
    private String updateUrl = Constants.DEFAULT_UPDATE_URL;
    /**
     * Depot GitHub surveille pour les mises a jour, au format proprietaire/nom.
     *
     * <p>Prioritaire sur updateUrl : renseigner ce champ suffit, la publication et son
     * empreinte sont lues directement dans les Releases du depot.</p>
     */
    private String githubRepo = Constants.DEFAULT_GITHUB_REPO;

    /* --- Sauvegarde cloud ---------------------------------------------- */

    private boolean cloudSyncEnabled = false;
    private String cloudSyncUrl = Constants.DEFAULT_CLOUD_SYNC_URL;
    /** Jeton porteur envoye a l'API de sauvegarde. */
    private String cloudSyncToken = "";

    /* --- Reglages du jeu ------------------------------------------------ */

    private GraphicsSettings graphics = new GraphicsSettings();

    /** Configuration neuve avec des valeurs adaptees a la machine courante. */
    public static LauncherSettings createDefault() {
        LauncherSettings settings = new LauncherSettings();
        settings.gameDirectory = OsUtil.defaultMinecraftDir().toString();
        settings.language = com.minicube.launcher.util.I18n.systemLanguage();
        settings.ramMb = recommendedRam();
        return settings;
    }

    /**
     * RAM conseillee : la moitie de la memoire physique, bornee entre 2 et 8 Go.
     * Laisser de la memoire au systeme et au pilote graphique evite les a-coups.
     */
    public static int recommendedRam() {
        int total = OsUtil.totalSystemRamMb();
        if (total <= 0) {
            return Constants.DEFAULT_RAM_MB;
        }
        int half = total / 2;
        return Math.max(2048, Math.min(8192, half - (half % 512)));
    }

    /** Corrige les valeurs incoherentes issues d'un fichier edite a la main. */
    public void sanitize() {
        if (ramMb < Constants.MIN_RAM_MB) {
            ramMb = Constants.DEFAULT_RAM_MB;
        }
        if (theme == null || (!theme.equals("dark") && !theme.equals("light"))) {
            theme = "dark";
        }
        if (language == null || language.isBlank()) {
            language = com.minicube.launcher.util.I18n.systemLanguage();
        }
        if (graphics == null) {
            graphics = new GraphicsSettings();
        }
        if (msClientId == null || msClientId.isBlank()) {
            msClientId = Constants.DEFAULT_MS_CLIENT_ID;
        }
    }

    /* --- Accesseurs ----------------------------------------------------- */

    public String getGameDirectory() {
        return gameDirectory == null ? "" : gameDirectory;
    }

    public void setGameDirectory(String gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    public boolean isFirstRunCompleted() {
        return firstRunCompleted;
    }

    public void setFirstRunCompleted(boolean firstRunCompleted) {
        this.firstRunCompleted = firstRunCompleted;
    }

    public String getLastVersionId() {
        return lastVersionId == null ? "" : lastVersionId;
    }

    public void setLastVersionId(String lastVersionId) {
        this.lastVersionId = lastVersionId;
    }

    public String getActiveAccountUuid() {
        return activeAccountUuid == null ? "" : activeAccountUuid;
    }

    public void setActiveAccountUuid(String activeAccountUuid) {
        this.activeAccountUuid = activeAccountUuid;
    }

    public int getRamMb() {
        return ramMb;
    }

    public void setRamMb(int ramMb) {
        this.ramMb = Math.max(Constants.MIN_RAM_MB, ramMb);
    }

    public String getJavaPath() {
        return javaPath == null ? "" : javaPath;
    }

    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    public String getExtraJvmArgs() {
        return extraJvmArgs == null ? "" : extraJvmArgs;
    }

    public void setExtraJvmArgs(String extraJvmArgs) {
        this.extraJvmArgs = extraJvmArgs;
    }

    public String getTheme() {
        return theme == null ? "dark" : theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isDarkTheme() {
        return "dark".equals(getTheme());
    }

    public String getLanguage() {
        return language == null || language.isBlank()
                ? com.minicube.launcher.util.I18n.systemLanguage() : language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getBackgroundImage() {
        return backgroundImage == null ? "" : backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public boolean isKeepLauncherOpen() {
        return keepLauncherOpen;
    }

    public void setKeepLauncherOpen(boolean keepLauncherOpen) {
        this.keepLauncherOpen = keepLauncherOpen;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isAutoUpdateLauncher() {
        return autoUpdateLauncher;
    }

    public void setAutoUpdateLauncher(boolean autoUpdateLauncher) {
        this.autoUpdateLauncher = autoUpdateLauncher;
    }

    public boolean isVerifyFilesBeforeLaunch() {
        return verifyFilesBeforeLaunch;
    }

    public void setVerifyFilesBeforeLaunch(boolean verifyFilesBeforeLaunch) {
        this.verifyFilesBeforeLaunch = verifyFilesBeforeLaunch;
    }

    public boolean isAutoInstallRequiredMods() {
        return autoInstallRequiredMods;
    }

    public void setAutoInstallRequiredMods(boolean autoInstallRequiredMods) {
        this.autoInstallRequiredMods = autoInstallRequiredMods;
    }

    public boolean isVoiceGreetingEnabled() {
        return voiceGreetingEnabled;
    }

    public void setVoiceGreetingEnabled(boolean voiceGreetingEnabled) {
        this.voiceGreetingEnabled = voiceGreetingEnabled;
    }

    public boolean isShadersEnabled() {
        return shadersEnabled;
    }

    public void setShadersEnabled(boolean shadersEnabled) {
        this.shadersEnabled = shadersEnabled;
    }

    public String getActiveShaderPack() {
        return activeShaderPack == null ? "" : activeShaderPack;
    }

    public void setActiveShaderPack(String activeShaderPack) {
        this.activeShaderPack = activeShaderPack;
    }

    public String getMsClientId() {
        return msClientId == null || msClientId.isBlank()
                ? Constants.DEFAULT_MS_CLIENT_ID : msClientId;
    }

    public void setMsClientId(String msClientId) {
        this.msClientId = msClientId;
    }

    public String getNewsUrl() {
        return newsUrl == null ? "" : newsUrl;
    }

    public void setNewsUrl(String newsUrl) {
        this.newsUrl = newsUrl;
    }

    public String getServersUrl() {
        return serversUrl == null ? "" : serversUrl;
    }

    public void setServersUrl(String serversUrl) {
        this.serversUrl = serversUrl;
    }

    public String getModsManifestUrl() {
        return modsManifestUrl == null ? "" : modsManifestUrl;
    }

    public void setModsManifestUrl(String modsManifestUrl) {
        this.modsManifestUrl = modsManifestUrl;
    }

    public String getGithubRepo() {
        return githubRepo == null ? "" : githubRepo.trim();
    }

    public void setGithubRepo(String githubRepo) {
        this.githubRepo = githubRepo;
    }

    public String getUpdateUrl() {
        return updateUrl == null ? "" : updateUrl;
    }

    public void setUpdateUrl(String updateUrl) {
        this.updateUrl = updateUrl;
    }

    public boolean isCloudSyncEnabled() {
        return cloudSyncEnabled;
    }

    public void setCloudSyncEnabled(boolean cloudSyncEnabled) {
        this.cloudSyncEnabled = cloudSyncEnabled;
    }

    public String getCloudSyncUrl() {
        return cloudSyncUrl == null ? "" : cloudSyncUrl;
    }

    public void setCloudSyncUrl(String cloudSyncUrl) {
        this.cloudSyncUrl = cloudSyncUrl;
    }

    public String getCloudSyncToken() {
        return cloudSyncToken == null ? "" : cloudSyncToken;
    }

    public void setCloudSyncToken(String cloudSyncToken) {
        this.cloudSyncToken = cloudSyncToken;
    }

    public GraphicsSettings getGraphics() {
        if (graphics == null) {
            graphics = new GraphicsSettings();
        }
        return graphics;
    }

    public void setGraphics(GraphicsSettings graphics) {
        this.graphics = graphics;
    }
}
