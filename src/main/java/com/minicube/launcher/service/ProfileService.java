package com.minicube.launcher.service;

import com.minicube.launcher.core.LauncherPaths;
import com.minicube.launcher.model.MiniCubeProfile;
import com.minicube.launcher.util.Hashing;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.minicube.launcher.util.Safety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Compte MiniCube conserve sur la machine.
 *
 * <p>Le mot de passe n'est jamais enregistre. Ce qui l'est, c'est le resultat de
 * PBKDF2 applique au mot de passe avec un sel tire au hasard : de cette empreinte on ne
 * peut pas revenir au mot de passe, et deux comptes partageant le meme mot de passe
 * n'ont pas la meme empreinte.</p>
 *
 * <p><b>Ce que cela protege, et ce que cela ne protege pas.</b> Sur une machine ou tout
 * est local, ce mot de passe n'ajoute rien a la protection deja offerte par la session
 * du systeme : qui accede au fichier accede aussi au reste. Il a deux autres raisons
 * d'exister : eviter qu'un mot de passe reutilise ailleurs ne traine en clair, et
 * permettre de basculer vers un vrai serveur sans rien changer au fonctionnement.</p>
 */
public class ProfileService {

    /** Nombre d'iterations PBKDF2, aligne sur les recommandations OWASP. */
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private MiniCubeProfile profile;
    /** Jeton de session courante ; nul tant que personne ne s'est identifie. */
    private volatile String sessionToken;

    public ProfileService() {
        this.profile = load();
    }

    private static Path profileFile() {
        return LauncherPaths.launcherDir().resolve("profile.json");
    }

    private MiniCubeProfile load() {
        Path file = profileFile();
        if (!Files.isRegularFile(file)) {
            return new MiniCubeProfile();
        }
        try {
            MiniCubeProfile loaded = Json.read(file, MiniCubeProfile.class);
            Log.info("Compte MiniCube charge : " + loaded.getUsername());
            return loaded;
        } catch (Exception e) {
            Log.warn("Compte MiniCube illisible, il sera recree : " + e.getMessage());
            return new MiniCubeProfile();
        }
    }

    /** Enregistre le compte et restreint l'acces au fichier a son proprietaire. */
    public void save() {
        try {
            Json.write(profileFile(), profile);
            Safety.restrictToOwner(profileFile());
            listeners.forEach(Runnable::run);
        } catch (IOException e) {
            Log.error("Enregistrement du compte MiniCube impossible : " + e.getMessage());
        }
    }

    public MiniCubeProfile profile() {
        return profile;
    }

    /** Vrai si un compte existe sur cette machine. */
    public boolean hasAccount() {
        return profile.exists();
    }

    /** Vrai si une session est ouverte. */
    public boolean isSignedIn() {
        return sessionToken != null;
    }

    public String sessionToken() {
        return sessionToken;
    }

    /* ------------------------------------------------------------------ */
    /* Creation et authentification                                        */
    /* ------------------------------------------------------------------ */

    /**
     * Cree le compte de cette machine.
     *
     * @throws IllegalArgumentException si le pseudo ou le mot de passe ne conviennent pas
     * @throws IllegalStateException    si un compte existe deja
     */
    public synchronized void register(String username, String password) {
        if (profile.exists()) {
            throw new IllegalStateException("Un compte existe deja sur cette machine.");
        }
        String cleaned = username == null ? "" : username.trim();
        if (!cleaned.matches("[A-Za-z0-9_-]{3,20}")) {
            throw new IllegalArgumentException("Le pseudo doit faire 3 a 20 caracteres : "
                    + "lettres, chiffres, tiret et tiret bas.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit faire au moins 8 caracteres.");
        }

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        String saltHex = Hashing.toHex(salt);

        profile.setUsername(cleaned);
        profile.setPasswordSalt(saltHex);
        profile.setPasswordHash(derive(password, salt));
        profile.setCreatedAt(STAMP.format(LocalDateTime.now()));
        profile.setLastSeenAt(profile.getCreatedAt());
        save();

        openSession();
        Log.info("Compte MiniCube cree : " + cleaned);
    }

    /**
     * Ouvre une session.
     *
     * @return true si le mot de passe correspond
     */
    public synchronized boolean signIn(String username, String password) {
        if (!profile.exists()) {
            return false;
        }
        if (!profile.getUsername().equalsIgnoreCase(username == null ? "" : username.trim())) {
            return false;
        }
        byte[] salt = fromHex(profile.getPasswordSalt());
        String candidate = derive(password == null ? "" : password, salt);

        // Comparaison a duree constante : une comparaison ordinaire s'arrete au premier
        // caractere different et renseigne indirectement sur l'empreinte attendue.
        if (!constantTimeEquals(candidate, profile.getPasswordHash())) {
            Log.warn("Tentative de connexion refusee pour " + profile.getUsername());
            return false;
        }
        profile.setLastSeenAt(STAMP.format(LocalDateTime.now()));
        save();
        openSession();
        Log.info("Session MiniCube ouverte pour " + profile.getUsername());
        return true;
    }

    /** Ferme la session sans toucher au compte. */
    public synchronized void signOut() {
        sessionToken = null;
        listeners.forEach(Runnable::run);
        Log.info("Session MiniCube fermee");
    }

    /** Supprime definitivement le compte de cette machine. */
    public synchronized void deleteAccount() {
        profile = new MiniCubeProfile();
        sessionToken = null;
        try {
            Files.deleteIfExists(profileFile());
        } catch (IOException e) {
            Log.warn("Suppression du compte impossible : " + e.getMessage());
        }
        listeners.forEach(Runnable::run);
        Log.info("Compte MiniCube supprime");
    }

    private void openSession() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        listeners.forEach(Runnable::run);
    }

    /* ------------------------------------------------------------------ */
    /* Profil et statistiques                                              */
    /* ------------------------------------------------------------------ */

    /** Change le role declare et la couleur d'accent. */
    public synchronized void updateProfile(MiniCubeProfile.Role role, String color) {
        if (role != null) {
            profile.setRole(role);
        }
        // La couleur est reinjectee dans une page web : n'accepter qu'un code
        // hexadecimal evite qu'une valeur arbitraire ne s'y retrouve.
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
            profile.setColor(color);
        }
        save();
    }

    /**
     * Enregistre une partie lancee.
     *
     * @param versionId version utilisee, telle qu'affichee dans le launcher
     */
    public synchronized void recordLaunch(String versionId) {
        profile.setLaunchCount(profile.getLaunchCount() + 1);
        profile.setLastVersion(versionId == null ? "" : versionId);
        profile.setLastSeenAt(STAMP.format(LocalDateTime.now()));

        // Les cinq dernieres versions distinctes, la plus recente en tete.
        List<String> recent = profile.getRecentVersions();
        recent.remove(versionId);
        recent.add(0, versionId);
        while (recent.size() > 5) {
            recent.remove(recent.size() - 1);
        }
        save();
    }

    /** Ajoute une duree de jeu, arrondie a la minute. */
    public synchronized void recordPlayTime(long millis) {
        long minutes = Math.max(0, millis / 60_000);
        if (minutes == 0) {
            return;
        }
        profile.setTotalPlayMinutes(profile.getTotalPlayMinutes() + minutes);
        save();
    }

    /** Abonne un composant aux changements de compte ou de session. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    /* ------------------------------------------------------------------ */
    /* Derivation du mot de passe                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Derive une empreinte a partir du mot de passe et du sel.
     *
     * <p>PBKDF2 est volontairement lent : les 210 000 iterations rendent une attaque par
     * dictionnaire couteuse, alors qu'une seule verification reste imperceptible.</p>
     */
    private String derive(String password, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS,
                    KEY_LENGTH_BITS);
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Hashing.toHex(factory.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Derivation du mot de passe impossible", e);
        }
    }

    /**
     * Compare deux empreintes sans divulguer ou elles different.
     *
     * <p>Une comparaison ordinaire s'interrompt au premier caractere distinct : le temps
     * de reponse renseigne alors, un peu, sur l'empreinte attendue. Ici toute la chaine
     * est parcourue quoi qu'il arrive.</p>
     */
    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < left.length(); i++) {
            difference |= left.charAt(i) ^ right.charAt(i);
        }
        return difference == 0;
    }

    private byte[] fromHex(String hex) {
        int length = hex.length() / 2;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
