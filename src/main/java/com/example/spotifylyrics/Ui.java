package com.example.spotifylyrics;

import net.minecraft.client.gui.DrawContext;

/** Kleine Zeichenhelfer für das Design (nur einfache Rechtecke, darum in jeder Version sicher). */
final class Ui {
    private Ui() {}

    /** Rechteck mit abgerundeten Ecken (1 Pixel abgeschnitten). */
    static void round(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        c.fill(x0 + 1, y0, x1 - 1, y1, color);
        c.fill(x0, y0 + 1, x1, y1 - 1, color);
    }

    /** Abgerundetes Feld mit Rand. */
    static void card(DrawContext c, int x0, int y0, int x1, int y1, int border, int fill) {
        round(c, x0, y0, x1, y1, border);
        round(c, x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
    }
}
