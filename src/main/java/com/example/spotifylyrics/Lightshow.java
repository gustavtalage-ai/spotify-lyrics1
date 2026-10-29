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
    private static double animT = 0;
    private static long lastNs = 0;
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

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Zeichnet Himmelsfarbe und Laser direkt auf den Bildschirm (HUD), ohne Minecraft-Partikel. */
    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (shown < 0.03f || mc.player == null || mc.world == null || mc.options.hudHidden) return;
            float td = tc.getTickProgress(false);
            if (!Overlay3D.begin(mc, td)) return;
            Config cfg = Config.INSTANCE;

            // Eigene Uhr: läuft schneller, wenn die Musik laut ist
            long ns = System.nanoTime();
            double dt = lastNs == 0 ? 0.016 : Math.min(0.1, (ns - lastNs) / 1e9);
            lastNs = ns;
            animT += dt * (0.7 + 1.3 * shown);
            float t = (float) animT;

            Vec3d pp = mc.player.getLerpedPos(td);
            int thick = shown > 0.5f ? 2 : 1;
            float power = 0.55f + 0.45f * Math.min(1f, shown * 1.4f);

            // Himmel einfärben: oben kräftig, zum Horizont hin weich ausblendend
            int sw = Overlay3D.screenW();
            int sh = Overlay3D.screenH();
            int hor = (int) Math.max(0, Math.min(sh, Overlay3D.horizonY()));
            if (hor > 4) {
                int c1 = color(cfg.lightMode, t * 0.3f, 0f);
                int c2 = color(cfg.lightMode, t * 0.3f + 1.5f, 1f);
                int bands = 24;
                for (int i = 0; i < bands; i++) {
                    int y0 = hor * i / bands;
                    int y1 = hor * (i + 1) / bands;
                    if (y1 <= y0) continue;
                    float f = 1f - (i + 0.5f) / bands;
                    int alpha = (int) (255 * 0.38f * power * f * f);
                    if (alpha < 2) continue;
                    ctx.fill(0, y0, sw, y1, (alpha << 24) | mix(c1, c2, 1f - f));
                }
            }

            // Konzert-Muster: wechselt alle 7 Sekunden
            int pat = ((int) (animT / 7.0)) % 3;
            int emitters = 6;
            int rays = 4;
            for (int e = 0; e < emitters; e++) {
                double ea = e / (double) emitters * Math.PI * 2 + animT * 0.05;
                double er = 8 + 3 * (e % 3);
                double ex = pp.x + Math.cos(ea) * er;
                double ez = pp.z + Math.sin(ea) * er;
                double ey = pp.y + 0.3;
                double dir = (e & 1) == 0 ? 1 : -1;
                for (int r = 0; r < rays; r++) {
                    int idx = e * rays + r;
                    double ph = idx * 0.9;
                    double spread = r - 1.5;
                    double az, elDeg;
                    if (pat == 0) {          // weit hin und her schwenken
                        az = ea + Math.PI + spread * 0.35 + Math.sin(animT * 1.1 + ph * 0.5 + e) * 1.2 * dir;
                        elDeg = 52 + 30 * Math.sin(animT * 0.9 + ph);
                    } else if (pat == 1) {   // Fächer: alle schwingen zusammen über dich
                        az = ea + Math.PI + spread * 0.25 + Math.sin(animT * 1.6) * 0.6 * dir;
                        elDeg = 62 + 24 * Math.sin(animT * 1.3 + e * 0.6);
                    } else {                 // Kreisen
                        az = ea + animT * 1.4 * dir + spread * 0.5;
                        elDeg = 40 + 38 * Math.abs(Math.sin(animT * 0.7 + ph * 0.3));
                    }
                    elDeg = Math.max(12, Math.min(88, elDeg));
                    double el = Math.toRadians(elDeg);
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
