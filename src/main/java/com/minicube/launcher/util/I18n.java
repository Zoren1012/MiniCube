package com.minicube.launcher.util;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Internationalisation du launcher.
 *
 * <p>Les traductions vivent dans {@code src/main/resources/i18n/messages_xx.properties}.
 * Ajouter une langue revient a deposer un fichier et a l'enregistrer dans
 * {@link #SUPPORTED}. Les fichiers sont lus en UTF-8 (comportement natif depuis Java 9).</p>
 */
public final class I18n {

    /** Langues proposees dans les parametres : code -> libelle affiche. */
    public static final Map<String, String> SUPPORTED = new LinkedHashMap<>();

    static {
        SUPPORTED.put("fr", "Francais");
        SUPPORTED.put("en", "English");
    }

    private static final String BUNDLE_BASE = "i18n.messages";
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static ResourceBundle bundle;
    private static String currentLanguage = "fr";

    private I18n() {
    }

    /**
     * Charge le catalogue de la langue demandee.
     *
     * @param language code ISO 639-1 ; retombe sur le francais si la langue est inconnue
     */
    public static void setLanguage(String language) {
        String code = (language == null || !SUPPORTED.containsKey(language)) ? "fr" : language;
        try {
            bundle = ResourceBundle.getBundle(BUNDLE_BASE, Locale.of(code));
            currentLanguage = code;
        } catch (MissingResourceException e) {
            Log.warn("Catalogue de traduction introuvable pour " + code + ", repli sur le francais");
            bundle = ResourceBundle.getBundle(BUNDLE_BASE, Locale.of("fr"));
            currentLanguage = "fr";
        }
        LISTENERS.forEach(Runnable::run);
    }

    /** Langue detectee sur le systeme si elle est supportee, sinon francais. */
    public static String systemLanguage() {
        String system = Locale.getDefault().getLanguage();
        return SUPPORTED.containsKey(system) ? system : "fr";
    }

    public static String currentLanguage() {
        return currentLanguage;
    }

    /**
     * Traduit une cle. La cle elle-meme est renvoyee entre chevrons si la traduction
     * manque, ce qui rend les oublis immediatement visibles a l'ecran.
     *
     * @param key  cle du catalogue
     * @param args parametres substitues aux emplacements {0}, {1}, ...
     */
    public static String tr(String key, Object... args) {
        if (bundle == null) {
            setLanguage(systemLanguage());
        }
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "<" + key + ">";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    /** Enregistre un rappel invoque a chaque changement de langue. */
    public static void addChangeListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeChangeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }
}
