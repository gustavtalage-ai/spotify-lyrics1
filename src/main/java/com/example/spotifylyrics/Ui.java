package com.example.spotifylyrics;

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
}
