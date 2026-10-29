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
    /** Soundboard: Lautstärke pro Sound (Dateiname -> Faktor, 1.0 = 100 %). */
    public java.util.Map<String, Float> soundVolumes = new java.util.HashMap<>();
    /** Soundboard: Sound auch selbst hören. */
    public boolean listenSelf = true;

    /** Lyrics per Spracherkennung heraushören, wenn lrclib nichts findet. */
    public boolean transcribe = true;
    /** Voice Changer: gewählter Effekt (0 = aus) und ob du dich selbst hörst. */
    public int voicePreset = 0;
    public boolean voiceSelfHear = true;
    /** Spotify (PC-Ton) in den Voice Chat mischen, Aufnahmegerät ("" = automatisch) und Lautstärke. */
    public boolean spotifyShare = false;
    public String spotifyDevice = "";
    public float spotifyVolume = 1.0f;
    /** Lichtshow am Bildschirmrand und Farbmodus. */
    public boolean lightshow = true;
    public int lightMode = 0;
    /** Noten-Symbol: Abstand von links und von unten (in GUI-Pixeln). */
    public int noteX = 20;
    public int noteY = 3;

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
