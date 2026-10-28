package com.example.spotifylyrics;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Kern des Soundboards. Enthält absichtlich KEINE Klassen von Simple Voice Chat,
 * damit die Mod auch ohne Voice-Chat-Mod startet. Die Anbindung steckt in SoundboardPlugin.
 */
public final class Soundboard {
    /** Simple Voice Chat arbeitet mit 48 kHz, 16 Bit, Mono. */
    public static final int SAMPLE_RATE = 48000;

    private static final String[] BUNDLED = {
        "airhorn.mp3", "sirene.mp3", "ding.mp3", "trauerposaune.mp3", "trommelwirbel.mp3"
    };

    /** Rohdaten aus dem MP3-Decoder (Samples hintereinander, bei Stereo abwechselnd links/rechts). */
    public record Pcm(short[] data, float sampleRate, int channels) {}

    public interface Loader {
        Pcm load(Path file) throws Exception;
    }

    /** Wird von SoundboardPlugin gesetzt, sobald Simple Voice Chat die Mod lädt. */
    public static volatile Loader loader;
    /** Lautstärke des Sounds, der gerade läuft (wird pro Sound aus der Config geholt). */
    public static volatile float volume = 1.0f;
    /** Welche Datei gerade spielt (für die Hervorhebung im Menü). */
    public static volatile Path nowPlaying;
    /** Wird von /soundboard gesetzt; der Tick öffnet dann das Menü. */
    public static volatile boolean openRequested = false;

    /** Wanduhr-Zeitpunkt (nanoTime), an dem der Sound zu laufen begann; 0 = noch nicht gestartet. */
    private static volatile long startNanos = 0;
    private static volatile long durationMs = 0;

    private static final Object LOCK = new Object();
    private static short[] current;
    private static int pos;
    private static volatile int frameSize = 960; // 20 ms bei 48 kHz, wird vom Voice Chat bestätigt
    private static volatile int localToken = 0;  // macht laufende lokale Wiedergabe ungültig
    private static final Map<String, short[]> CACHE = new ConcurrentHashMap<>();
    private static boolean keyWasDown;

    private static final ScheduledExecutorService SAVER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "soundboard-save");
        t.setDaemon(true);
        return t;
    });
    private static ScheduledFuture<?> pendingSave;

    private Soundboard() {}

    // ---------- Ordner ----------

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("soundboard");
    }

    /** Legt den Ordner an und kopiert beim allerersten Mal die mitgelieferten Sounds hinein. */
    public static void prepareFolder() {
        try {
            Path dir = folder();
            boolean first = !Files.exists(dir);
            Files.createDirectories(dir);
            if (!first) return;
            for (String name : BUNDLED) {
                try (InputStream in = Soundboard.class.getResourceAsStream("/assets/spotifylyrics/sounds/" + name)) {
                    if (in != null) Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Soundboard-Ordner konnte nicht vorbereitet werden", e);
        }
    }

    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
             .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
             .forEach(out::add);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Soundboard-Ordner nicht lesbar", e);
        }
        return out;
    }

    public static void openFolder() {
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("explorer.exe", dir.toString());
            else if (os.contains("mac")) pb = new ProcessBuilder("open", dir.toString());
            else pb = new ProcessBuilder("xdg-open", dir.toString());
            pb.start();
        } catch (Exception e) {
            SpotifyLyricsClient.chat(Text.literal("Ordner konnte nicht geöffnet werden. Er liegt hier: " + folder().toAbsolutePath()));
        }
    }

    // ---------- Lautstärke pro Sound ----------

    /** Auf 5-%-Schritte runden, 0 bis 200 %. */
    public static float snap(double v) {
        return Math.max(0f, Math.min(2f, Math.round(v * 20.0) / 20f));
    }

    public static float getVolume(Path file) {
        Map<String, Float> m = Config.INSTANCE.soundVolumes;
        Float v = m == null ? null : m.get(file.getFileName().toString());
        return v == null ? 1.0f : v;
    }

    public static void setVolume(Path file, float v) {
        if (Config.INSTANCE.soundVolumes == null) Config.INSTANCE.soundVolumes = new HashMap<>();
        Config.INSTANCE.soundVolumes.put(file.getFileName().toString(), v);
        if (file.equals(nowPlaying)) volume = v; // wirkt sofort beim laufenden Sound
        scheduleSave();
    }

    public static boolean isListenSelf() {
        return Config.INSTANCE.listenSelf;
    }

    public static void setListenSelf(boolean on) {
        Config.INSTANCE.listenSelf = on;
        if (!on) localToken++; // laufende lokale Wiedergabe beenden
        scheduleSave();
    }

    /** Speichert verzögert, damit beim Ziehen am Regler nicht dauernd geschrieben wird. */
    private static synchronized void scheduleSave() {
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = SAVER.schedule(() -> Config.INSTANCE.save(), 500, TimeUnit.MILLISECONDS);
    }

    // ---------- Abspielen ----------

    public static void play(Path file) {
        Loader l = loader;
        if (l == null) {
            SpotifyLyricsClient.chat(Text.literal("Soundboard: Simple Voice Chat wurde nicht gefunden (Mod im mods-Ordner?)."));
            return;
        }
        nowPlaying = file;
        startNanos = 0;
        durationMs = 0;
        volume = getVolume(file);
        final int token = ++localToken;
        SoundLyrics.clear();
        Thread t = new Thread(() -> {
            try {
                String key = file + ":" + Files.size(file);
                short[] pcm = CACHE.get(key);
                if (pcm == null) {
                    pcm = toMono48k(l.load(file));
                    if (pcm.length <= SAMPLE_RATE * 30) CACHE.put(key, pcm); // kurze Sounds merken
                }
                if (token != localToken) return; // inzwischen wurde etwas anderes gestartet oder gestoppt
                synchronized (LOCK) {
                    current = pcm;
                    pos = 0;
                }
                durationMs = pcm.length * 1000L / SAMPLE_RATE;
                startNanos = System.nanoTime();
                SoundLyrics.request(file, durationMs, token);
                if (Config.INSTANCE.listenSelf) startLocal(pcm, token);
            } catch (Throwable e) {
                SpotifyLyricsClient.LOGGER.warn("Sound konnte nicht geladen werden: " + file, e);
                SpotifyLyricsClient.chat(Text.literal("Soundboard: Konnte " + file.getFileName() + " nicht laden."));
                if (file.equals(nowPlaying)) nowPlaying = null;
            }
        }, "soundboard-load");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        localToken++;
        nowPlaying = null;
        startNanos = 0;
        SoundLyrics.clear();
        synchronized (LOCK) {
            current = null;
        }
    }

    /**
     * Läuft gerade ein Sound? Zählt die echte Uhr (nicht den Voice Chat), damit Icon und Lyrics
     * auch dann stimmen, wenn der Voice Chat gerade keine Stücke abholt.
     */
    public static boolean isActive() {
        if (nowPlaying == null) return false;
        long st = startNanos;
        if (st == 0) return true; // wird noch geladen
        if ((System.nanoTime() - st) / 1_000_000L > durationMs + 300) {
            nowPlaying = null;
            return false;
        }
        return true;
    }

    /** Position im laufenden Sound in ms, oder -1 wenn nichts läuft / noch lädt. */
    public static long positionMs() {
        if (!isActive()) return -1;
        long st = startNanos;
        return st == 0 ? -1 : (System.nanoTime() - st) / 1_000_000L;
    }

    public static boolean isPlaying() {
        synchronized (LOCK) {
            return current != null;
        }
    }

    /** Spielt den Sound zusätzlich bei dir selbst ab (reines Java, unabhängig vom Voice Chat). */
    private static void startLocal(short[] pcm, int token) {
        Thread t = new Thread(() -> {
            SourceDataLine line = null;
            try {
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, 19200);
                line.start();
                byte[] buf = new byte[1920];
                int p = 0;
                while (p < pcm.length && token == localToken) {
                    int n = Math.min(960, pcm.length - p);
                    float v = volume;
                    for (int i = 0; i < n; i++) {
                        int s = Math.max(-32768, Math.min(32767, Math.round(pcm[p + i] * v)));
                        buf[2 * i] = (byte) s;
                        buf[2 * i + 1] = (byte) (s >> 8);
                    }
                    line.write(buf, 0, n * 2);
                    p += n;
                }
                if (token == localToken) line.drain(); else line.flush();
            } catch (Throwable e) {
                SpotifyLyricsClient.LOGGER.debug("Lokale Wiedergabe nicht möglich", e);
            } finally {
                if (line != null) line.close();
            }
        }, "soundboard-local");
        t.setDaemon(true);
        t.start();
    }

    public static void noteFrameSize(int n) {
        if (n > 0) frameSize = n;
    }

    /** Wird vom Voice-Chat-Thread für jedes 20-ms-Stück gerufen. Gibt null zurück, wenn nichts läuft. */
    public static short[] nextFrame() {
        synchronized (LOCK) {
            if (current == null) return null;
            int n = frameSize;
            short[] out = new short[n];
            int copy = Math.min(n, current.length - pos);
            float v = volume;
            for (int i = 0; i < copy; i++) {
                int s = Math.round(current[pos + i] * v);
                out[i] = (short) Math.max(-32768, Math.min(32767, s));
            }
            pos += copy;
            if (pos >= current.length) {
                current = null;
                nowPlaying = null;
            }
            return out;
        }
    }

    // ---------- Umrechnung ----------

    /** Beliebiges PCM -> Mono, 48 kHz (lineare Interpolation). */
    static short[] toMono48k(Pcm p) {
        int ch = Math.max(1, p.channels());
        float rate = p.sampleRate() > 0 ? p.sampleRate() : 44100f;
        short[] d = p.data();
        int frames = d.length / ch;
        if (frames == 0) return new short[0];
        double ratio = rate / (double) SAMPLE_RATE;
        int outLen = (int) (frames / ratio);
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double src = i * ratio;
            int i0 = (int) src;
            int i1 = Math.min(i0 + 1, frames - 1);
            double frac = src - i0;
            double a = monoAt(d, i0, ch);
            double b = monoAt(d, i1, ch);
            long v = Math.round(a + (b - a) * frac);
            out[i] = (short) Math.max(-32768, Math.min(32767, v));
        }
        return out;
    }

    private static double monoAt(short[] d, int frame, int ch) {
        double sum = 0;
        for (int c = 0; c < ch; c++) sum += d[frame * ch + c];
        return sum / ch;
    }

    // ---------- Taste K / Befehl ----------

    /** Wird jeden Client-Tick gerufen. */
    public static void tick(MinecraftClient mc) {
        boolean open = false;
        long window = GLFW.glfwGetCurrentContext();
        if (window != 0) {
            boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
            if (down && !keyWasDown && mc.currentScreen == null && mc.player != null) open = true;
            keyWasDown = down;
        }
        if (openRequested && mc.currentScreen == null) {
            openRequested = false;
            open = true;
        }
        if (open) mc.setScreen(new SoundboardScreen(0));
    }
}
