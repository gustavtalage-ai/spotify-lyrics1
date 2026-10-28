package com.example.spotifylyrics;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Kern des Soundboards. Enthaelt absichtlich KEINE Klassen von Simple Voice Chat,
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

    /** Wird von SoundboardPlugin gesetzt, sobald Simple Voice Chat die Mod laedt. */
    public static volatile Loader loader;
    public static volatile float volume = 1.0f;
    /** Wird von /soundboard gesetzt; der Tick oeffnet dann das Menue. */
    public static volatile boolean openRequested = false;

    private static final Object LOCK = new Object();
    private static short[] current;
    private static int pos;
    private static volatile int frameSize = 960; // 20 ms bei 48 kHz, wird vom Voice Chat bestaetigt
    private static final Map<String, short[]> CACHE = new ConcurrentHashMap<>();
    private static boolean keyWasDown;

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
            SpotifyLyricsClient.chat(Text.literal("Ordner konnte nicht geoeffnet werden. Er liegt hier: " + folder().toAbsolutePath()));
        }
    }

    // ---------- Abspielen ----------

    public static void play(Path file) {
        Loader l = loader;
        if (l == null) {
            SpotifyLyricsClient.chat(Text.literal("Soundboard: Simple Voice Chat wurde nicht gefunden (Mod im mods-Ordner?)."));
            return;
        }
        Thread t = new Thread(() -> {
            try {
                String key = file + ":" + Files.size(file);
                short[] pcm = CACHE.get(key);
                if (pcm == null) {
                    pcm = toMono48k(l.load(file));
                    if (pcm.length <= SAMPLE_RATE * 30) CACHE.put(key, pcm); // kurze Sounds merken
                }
                synchronized (LOCK) {
                    current = pcm;
                    pos = 0;
                }
            } catch (Throwable e) {
                SpotifyLyricsClient.LOGGER.warn("Sound konnte nicht geladen werden: " + file, e);
                SpotifyLyricsClient.chat(Text.literal("Soundboard: Konnte " + file.getFileName() + " nicht laden."));
            }
        }, "soundboard-load");
        t.setDaemon(true);
        t.start();
    }

    public static void stop() {
        synchronized (LOCK) {
            current = null;
        }
    }

    public static boolean isPlaying() {
        synchronized (LOCK) {
            return current != null;
        }
    }

    public static void noteFrameSize(int n) {
        if (n > 0) frameSize = n;
    }

    /** Wird vom Voice-Chat-Thread fuer jedes 20-ms-Stueck gerufen. Gibt null zurueck, wenn nichts laeuft. */
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
            if (pos >= current.length) current = null;
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
