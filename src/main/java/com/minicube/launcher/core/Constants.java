package com.minicube.launcher.core;

/**
 * Constantes globales du launcher (identite, endpoints distants, valeurs par defaut).
 *
 * <p>Les URL "projet" (news, serveurs, manifeste de mods, mise a jour) sont surchargeables
 * depuis le fichier de configuration : voir {@code config.example.json}.</p>
 */
public final class Constants {

    private Constants() {
    }

    /* ------------------------------------------------------------------ */
    /* Identite du launcher                                                */
    /* ------------------------------------------------------------------ */

    public static final String APP_NAME = "MiniCube";
    public static final String APP_VERSION = "1.18.0";
    /** Nom du dossier de travail cree dans le repertoire utilisateur. */
    public static final String APP_DIR_NAME = ".minicube";
    public static final String USER_AGENT = APP_NAME + "/" + APP_VERSION;

    /* ------------------------------------------------------------------ */
    /* Authentification Microsoft                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Identifiant de l'application Azure AD (public client, flux "device code").
     * <p><b>A remplacer par le votre</b> : portal.azure.com &gt; Azure Active Directory &gt;
     * App registrations &gt; New registration &gt; "Allow public client flows" = Oui.
     * Peut aussi etre defini via {@code msClientId} dans la configuration.</p>
     */
    public static final String DEFAULT_MS_CLIENT_ID = "00000000-0000-0000-0000-000000000000";

    public static final String MS_DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    public static final String MS_TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    public static final String MS_SCOPE = "XboxLive.signin offline_access";

    public static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";
    public static final String MC_ENTITLEMENTS_URL =
            "https://api.minecraftservices.com/entitlements/mcstore";
    public static final String MC_SKINS_URL =
            "https://api.minecraftservices.com/minecraft/profile/skins";
    public static final String MC_ACTIVE_CAPE_URL =
            "https://api.minecraftservices.com/minecraft/profile/capes/active";

    /* ------------------------------------------------------------------ */
    /* Ressources Mojang                                                   */
    /* ------------------------------------------------------------------ */

    public static final String VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String RESOURCES_BASE_URL = "https://resources.download.minecraft.net/";
    public static final String SESSION_PROFILE_URL =
            "https://sessionserver.mojang.com/session/minecraft/profile/";
    /** Rendu de tete de joueur (service public, sans authentification). */
    public static final String AVATAR_URL = "https://mc-heads.net/avatar/%s/64";

    /* ------------------------------------------------------------------ */
    /* Endpoints du projet (surchargeables)                                */
    /* ------------------------------------------------------------------ */

    /**
     * Invitation Discord du projet.
     *
     * <p>Ecrite ici et non dans la configuration : c'est l'adresse de la communaute, pas
     * un reglage. La laisser modifiable permettrait de rediriger les joueurs ailleurs en
     * editant un fichier, ce qui n'a aucun interet legitime.</p>
     */
    public static final String DISCORD_INVITE_URL = "https://discord.gg/fxEnUhmUHj";

    /* ------------------------------------------------------------------ */
    /* Apparence                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Styles livres avec le launcher.
     *
     * <p>Cette liste vit ici, et non dans le catalogue des styles, parce que la
     * validation des reglages doit y acceder sans dependre de JavaFX. Les deux listes
     * sont tenues identiques par la suite de verifications : ajouter un style au
     * catalogue sans l'ajouter ici le ferait effacer au prochain chargement.</p>
     */
    public static final java.util.List<String> STYLE_IDS = java.util.List.of(
            "dark", "light", "minecraft", "nether", "abysse", "foret", "sakura");

    /** Depot GitHub surveille par defaut pour les mises a jour du launcher. */
    public static final String DEFAULT_GITHUB_REPO = "Zoren1012/MiniCube";

    /** Laisser vide pour utiliser les donnees embarquees dans le jar. */
    public static final String DEFAULT_NEWS_URL = "";
    public static final String DEFAULT_SERVERS_URL = "";

    /**
     * Manifeste des mods requis, servi par la derniere publication du depot.
     *
     * <p>L'adresse {@code releases/latest/download} suit automatiquement la publication
     * la plus recente : le manifeste y est genere a la fabrication, avec l'empreinte
     * reelle du jar qui vient d'etre construit. Une empreinte figee dans le depot serait
     * fausse des la compilation suivante, Gradle ne produisant pas deux jars identiques
     * a l'octet pres.</p>
     */
    public static final String DEFAULT_MODS_MANIFEST_URL =
            "https://github.com/" + DEFAULT_GITHUB_REPO
                    + "/releases/latest/download/mods-manifest.json";

    public static final String DEFAULT_UPDATE_URL = "";
    public static final String DEFAULT_CLOUD_SYNC_URL = "";

    /* ------------------------------------------------------------------ */
    /* Valeurs par defaut                                                  */
    /* ------------------------------------------------------------------ */

    public static final int DEFAULT_RAM_MB = 4096;
    public static final int MIN_RAM_MB = 1024;
    public static final int DEFAULT_WINDOW_WIDTH = 1180;
    public static final int DEFAULT_WINDOW_HEIGHT = 720;
    public static final int NETWORK_TIMEOUT_SECONDS = 20;
    /** Nombre de telechargements simultanes lors de la verification des fichiers. */
    public static final int DOWNLOAD_THREADS = 8;
}
