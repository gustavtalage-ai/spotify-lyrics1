package com.example.spotifylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen, gespeichert in .minecraft/config/spotifylyrics.json */
public class Config {
    public String clientId = "";
    public String refreshToken = "";
    public boolean enabled = true;
    /** Kommt dieses Wort in der Zeile vor, wird sie lila umrandet. */
    public String keyword = "musik";
    /** Feinabstimmung: positiv = Lyrics kommen früher. */
    public int offsetMs = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spotifylyrics.json");
    public static final Config INSTANCE = load();

    private static Config load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Config konnte nicht gelesen werden", e);
        }
        return new Config();
    }

    public synchronized void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Config konnte nicht gespeichert werden", e);
        }
    }
}
