package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.Random;

/** Lichtshow: Scheinwerfer-Strahlen, die rund um dich in den Himmel leuchten, im Takt der Musik. Der Bildschirm bleibt frei. */
public class Lightshow {
    public static final String[] MODES = {"Regenbogen", "Lila", "Blau", "Feuer"};

    private static final Random RND = new Random();
    private static volatile float level = 0f;       // 0..1, geglättet
    private static volatile long lastAudioMs = 0;    // wann zuletzt echtes Audio ankam
    private static float shown = 0f;
    private static int counter = 0;
    /** Testmodus aus dem Menü: bis zu diesem Zeitpunkt läuft die Lichtshow auch ohne Musik. */
    public static volatile long testUntilMs = 0;
    private static volatile String status = "";

    public static String status() {
        return status;
    }

    /** Echtes Audio (Soundboard), 20-ms-Stücke aus dem Voice-Chat-Plugin. */
    public static void feed(short[] frame) {
        if (frame == null || frame.length == 0) return;
        double sum = 0;
        for (short v : frame) sum += (double) v * v;
        double rms = Math.sqrt(sum / frame.length) / 32768.0;
        float target = (float) Math.min(1.0, rms * 5.0);
        if (target > level) level = target;
        else level = level * 0.85f;
        if (rms > 0.002) lastAudioMs = System.currentTimeMillis();
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float r = 0, g = 0, b = 0;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static int color(int mode, float t, float side) {
        switch (mode) {
            case 1: return hsv(0.76f + 0.05f * (float) Math.sin(t * 2 + side), 0.75f, 1f);
            case 2: return hsv(0.58f + 0.06f * (float) Math.sin(t * 2 + side), 0.8f, 1f);
            case 3: return hsv(0.02f + 0.07f * (0.5f + 0.5f * (float) Math.sin(t * 2 + side)), 0.9f, 1f);
            default: return hsv(t * 0.12f + side * 0.25f, 0.8f, 1f);
        }
    }

    /** Akzentfarbe passend zum gewählten Farbmodus (für den Lyrics-Kasten). */
    static int accent(float t, float side) {
        return color(Config.INSTANCE.lightMode, t, side);
    }

    /** Jeden Client-Tick: setzt Leuchtpunkte entlang von Scheinwerfer-Strahlen in den Himmel. */
    public static void tick(MinecraftClient mc) {
        try {
            Config cfg = Config.INSTANCE;
            long now = System.currentTimeMillis();
            boolean testing = now < testUntilMs;
            if ((!cfg.lightshow && !testing) || mc.world == null || mc.player == null || mc.isPaused()) {
                shown = shown * 0.8f;
                status = !cfg.lightshow ? "Lichtshow ist ausgeschaltet" : "";
                return;
            }
            boolean realAudio = now - lastAudioMs < 600;
            SpotifyApi.State st = SpotifyApi.state;
            boolean spotifyPlaying = st != null && st.playing();
            boolean active = testing || realAudio || spotifyPlaying || Soundboard.isActive();
            status = testing ? "Test läuft" : (active ? "Lichtshow läuft" : "Wartet auf Musik (Spotify oder Sound spielen)");
            if (!active) {
                shown = shown * 0.85f;
                if (shown < 0.03f) return;
            }

            // Stärke: echtes Audio, sonst ein gleichmäßiger Takt (Spotify liefert dem Mod keinen Ton)
            float target;
            if (realAudio) {
                target = level;
            } else {
                double beat = (now % 500L) / 500.0;                 // ca. 120 BPM
                target = (float) (0.25 + 0.45 * Math.pow(1.0 - beat, 3));
            }
            if (!active) target = 0f;
            shown += (target - shown) * (target > shown ? 0.6f : 0.15f);
            if (shown < 0.03f) return;

            if ((counter++ & 1) != 0) return; // nur jeden 2. Tick, spart Leistung
            boolean sparkTick = (counter & 3) == 0;
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            float t = now / 1000f;
            // Laser: dünne, lange Strahlen von mehreren Punkten rund um dich, die sich kreuzen und langsam schwenken
            int emitters = 5;
            int rays = 3;
            for (int e = 0; e < emitters; e++) {
                double ea = e / (double) emitters * Math.PI * 2 + t * 0.05;
                double er = 7 + 3 * (e % 3);
                double ex = px + Math.cos(ea) * er;
                double ez = pz + Math.sin(ea) * er;
                double ey = py + 0.3;
                for (int r = 0; r < rays; r++) {
                    int idx = e * rays + r;
                    double ph = idx * 1.37;
                    double az = ea + Math.PI + (r - 1) * 0.55 + Math.sin(t * 0.6 + ph) * 0.8;
                    double el = Math.toRadians(55 + 22 * Math.sin(t * 0.8 + ph * 0.8));
                    double ch = Math.cos(el);
                    double dx = Math.cos(az) * ch, dz = Math.sin(az) * ch, dy = Math.sin(el);
                    boolean white = idx % 2 == 0;
                    int rgb = white ? 0xFFFFFF : color(cfg.lightMode, t * 0.5f, idx * 0.35f);
                    int n = 0;
                    for (double d = 2; d < 80; d += 0.5 + d * 0.04, n++) {
                        double x = ex + dx * d, y = ey + dy * d, z = ez + dz * d;
                        float size = (float) Math.min(4.0, 0.4 + d * 0.03 + shown * 0.3);
                        mc.particleManager.addParticle(new DustParticleEffect(rgb, size), x, y, z, 0, 0, 0);
                        if (sparkTick && white && n % 4 == 0) mc.particleManager.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
                    }
                }
            }
        } catch (Throwable e) {
            shown = 0f;
        }
    }
}
