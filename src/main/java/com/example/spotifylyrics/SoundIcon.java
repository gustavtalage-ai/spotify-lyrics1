package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Kleine Note unten links (neben den Voice-Chat-Symbolen), solange ein Soundboard-Sound läuft. */
public final class SoundIcon {
    private static final String[] NOTE = {
        "......##......",
        "......####....",
        "......##.###..",
        "......##...##.",
        "......##....#.",
        "......##......",
        "......##......",
        "..#####.......",
        ".#######......",
        ".#######......",
        "..#####.......",
    };

    private SoundIcon() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Icon-Fehler", e);
        }
    }

    private static void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || !Soundboard.isActive()) return;
        int h = mc.getWindow().getScaledHeight();
        int x = Config.INSTANCE.iconX;
        int y = h - Config.INSTANCE.iconY - NOTE.length;
        boolean beat = (System.currentTimeMillis() / 450) % 2 == 0; // sanftes Pulsieren
        Ui.round(ctx, x - 5, y - 4, x + NOTE[0].length() + 4, y + NOTE.length + 4, beat ? 0x50AA00FF : 0x30AA00FF);
        Ui.card(ctx, x - 3, y - 2, x + NOTE[0].length() + 2, y + NOTE.length + 2, 0xFFAA00FF, 0xE0140A24);
        paint(ctx, x + 1, y + 1, 0xA0000000); // Schatten
        paint(ctx, x, y, 0xFFFFFFFF);
    }

    private static void paint(DrawContext ctx, int x, int y, int color) {
        for (int r = 0; r < NOTE.length; r++) {
            String row = NOTE[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') { c++; continue; }
                int s = c;
                while (c < row.length() && row.charAt(c) == '#') c++;
                ctx.fill(x + s, y + r, x + c, y + r + 1, color);
            }
        }
    }
}
