package com.minicube.launcher.model;

import com.minicube.launcher.util.Hashing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compte joueur enregistre par le launcher.
 *
 * <p>Serialise tel quel dans {@code accounts.json}. Pour un compte Microsoft, le jeton
 * d'acces est de courte duree tandis que le jeton de rafraichissement permet de rouvrir
 * une session sans redemander de code : c'est lui qui rend la reconnexion automatique.</p>
 */
public class Account {

    private AccountType type = AccountType.OFFLINE;
    private String username = "Player";
    /** UUID sans tirets, format attendu par les arguments de lancement. */
    private String uuid;
    private String accessToken;
    private String refreshToken;
    /** Expiration du jeton d'acces, en millisecondes depuis l'epoque Unix. */
    private long expiresAt;
    private String skinUrl;
    private String capeUrl;
    /** classic ou slim (bras fins). */
    private String skinModel = "classic";
    /**
     * Skin importe conserve sur cette machine.
     *
     * <p>Un compte hors-ligne ne peut rien envoyer a Mojang : sa texture n existe donc
     * que localement. Elle sert a l apercu 3D et a la vignette de la barre laterale,
     * et survit au redemarrage du launcher.</p>
     */
    private String localSkinPath = "";
    private List<Cape> capes = new ArrayList<>();

    public Account() {
    }

    /** Cree un compte hors-ligne dont l'UUID est derive du pseudo. */
    public static Account offline(String username) {
        Account account = new Account();
        account.type = AccountType.OFFLINE;
        account.username = username;
        account.uuid = Hashing.undashed(Hashing.offlineUuid(username));
        account.accessToken = "0";
        return account;
    }

    public AccountType getType() {
        return type == null ? AccountType.OFFLINE : type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /** UUID au format canonique avec tirets. */
    public UUID uuidWithDashes() {
        return Hashing.fromUndashed(uuid);
    }

    public String getAccessToken() {
        return accessToken == null ? "0" : accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl;
    }

    public String getCapeUrl() {
        return capeUrl;
    }

    public void setCapeUrl(String capeUrl) {
        this.capeUrl = capeUrl;
    }

    public String getSkinModel() {
        return skinModel == null ? "classic" : skinModel;
    }

    public void setSkinModel(String skinModel) {
        this.skinModel = skinModel;
    }

    public String getLocalSkinPath() {
        return localSkinPath == null ? "" : localSkinPath;
    }

    public void setLocalSkinPath(String localSkinPath) {
        this.localSkinPath = localSkinPath;
    }

    /** Vrai si une texture importee est disponible sur cette machine. */
    public boolean hasLocalSkin() {
        return !getLocalSkinPath().isBlank();
    }

    public List<Cape> getCapes() {
        if (capes == null) {
            capes = new ArrayList<>();
        }
        return capes;
    }

    public void setCapes(List<Cape> capes) {
        this.capes = capes;
    }

    public boolean isOffline() {
        return getType() == AccountType.OFFLINE;
    }

    /**
     * Indique si le jeton d'acces est encore utilisable.
     * Une marge de securite de deux minutes evite qu'il expire pendant le lancement.
     */
    public boolean isTokenValid() {
        if (isOffline()) {
            return true;
        }
        return accessToken != null && !accessToken.isBlank()
                && System.currentTimeMillis() < expiresAt - 120_000L;
    }

    /** Vrai si une reconnexion silencieuse est possible via le jeton de rafraichissement. */
    public boolean canRefresh() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    @Override
    public String toString() {
        return username + " (" + getType().label() + ")";
    }
}
