package com.minicube.launcher.service;

import com.minicube.launcher.core.Constants;
import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.model.Cape;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Gestion du skin et des capes du compte actif.
 *
 * <p>Le changement de skin passe par l'API officielle Minecraft Services et n'est donc
 * possible qu'avec un compte Microsoft authentifie. Pour un compte hors-ligne, le skin
 * importe reste local et sert uniquement a la previsualisation.</p>
 */
public class SkinService {

    /** Levee lorsqu'une image ne respecte pas le format attendu par le jeu. */
    public static class InvalidSkinException extends Exception {
        public InvalidSkinException(String message) {
            super(message);
        }
    }

    private final MicrosoftAuthService authService;

    public SkinService(MicrosoftAuthService authService) {
        this.authService = authService;
    }

    /* ------------------------------------------------------------------ */
    /* Lecture                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Recupere la texture de skin du compte et la met en cache.
     *
     * @return le fichier PNG local, ou null si le compte n'a pas de skin personnalise
     */
    public Path downloadSkinTexture(Account account) {
        String url = account.getSkinUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        return downloadTexture(url, "skin-" + account.getUuid() + ".png");
    }

    /** Recupere la texture de cape active du compte. */
    public Path downloadCapeTexture(Account account) {
        String url = account.getCapeUrl();
        if (url == null || url.isBlank()) {
            return null;
        }
        return downloadTexture(url, "cape-" + account.getUuid() + ".png");
    }

    private Path downloadTexture(String url, String fileName) {
        try {
            Path cacheDir = LauncherPaths.cacheDir().resolve("textures");
            Files.createDirectories(cacheDir);
            Path target = cacheDir.resolve(fileName);
            Http.download(url, target, null);
            return target;
        } catch (IOException e) {
            Log.warn("Telechargement de la texture impossible (" + url + ") : " + e.getMessage());
            return null;
        }
    }

    /**
     * Recupere le skin d'un compte hors-ligne s'il existe un joueur premium du meme nom.
     * Permet d'afficher un apercu credible sans authentification.
     *
     * @return le fichier PNG local, ou null si aucun profil ne correspond
     */
    public Path downloadSkinByUsername(String username) {
        try {
            String url = String.format(Constants.AVATAR_URL, username);
            return downloadTexture(url, "avatar-" + username + ".png");
        } catch (Exception e) {
            Log.debug("Aucun skin public pour " + username);
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Import et validation                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Verifie qu'un fichier est un skin Minecraft valide.
     *
     * <p>Formats acceptes : 64x64 (format moderne, avec seconde couche et bras separes)
     * et 64x32 (format historique).</p>
     *
     * @throws InvalidSkinException si le fichier n'est pas exploitable
     */
    public void validateSkinFile(Path file) throws InvalidSkinException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new InvalidSkinException("Fichier introuvable.");
        }
        if (!file.getFileName().toString().toLowerCase().endsWith(".png")) {
            throw new InvalidSkinException("Le skin doit etre une image PNG.");
        }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                throw new InvalidSkinException("Image illisible : le fichier n'est pas un PNG "
                        + "valide.");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            boolean modern = width == 64 && height == 64;
            boolean legacy = width == 64 && height == 32;
            if (!modern && !legacy) {
                throw new InvalidSkinException("Dimensions invalides : " + width + "x" + height
                        + ". Un skin doit mesurer 64x64 ou 64x32 pixels.");
            }
        } catch (IOException e) {
            throw new InvalidSkinException("Lecture de l'image impossible : " + e.getMessage());
        }
    }

    /**
     * Copie un skin dans la bibliotheque locale du launcher.
     *
     * @return le chemin du skin enregistre
     */
    public Path importSkin(Path source) throws InvalidSkinException, IOException {
        validateSkinFile(source);
        Files.createDirectories(LauncherPaths.skinsDir());
        Path target = LauncherPaths.skinsDir().resolve(source.getFileName().toString());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        Log.info("Skin importe : " + target.getFileName());
        return target;
    }

    /**
     * Rattache une texture importee au compte, sur cette machine uniquement.
     *
     * <p>C'est la seule forme d'application possible pour un compte hors-ligne : rien
     * ne peut etre envoye a Mojang sans authentification. La texture sert alors a
     * l'apercu 3D et a la vignette de la barre laterale, et elle est conservee d'une
     * session a l'autre.</p>
     *
     * <p>Elle ne sera en revanche pas visible par les autres joueurs : en multijoueur,
     * le serveur demande la texture aux serveurs de Mojang a partir de l'UUID du
     * compte, et un compte hors-ligne n'y possede aucun profil.</p>
     *
     * @param account compte auquel rattacher la texture
     * @param skin    fichier PNG deja valide
     */
    public void applyLocalSkin(Account account, Path skin) throws InvalidSkinException {
        validateSkinFile(skin);
        account.setLocalSkinPath(skin.toAbsolutePath().toString());
        Log.info("Skin local applique a " + account.getUsername() + " : "
                + skin.getFileName());
    }

    /**
     * Texture a afficher pour ce compte dans le launcher.
     *
     * <p>La texture importee localement a la priorite : si l'utilisateur en a choisi
     * une, c'est elle qu'il s'attend a voir, meme sur un compte Microsoft dont le skin
     * en ligne differe encore.</p>
     *
     * @return le fichier a afficher, ou null s'il faut se rabattre sur le personnage
     *         par defaut
     */
    public Path resolveDisplaySkin(Account account) {
        if (account.hasLocalSkin()) {
            Path local = Path.of(account.getLocalSkinPath());
            if (Files.isRegularFile(local)) {
                return local;
            }
            // Le fichier a ete deplace ou supprime depuis : on oublie la reference
            // plutot que de reessayer indefiniment.
            Log.warn("Skin local introuvable, reference abandonnee : " + local);
            account.setLocalSkinPath("");
        }
        return downloadSkinTexture(account);
    }

    /* ------------------------------------------------------------------ */
    /* Envoi vers Mojang                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Applique un skin au compte Microsoft via l'API officielle.
     *
     * @param account compte cible (doit etre un compte Microsoft avec un jeton valide)
     * @param skin    fichier PNG deja valide
     * @param slim    true pour le modele Alex (bras fins), false pour Steve
     * @throws InvalidSkinException si le fichier est invalide
     * @throws IOException          en cas d'erreur reseau ou de refus du serveur
     */
    public void uploadSkin(Account account, Path skin, boolean slim)
            throws InvalidSkinException, IOException {
        validateSkinFile(skin);
        if (account.isOffline()) {
            throw new IOException("Le changement de skin en ligne necessite un compte "
                    + "Microsoft. Le skin importe reste disponible pour l'apercu.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("variant", slim ? "slim" : "classic");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + account.getAccessToken());

        String response = Http.postMultipart(Constants.MC_SKINS_URL, fields, "file", skin,
                headers);
        Log.info("Skin envoye pour " + account.getUsername());

        account.setSkinModel(slim ? "slim" : "classic");
        // La reponse contient le profil mis a jour : on en extrait la nouvelle URL.
        try {
            JsonObject profile = com.minicube.launcher.util.Json.parseObject(response);
            var skins = com.minicube.launcher.util.Json.array(profile, "skins");
            for (var element : skins) {
                JsonObject entry = element.getAsJsonObject();
                if ("ACTIVE".equalsIgnoreCase(
                        com.minicube.launcher.util.Json.string(entry, "state", ""))) {
                    account.setSkinUrl(com.minicube.launcher.util.Json.string(entry, "url", ""));
                    break;
                }
            }
        } catch (Exception e) {
            Log.debug("Reponse de changement de skin non exploitable : " + e.getMessage());
        }
    }

    /**
     * Active une cape possedee par le compte.
     *
     * @param capeId identifiant de la cape renvoye par l'API
     */
    public void activateCape(Account account, String capeId) throws IOException {
        requireMicrosoft(account);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + account.getAccessToken());

        JsonObject body = new JsonObject();
        body.addProperty("capeId", capeId);
        Http.putJson(Constants.MC_ACTIVE_CAPE_URL, body, headers);

        for (Cape cape : account.getCapes()) {
            cape.setState(cape.getId().equals(capeId) ? "ACTIVE" : "INACTIVE");
            if (cape.isActive()) {
                account.setCapeUrl(cape.getUrl());
            }
        }
        Log.info("Cape activee pour " + account.getUsername());
    }

    /** Retire la cape actuellement portee. */
    public void disableCape(Account account) throws IOException {
        requireMicrosoft(account);
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + account.getAccessToken());
        Http.delete(Constants.MC_ACTIVE_CAPE_URL, headers);

        account.getCapes().forEach(cape -> cape.setState("INACTIVE"));
        account.setCapeUrl("");
        Log.info("Cape desactivee pour " + account.getUsername());
    }

    /**
     * Rafraichit la liste des capes et le skin actif depuis le serveur.
     * Utile apres un achat ou l'obtention d'une nouvelle cape.
     */
    public void refreshProfile(Account account) throws IOException {
        requireMicrosoft(account);
        try {
            Account fresh = authService.fetchProfile(account.getAccessToken());
            account.setSkinUrl(fresh.getSkinUrl());
            account.setSkinModel(fresh.getSkinModel());
            account.setCapeUrl(fresh.getCapeUrl());
            account.setCapes(fresh.getCapes());
        } catch (MicrosoftAuthService.AuthException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    private void requireMicrosoft(Account account) throws IOException {
        if (account.isOffline()) {
            throw new IOException("Cette action necessite un compte Microsoft.");
        }
        if (!account.isTokenValid()) {
            throw new IOException("Session expiree : reconnectez-vous avant de continuer.");
        }
    }
}
