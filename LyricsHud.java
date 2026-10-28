package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.Locale;

/** Zeichnet die Lyrics-Leiste oben in der Mitte des Bildschirms. */
public class LyricsHud {
    private static final int PURPLE = 0xFFAA00FF;
    private static final int BACKGROUND = 0x50000000; // stark durchsichtig

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("HUD-Fehler", e);
        }
    }

    private static void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.INSTANCE;
        if (!cfg.enabled || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        int screenW = mc.getWindow().getScaledWidth();
        int maxText = (int) (screenW * 0.7);

        // Läuft ein Soundboard-Song mit "Künstler - Titel"-Namen, hat der Vorrang vor Spotify.
        boolean sound = Soundboard.isActive() && !SoundLyrics.key.isEmpty();
        SpotifyApi.State st = SpotifyApi.state;
        if (!sound && st == null) return;

        String header;
        List<Lyrics.Line> lines;
        String status;
        long pos;
        boolean ready;
        if (sound) {
            header = SoundLyrics.header;
            lines = SoundLyrics.lines;
            status = SoundLyrics.status;
            pos = Soundboard.positionMs() + cfg.offsetMs;
            ready = !lines.isEmpty();
        } else {
            header = st.track().artist() + " – " + st.track().title();
            lines = Lyrics.lines;
            status = Lyrics.status;
            pos = st.position() + cfg.offsetMs;
            ready = !lines.isEmpty() && st.track().id().equals(Lyrics.forTrackId);
        }
        header = tr.trimToWidth(header, maxText);

        String current;
        String next = null;
        if (!ready) {
            current = status.isEmpty() ? "♪" : status;
        } else {
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs() <= pos) idx = i; else break;
            }
            current = idx < 0 || lines.get(idx).text().isBlank() ? "♪" : lines.get(idx).text();
            if (idx + 1 < lines.size() && !lines.get(idx + 1).text().isBlank()) next = lines.get(idx + 1).text();
        }
        current = tr.trimToWidth(current, maxText);
        if (next != null) next = tr.trimToWidth(next, maxText);

        boolean hit = !cfg.keyword.isBlank()
                && current.toLowerCase(Locale.ROOT).contains(cfg.keyword.toLowerCase(Locale.ROOT));

        int textW = Math.max(tr.getWidth(header), Math.max(tr.getWidth(current), next == null ? 0 : tr.getWidth(next)));
        int w = textW + 16;
        int lineH = 11;
        int h = 8 + lineH * (next == null ? 2 : 3);
        int x = (screenW - w) / 2;
        int y = 6;

        ctx.fill(x, y, x + w, y + h, BACKGROUND);
        if (hit) { // lila Kasten, 2 Pixel dick (aus vier Rechtecken, ohne versionsabhängige Methode)
            int t = 2;
            ctx.fill(x, y, x + w, y + t, PURPLE);               // oben
            ctx.fill(x, y + h - t, x + w, y + h, PURPLE);       // unten
            ctx.fill(x, y, x + t, y + h, PURPLE);               // links
            ctx.fill(x + w - t, y, x + w, y + h, PURPLE);       // rechts
        }

        int ty = y + 5;
        centered(ctx, tr, header, screenW, ty, 0xFFB0B0B0);
        ty += lineH;
        centered(ctx, tr, current, screenW, ty, 0xFFFFFFFF);
        ty += lineH;
        if (next != null) centered(ctx, tr, next, screenW, ty, 0xFF909090);
    }

    private static void centered(DrawContext ctx, TextRenderer tr, String s, int screenW, int y, int color) {
        ctx.drawTextWithShadow(tr, s, (screenW - tr.getWidth(s)) / 2, y, color);
    }
}
