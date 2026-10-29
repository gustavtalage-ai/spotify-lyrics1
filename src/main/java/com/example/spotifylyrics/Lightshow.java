package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;

import java.util.Random;

/** Lichtshow: Scheinwerfer-Strahlen, die rund um dich in den Himmel leuchten, im Takt der Musik. Der Bildschirm bleibt frei. */
public class Lightshow {
    public static final String[] MODES = {"Regenbogen", "Lila", "Blau", "Feuer"};

    private static final Random RND = new Random();
    private static volatile float level = 0f;       // 0..1, geglättet
    private static volatile long lastAudioMs = 0;    // wann zuletzt echtes Audio ankam
    private static float shown = 0f;

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
            if (!cfg.lightshow || mc.world == null || mc.player == null || mc.isPaused()) {
                shown = shown * 0.8f;
                return;
            }
            long now = System.currentTimeMillis();
            boolean realAudio = now - lastAudioMs < 600;
            SpotifyApi.State st = SpotifyApi.state;
            boolean spotifyPlaying = st != null && st.playing();
            boolean active = realAudio || spotifyPlaying || Soundboard.isActive();
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

            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            float t = now / 1000f;
            // Scheinwerfer: Strahlen, die vom Boden rund um dich in den Himmel leuchten und hin und her schwenken
            int beams = 5 + (int) (shown * 5);
            float size = 2.6f + shown * 1.4f;
            double sweep = 0.35 + 0.35 * shown;
            for (int b = 0; b < beams; b++) {
                double baseAng = (b / (double) beams) * Math.PI * 2 + t * 0.08;
                double rad = 9 + 5 * (b % 3);
                double bx = px + Math.cos(baseAng) * rad;
                double bz = pz + Math.sin(baseAng) * rad;
                double by = py + 0.5;
                // Schwenken: jeder Strahl pendelt anders, die Richtung zeigt grob nach oben
                double ph = b * 1.7;
                double dx = Math.sin(t * 1.1 + ph) * sweep;
                double dz = Math.cos(t * 0.9 + ph * 1.3) * sweep;
                double dy = 1.0;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= len; dy /= len; dz /= len;
                for (double d = 2; d < 70; d += 3.0) {
                    if (RND.nextFloat() < 0.35f) continue;
                    double jx = (RND.nextDouble() - 0.5) * 0.5;
                    double jz = (RND.nextDouble() - 0.5) * 0.5;
                    double x = bx + dx * d + jx;
                    double y = by + dy * d;
                    double z = bz + dz * d + jz;
                    int rgb = color(cfg.lightMode, t + (float) d * 0.02f, (float) b / beams * 4f);
                    mc.particleManager.addParticle(new DustParticleEffect(rgb, size), x, y, z, 0, 0, 0);
                }
            }
        } catch (Throwable e) {
            shown = 0f;
        }
    }
}
