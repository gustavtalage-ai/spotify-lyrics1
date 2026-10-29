package com.example.spotifylyrics;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Greift den Ton ab, den dein PC gerade abspielt (z. B. Spotify, über "Stereo Mix" oder VB-Cable),
 * und mischt ihn live in den Voice Chat. Enthält absichtlich KEINE Klassen von Simple Voice Chat.
 */
public final class SpotifyShare {
    private static final int RATE = 48000;
    private static final int MAX_BUFFER = 9600;   // höchstens 200 ms Verzögerung
    private static final Object LOCK = new Object();
    private static final short[] RING = new short[RATE];
    private static int head, size;

    private static volatile Thread thread;
    private static volatile boolean stopRequested;
    private static volatile String status = "Aus";
    private static volatile int frameSize = 960;
    private static volatile long retryAfter = 0;

    private SpotifyShare() {}

    public static String status() {
        return Config.INSTANCE.spotifyShare ? status : "Aus";
    }

    public static boolean isEnabled() {
        return Config.INSTANCE.spotifyShare;
    }

    public static void setEnabled(boolean on) {
        Config.INSTANCE.spotifyShare = on;
        Config.INSTANCE.save();
        if (on) {
            retryAfter = 0;
            ensureRunning();
        } else {
            stop();
        }
    }

    public static String getDevice() {
        return Config.INSTANCE.spotifyDevice == null ? "" : Config.INSTANCE.spotifyDevice;
    }

    public static void setDevice(String name) {
        Config.INSTANCE.spotifyDevice = name == null ? "" : name;
        Config.INSTANCE.save();
        stop();
        retryAfter = 0;
        if (Config.INSTANCE.spotifyShare) ensureRunning();
    }

    public static float getVolume() {
        return Math.max(0f, Math.min(2f, Config.INSTANCE.spotifyVolume));
    }

    public static void setVolume(float v) {
        Config.INSTANCE.spotifyVolume = Math.max(0f, Math.min(2f, Math.round(v * 20f) / 20f));
        Config.INSTANCE.save();
    }

    public static void noteFrameSize(int n) {
        if (n > 0) frameSize = n;
    }

    /** Alle Geräte, von denen man aufnehmen kann (ohne das normale Mikrofon-Standardgerät). */
    public static List<String> devices() {
        List<String> out = new ArrayList<>();
        try {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                String n = mi.getName();
                if (n == null || n.toLowerCase(Locale.ROOT).startsWith("primary")) continue;
                if (supports(mi, 48000f) || supports(mi, 44100f)) {
                    if (!out.contains(n)) out.add(n);
                }
            }
        } catch (Throwable ignored) {
            // keine Geräte lesbar
        }
        return out;
    }

    private static boolean supports(Mixer.Info mi, float rate) {
        try {
            Mixer m = AudioSystem.getMixer(mi);
            return m.isLineSupported(new DataLine.Info(TargetDataLine.class, new AudioFormat(rate, 16, 2, true, false)));
        } catch (Throwable e) {
            return false;
        }
    }

    private static Mixer.Info pickDevice() {
        String wanted = getDevice();
        Mixer.Info best = null;
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            String n = mi.getName() == null ? "" : mi.getName();
            String low = n.toLowerCase(Locale.ROOT);
            if (low.startsWith("primary")) continue;
            if (!(supports(mi, 48000f) || supports(mi, 44100f))) continue;
            if (!wanted.isEmpty()) {
                if (n.equals(wanted)) return mi;
                continue;
            }
            if (low.contains("stereo mix") || low.contains("stereomix") || low.contains("cable output")
                || low.contains("loopback") || low.contains("what u hear") || low.contains("wave out")
                || low.contains("voicemeeter") || low.contains("virtual")) {
                best = mi;
                break;
            }
        }
        return best;
    }

    /** Wird vom Voice-Chat-Thread für jedes 20-ms-Stück gerufen. Gibt null zurück, wenn nichts da ist. */
    public static short[] nextFrame() {
        if (!Config.INSTANCE.spotifyShare) return null;
        ensureRunning();
        int n = frameSize;
        float vol = getVolume();
        synchronized (LOCK) {
            if (size < n) return null;
            short[] out = new short[n];
            for (int i = 0; i < n; i++) {
                int s = Math.round(RING[(head + i) % RING.length] * vol);
                out[i] = (short) Math.max(-32768, Math.min(32767, s));
            }
            head = (head + n) % RING.length;
            size -= n;
            return out;
        }
    }

    /** Zwei Stücke zusammenmischen (null = nichts). */
    public static short[] mix(short[] a, short[] b) {
        if (a == null) return b;
        if (b == null) return a;
        int n = Math.min(a.length, b.length);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            out[i] = (short) Math.max(-32768, Math.min(32767, a[i] + b[i]));
        }
        return out;
    }

    private static void write(short[] mono, int len) {
        synchronized (LOCK) {
            for (int i = 0; i < len; i++) {
                RING[(head + size) % RING.length] = mono[i];
                if (size < RING.length) size++; else head = (head + 1) % RING.length;
            }
            if (size > MAX_BUFFER) { // zu viel angestaut: alte Daten verwerfen
                int drop = size - MAX_BUFFER / 2;
                head = (head + drop) % RING.length;
                size -= drop;
            }
        }
    }

    private static synchronized void ensureRunning() {
        Thread t = thread;
        if (t != null && t.isAlive()) return;
        if (System.currentTimeMillis() < retryAfter) return;
        stopRequested = false;
        status = "Suche Aufnahmegerät …";
        Thread nt = new Thread(SpotifyShare::captureLoop, "spotifyshare-capture");
        nt.setDaemon(true);
        thread = nt;
        nt.start();
    }

    private static void stop() {
        stopRequested = true;
        Thread t = thread;
        if (t != null) t.interrupt();
        synchronized (LOCK) {
            head = 0;
            size = 0;
        }
        status = "Aus";
    }

    private static void captureLoop() {
        TargetDataLine line = null;
        try {
            Mixer.Info mi = pickDevice();
            if (mi == null) {
                status = getDevice().isEmpty()
                    ? "Kein PC-Ton-Gerät gefunden (Stereo Mix oder VB-Cable nötig)"
                    : "Gerät nicht gefunden: " + getDevice();
                retryAfter = System.currentTimeMillis() + 5000;
                return;
            }
            float rate = supports(mi, 48000f) ? 48000f : 44100f;
            AudioFormat fmt = new AudioFormat(rate, 16, 2, true, false);
            Mixer m = AudioSystem.getMixer(mi);
            line = (TargetDataLine) m.getLine(new DataLine.Info(TargetDataLine.class, fmt));
            line.open(fmt, 16384);
            line.start();
            status = "Läuft: " + mi.getName();
            byte[] buf = new byte[3840];
            short[] mono = new short[2400];
            while (!stopRequested && !Thread.currentThread().isInterrupted()) {
                int r = line.read(buf, 0, buf.length);
                if (r <= 0) continue;
                int frames = r / 4;
                if (rate == 48000f) {
                    for (int i = 0; i < frames; i++) {
                        int l = (short) ((buf[4 * i] & 0xFF) | (buf[4 * i + 1] << 8));
                        int rr = (short) ((buf[4 * i + 2] & 0xFF) | (buf[4 * i + 3] << 8));
                        mono[i] = (short) ((l + rr) / 2);
                    }
                    write(mono, frames);
                } else {
                    int outN = Math.min(mono.length, (int) (frames * 48000L / 44100L));
                    for (int i = 0; i < outN; i++) {
                        double src = i * 44100.0 / 48000.0;
                        int i0 = Math.min(frames - 1, (int) src);
                        int i1 = Math.min(frames - 1, i0 + 1);
                        double fr = src - i0;
                        double a = ((short) ((buf[4 * i0] & 0xFF) | (buf[4 * i0 + 1] << 8)) + (short) ((buf[4 * i0 + 2] & 0xFF) | (buf[4 * i0 + 3] << 8))) / 2.0;
                        double b = ((short) ((buf[4 * i1] & 0xFF) | (buf[4 * i1 + 1] << 8)) + (short) ((buf[4 * i1 + 2] & 0xFF) | (buf[4 * i1 + 3] << 8))) / 2.0;
                        mono[i] = (short) Math.round(a * (1 - fr) + b * fr);
                    }
                    write(mono, outN);
                }
            }
        } catch (Throwable e) {
            if (!stopRequested) {
                SpotifyLyricsClient.LOGGER.warn("PC-Ton aufnehmen fehlgeschlagen", e);
                status = "Fehler beim Aufnehmen";
                retryAfter = System.currentTimeMillis() + 5000;
            }
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.close();
                } catch (Throwable ignored) {
                    // nichts zu tun
                }
            }
        }
    }
}
