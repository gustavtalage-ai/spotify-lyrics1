package com.example.spotifylyrics;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * Design-System der Mod: eine Farbwelt (dunkles Anthrazit mit hellem Cyan-Akzent), flache Flächen,
 * feine Ränder, weiche Schatten. Nur einfache Rechtecke, darum in jeder Version sicher.
 */
final class Ui {
    private Ui() {}

    // ---------- Farben (änderbar über Einstellungen) ----------
    static int ACCENT = 0xFF38BDF8;
    static int ACCENT_LIGHT = 0xFF7DD3FC;
    static int ACCENT_DIM = 0xFF123A55;
    static int ACCENT_DIM_HOV = 0xFF1A4E73;

    static int BG = 0xFF0E1015;
    static int HEAD_BG = 0xFF14171E;
    static int SURFACE = 0xFF1A1E27;
    static int SURFACE_HOV = 0xFF252B38;
    static int BORDER = 0xFF2B3140;
    /** Kasten-Hintergrund (zwischen BG und SURFACE). */
    static int CARD = 0xFF151921;
    static int CARD_HOV = 0xFF1A202B;
    static final int DIM = 0xB006080C;

    static final int TEXT = 0xFFE8EAF0;
    static final int MUTED = 0xFF8B92A5;
    static final int GREEN = 0xFF4ADE80;
    static final int RED = 0xFFFB7185;
    static final int AMBER = 0xFFFBBF24;

    /** Akzentfarben: Name, Akzent, hell, dunkel, dunkel (Maus drüber). */
    static final String[] ACCENT_NAMES = {"Cyan", "Pink", "Lila", "Grün", "Orange", "Rot", "Gold", "Weiß"};
    private static final int[][] ACCENTS = {
        {0xFF38BDF8, 0xFF7DD3FC, 0xFF123A55, 0xFF1A4E73},
        {0xFFF472B6, 0xFFF9A8D4, 0xFF4A1834, 0xFF652147},
        {0xFFA78BFA, 0xFFC4B5FD, 0xFF2E2358, 0xFF3D2F75},
        {0xFF4ADE80, 0xFF86EFAC, 0xFF14432A, 0xFF1C5A38},
        {0xFFFB923C, 0xFFFDBA74, 0xFF4A2810, 0xFF633616},
        {0xFFF87171, 0xFFFCA5A5, 0xFF4A1616, 0xFF632020},
        {0xFFFACC15, 0xFFFDE047, 0xFF4A3D0A, 0xFF63520E},
        {0xFFE5E7EB, 0xFFF3F4F6, 0xFF2A2D35, 0xFF373B45}};
    /** Hintergründe: Name, BG, Kopf, Fläche, Fläche (Maus), Rand. */
    static final String[] BG_NAMES = {"Dunkel", "Schwarz", "Nachtblau", "Lila-Nacht", "Grau"};
    private static final int[][] BGS = {
        {0xFF0E1015, 0xFF14171E, 0xFF1A1E27, 0xFF252B38, 0xFF2B3140},
        {0xFF000000, 0xFF0A0A0C, 0xFF141416, 0xFF1F1F23, 0xFF26262B},
        {0xFF0B1220, 0xFF101A2E, 0xFF16223A, 0xFF1F2E4D, 0xFF263757},
        {0xFF120D1C, 0xFF191226, 0xFF221933, 0xFF2D2242, 0xFF35284D},
        {0xFF1B1D22, 0xFF22252B, 0xFF2A2E36, 0xFF353A44, 0xFF3D4350}};

    static int accentPreview(int i) {
        return ACCENTS[Math.floorMod(i, ACCENTS.length)][0];
    }

    static int bgPreview(int i) {
        return BGS[Math.floorMod(i, BGS.length)][2];
    }

    /** Farben aus den Einstellungen übernehmen. */
    static void applyTheme() {
        Config c = Config.INSTANCE;
        int[] a = ACCENTS[Math.floorMod(c.uiAccent, ACCENTS.length)];
        ACCENT = a[0];
        ACCENT_LIGHT = a[1];
        ACCENT_DIM = a[2];
        ACCENT_DIM_HOV = a[3];
        int[] b = BGS[Math.floorMod(c.uiBg, BGS.length)];
        BG = b[0];
        HEAD_BG = b[1];
        SURFACE = b[2];
        SURFACE_HOV = b[3];
        BORDER = b[4];
        CARD = mix(BG, SURFACE, 0.5f);
        CARD_HOV = mix(BG, SURFACE_HOV, 0.6f);
    }

    static {
        try {
            applyTheme();
        } catch (Throwable ignored) {
        }
    }

    // ---------- Text (flach, ohne Schatten) ----------
    static void txt(DrawContext c, TextRenderer tr, String s, int x, int y, int color) {
        c.drawText(tr, s, x, y, color, false);
    }

    static void txt(DrawContext c, TextRenderer tr, Text s, int x, int y, int color) {
        c.drawText(tr, s, x, y, color, false);
    }

    static void txt(DrawContext c, TextRenderer tr, OrderedText s, int x, int y, int color) {
        c.drawText(tr, s, x, y, color, false);
    }

    /** Zwei Farben (ARGB) mischen, t = Anteil von b. */
    static int mix(int a, int b, float t) {
        int al = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    // ---------- Formen ----------

    /** Rechteck mit abgerundeten Ecken (1 Pixel). Die Teile überlappen nicht, darum sehen auch durchsichtige Farben gleichmäßig aus. */
    static void round(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 3 || y1 - y0 < 3) {
            c.fill(x0, y0, x1, y1, color);
            return;
        }
        c.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        c.fill(x0, y0 + 1, x1, y1 - 1, color);
        c.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
    }

    /** Stärker abgerundet (2 Pixel), auch ohne Überlappung. */
    static void round2(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        if (x1 - x0 < 6 || y1 - y0 < 6) {
            round(c, x0, y0, x1, y1, color);
            return;
        }
        c.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
        c.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
        c.fill(x0, y0 + 2, x1, y1 - 2, color);
        c.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
        c.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
    }

    /** Abgerundetes Feld mit 1 Pixel Rand. */
    static void card(DrawContext c, int x0, int y0, int x1, int y1, int border, int fill) {
        round2(c, x0, y0, x1, y1, border);
        round2(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }

    /** Waagrechter Farbverlauf von a nach b (in schmalen Streifen gezeichnet). */
    static void hgrad(DrawContext c, int x0, int y0, int x1, int y1, int a, int b) {
        int w = x1 - x0;
        if (w <= 0) return;
        for (int x = x0; x < x1; x += 2) {
            c.fill(x, y0, Math.min(x1, x + 2), y1, mix(a, b, (x - x0) / (float) w));
        }
    }

    static final int PINK = 0xFFF472B6;

    static boolean in(int mx, int my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** Weicher Schatten unter einer Fläche. */
    static void shadow(DrawContext c, int x0, int y0, int x1, int y1) {
        round2(c, x0 - 4, y0 - 2, x1 + 4, y1 + 6, 0x12000000);
        round2(c, x0 - 3, y0 - 1, x1 + 3, y1 + 4, 0x1A000000);
        round2(c, x0 - 2, y0, x1 + 2, y1 + 3, 0x22000000);
    }

    // ---------- Bausteine ----------

    /** Moderner Knopf: flach, feiner Rand, Text mittig. Deckt den normalen Knopf darunter komplett ab. */
    static void btn(DrawContext c, TextRenderer tr, int x0, int y0, int x1, int y1, String label, boolean hov, int bg, int bgHov, int fg) {
        c.fill(x0, y0, x1, y1, BG);
        int base = hov ? bgHov : bg;
        round2(c, x0, y0, x1, y1, mix(base, 0xFFFFFFFF, hov ? 0.22f : 0.08f));
        round2(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, base);
        c.fill(x0 + 3, y0 + 1, x1 - 3, y0 + 2, mix(base, 0xFFFFFFFF, hov ? 0.30f : 0.14f));
        String t = tr.trimToWidth(label, Math.max(4, x1 - x0 - 8));
        txt(c, tr, t, x0 + (x1 - x0 - tr.getWidth(t)) / 2, y0 + (y1 - y0 - 8) / 2, fg);
    }

    /** Schalter mit Name links und kleinem Ein/Aus-Schieber rechts. */
    static void toggle(DrawContext c, TextRenderer tr, int x0, int y0, int x1, int y1, String label, boolean on, boolean hov) {
        c.fill(x0, y0, x1, y1, BG);
        int base = hov ? SURFACE_HOV : SURFACE;
        if (on) base = mix(base, ACCENT, 0.12f);
        round2(c, x0, y0, x1, y1, on ? mix(base, ACCENT, 0.45f) : mix(base, 0xFFFFFFFF, hov ? 0.18f : 0.06f));
        round2(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, base);
        txt(c, tr, tr.trimToWidth(label, Math.max(4, x1 - x0 - 50)), x0 + 9, y0 + (y1 - y0 - 8) / 2, on ? TEXT : MUTED);
        int sx1 = x1 - 9, sx0 = sx1 - 24, sy0 = y0 + (y1 - y0 - 12) / 2;
        round2(c, sx0, sy0, sx1, sy0 + 12, on ? ACCENT : 0xFF333A4A);
        int kx = on ? sx1 - 10 : sx0 + 2;
        round(c, kx, sy0 + 2, kx + 8, sy0 + 10, on ? 0xFF04121B : 0xFFE8EAF0);
    }

    /** Abschnitts-Überschrift mit Akzent-Strich und feiner Linie. */
    static void section(DrawContext c, TextRenderer tr, int x, int y, String text, int width) {
        c.fill(x, y + 1, x + 2, y + 9, ACCENT);
        txt(c, tr, text, x + 6, y, ACCENT_LIGHT);
        int lx = x + 6 + tr.getWidth(text) + 6;
        hgrad(c, lx, y + 4, x + width - 4, y + 5, (ACCENT & 0x00FFFFFF) | 0x40000000, 0x08FFFFFF);
    }

    /** Dunkler Hintergrund mit Panel, Schatten, feinem Rand und Kopfzeile (für alle Menüs gleich). */
    static void panel(DrawContext c, int sw, int sh, int px0, int py0, int px1, int py1, int head) {
        c.fill(0, 0, sw, sh, DIM);
        shadow(c, px0, py0, px1, py1);
        round2(c, px0 - 1, py0 - 1, px1 + 1, py1 + 1, BORDER);
        round2(c, px0, py0, px1, py1, BG);
        round2(c, px0, py0, px1, py0 + head, HEAD_BG);
        c.fill(px0, py0 + head - 4, px1, py0 + head, HEAD_BG);
        c.fill(px0 + 1, py0 + head, px1 - 1, py0 + head + 1, 0xFF232834);
        c.fill(px0 + 3, py0 + 1, px1 - 3, py0 + 2, 0x14FFFFFF);
        hgrad(c, px0 + 1, py0 + head, px1 - 1, py0 + head + 1, (ACCENT & 0x00FFFFFF) | 0xA0000000, 0x60F472B6);
        hgrad(c, px0 + 10, py0 + head - 1, px0 + 60, py0 + head + 1, ACCENT, PINK);
    }

    /** Kleine Anzeige-Fläche für HUD-Elemente (halbdurchsichtig, abgerundet, mit schmalem Akzentstrich). */
    static void hudBox(DrawContext c, int x0, int y0, int x1, int y1, int accent) {
        round(c, x0, y0, x1, y1, 0xA8101319);
        c.fill(x0, y0 + 2, x0 + 1, y1 - 2, accent);
    }
}
