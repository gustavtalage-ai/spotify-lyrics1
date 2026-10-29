package com.example.spotifylyrics;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Hört Lyrics aus dem Song heraus (Spracherkennung "Whisper", läuft auf deinem PC).
 * Die Bibliothek (whisper-jni) und das Sprachmodell werden beim ersten Mal heruntergeladen.
 * Alles läuft über Reflection, damit die Mod auch ohne diese Bibliothek baut und startet.
 */
final class Transcriber {
    static final String JAR_URL = System.getProperty("spotifylyrics.whisperJar",
        "https://repo1.maven.org/maven2/io/github/givimad/whisper-jni/1.7.1/whisper-jni-1.7.1.jar");
    static final String MODEL_URL = System.getProperty("spotifylyrics.whisperModel",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin");
    private static final long MIN_JAR = 100_000L;
    private static final long MIN_MODEL = 50_000_000L;

    private static final Object LOCK = new Object();
    private static Object whisper;      // WhisperJNI
    private static ClassLoader loader;
    private static Class<?> whisperCls, paramsCls;

    private Transcriber() {}

    static Path dir() {
        return FabricLoader.getInstance().getGameDir().resolve("soundboard").resolve("whisper");
    }

    /** Hört den Song heraus. Dauert je nach PC ein paar Minuten. */
    static List<Lyrics.Line> transcribe(short[] pcm48k, Consumer<String> status) throws Exception {
        synchronized (LOCK) { // immer nur ein Song gleichzeitig
            Files.createDirectories(dir());
            Path jar = dir().resolve("whisper-jni.jar");
            Path model = dir().resolve("ggml-base.bin");
            if (!ok(jar, MIN_JAR)) {
                status.accept("Lade Spracherkennung herunter …");
                download(JAR_URL, jar, null);
            }
            if (!ok(model, MIN_MODEL)) {
                download(MODEL_URL, model, status);
            }
            status.accept("Höre Lyrics heraus … (kann einige Minuten dauern)");
            return run(jar, model, to16k(pcm48k));
        }
    }

    private static boolean ok(Path p, long min) {
        try {
            return Files.exists(p) && Files.size(p) >= min;
        } catch (Exception e) {
            return false;
        }
    }

    private static void download(String url, Path target, Consumer<String> status) throws Exception {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20)).build();
        HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMinutes(30)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IllegalStateException("Download fehlgeschlagen (" + r.statusCode() + "): " + url);
        long total = r.headers().firstValueAsLong("content-length").orElse(-1);
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = r.body(); var out = Files.newOutputStream(part)) {
            byte[] buf = new byte[1 << 16];
            long done = 0, lastReport = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (status != null && done - lastReport >= 20_000_000L) {
                    lastReport = done;
                    status.accept("Lade Sprachmodell … " + (done / 1_000_000L) + (total > 0 ? " von " + (total / 1_000_000L) : "") + " MB");
                }
            }
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** 48 kHz Mono (short) -> 16 kHz Mono (float), wie Whisper es braucht. */
    static float[] to16k(short[] in) {
        int n = in.length / 3;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            out[i] = (in[3 * i] + in[3 * i + 1] + in[3 * i + 2]) / (3f * 32768f);
        }
        return out;
    }

    // ---------- Aufruf der Bibliothek per Reflection ----------

    private static void ensureLoaded(Path jar) throws Exception {
        if (whisper != null) return;
        URLClassLoader cl = new URLClassLoader(new URL[]{jar.toUri().toURL()}, Transcriber.class.getClassLoader());
        Class<?> w = cl.loadClass("io.github.givimad.whisperjni.WhisperJNI");
        w.getMethod("loadLibrary").invoke(null);
        Object inst = w.getDeclaredConstructor().newInstance();
        loader = cl;
        whisperCls = w;
        paramsCls = cl.loadClass("io.github.givimad.whisperjni.WhisperFullParams");
        whisper = inst;
    }

    private static Method find(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        }
        return null;
    }

    private static void setField(Object o, String name, Object value) {
        try {
            var f = o.getClass().getField(name);
            f.set(o, value);
        } catch (Throwable ignored) {
            // Feld gibt es in dieser Version nicht: ist nicht schlimm
        }
    }

    private static List<Lyrics.Line> run(Path jar, Path model, float[] samples) throws Exception {
        ensureLoaded(jar);
        Method init = find(whisperCls, "init", 1);
        Method full = find(whisperCls, "full", 4);
        Method nSeg = find(whisperCls, "fullNSegments", 1);
        Method segText = find(whisperCls, "fullGetSegmentText", 2);
        Method seg0 = find(whisperCls, "fullGetSegmentTimestamp0", 2);
        if (init == null || full == null || nSeg == null || segText == null) {
            throw new IllegalStateException("Spracherkennungs-Bibliothek passt nicht (andere Version?)");
        }
        Object ctx = init.invoke(whisper, model);
        try {
            Object params = paramsCls.getDeclaredConstructor().newInstance();
            setField(params, "language", "auto");
            setField(params, "translate", false);
            setField(params, "printProgress", false);
            setField(params, "printRealtime", false);
            setField(params, "printTimestamps", false);
            setField(params, "printSpecial", false);
            setField(params, "nThreads", Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2)));
            int code = ((Number) full.invoke(whisper, ctx, params, samples, samples.length)).intValue();
            if (code != 0) throw new IllegalStateException("Spracherkennung fehlgeschlagen (Code " + code + ")");
            int n = ((Number) nSeg.invoke(whisper, ctx)).intValue();
            long totalMs = samples.length * 1000L / 16000L;
            List<Lyrics.Line> out = new ArrayList<>();
            String last = "";
            for (int i = 0; i < n; i++) {
                String text = String.valueOf(segText.invoke(whisper, ctx, i)).trim();
                if (!useful(text) || text.equalsIgnoreCase(last)) continue;
                last = text;
                long t = seg0 != null
                    ? ((Number) seg0.invoke(whisper, ctx, i)).longValue() * 10L          // in 10-ms-Schritten
                    : totalMs * i / Math.max(1, n);                                      // Notlösung: gleichmäßig verteilt
                out.add(new Lyrics.Line(Math.max(0, t), text));
            }
            return out;
        } finally {
            try {
                ctx.getClass().getMethod("close").invoke(ctx);
            } catch (Throwable ignored) {
                // nichts zu tun
            }
        }
    }

    /** Filtert Platzhalter wie [Musik] oder (Applaus) heraus. */
    static boolean useful(String t) {
        if (t.isBlank()) return false;
        String s = t.toLowerCase(Locale.ROOT).trim();
        if ((s.startsWith("[") && s.endsWith("]")) || (s.startsWith("(") && s.endsWith(")"))) return false;
        return s.chars().anyMatch(Character::isLetter);
    }

    // ---------- Zwischenspeicher als LRC-Datei ----------

    static Path cacheFile(Path sound) {
        return Soundboard.folder().resolve("lyrics-cache").resolve(sound.getFileName().toString() + ".lrc");
    }

    static void save(Path sound, List<Lyrics.Line> lines) {
        try {
            Path f = cacheFile(sound);
            Files.createDirectories(f.getParent());
            StringBuilder sb = new StringBuilder();
            for (Lyrics.Line l : lines) {
                long ms = l.timeMs();
                sb.append(String.format(Locale.ROOT, "[%02d:%02d.%02d] %s%n", ms / 60000, (ms / 1000) % 60, (ms % 1000) / 10, l.text()));
            }
            Files.writeString(f, sb.toString());
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Lyrics konnten nicht zwischengespeichert werden", e);
        }
    }

    static List<Lyrics.Line> loadCached(Path sound) {
        try {
            Path f = cacheFile(sound);
            if (Files.exists(f)) return Lyrics.parse(Files.readString(f));
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Lyrics-Zwischenspeicher nicht lesbar", e);
        }
        return List.of();
    }
}
