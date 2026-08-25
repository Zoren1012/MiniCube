package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.model.AccountType;
import com.minicube.launcher.model.Cape;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Authentification Microsoft complete, en flux "device code".
 *
 * <p>Ce flux est celui recommande pour une application de bureau : aucun secret client
 * n'est stocke et aucun serveur de redirection local n'est necessaire. L'utilisateur
 * ouvre une page Microsoft, saisit un code court, et le launcher recupere les jetons.</p>
 *
 * <p>Chaine complete : Microsoft -&gt; Xbox Live -&gt; XSTS -&gt; Minecraft Services.</p>
 */
public class MicrosoftAuthService {

    /** Erreur fonctionnelle d'authentification, porteuse d'un message affichable. */
    public static class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Code a saisir par l'utilisateur.
     *
     * @param deviceCode      code technique utilise pour interroger Microsoft
     * @param userCode        code court affiche a l'utilisateur
     * @param verificationUri page a ouvrir dans le navigateur
     * @param intervalSeconds delai minimal entre deux interrogations
     * @param expiresInSecond duree de validite du code
     */
    public record DeviceCode(String deviceCode, String userCode, String verificationUri,
                             int intervalSeconds, int expiresInSecond) {
    }

    private final ConfigService config;

    public MicrosoftAuthService(ConfigService config) {
        this.config = config;
    }

    /**
     * Demande un code d'appairage a Microsoft.
     *
     * @throws AuthException si l'identifiant d'application n'est pas configure ou si
     *                       Microsoft refuse la demande
     */
    public DeviceCode requestDeviceCode() throws AuthException {
        String clientId = config.settings().getMsClientId();
        if (clientId == null || clientId.isBlank()
                || clientId.equals(Constants.DEFAULT_MS_CLIENT_ID)) {
            throw new AuthException("Aucun identifiant d'application Azure n'est configure. "
                    + "Renseignez msClientId dans les parametres du launcher "
                    + "(voir la documentation d'installation).");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("scope", Constants.MS_SCOPE);
        try {
            JsonObject response = Http.postForm(Constants.MS_DEVICE_CODE_URL, form);
            if (response.has("error")) {
                throw new AuthException("Microsoft a refuse la demande : "
                        + Json.string(response, "error_description",
                        Json.string(response, "error", "erreur inconnue")));
            }
            DeviceCode code = new DeviceCode(
                    response.get("device_code").getAsString(),
                    response.get("user_code").getAsString(),
                    Json.string(response, "verification_uri",
                            "https://microsoft.com/link"),
                    Json.integer(response, "interval", 5),
                    Json.integer(response, "expires_in", 900));
            Log.info("Code d'appairage Microsoft obtenu : " + code.userCode());
            return code;
        } catch (IOException e) {
            throw new AuthException("Connexion a Microsoft impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Interroge Microsoft jusqu'a ce que l'utilisateur ait valide le code, puis deroule
     * toute la chaine d'authentification.
     *
     * @param code      code obtenu par {@link #requestDeviceCode()}
     * @param onStatus  rappel de progression (libelle de l'etape en cours)
     * @param cancelled consulte a chaque tour de boucle pour permettre l'annulation
     * @return le compte authentifie, pret a etre enregistre
     */
    public Account completeDeviceCodeFlow(DeviceCode code, Consumer<String> onStatus,
                                          BooleanSupplier cancelled) throws AuthException {
        MsTokens microsoftTokens = pollForMicrosoftToken(code, onStatus, cancelled);
        return buildAccountFromMicrosoftToken(microsoftTokens, onStatus);
    }

    /** Jetons OAuth Microsoft (etape 1 de la chaine). */
    private record MsTokens(String accessToken, String refreshToken) {
    }

    /** Boucle d'attente de la validation utilisateur sur la page Microsoft. */
    private MsTokens pollForMicrosoftToken(DeviceCode code, Consumer<String> onStatus,
                                           BooleanSupplier cancelled) throws AuthException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("client_id", config.settings().getMsClientId());
        form.put("device_code", code.deviceCode());

        long deadline = System.currentTimeMillis() + code.expiresInSecond() * 1000L;
        int intervalMillis = Math.max(2, code.intervalSeconds()) * 1000;

        report(onStatus, "En attente de la validation sur la page Microsoft...");
        while (System.currentTimeMillis() < deadline) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                throw new AuthException("Connexion annulee.");
            }
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AuthException("Connexion interrompue.");
            }
            try {
                JsonObject response = Http.postForm(Constants.MS_TOKEN_URL, form);
                if (!response.has("error")) {
                    report(onStatus, "Compte Microsoft valide.");
                    return new MsTokens(response.get("access_token").getAsString(),
                            Json.string(response, "refresh_token", ""));
                }
                String error = Json.string(response, "error", "");
                switch (error) {
                    case "authorization_pending" -> {
                        // Cas normal : l'utilisateur n'a pas encore valide, on continue.
                    }
                    case "slow_down" -> intervalMillis += 5000;
                    case "expired_token" -> throw new AuthException(
                            "Le code a expire. Relancez la connexion.");
                    case "authorization_declined" -> throw new AuthException(
                            "Connexion refusee dans le navigateur.");
                    default -> throw new AuthException("Microsoft a renvoye une erreur : "
                            + Json.string(response, "error_description", error));
                }
            } catch (IOException e) {
                throw new AuthException("Erreur reseau pendant la connexion : "
                        + e.getMessage(), e);
            }
        }
        throw new AuthException("Delai depasse : le code n'a pas ete valide a temps.");
    }

    /** Deroule Xbox Live, XSTS, Minecraft Services puis recupere le profil. */
    private Account buildAccountFromMicrosoftToken(MsTokens tokens, Consumer<String> onStatus)
            throws AuthException {
        try {
            report(onStatus, "Authentification Xbox Live...");
            JsonObject xbl = authenticateXboxLive(tokens.accessToken());
            String xblToken = xbl.get("Token").getAsString();
            String userHash = extractUserHash(xbl);

            report(onStatus, "Autorisation XSTS...");
            JsonObject xsts = authorizeXsts(xblToken);
            String xstsToken = xsts.get("Token").getAsString();

            report(onStatus, "Ouverture de la session Minecraft...");
            JsonObject mcLogin = loginWithXbox(userHash, xstsToken);
            String mcAccessToken = mcLogin.get("access_token").getAsString();
            long expiresIn = Json.longValue(mcLogin, "expires_in", 86400L);

            report(onStatus, "Recuperation du profil...");
            Account account = fetchProfile(mcAccessToken);
            account.setType(AccountType.MICROSOFT);
            account.setAccessToken(mcAccessToken);
            account.setRefreshToken(tokens.refreshToken());
            account.setExpiresAt(System.currentTimeMillis() + expiresIn * 1000L);
            Log.info("Connexion Microsoft reussie pour " + account.getUsername());
            return account;
        } catch (Http.HttpStatusException e) {
            throw new AuthException(describeHttpError(e), e);
        } catch (IOException e) {
            throw new AuthException("Erreur reseau : " + e.getMessage(), e);
        }
    }

    /** Etape Xbox Live : echange du jeton Microsoft contre un jeton XBL. */
    private JsonObject authenticateXboxLive(String microsoftAccessToken) throws IOException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftAccessToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        return Http.postJson(Constants.XBL_AUTH_URL, body, headers);
    }

    /** Etape XSTS : autorisation d'acces au service Minecraft. */
    private JsonObject authorizeXsts(String xblToken) throws IOException, AuthException {
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);

        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        try {
            return Http.postJson(Constants.XSTS_AUTH_URL, body, headers);
        } catch (Http.HttpStatusException e) {
            throw new AuthException(describeXstsError(e.body()), e);
        }
    }

    /**
     * Traduit les codes XErr renvoyes par XSTS en messages comprehensibles.
     * Ce sont les erreurs les plus frequentes en pratique.
     */
    private String describeXstsError(String body) {
        long code = 0;
        try {
            JsonObject json = Json.parseObject(body);
            code = Json.longValue(json, "XErr", 0);
        } catch (Exception ignored) {
            // Corps non exploitable : on retombe sur le message generique.
        }
        if (code == 2148916233L) {
            return "Ce compte Microsoft ne possede pas de profil Xbox. "
                    + "Creez-en un sur xbox.com puis reessayez.";
        }
        if (code == 2148916235L) {
            return "Le service Xbox Live n'est pas disponible dans le pays de ce compte.";
        }
        if (code == 2148916236L || code == 2148916237L) {
            return "Ce compte necessite une verification d'adulte (Xbox).";
        }
        if (code == 2148916238L) {
            return "Compte enfant : il doit etre rattache a une famille Microsoft "
                    + "pour acceder a Minecraft.";
        }
        return "Autorisation Xbox refusee (XErr " + code + ").";
    }

    /** Etape Minecraft Services : obtention du jeton de session du jeu. */
    private JsonObject loginWithXbox(String userHash, String xstsToken) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");
        return Http.postJson(Constants.MC_LOGIN_URL, body, headers);
    }

    private String extractUserHash(JsonObject xblResponse) throws AuthException {
        JsonObject claims = Json.object(xblResponse, "DisplayClaims");
        if (claims == null) {
            throw new AuthException("Reponse Xbox Live inattendue (DisplayClaims absent).");
        }
        JsonArray xui = Json.array(claims, "xui");
        if (xui.isEmpty()) {
            throw new AuthException("Reponse Xbox Live inattendue (identifiant absent).");
        }
        return xui.get(0).getAsJsonObject().get("uhs").getAsString();
    }

    /**
     * Recupere le profil Minecraft (pseudo, UUID, skin, capes).
     *
     * @throws AuthException si le compte ne possede pas Minecraft Java Edition
     */
    public Account fetchProfile(String mcAccessToken) throws IOException, AuthException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + mcAccessToken);
        JsonObject profile;
        try {
            profile = Http.getJson(Constants.MC_PROFILE_URL, headers);
        } catch (Http.HttpStatusException e) {
            if (e.status() == 404) {
                throw new AuthException("Ce compte Microsoft ne possede pas "
                        + "Minecraft Java Edition.");
            }
            throw e;
        }

        Account account = new Account();
        account.setType(AccountType.MICROSOFT);
        account.setUuid(Json.string(profile, "id", ""));
        account.setUsername(Json.string(profile, "name", "Joueur"));

        JsonArray skins = Json.array(profile, "skins");
        for (int i = 0; i < skins.size(); i++) {
            JsonObject skin = skins.get(i).getAsJsonObject();
            if ("ACTIVE".equalsIgnoreCase(Json.string(skin, "state", ""))) {
                account.setSkinUrl(Json.string(skin, "url", ""));
                String variant = Json.string(skin, "variant", "CLASSIC");
                account.setSkinModel("SLIM".equalsIgnoreCase(variant) ? "slim" : "classic");
                break;
            }
        }

        JsonArray capes = Json.array(profile, "capes");
        for (int i = 0; i < capes.size(); i++) {
            JsonObject cape = capes.get(i).getAsJsonObject();
            Cape entry = new Cape(
                    Json.string(cape, "id", ""),
                    Json.string(cape, "alias", "Cape"),
                    Json.string(cape, "url", ""),
                    Json.string(cape, "state", "INACTIVE"));
            account.getCapes().add(entry);
            if (entry.isActive()) {
                account.setCapeUrl(entry.getUrl());
            }
        }
        return account;
    }

    /**
     * Renouvelle silencieusement la session d'un compte a partir de son jeton de
     * rafraichissement. C'est ce qui permet de retrouver son compte au demarrage sans
     * ressaisir de code.
     *
     * @param account compte a rafraichir (modifie sur place en cas de succes)
     * @throws AuthException si le rafraichissement echoue ; l'appelant doit alors
     *                       proposer une reconnexion complete
     */
    public Account refresh(Account account) throws AuthException {
        if (!account.canRefresh()) {
            throw new AuthException("Aucun jeton de rafraichissement disponible.");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", config.settings().getMsClientId());
        form.put("refresh_token", account.getRefreshToken());
        form.put("scope", Constants.MS_SCOPE);
        try {
            JsonObject response = Http.postForm(Constants.MS_TOKEN_URL, form);
            if (response.has("error")) {
                throw new AuthException("Session expiree : "
                        + Json.string(response, "error_description", "reconnexion necessaire"));
            }
            MsTokens tokens = new MsTokens(response.get("access_token").getAsString(),
                    Json.string(response, "refresh_token", account.getRefreshToken()));
            Account refreshed = buildAccountFromMicrosoftToken(tokens, null);
            // Conserve l'identite locale : seuls les jetons et le profil changent.
            account.setUsername(refreshed.getUsername());
            account.setUuid(refreshed.getUuid());
            account.setAccessToken(refreshed.getAccessToken());
            account.setRefreshToken(refreshed.getRefreshToken());
            account.setExpiresAt(refreshed.getExpiresAt());
            account.setSkinUrl(refreshed.getSkinUrl());
            account.setCapeUrl(refreshed.getCapeUrl());
            account.setSkinModel(refreshed.getSkinModel());
            account.setCapes(refreshed.getCapes());
            Log.info("Session Microsoft renouvelee pour " + account.getUsername());
            return account;
        } catch (IOException e) {
            throw new AuthException("Renouvellement impossible : " + e.getMessage(), e);
        }
    }

    /** Verifie que le compte possede bien une licence Minecraft Java. */
    public boolean hasGameEntitlement(String mcAccessToken) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + mcAccessToken);
            JsonObject response = Http.getJson(Constants.MC_ENTITLEMENTS_URL, headers);
            return !Json.array(response, "items").isEmpty();
        } catch (IOException e) {
            Log.debug("Verification de licence impossible : " + e.getMessage());
            return true;
        }
    }

    private String describeHttpError(Http.HttpStatusException e) {
        if (e.status() == 401 || e.status() == 403) {
            return "Session refusee par le service Minecraft. Reconnectez-vous.";
        }
        if (e.status() >= 500) {
            return "Les services Mojang sont momentanement indisponibles.";
        }
        return "Echec de l'authentification (HTTP " + e.status() + ").";
    }

    private void report(Consumer<String> onStatus, String message) {
        Log.debug("[auth] " + message);
        if (onStatus != null) {
            onStatus.accept(message);
        }
    }
}
