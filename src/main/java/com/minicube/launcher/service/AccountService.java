package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.Account;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.Safety;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gestion des comptes enregistres : persistance, compte actif et reconnexion
 * automatique au demarrage.
 *
 * <p>Les comptes sont stockes dans {@code ~/.minicube/accounts.json}. Ce fichier
 * contient des jetons de session : il est cree avec des permissions restreintes lorsque
 * le systeme de fichiers le permet.</p>
 */
public class AccountService {

    private final MicrosoftAuthService authService;
    private final ConfigService config;
    private final List<Account> accounts = new ArrayList<>();
    private final List<Consumer<Account>> listeners = new CopyOnWriteArrayList<>();

    private Account active;

    public AccountService(ConfigService config, MicrosoftAuthService authService) {
        this.config = config;
        this.authService = authService;
        load();
    }

    /* ------------------------------------------------------------------ */
    /* Persistance                                                         */
    /* ------------------------------------------------------------------ */

    private void load() {
        var file = LauncherPaths.accountsFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<Account> loaded = Json.read(file,
                    new TypeToken<List<Account>>() { }.getType());
            accounts.addAll(loaded);
            String activeUuid = config.settings().getActiveAccountUuid();
            active = accounts.stream()
                    .filter(a -> activeUuid.equalsIgnoreCase(a.getUuid()))
                    .findFirst()
                    .orElse(accounts.isEmpty() ? null : accounts.get(0));
            Log.info(accounts.size() + " compte(s) charge(s)");
        } catch (Exception e) {
            Log.error("Lecture des comptes impossible : " + e.getMessage());
        }
    }

    /** Ecrit la liste des comptes et memorise le compte actif dans la configuration. */
    public void save() {
        try {
            Json.write(LauncherPaths.accountsFile(), accounts);
            restrictPermissions();
            config.settings().setActiveAccountUuid(active == null ? "" : active.getUuid());
            config.save();
        } catch (IOException e) {
            Log.error("Enregistrement des comptes impossible : " + e.getMessage());
        }
    }

    /**
     * Reserve le fichier de comptes au seul utilisateur courant.
     *
     * <p>{@code File.setReadable} ne suffit pas : sous Windows, cette API ne touche pas
     * les listes de controle d'acces NTFS et renvoie un succes trompeur. La restriction
     * reelle est faite par {@link Safety#restrictToOwner}.</p>
     */
    private void restrictPermissions() {
        Safety.restrictToOwner(LauncherPaths.accountsFile());
    }

    /* ------------------------------------------------------------------ */
    /* Operations                                                          */
    /* ------------------------------------------------------------------ */

    public List<Account> accounts() {
        return List.copyOf(accounts);
    }

    public Optional<Account> active() {
        return Optional.ofNullable(active);
    }

    public boolean hasAccount() {
        return active != null;
    }

    /** Ajoute ou remplace un compte (par UUID) et le rend actif. */
    public void addOrReplace(Account account) {
        accounts.removeIf(a -> a.getUuid() != null && a.getUuid().equalsIgnoreCase(
                account.getUuid()));
        accounts.add(account);
        setActive(account);
    }

    /** Cree un compte hors-ligne apres validation du pseudo. */
    public Account createOfflineAccount(String username) {
        String cleaned = username == null ? "" : username.trim();
        if (!isValidUsername(cleaned)) {
            throw new IllegalArgumentException(
                    "Pseudo invalide : 3 a 16 caracteres, lettres, chiffres et _ uniquement.");
        }
        Account account = Account.offline(cleaned);
        addOrReplace(account);
        Log.info("Compte hors-ligne cree : " + cleaned);
        return account;
    }

    /** Regle de nommage Minecraft : 3 a 16 caracteres alphanumeriques ou tiret bas. */
    public static boolean isValidUsername(String username) {
        return username != null && username.matches("[A-Za-z0-9_]{3,16}");
    }

    public void setActive(Account account) {
        this.active = account;
        save();
        listeners.forEach(listener -> listener.accept(account));
    }

    public void remove(Account account) {
        accounts.remove(account);
        if (active == account) {
            active = accounts.isEmpty() ? null : accounts.get(0);
            listeners.forEach(listener -> listener.accept(active));
        }
        save();
        Log.info("Compte supprime : " + account.getUsername());
    }

    /**
     * Garantit que le compte actif dispose d'un jeton valide.
     *
     * <p>Appelee juste avant le lancement du jeu : un jeton expire est renouvele en
     * silence grace au jeton de rafraichissement.</p>
     *
     * @return le compte pret a l'emploi
     * @throws MicrosoftAuthService.AuthException si une reconnexion manuelle est requise
     */
    public Account ensureValidSession() throws MicrosoftAuthService.AuthException {
        if (active == null) {
            throw new MicrosoftAuthService.AuthException("Aucun compte selectionne.");
        }
        if (active.isTokenValid()) {
            return active;
        }
        Log.info("Jeton expire, renouvellement de la session...");
        Account refreshed = authService.refresh(active);
        save();
        listeners.forEach(listener -> listener.accept(refreshed));
        return refreshed;
    }

    /**
     * Tente un renouvellement silencieux au demarrage. Un echec n'est pas bloquant :
     * l'utilisateur pourra se reconnecter manuellement depuis l'interface.
     */
    public void refreshActiveQuietly() {
        if (active == null || active.isOffline() || active.isTokenValid()) {
            return;
        }
        try {
            authService.refresh(active);
            save();
            listeners.forEach(listener -> listener.accept(active));
        } catch (MicrosoftAuthService.AuthException e) {
            Log.warn("Reconnexion automatique impossible : " + e.getMessage());
        }
    }

    /** URL de la tete du joueur, utilisee par l'interface. */
    public String avatarUrl(Account account) {
        String key = account.getUuid() == null || account.getUuid().isBlank()
                ? account.getUsername()
                : account.getUuid().replace("-", "");
        return String.format(com.minicube.launcher.core.Constants.AVATAR_URL, key);
    }

    /** Abonne un composant aux changements de compte actif. */
    public void addChangeListener(Consumer<Account> listener) {
        listeners.add(listener);
    }
}
