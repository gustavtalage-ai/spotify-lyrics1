package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.Locale;

/** Zeichnet den Lyrics-Kasten oben rechts (im Spotify-Stil). */
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
        final int W = 176, pad = 8, inner = W - 2 * pad, lineH = 11;
        header = tr.trimToWidth(header, inner);

        List<String> cur;
        String prev = null, next1 = null, next2 = null;
        if (!ready) {
            cur = wrap(tr, status.isEmpty() ? "♪" : status, inner, 3);
        } else {
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs() <= pos) idx = i; else break;
            }
            String c = idx < 0 || lines.get(idx).text().isBlank() ? "♪" : lines.get(idx).text();
            cur = wrap(tr, c, inner, 3);
            prev = lineAt(lines, idx - 1);
            next1 = lineAt(lines, idx + 1);
            next2 = lineAt(lines, idx + 2);
            if (idx < 0) { prev = null; }
        }
        String curText = String.join(" ", cur);
        boolean hit = !cfg.keyword.isBlank()
                && curText.toLowerCase(Locale.ROOT).contains(cfg.keyword.toLowerCase(Locale.ROOT));

        int rows = 1 + cur.size() + (prev != null ? 1 : 0) + (next1 != null ? 1 : 0) + (next2 != null ? 1 : 0);
        int h = pad + lineH + 5 + rows * lineH - lineH + 4 + pad;
        if (sound && Soundboard.durationMs() > 0) h += 5;
        int x = screenW - W - 6;
        int y = 6;

        Ui.round(ctx, x - 1, y - 1, x + W + 1, y + h + 1, 0x40AA00FF);   // Leuchten
        Ui.round(ctx, x, y, x + W, y + h, 0xEE1A1030);                    // dunkles Lila wie bei Spotify
        if (hit) { // lila Rahmen, 2 Pixel dick
            int t = 2;
            ctx.fill(x, y, x + W, y + t, PURPLE);
            ctx.fill(x, y + h - t, x + W, y + h, PURPLE);
            ctx.fill(x, y, x + t, y + h, PURPLE);
            ctx.fill(x + W - t, y, x + W, y + h, PURPLE);
        }

        int tx = x + pad;
        int ty = y + pad;
        ctx.drawTextWithShadow(tr, header, tx, ty, 0xFFC9A0FF);            // Titel
        ty += lineH + 5;
        if (prev != null) { ctx.drawTextWithShadow(tr, tr.trimToWidth(prev, inner), tx, ty, 0xFF6F6A85); ty += lineH; }
        for (String c : cur) { ctx.drawTextWithShadow(tr, c, tx, ty, 0xFFFFFFFF); ty += lineH; } // aktuelle Zeile hell
        if (next1 != null) { ctx.drawTextWithShadow(tr, tr.trimToWidth(next1, inner), tx, ty, 0xFF8F8AA8); ty += lineH; }
        if (next2 != null) { ctx.drawTextWithShadow(tr, tr.trimToWidth(next2, inner), tx, ty, 0xFF6F6A85); }

        if (sound && Soundboard.durationMs() > 0) { // Fortschrittsbalken unten
            long p = Math.max(0, Math.min(Soundboard.positionMs(), Soundboard.durationMs()));
            int by = y + h - pad + 1;
            ctx.fill(tx, by, x + W - pad, by + 2, 0x40FFFFFF);
            ctx.fill(tx, by, tx + (int) ((W - 2 * pad) * p / Soundboard.durationMs()), by + 2, PURPLE);
        }
    }

    private static String lineAt(List<Lyrics.Line> lines, int i) {
        if (i < 0 || i >= lines.size()) return null;
        String t = lines.get(i).text();
        return t == null || t.isBlank() ? null : t;
    }

    /** Bricht einen Text in Zeilen der Breite maxW um (höchstens maxLines). */
    private static List<String> wrap(TextRenderer tr, String s, int maxW, int maxLines) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (tr.getWidth(test) <= maxW) {
                line = new StringBuilder(test);
            } else {
                if (line.length() > 0) out.add(line.toString());
                line = new StringBuilder(tr.getWidth(word) <= maxW ? word : tr.trimToWidth(word, maxW));
            }
            if (out.size() >= maxLines) break;
        }
        if (line.length() > 0 && out.size() < maxLines) out.add(line.toString());
        if (out.isEmpty()) out.add("♪");
        return out;
    }
}
