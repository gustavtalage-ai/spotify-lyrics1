package com.example.spotifylyrics;

import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/** Kleine selbst gezeichnete Pixel-Symbole (12x12) für die Menü-Kästen, statt Minecraft-Items. */
final class Icons {
    private Icons() {}

    private static final Map<String, int[]> ICONS = new HashMap<>();
    private static final int[] GEAR = {16, 186, 124, 198, 455, 198, 124, 186, 16};

    static {
        ICONS.put("gavel", new int[] {1792, 3968, 1984, 992, 448, 96, 48, 24, 12, 6, 0, 4032});
        ICONS.put("play", new int[] {512, 768, 896, 960, 992, 1008, 1008, 992, 960, 896, 768, 512});
        ICONS.put("cog", new int[] {96, 612, 2046, 924, 1806, 3855, 3855, 1806, 924, 2046, 612, 96});
        ICONS.put("mic", new int[] {240, 504, 504, 504, 504, 504, 240, 1122, 612, 504, 96, 504});
        ICONS.put("sword", new int[] {3, 7, 14, 28, 568, 880, 480, 192, 480, 816, 1536, 3072});
        ICONS.put("shield", new int[] {2046, 2046, 1542, 1542, 1638, 1782, 1638, 780, 780, 408, 240, 96});
        ICONS.put("cross", new int[] {96, 96, 96, 0, 96, 3831, 3831, 96, 0, 96, 96, 96});
        ICONS.put("target", new int[] {240, 780, 1026, 1122, 2193, 2313, 2313, 2193, 1122, 1026, 780, 240});
        ICONS.put("ring", new int[] {240, 780, 1026, 1026, 2049, 2049, 2049, 2049, 1026, 1026, 780, 240});
        ICONS.put("bolt", new int[] {28, 56, 112, 224, 508, 1016, 112, 224, 448, 896, 1536, 3072});
        ICONS.put("heart", new int[] {0, 1806, 3999, 4095, 4095, 4095, 2046, 1020, 504, 240, 96, 0});
        ICONS.put("clock", new int[] {240, 780, 1058, 1058, 2081, 2109, 2049, 2049, 1026, 1026, 780, 240});
        ICONS.put("chart", new int[] {3, 3, 51, 51, 51, 819, 819, 819, 819, 819, 4095, 4095});
        ICONS.put("note", new int[] {127, 127, 65, 65, 65, 65, 65, 79, 95, 2014, 4044, 1920});
        ICONS.put("run", new int[] {0, 2112, 3168, 1584, 792, 396, 396, 792, 1584, 3168, 2112, 0});
        ICONS.put("armor", new int[] {1542, 3855, 4095, 4095, 2046, 1020, 1020, 1020, 1020, 1020, 1020, 504});
        ICONS.put("totem", new int[] {240, 504, 360, 504, 240, 2046, 4095, 1530, 504, 408, 408, 408});
        ICONS.put("info", new int[] {240, 780, 1122, 1122, 2049, 2145, 2145, 2145, 1122, 1122, 780, 240});
        ICONS.put("mouse", new int[] {504, 548, 1058, 1058, 1058, 2046, 1026, 1026, 1026, 1026, 516, 504});
        ICONS.put("fire", new int[] {64, 192, 224, 484, 500, 1020, 2046, 1950, 1806, 1806, 780, 504});
        ICONS.put("potion", new int[] {240, 96, 96, 144, 264, 516, 1026, 2046, 2046, 2046, 1020, 504});
        ICONS.put("apple", new int[] {24, 48, 942, 2046, 4095, 4095, 4095, 4095, 2046, 2046, 1020, 408});
        ICONS.put("eye", new int[] {0, 0, 240, 780, 1122, 2289, 2289, 1122, 780, 240, 0, 0});
        ICONS.put("people", new int[] {780, 1950, 1950, 780, 0, 1950, 4095, 4095, 4095, 4095, 0, 0});
        ICONS.put("star", new int[] {96, 96, 240, 4095, 2046, 1020, 504, 504, 924, 780, 1542, 0});
        ICONS.put("hourglass", new int[] {4095, 1026, 516, 264, 144, 96, 96, 144, 360, 756, 1530, 4095});
        ICONS.put("skull", new int[] {504, 1020, 2046, 1638, 1638, 2046, 924, 504, 360, 0, 0, 0});
        ICONS.put("ruler", new int[] {0, 0, 0, 4095, 2731, 2731, 2051, 2051, 4095, 0, 0, 0});
        ICONS.put("bar", new int[] {0, 0, 0, 0, 4095, 4065, 4065, 4095, 0, 0, 0, 0});
        ICONS.put("bang", new int[] {96, 240, 240, 240, 240, 96, 96, 0, 96, 240, 96, 0});
        ICONS.put("grid", new int[] {2184, 4095, 2184, 2184, 2184, 4095, 2184, 2184, 2184, 4095, 2184, 2184});
        ICONS.put("mirror", new int[] {96, 2145, 3075, 3687, 3951, 3855, 3951, 3687, 3075, 2145, 96, 0});
        ICONS.put("cube", new int[] {96, 408, 1542, 2457, 2145, 2081, 2081, 2081, 2081, 1574, 428, 112});
        ICONS.put("packs", new int[] {255, 129, 1021, 517, 4085, 2069, 2071, 2068, 2076, 2064, 4080, 0});
    }

    /** Symbol zeichnen (oben links bei x, y); s = Pixelgröße. */
    static void draw(DrawContext c, String name, int x, int y, int s, int color) {
        int[] rows = ICONS.get(name);
        if (rows == null) rows = ICONS.get("info");
        for (int r = 0; r < 12; r++) {
            int bits = rows[r];
            for (int col = 0; col < 12; col++) {
                if (((bits >> (11 - col)) & 1) != 0) c.fill(x + col * s, y + r * s, x + col * s + s, y + r * s + s, color);
            }
        }
    }

    /** Zahnrad (9x9). */
    static void gear(DrawContext c, int x, int y, int color) {
        for (int r = 0; r < 9; r++) {
            for (int col = 0; col < 9; col++) {
                if (((GEAR[r] >> (8 - col)) & 1) != 0) c.fill(x + col, y + r, x + col + 1, y + r + 1, color);
            }
        }
    }
}
