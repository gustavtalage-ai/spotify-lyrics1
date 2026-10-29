package com.example.spotifylyrics;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Lyrics für Soundboard-Sounds. Der Dateiname muss "Künstler - Titel.mp3" lauten.
 * Ohne " - " im Namen wird nichts gesucht (so bekommen Effekte wie "airhorn" keine falschen Lyrics).
 */
public final class SoundLyrics {
    public static volatile String key = "";
    public static volatile String header = "";
    public static volatile List<Lyrics.Line> lines = List.of();
    public static volatile String status = "";

    private SoundLyrics() {}

    /** Gibt {Künstler, Titel} zurück oder null, wenn der Name nicht passt. */
    static String[] parseName(Path file) {
        String n = file.getFileName().toString();
        if (n.toLowerCase(Locale.ROOT).endsWith(".mp3")) n = n.substring(0, n.length() - 4);
        int i = n.indexOf(" - ");
        int len = 3;
        int j = n.indexOf(" – ");
        if (i < 0 || (j >= 0 && j < i)) { i = j; len = 3; }
        if (i <= 0) return null;
        String artist = n.substring(0, i).trim();
        String title = n.substring(i + len).trim();
        if (artist.isEmpty() || title.isEmpty()) return null;
        return new String[]{artist, title};
    }

    public static void clear() {
        key = "";
        header = "";
        lines = List.of();
        status = "";
    }

    static void request(Path file, long durationMs, int token) {
        String[] at = parseName(file);
        if (at == null) {
            clear();
            SpotifyLyricsClient.chat(net.minecraft.text.Text.literal("Soundboard: Keine Lyrics gesucht. Die Datei muss 'Künstler - Titel.mp3' heißen."));
            return;
        }
        final String k = file + "#" + token;
        key = k;
        header = at[0] + " – " + at[1];
        lines = List.of();
        status = "Lade Lyrics …";
        SpotifyApi.Track t = new SpotifyApi.Track(k, at[1], at[0], "", durationMs);
        Thread th = new Thread(() -> {
            List<Lyrics.Line> res = Lyrics.fetch(t);
            if (k.equals(key)) {
                SpotifyLyricsClient.chat(net.minecraft.text.Text.literal(res.isEmpty()
                    ? "Soundboard: Keine Lyrics gefunden für " + at[0] + " – " + at[1]
                    : "Soundboard: Lyrics geladen (" + res.size() + " Zeilen)"));
                lines = res;
                status = res.isEmpty() ? "Keine Lyrics gefunden" : "";
            }
        }, "soundboard-lyrics");
        th.setDaemon(true);
        th.start();
    }
}
