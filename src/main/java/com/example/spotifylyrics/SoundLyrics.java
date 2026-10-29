package com.example.spotifylyrics;

import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lyrics für Soundboard-Sounds.
 * 1. Zwischenspeicher, 2. lrclib (Dateiname "Künstler - Titel.mp3"), 3. aus dem Song heraushören (Whisper).
 */
public final class SoundLyrics {
    public static volatile String key = "";
    public static volatile String header = "";
    public static volatile List<Lyrics.Line> lines = List.of();
    public static volatile String status = "";

    private static final long MIN_TRANSCRIBE_MS = 20_000; // kürzere Sounds sind Effekte, keine Songs
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "soundboard-lyrics");
        t.setDaemon(true);
        return t;
    });

    private SoundLyrics() {}

    /** Gibt {Künstler, Titel} zurück oder null, wenn der Name nicht passt. */
    static String[] parseName(Path file) {
        String n = file.getFileName().toString();
        if (n.toLowerCase(Locale.ROOT).endsWith(".mp3")) n = n.substring(0, n.length() - 4);
        int i = n.indexOf(" - ");
        int j = n.indexOf(" – ");
        if (i < 0 || (j >= 0 && j < i)) i = j;
        if (i <= 0) return null;
        String artist = n.substring(0, i).trim();
        String title = n.substring(i + 3).trim();
        if (artist.isEmpty() || title.isEmpty()) return null;
        return new String[]{artist, title};
    }

    private static String baseName(Path file) {
        String n = file.getFileName().toString();
        return n.toLowerCase(Locale.ROOT).endsWith(".mp3") ? n.substring(0, n.length() - 4) : n;
    }

    public static void clear() {
        key = "";
        header = "";
        lines = List.of();
        status = "";
    }

    static void request(Path file, long durationMs, int token, short[] pcm) {
        String[] at = parseName(file);
        boolean song = durationMs >= MIN_TRANSCRIBE_MS;
        if (at == null && !(song && Config.INSTANCE.transcribe)) {
            clear();
            if (song) {
                SpotifyLyricsClient.chat(Text.literal("Soundboard: Keine Lyrics gesucht. Die Datei muss 'Künstler - Titel.mp3' heißen."));
            }
            return;
        }
        final String k = file + "#" + token;
        key = k;
        header = at != null ? at[0] + " – " + at[1] : baseName(file);
        lines = List.of();
        status = "Lade Lyrics …";
        WORKER.execute(() -> work(file, at, durationMs, k, song, pcm));
    }

    private static void work(Path file, String[] at, long durationMs, String k, boolean song, short[] pcm) {
        try {
            List<Lyrics.Line> res = Transcriber.loadCached(file);
            if (res.isEmpty() && at != null) {
                SpotifyApi.Track t = new SpotifyApi.Track(k, at[1], at[0], "", durationMs);
                res = Lyrics.fetch(t);
            }
            if (res.isEmpty() && song && Config.INSTANCE.transcribe) {
                if (!k.equals(key)) return; // inzwischen anderer Song: Minuten sparen
                SpotifyLyricsClient.chat(Text.literal("Soundboard: Keine fertigen Lyrics gefunden, ich höre sie aus dem Song heraus. Das kann einige Minuten dauern."));
                res = Transcriber.transcribe(pcm, s -> {
                    if (k.equals(key)) status = s;
                });
                if (!res.isEmpty()) Transcriber.save(file, res);
            }
            if (k.equals(key)) {
                lines = res;
                status = res.isEmpty() ? "Keine Lyrics gefunden" : "";
                SpotifyLyricsClient.chat(Text.literal(res.isEmpty()
                    ? "Soundboard: Keine Lyrics gefunden für " + header
                    : "Soundboard: Lyrics geladen (" + res.size() + " Zeilen)"));
            }
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.warn("Lyrics fehlgeschlagen", e);
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            if (k.equals(key)) {
                status = "Keine Lyrics gefunden";
                SpotifyLyricsClient.chat(Text.literal("Soundboard: Heraushören fehlgeschlagen: " + root));
            }
        }
    }
}
