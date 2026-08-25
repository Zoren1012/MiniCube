package com.minicube.launcher.service;

import com.minicube.launcher.model.NewsItem;
import com.minicube.launcher.util.Http;
import com.minicube.launcher.util.Json;
import com.minicube.launcher.util.Log;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Actualites affichees sur l'onglet Accueil.
 *
 * <p>Source par defaut : le fichier embarque {@code config/news.json}. En renseignant
 * {@code newsUrl} dans la configuration, les actualites sont recuperees en ligne, ce qui
 * permet de communiquer avec les joueurs sans redistribuer le launcher.</p>
 */
public class NewsService {

    private static final String BUNDLED_RESOURCE = "/config/news.json";

    private final ConfigService config;

    public NewsService(ConfigService config) {
        this.config = config;
    }

    /**
     * Charge les actualites.
     *
     * <p>Methode bloquante si une URL est configuree : a appeler depuis un thread de
     * fond. Un echec reseau retombe silencieusement sur les actualites embarquees.</p>
     */
    public List<NewsItem> load() {
        String url = config.settings().getNewsUrl();
        if (!url.isBlank()) {
            try {
                JsonObject payload = Http.getJson(url);
                List<NewsItem> items = parse(payload);
                if (!items.isEmpty()) {
                    Log.info(items.size() + " actualite(s) chargee(s) depuis " + url);
                    return items;
                }
            } catch (IOException e) {
                Log.warn("Actualites distantes indisponibles : " + e.getMessage());
            } catch (Exception e) {
                Log.warn("Actualites distantes invalides : " + e.getMessage());
            }
        }
        return loadBundled();
    }

    private List<NewsItem> loadBundled() {
        try (InputStream in = getClass().getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                return List.of();
            }
            JsonObject payload = Json.GSON.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            return parse(payload);
        } catch (Exception e) {
            Log.warn("Actualites embarquees illisibles : " + e.getMessage());
            return List.of();
        }
    }

    /** Convertit le document JSON en liste d'actualites, en tolerant les champs absents. */
    private List<NewsItem> parse(JsonObject payload) {
        List<NewsItem> items = new ArrayList<>();
        for (JsonElement element : Json.array(payload, "news")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            items.add(new NewsItem(
                    Json.string(entry, "title", "Sans titre"),
                    Json.string(entry, "content", ""),
                    Json.string(entry, "date", ""),
                    Json.string(entry, "category", "Info"),
                    Json.string(entry, "imageUrl", ""),
                    Json.string(entry, "link", "")));
        }
        return items;
    }
}
