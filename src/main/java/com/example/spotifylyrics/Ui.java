package com.example.spotifylyrics;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Kleine Zeichenhelfer für das Design (nur einfache Rechtecke, darum in jeder Version sicher). */
final class Ui {
    private Ui() {}

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

    /** Abgerundetes Feld mit Rand. */
    static void card(DrawContext c, int x0, int y0, int x1, int y1, int border, int fill) {
        round(c, x0, y0, x1, y1, border);
        round(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }

    static boolean in(int mx, int my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    /** Moderner Knopf: abgerundet, Text mittig. Deckt den normalen Knopf darunter komplett ab. */
    static void btn(DrawContext c, TextRenderer tr, int x0, int y0, int x1, int y1, String label, boolean hov, int bg, int bgHov, int fg) {
        c.fill(x0, y0, x1, y1, 0xFF0F1117);
        round2(c, x0, y0, x1, y1, hov ? bgHov : bg);
        String t = tr.trimToWidth(label, Math.max(4, x1 - x0 - 8));
        c.drawTextWithShadow(tr, t, x0 + (x1 - x0 - tr.getWidth(t)) / 2, y0 + (y1 - y0 - 8) / 2, fg);
    }

    /** Schalter mit Name links und kleinem Ein/Aus-Schieber rechts. */
    static void toggle(DrawContext c, TextRenderer tr, int x0, int y0, int x1, int y1, String label, boolean on, boolean hov) {
        c.fill(x0, y0, x1, y1, 0xFF0F1117);
        round2(c, x0, y0, x1, y1, hov ? 0xFF2A3040 : 0xFF1F232E);
        c.drawTextWithShadow(tr, tr.trimToWidth(label, Math.max(4, x1 - x0 - 44)), x0 + 8, y0 + (y1 - y0 - 8) / 2, on ? 0xFFFFFFFF : 0xFF9AA0AE);
        int sx1 = x1 - 8, sx0 = sx1 - 22, sy0 = y0 + (y1 - y0 - 10) / 2;
        round(c, sx0, sy0, sx1, sy0 + 10, on ? 0xFF4ADE80 : 0xFF3A3F50);
        int kx = on ? sx1 - 9 : sx0 + 1;
        round(c, kx, sy0 + 1, kx + 8, sy0 + 9, 0xFFFFFFFF);
    }

    /** Abschnitts-Ueberschrift mit feiner Linie. */
    static void section(DrawContext c, TextRenderer tr, int x, int y, String text, int width) {
        c.drawTextWithShadow(tr, text, x, y, 0xFFC77DFF);
        int lx = x + tr.getWidth(text) + 6;
        c.fill(lx, y + 4, x + width - 4, y + 5, 0x22FFFFFF);
    }

    /** Dunkler Hintergrund mit Panel, Leuchtrand und Kopfzeile (für alle Menüs gleich). */
    static void panel(DrawContext c, int sw, int sh, int px0, int py0, int px1, int py1, int head) {
        c.fill(0, 0, sw, sh, 0xB0050609);
        round2(c, px0 - 5, py0 - 5, px1 + 5, py1 + 5, 0x20AA00FF);
        round2(c, px0 - 3, py0 - 3, px1 + 3, py1 + 3, 0x28AA00FF);
        round2(c, px0 - 1, py0 - 1, px1 + 1, py1 + 1, 0xFFAA00FF);
        round2(c, px0, py0, px1, py1, 0xFF0F1117);
        round2(c, px0, py0, px1, py0 + head, 0xFF181B24);
        c.fill(px0, py0 + head - 4, px1, py0 + head, 0xFF181B24);
        c.fill(px0 + 1, py0 + head, px1 - 1, py0 + head + 1, 0xFFAA00FF);
        c.fill(px0 + 1, py0 + head + 1, px1 - 1, py0 + head + 3, 0x20AA00FF);
    }
}
