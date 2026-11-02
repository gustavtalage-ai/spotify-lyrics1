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
        {0x3A0010, 0xB3122E, 0xFFC23D, 0xFFF6DC, 0x26140A, 0xFFD45A},   // Königsglanz
        {0x140003, 0x7A0012, 0xFF2A3D, 0xFFB0A0, 0x0E0608, 0xC4102F},   // Blutmond
        {0x04200E, 0x1F8A3A, 0x7CFF4F, 0xE8FFB0, 0x1A1408, 0x9BE15A},   // Giftranke
        {0x0B1424, 0x2A4A8C, 0x5AD1FF, 0xFFF35A, 0x14161E, 0x5AD1FF},   // Gewittersturm
        {0x0E0302, 0x3A0E06, 0xFF6A00, 0xFFE070, 0x1A0C08, 0xFF8A20},   // Magmakern
        {0x0A0E2A, 0x3A3590, 0xFFB8F0, 0xFFFFFF, 0x141630, 0xA8C8FF},   // Sternenstaub
        {0x061A1A, 0x1E5A5A, 0x7FFFE0, 0xE8FFFA, 0x0C1414, 0x7FFFE0},   // Geisterhauch
        {0x021226, 0x0B4A7A, 0x2EE6E6, 0xCFFFFF, 0x0A1420, 0x2EC4E6},   // Tiefsee
        {0x2A1206, 0xB0602A, 0xE8945A, 0x7FE0C0, 0x1A0E08, 0x3FB89A},   // Kupferrost
        {0x4A2E10, 0xC8A060, 0xF4D896, 0xFFF6DC, 0x2A1A0A, 0xE0B060},   // Wüstensand
        {0x5A1A3A, 0xF08CB8, 0xFFD0E4, 0xFFFFFF, 0x3A1A10, 0xFFB0D0},   // Kirschblüte
        {0x040A1A, 0x0E2A4A, 0x4AFFA0, 0xC07CFF, 0x0A0E18, 0x4AFFA0},   // Polarlicht
        {0x050506, 0x1C1C24, 0x5A5A6E, 0xFF2A3A, 0x08080A, 0xFF2A3A},   // Schattenklinge
        {0x1A0630, 0x7A2FD0, 0xD08CFF, 0xFFE0FF, 0x140A20, 0xFF5AD0},   // Kristallherz
        {0x061A0C, 0x1E6A3A, 0x4AD07A, 0xFFD45A, 0x14100A, 0xFFD45A},   // Drachenschuppe
        {0x000000, 0x101014, 0xFFB040, 0xFFFFFF, 0x0A0A0C, 0xFFFFFF},   // Sonnenfinsternis
        {0xB8B0A0, 0xF4F0E6, 0xFFD45A, 0xFFFFFF, 0x8A7A5A, 0xFFD45A}    // Himmelsthron
    };

    static BufferedImage make(BufferedImage src, int style, int seed) {
        return make(src, style, seed, false);
    }

    /** woodIsBody: bei Bogen/Armbrust ist das Holz der Hauptteil (bekommt das Muster). */
    static BufferedImage make(BufferedImage src, int style, int seed, boolean woodIsBody) {
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
                handle[i] = !woodIsBody && isWood(r, g, b);
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
            case 5 -> { // Blutmond: dunkelrot, Tropfen laufen nach unten
                col = mix(P[0], P[1], (float) Math.pow(l, 0.9));
                int colSeed = hash(seed, x, 77) % 100;
                if (colSeed < 35) {
                    float pos = ((y / (float) h) - ft - colSeed / 100f) % 1f;
                    if (pos < 0) pos += 1;
                    if (pos < 0.18f) col = mix(col, P[2], 1 - pos / 0.18f);
                }
                if (hash(seed, x * 7 + y, 13) % 41 == 0) col = mix(col, P[3], 0.5f + 0.5f * (float) Math.sin(ft * Math.PI * 2));
            }
            case 6 -> { // Giftranke: grüne Ranken schlängeln sich, Sporen leuchten
                col = mix(P[0], P[1], (float) Math.pow(l, 0.85));
                float v = (float) Math.sin((x * 0.6f + y * 0.6f) * 0.9f + Math.sin(y * 0.5f + ft * Math.PI * 2) * 2.2f);
                if (Math.abs(v) < 0.22f) col = mix(col, P[2], 0.85f);
                if (hash(seed, x * 11 + y * 5, 21) % 31 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft * 2 + (hash(seed, x, y) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, P[3], tw);
                }
            }
            case 7 -> { // Gewittersturm: dunkle Wolken, Blitze zucken über die Klinge
                col = mix(P[0], P[1], (float) Math.pow(l, 0.9));
                float n = fbm(x * 0.25f, ((y + ft * h) % h) * 0.25f, seed + 9, Math.max(1, h / 4));
                col = mix(col, 0x3A4660, clamp((n - 0.5f) * 2) * 0.6f);
                int fi = (int) (ft * FRAMES);
                if (fi % 5 == 0 || fi % 5 == 1) {
                    int bx = (hash(seed, fi / 5, 3) % Math.max(1, w / 2)) + w / 4;
                    int zig = bx + (int) (Math.sin(y * 0.9f + fi) * 2) + (y % 6 < 3 ? 1 : -1);
                    if (Math.abs(x - zig) <= (fi % 5 == 0 ? 1 : 0)) col = mix(P[3], 0xFFFFFF, 0.4f);
                    else if (Math.abs(x - zig) == 2) col = mix(col, P[2], 0.6f);
                }
            }
            case 8 -> { // Magmakern: schwarzes Gestein, glühende Risse pulsieren
                col = mix(P[0], P[1], (float) Math.pow(l, 0.9));
                float n = fbm(x * 0.25f, y * 0.25f, seed + 13, Math.max(1, h / 4));
                float vein = clamp(1 - Math.abs(n - 0.5f) * 14);
                float pulse = 0.55f + 0.45f * (float) Math.sin((ft + diag) * Math.PI * 2);
                if (vein > 0) col = mix(col, vein > 0.6f ? P[3] : P[2], vein * pulse);
                if (hash(seed, x * 9 + y * 13, 31) % 37 == 0) col = mix(col, P[2], 0.4f + 0.6f * pulse);
            }
            case 9 -> { // Sternenstaub: Nachthimmel, rosa Schimmer-Welle, funkelnde Sterne
                col = mix(P[0], P[1], clamp(l * 0.7f + diag * 0.4f));
                float wave = (float) Math.sin((diag * 3 - ft * 2) * Math.PI * 2);
                if (wave > 0.8f) col = mix(col, P[2], (wave - 0.8f) / 0.2f * 0.55f);
                int hs = hash(seed, x * 23 + y * 7, 17);
                if (hs % 11 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft * 2 + (hash(seed, x, y) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, hs % 2 == 0 ? 0xFFFFFF : P[5], 0.15f + 0.85f * tw * tw);
                }
            }
            case 10 -> { // Geisterhauch: blasser Nebel steigt auf
                col = mix(P[0], P[1], (float) Math.pow(l, 0.85));
                float yy = (y + (1 - ft) * h) % h;
                float n = fbm(x * 0.25f, yy * 0.25f, seed + 21, Math.max(1, h / 4));
                col = mix(col, n > 0.62f ? P[3] : P[2], clamp((n - 0.46f) * 2.5f) * 0.75f);
            }
            case 11 -> { // Tiefsee: Lichtwellen wie unter Wasser, Blasen steigen
                col = mix(P[0], P[1], (float) Math.pow(l, 0.85));
                float c1 = (float) Math.sin(x * 0.9f + ft * Math.PI * 2) + (float) Math.sin(y * 0.7f - ft * Math.PI * 2 + x * 0.3f);
                if (c1 > 1.3f) col = mix(col, P[2], (c1 - 1.3f) / 0.7f * 0.7f);
                int bx = hash(seed, x, 41) % 9;
                if (bx == 0) {
                    float pos = ((y / (float) h) + ft + (hash(seed, x, 43) % 100) / 100f) % 1f;
                    if (pos < 0.12f) col = mix(col, P[3], 0.8f);
                }
            }
            case 12 -> { // Kupferrost: Kupfer mit grüner Patina, Glanz wandert
                col = mix(P[0], P[1], (float) Math.pow(l, 0.8));
                float n = fbm(x * 0.25f, y * 0.25f, seed + 51, Math.max(1, h / 4));
                if (n > 0.55f) col = mix(col, P[5], clamp((n - 0.55f) * 4) * 0.85f);
                if (hash(seed, x * 5 + y * 3, 53) % 29 == 0) col = mix(col, P[2], 0.6f);
            }
            case 13 -> { // Wüstensand: Dünen ziehen vorbei, Körner glitzern
                col = mix(P[0], P[1], (float) Math.pow(l, 0.75));
                float dune = (float) Math.sin((x * 0.5f + y * 0.9f) - ft * Math.PI * 2);
                col = mix(col, dune > 0 ? P[2] : P[0], Math.abs(dune) * 0.35f);
                if (hash(seed, x * 13 + y, 61) % 17 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft * 2 + (hash(seed, x, y) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, P[3], tw);
                }
            }
            case 14 -> { // Kirschblüte: rosa, Blütenblätter fallen schräg
                col = mix(P[1], P[2], (float) Math.pow(l, 0.9));
                col = mix(col, P[0], 0.25f * (1 - l));
                int lane = Math.floorMod(x + y, 7);
                int pid = Math.floorDiv(x + y, 7);
                float pos = ((y / (float) h) + ft + (hash(seed, pid, 71) % 100) / 100f) % 1f;
                if (lane < 2 && pos < 0.1f) col = mix(P[3], P[5], lane * 0.5f);
            }
            case 15 -> { // Polarlicht: grün-lila Lichtbänder wogen über dunklen Himmel
                col = mix(P[0], P[1], (float) Math.pow(l, 0.9));
                float band = (float) Math.sin(y * 0.45f + (float) Math.sin(x * 0.4f + ft * Math.PI * 2) * 1.8f);
                float k = clamp((band - 0.4f) * 2.2f);
                int ac = mix(P[2], P[3], 0.5f + 0.5f * (float) Math.sin(x * 0.3f + ft * Math.PI * 2));
                col = mix(col, ac, k * 0.8f);
                if (hash(seed, x * 19 + y * 11, 73) % 37 == 0) col = mix(col, 0xFFFFFF, 0.6f);
            }
            case 16 -> { // Schattenklinge: schwarzer Rauch, rote Funken
                col = mix(P[0], P[1], (float) Math.pow(l, 0.9));
                float yy = (y + (1 - ft) * h) % h;
                float n = fbm(x * 0.25f, yy * 0.25f, seed + 81, Math.max(1, h / 4));
                col = mix(col, P[2], clamp((n - 0.5f) * 2.5f) * 0.6f);
                if (hash(seed, x * 7 + y * 17, 83) % 31 == 0) col = mix(col, P[3], 0.5f + 0.5f * (float) Math.sin((ft * 3 + x) * Math.PI));
            }
            case 17 -> { // Kristallherz: Amethyst-Facetten, inneres Pochen
                int band = Math.floorDiv(x + y, 4);
                float facet = (hash(seed, band, 91) % 100) / 100f;
                col = mix(P[0], facet > 0.5f ? P[2] : P[1], 0.55f + 0.4f * l);
                float beat = (float) Math.pow(Math.max(0, Math.sin(ft * Math.PI * 4)), 6);
                col = mix(col, P[5], beat * 0.35f * l);
                if (hash(seed, x * 31 + y, 93) % 23 == 0) col = mix(col, P[3], 0.7f);
            }
            case 18 -> { // Drachenschuppe: Schuppenmuster mit goldenem Rand, Schimmer
                col = mix(P[0], P[1], (float) Math.pow(l, 0.85));
                int sx = Math.floorMod(x + ((y / 3) % 2) * 2, 4), sy = Math.floorMod(y, 3);
                if (sy == 2 || sx == 0) col = mix(col, P[5], 0.45f);
                else col = mix(col, P[2], 0.25f * (float) Math.max(0, Math.sin((diag * 2 - ft * 2) * Math.PI * 2)));
            }
            case 19 -> { // Sonnenfinsternis: schwarzer Kern, Korona leuchtet am Rand
                col = mix(P[0], P[1], l * 0.6f);
                float cor = (float) Math.sin((diag * 6 - ft * 2) * Math.PI * 2);
                if (cor > 0.7f) col = mix(col, P[2], (cor - 0.7f) / 0.3f * 0.5f);
            }
            case 20 -> { // Himmelsthron: weiß-goldener Marmor, Lichtstrahlen, Funkeln
                col = mix(P[0], P[1], (float) Math.pow(l, 0.7));
                float n = fbm(x * 0.25f, y * 0.25f, seed + 101, Math.max(1, h / 4));
                float vein = clamp(1 - Math.abs(n - 0.5f) * 16);
                col = mix(col, P[2], vein * 0.85f);
                if (hash(seed, x * 23 + y * 13, 103) % 19 == 0) {
                    float tw = (float) Math.max(0, Math.sin((ft * 2 + (hash(seed, x, y) % 100) / 100f) * Math.PI * 2));
                    col = mix(col, 0xFFFFFF, tw);
                }
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
                : style == 16 || style == 19 ? P[3] : style == 4 || style == 7 || style == 11 || style == 15 || style == 17 || style == 18 || style == 20 ? P[2] : style == 5 ? P[2] : P[1];
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
