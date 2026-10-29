package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Pixel-Note unten links, im Stil der Voice-Chat-Symbole (weißer Rand, dunkelgraue Füllung).
 * Sie erscheint, solange ein Soundboard-Sound läuft.
 */
public final class SoundIcon {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int FILL = 0xFF4A4D52;

    /** '#' = Füllung. Der weiße Rand wird automatisch außen herum gezeichnet. */
    private static final String[] MASK = {
        "....########..",
        "....########..",
        "....##....##..",
        "....##....##..",
        "....##....##..",
        "....##....##..",
        "....##....##..",
        "..####..####..",
        ".#####.#####..",
        ".#####.#####..",
        "..###...###...",
    };

    private SoundIcon() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Icon-Fehler", e);
        }
    }

    private static boolean on(int r, int c) {
        return r >= 0 && r < MASK.length && c >= 0 && c < MASK[r].length() && MASK[r].charAt(c) == '#';
    }

    private static void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || !Soundboard.isActive()) return;
        int h = mc.getWindow().getScaledHeight();
        int x = Config.INSTANCE.noteX;
        int y = h - Config.INSTANCE.noteY - (MASK.length + 2);
        int w = MASK[0].length();
        // Rand zuerst (ein Pixel um die Füllung herum), dann die Füllung darüber
        for (int r = -1; r <= MASK.length; r++) {
            for (int c = -1; c <= w; c++) {
                if (on(r, c)) continue;
                if (on(r - 1, c) || on(r + 1, c) || on(r, c - 1) || on(r, c + 1)) {
                    ctx.fill(x + c + 1, y + r + 1, x + c + 2, y + r + 2, WHITE);
                }
            }
        }
        for (int r = 0; r < MASK.length; r++) {
            for (int c = 0; c < w; c++) {
                if (on(r, c)) ctx.fill(x + c + 1, y + r + 1, x + c + 2, y + r + 2, FILL);
            }
        }
    }
}
