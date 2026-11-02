package com.example.spotifylyrics;

import java.awt.image.BufferedImage;

/**
 * Erzeugt animierte Waffen-Texturen für die Kollektionen (reines Java, ohne Minecraft).
 * Die Original-Textur wird verdoppelt (16 -> 32 Pixel), Griff und Klinge werden getrennt eingefärbt,
 * die Klinge bekommt Leuchtkanten und ein eigenes, bewegtes Muster je Kollektion.
 */
final class SkinGen {
    private SkinGen() {
    }

    static final int FRAMES = 16;

    /** Farben je Kollektion: dunkel, mitte, hell, Glanz, Griff dunkel, Griff hell/Zierde. */
    static final int[][] PAL = {
        {0x4A0E00, 0xE24A00, 0xFFB020, 0xFFF4C0, 0x2A1208, 0xFFB020},   // Sonnenfeuer
        {0x0A2A5E, 0x2F86F5, 0x8FF3FF, 0xFFFFFF, 0x1A2238, 0xBFEFFF},   // Frostkristall
        {0x07001A, 0x3A137A, 0x8E3CFF, 0xFF8AF0, 0x0C0614, 0xB14BFF},   // Leere
        {0x07070F, 0xE600D0, 0x00E5FF, 0x5CFF3A, 0x121218, 0x00E5FF},   // Neon-Glitch
        {0x3A0010, 0xB3122E, 0xFFC23D, 0xFFF6DC, 0x26140A, 0xFFD45A}    // Königsglanz
    };

    static BufferedImage make(BufferedImage src, int style, int seed) {
        int[] P = PAL[style];
        int sw = src.getWidth(), sh = src.getHeight();
        int sc = sw <= 16 ? 2 : 1;
        int w = sw * sc, h = sh * sc;
        int[] a = new int[w * h], lum = new int[w * h];
        boolean[] handle = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x / sc, y / sc);
                int i = y * w + x;
                a[i] = argb >>> 24;
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                lum[i] = (r * 299 + g * 587 + b * 114) / 1000;
                handle[i] = isWood(r, g, b);
            }
        }
        // Rand der Klinge (Nachbar durchsichtig oder Griff)
        boolean[] edge = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (a[i] == 0 || handle[i]) continue;
                edge[i] = clear(a, w, h, x - 1, y) || clear(a, w, h, x + 1, y) || clear(a, w, h, x, y - 1) || clear(a, w, h, x, y + 1);
            }
        }
        BufferedImage out = new BufferedImage(w, h * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) {
            float ft = f / (float) FRAMES;
            int glitchRow = style == 3 && (f % 4 == 1) ? hash(seed, f, 7) % h : -99;
            int glitchShift = style == 3 ? (hash(seed, f, 9) % 2 == 0 ? 2 : -2) : 0;
            for (int y = 0; y < h; y++) {
                for (int x0 = 0; x0 < w; x0++) {
                    int x = x0;
                    if (Math.abs(y - glitchRow) <= 1) x = Math.floorMod(x0 - glitchShift, w);
                    int i = y * w + x;
                    if (a[i] == 0) {
                        out.setRGB(x0, f * h + y, 0);
                        continue;
                    }
                    float l = lum[i] / 255f;
                    int col;
                    if (handle[i]) {
                        col = mix(P[4], P[5], (float) Math.pow(l, 1.6));
                    } else {
                        col = blade(style, P, l, x, y, w, h, ft, seed, edge[i]);
                    }
                    out.setRGB(x0, f * h + y, a[i] << 24 | col);
                }
            }
        }
        return out;
    }

    private static boolean clear(int[] a, int w, int h, int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h || a[y * w + x] == 0;
    }

    /** Holz-Griff erkennen (braune Töne). */
    private static boolean isWood(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max < 25 || max - min < 22) return false;
        return r >= g && g >= b && r - b > 30 && max < 210;
    }

    private static int blade(int style, int[] P, float l, int x, int y, int w, int h, float ft, int seed, boolean edge) {
        // Grundfarbe nach Helligkeit
        int base = grad(P, (float) Math.pow(l, 0.9));
        float diag = (x + (h - 1 - y)) / (float) (w + h); // 0 unten links (Griff) .. 1 oben rechts (Spitze)
        int col = base;
        switch (style) {
            case 0 -> { // Sonnenfeuer: Flammen ziehen zur Spitze
                float yy = (y + ft * h) % h;
                float n = fbm(x * 0.25f, yy * 0.25f, seed, Math.max(1, h / 4));
                col = mix(col, P[0], 0.55f * clamp(0.55f - n) * 2);
                float flame = clamp((n - 0.45f) * 3.5f) * (0.5f + 0.5f * diag);
                col = mix(col, n > 0.66f ? P[3] : P[2], flame);
            }
            case 1 -> { // Frostkristall: Facetten + funkelnde Punkte
                int band = Math.floorDiv(x - y, 5);
                float facet = (hash(seed, band, 3) % 100) / 100f;
                col = mix(col, facet > 0.5f ? P[2] : P[1], 0.35f);
                if (hash(seed, x * 31 + y, 5) % 23 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft * 2 + (hash(seed, x, y) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, 0xFFFFFF, tw * tw);
                }
            }
            case 2 -> { // Leere: dunkle Galaxie mit Nebel und Sternen
                col = mix(P[0], P[1], l * 0.8f);
                float yy = (y + ft * h) % h;
                float n = fbm(x * 0.25f, yy * 0.25f, seed + 5, Math.max(1, h / 4));
                col = mix(col, n > 0.6f ? P[3] : P[2], clamp((n - 0.45f) * 2.2f) * 0.7f);
                if (hash(seed, x * 17 + y * 3, 11) % 19 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft + (hash(seed, x, y + 1) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, 0xFFFFFF, 0.3f + 0.7f * tw);
                }
            }
            case 3 -> { // Neon-Glitch: dunkler Kern, Neon-Kanten, Scanlinien
                col = mix(P[0], 0x22223A, l);
                int scan = Math.floorMod(y + (int) (ft * h), 4);
                if (scan == 0) col = mix(col, P[2], 0.35f);
                if ((x + y) % 8 == 0) col = mix(col, P[1], 0.55f);

            }
            default -> { // Königsglanz: roter Kern, goldene Kanten, Juwel-Funkeln
                col = mix(P[0], P[1], (float) Math.pow(l, 0.8));
                if (hash(seed, x * 13 + y * 7, 2) % 29 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft * 2 + (hash(seed, y, x) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, 0xFFFFFF, tw * tw);
                }
            }
        }
        // Umriss: dunkel mit farbigem Schimmer (Neon/Gold leuchten stärker)
        if (edge) {
            int glow = style == 3 ? ((int) ((ft * 2 + diag * 2) * 3) % 3 == 0 ? P[1] : (int) ((ft * 2 + diag * 2) * 3) % 3 == 1 ? P[2] : P[3])
                : style == 4 ? P[2] : P[1];
            float pulse = 0.5f + 0.5f * (float) Math.sin((ft * 2 + diag) * Math.PI * 2);
            col = mix(mix(P[0], 0x000000, 0.25f), glow, style >= 3 ? 0.75f + 0.25f * pulse : 0.3f + 0.45f * pulse);
        }
        // Glanz-Streifen läuft über die Klinge
        float c = -0.25f + 1.5f * ft;
        float band = clamp(1 - Math.abs(diag - c) * 9);
        col = mix(col, P[3], band * 0.7f);
        return col;
    }

    // ---------- Hilfen ----------

    static int grad(int[] P, float k) {
        k = clamp(k);
        if (k < 0.45f) return mix(P[0], P[1], k / 0.45f);
        if (k < 0.8f) return mix(P[1], P[2], (k - 0.45f) / 0.35f);
        return mix(P[2], P[3], (k - 0.8f) / 0.2f);
    }

    static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    static int mix(int a, int b, float t) {
        t = clamp(t);
        int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    static int hash(int s, int x, int y) {
        int h = s * 374761393 + x * 668265263 + y * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }

    /** Wert-Rauschen, in y kachelbar (Periode = Bildhöhe), damit die Animation nahtlos läuft. */
    private static float vnoise(float x, float y, int seed, int py) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        float v00 = rnd(seed, x0, Math.floorMod(y0, py)), v10 = rnd(seed, x0 + 1, Math.floorMod(y0, py));
        float v01 = rnd(seed, x0, Math.floorMod(y0 + 1, py)), v11 = rnd(seed, x0 + 1, Math.floorMod(y0 + 1, py));
        float sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        return (v00 * (1 - sx) + v10 * sx) * (1 - sy) + (v01 * (1 - sx) + v11 * sx) * sy;
    }

    private static float rnd(int s, int x, int y) {
        return (hash(s, x, y) % 1000) / 1000f;
    }

    private static float fbm(float x, float y, int seed, int period) {
        return vnoise(x, y, seed, period) * 0.65f + vnoise(x * 2, y * 2, seed + 1, period * 2) * 0.35f;
    }
}
