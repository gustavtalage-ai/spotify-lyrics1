package com.example.spotifylyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Lichtshow: farbiges Leuchten an den Bildschirmrändern, das zur Musik pulsiert. */
public class Lightshow {
    public static final String[] MODES = {"Regenbogen", "Lila", "Blau", "Feuer"};

    private static volatile float level = 0f;       // 0..1, geglättet
    private static volatile long lastAudioMs = 0;    // wann zuletzt echtes Audio ankam
    private static float shown = 0f;

    /** Echtes Audio (Soundboard oder PC-Ton), 20-ms-Stücke aus dem Voice-Chat-Plugin. */
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

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        Config cfg = Config.INSTANCE;
        if (!cfg.lightshow) return;
        long now = System.currentTimeMillis();
        boolean realAudio = now - lastAudioMs < 600;
        SpotifyApi.State st = SpotifyApi.state;
        boolean spotifyPlaying = st != null && st.playing();
        if (!realAudio && !spotifyPlaying && !Soundboard.isActive()) {
            shown = shown * 0.9f;
            if (shown < 0.01f) return;
        }

        // Stärke: echtes Audio, sonst ein gleichmäßiger Takt (Spotify liefert dem Mod keinen Ton)
        float target;
        if (realAudio) {
            target = level;
        } else {
            double beat = (now % 500L) / 500.0;                 // ca. 120 BPM
            target = (float) (0.25 + 0.45 * Math.pow(1.0 - beat, 3));
        }
        shown += (target - shown) * (target > shown ? 0.6f : 0.15f);

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float t = now / 1000f;
        int layers = 14;
        int thick = 2 + (int) (shown * 3);
        for (int side = 0; side < 4; side++) {
            int rgb = color(cfg.lightMode, t, side);
            for (int i = 0; i < layers; i++) {
                float fall = 1f - i / (float) layers;
                int a = (int) (shown * 150 * fall * fall);
                if (a < 2) continue;
                int c = (a << 24) | rgb;
                int d0 = i * thick / 2;
                int d1 = d0 + Math.max(1, thick / 2 + 1);
                switch (side) {
                    case 0: ctx.fill(0, d0, w, d1, c); break;                 // oben
                    case 1: ctx.fill(0, h - d1, w, h - d0, c); break;         // unten
                    case 2: ctx.fill(d0, 0, d1, h, c); break;                 // links
                    default: ctx.fill(w - d1, 0, w - d0, h, c); break;        // rechts
                }
            }
        }
        // Equalizer-Balken unten Mitte
        int bars = 24;
        int bw = 4, gap = 2;
        int total = bars * (bw + gap);
        int bx = (w - total) / 2;
        for (int i = 0; i < bars; i++) {
            double wave = 0.5 + 0.5 * Math.sin(t * 6 + i * 0.7) * Math.sin(t * 2.3 + i * 0.3);
            int bh = 2 + (int) (shown * 26 * (0.3 + 0.7 * wave));
            int c = 0xC0000000 | color(cfg.lightMode, t, i / (float) bars);
            ctx.fill(bx + i * (bw + gap), h - 2 - bh, bx + i * (bw + gap) + bw, h - 2, c);
        }
    }
}
