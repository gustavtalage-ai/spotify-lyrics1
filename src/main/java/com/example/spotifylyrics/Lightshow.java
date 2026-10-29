package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

/** Lichtshow: Laser-Strahlen, die rund um dich in den Himmel leuchten, im Takt der Musik (direkt auf den Bildschirm gezeichnet, ohne Partikel). */
public class Lightshow {
    public static final String[] MODES = {"Regenbogen", "Lila", "Blau", "Feuer"};

    private static volatile float level = 0f;       // 0..1, geglättet
    private static volatile long lastAudioMs = 0;    // wann zuletzt echtes Audio ankam
    private static float shown = 0f;
    private static boolean logged = false;
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

    /** Jeden Client-Tick: Stärke und Status berechnen. */
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
        } catch (Throwable e) {
            shown = 0f;
        }
    }

    /** Zeichnet die Laser direkt auf den Bildschirm (HUD), ohne Minecraft-Partikel. */
    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (shown < 0.03f || mc.player == null || mc.world == null || mc.options.hudHidden) return;
            float td = tc.getTickProgress(false);
            if (!Overlay3D.begin(mc, td)) return;
            Config cfg = Config.INSTANCE;
            float t = System.currentTimeMillis() / 1000f;
            Vec3d pp = mc.player.getLerpedPos(td);
            int thick = shown > 0.5f ? 2 : 1;
            float power = 0.55f + 0.45f * Math.min(1f, shown * 1.4f);
            int emitters = 5;
            int rays = 3;
            for (int e = 0; e < emitters; e++) {
                double ea = e / (double) emitters * Math.PI * 2 + t * 0.05;
                double er = 7 + 3 * (e % 3);
                double ex = pp.x + Math.cos(ea) * er;
                double ez = pp.z + Math.sin(ea) * er;
                double ey = pp.y + 0.3;
                for (int r = 0; r < rays; r++) {
                    int idx = e * rays + r;
                    double ph = idx * 1.37;
                    double az = ea + Math.PI + (r - 1) * 0.55 + Math.sin(t * 0.6 + ph) * 0.8;
                    double el = Math.toRadians(55 + 22 * Math.sin(t * 0.8 + ph * 0.8));
                    double ch = Math.cos(el);
                    double dx = Math.cos(az) * ch, dz = Math.sin(az) * ch, dy = Math.sin(el);
                    int glow = color(cfg.lightMode, t * 0.5f, idx * 0.35f);
                    int core = idx % 2 == 0 ? 0xFFFFFF : glow;
                    double[] a = Overlay3D.view(ex + dx * 2, ey + dy * 2, ez + dz * 2);
                    double[] b = Overlay3D.view(ex + dx * 180, ey + dy * 180, ez + dz * 180);
                    Overlay3D.beam(ctx, a, b, core, glow, thick, power);
                }
            }
        } catch (Throwable ex) {
            status = "Fehler: " + ex;
            if (!logged) {
                logged = true;
                SpotifyLyricsClient.LOGGER.warn("Lichtshow-Fehler", ex);
            }
        }
    }
}
