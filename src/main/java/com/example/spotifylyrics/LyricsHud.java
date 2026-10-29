package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Zeichnet den Lyrics-Kasten oben rechts: dunkles Glas, weiche Übergänge, Zeile füllt sich im Takt. */
public class LyricsHud {
    private static final int W = 204, PAD = 9, LINE_H = 11;
    /** Spotify meldet die Position etwas zu spät (Netz und Audio-Puffer): die Lyrics laufen darum minimal voraus. */
    private static final long LEAD_SPOTIFY_MS = 280;
    private static final long LEAD_SOUND_MS = 120;

    /** Eine Textzeile im Kasten; Position und Helligkeit gleiten weich zum Ziel. */
    private static final class Anim {
        String text = "";
        float y, a, e;          // aktuell: Höhe, Deckkraft, Betonung
        float ty, ta, te;       // Ziel
        float fill;             // 0..1: wie weit die aktuelle Zeile "gesungen" ist
        boolean live;
    }

    private static final Map<Integer, Anim> ANIMS = new LinkedHashMap<>();
    private static float panelH = -1f;
    private static float appear = 0f;
    private static long lastNanos = 0;
    private static String lastSource = "";

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("HUD-Fehler", e);
        }
    }

    private static int argb(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void target(int key, String text, float y, float alpha, float emph, float fill, float ty0) {
        Anim a = ANIMS.get(key);
        if (a == null) {
            a = new Anim();
            a.y = ty0 + 9;      // neue Zeilen schweben von unten herein
            a.a = 0f;
            a.e = emph;
            ANIMS.put(key, a);
        }
        a.text = text;
        a.ty = y;
        a.ta = alpha;
        a.te = emph;
        a.fill = fill;
        a.live = true;
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
        if (!sound && st == null) {
            appear = 0f;
            panelH = -1f;
            ANIMS.clear();
            lastSource = "";
            return;
        }

        String header;
        List<Lyrics.Line> lines;
        String status;
        long pos;
        boolean ready, playing;
        long dur, progPos;
        if (sound) {
            header = SoundLyrics.header;
            lines = SoundLyrics.lines;
            status = SoundLyrics.status;
            long sp = Math.max(0, Soundboard.positionMs());
            pos = sp + cfg.offsetMs + LEAD_SOUND_MS;
            ready = !lines.isEmpty();
            playing = true;
            dur = Soundboard.durationMs();
            progPos = sp;
        } else {
            header = st.track().artist() + " – " + st.track().title();
            lines = Lyrics.lines;
            status = Lyrics.status;
            long sp = st.position();
            pos = sp + cfg.offsetMs + LEAD_SPOTIFY_MS;
            ready = !lines.isEmpty() && st.track().id().equals(Lyrics.forTrackId);
            playing = st.playing();
            dur = st.track().durationMs();
            progPos = sp;
        }

        // neuer Song: Animationen zurücksetzen, Kasten blendet neu ein
        String source = (sound ? "s:" : "t:") + header;
        if (!source.equals(lastSource)) {
            lastSource = source;
            ANIMS.clear();
            appear = 0f;
            panelH = -1f;
        }

        long nowN = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (nowN - lastNanos) / 1_000_000_000f);
        lastNanos = nowN;
        float k = 1f - (float) Math.exp(-dt * 15f);
        float kSlow = 1f - (float) Math.exp(-dt * 8f);
        float t = (float) ((nowN / 1_000_000L) % 3_600_000L) / 1000f;
        appear += (1f - appear) * kSlow;

        final int inner = W - 2 * PAD;
        String hdr = tr.trimToWidth(header, inner - 26);

        // ---- Zielzustand berechnen ----
        for (Anim a : ANIMS.values()) a.live = false;
        float ly = PAD + LINE_H + 8;
        boolean hit = false;
        if (!ready) {
            List<String> rows = wrap(tr, status.isEmpty() ? "♪" : status, inner - 16, 3);
            for (int r = 0; r < rows.size(); r++) {
                target(-1000 + r, rows.get(r), ly, 0.8f, 0f, 0f, ly);
                ly += LINE_H + 1;
            }
        } else {
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs() <= pos) idx = i; else break;
            }
            String c = idx < 0 || lines.get(idx).text().isBlank() ? "♪" : lines.get(idx).text();
            List<String> cur = wrap(tr, c, inner - 16, 3);
            String prev = idx < 0 ? null : lineAt(lines, idx - 1);
            String next1 = lineAt(lines, idx + 1);
            String next2 = lineAt(lines, idx + 2);

            hit = !cfg.keyword.isBlank()
                    && String.join(" ", cur).toLowerCase(Locale.ROOT).contains(cfg.keyword.toLowerCase(Locale.ROOT));

            // wie weit ist die aktuelle Zeile schon gesungen?
            float p = 0f;
            if (idx >= 0) {
                long start = lines.get(idx).timeMs();
                long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
                long len = Math.max(400, Math.min(end - start, 5500));
                p = Math.max(0f, Math.min(1f, (pos - start) / (float) len * 1.1f));
            }

            if (prev != null) {
                target((idx - 1) * 10, tr.trimToWidth(prev, inner), ly, 0.4f, 0f, 1f, ly);
                ly += LINE_H + 1;
            }
            for (int r = 0; r < cur.size(); r++) {
                float rf = Math.max(0f, Math.min(1f, p * cur.size() - r));
                target(idx * 10 + r, cur.get(r), ly, 1f, 1f, rf, ly);
                ly += LINE_H + 2;
            }
            ly += 2;
            if (next1 != null) {
                target((idx + 1) * 10, tr.trimToWidth(next1, inner), ly, 0.55f, 0f, 0f, ly);
                ly += LINE_H + 1;
            }
            if (next2 != null) {
                target((idx + 2) * 10, tr.trimToWidth(next2, inner), ly, 0.3f, 0f, 0f, ly);
                ly += LINE_H + 1;
            }
        }
        boolean showBar = dur > 0;
        float targetH = ly + PAD + (showBar ? 7 : 1);
        if (panelH < 0) panelH = targetH;
        panelH += (targetH - panelH) * kSlow;

        // ---- Animation weiterrechnen ----
        for (Iterator<Anim> it = ANIMS.values().iterator(); it.hasNext(); ) {
            Anim a = it.next();
            if (!a.live) {          // verschwindende Zeile: nach oben wegblenden
                a.ta = 0f;
                a.ty = a.y - 5f;
            }
            a.y += (a.ty - a.y) * k;
            a.a += (a.ta - a.a) * k;
            a.e += (a.te - a.e) * k;
            if (!a.live && a.a < 0.02f) it.remove();
        }

        // ---- Zeichnen ----
        float ga = Math.min(1f, appear * 1.2f);
        int x = screenW - W - 8 + (int) ((1f - appear) * 16f);
        int y = 8;
        int h = Math.round(panelH);
        int acc1 = Lightshow.accent(t, 0f);
        int acc2 = Lightshow.accent(t, 1f);

        float pulse = hit ? 0.5f + 0.5f * (float) Math.sin(t * 7f) : 0.35f;
        Ui.round2(ctx, x - 3, y - 3, x + W + 3, y + h + 3, argb(acc1, (hit ? 0.10f : 0.05f) * pulse * ga));
        Ui.round2(ctx, x - 1, y - 1, x + W + 1, y + h + 1, argb(hit ? acc1 : 0xFFFFFF, (hit ? 0.35f + 0.3f * pulse : 0.13f) * ga));
        Ui.round2(ctx, x, y, x + W, y + h, argb(0x0A0C12, 0.84f * ga));

        // dünne Farblinie oben
        for (int i = 0; i < W - 8; i += 2) {
            int col = mix(acc1, acc2, i / (float) (W - 8));
            ctx.fill(x + 4 + i, y + 1, Math.min(x + 6 + i, x + W - 4), y + 2, argb(col, 0.9f * ga));
        }

        // Kopf: Note, Name, kleine Balken
        ctx.drawTextWithShadow(tr, "♪", x + PAD, y + PAD, argb(acc1, ga));
        ctx.drawTextWithShadow(tr, hdr, x + PAD + 11, y + PAD, argb(0xF1EEFF, ga));
        int bx = x + W - PAD - 11;
        for (int i = 0; i < 3; i++) {
            float hh = playing ? 2f + 5f * (0.5f + 0.5f * (float) Math.sin(t * (7f + i * 2.3f) + i * 1.7f)) : 2f;
            int bh = Math.round(hh);
            ctx.fill(bx + i * 4, y + PAD + 8 - bh, bx + i * 4 + 3, y + PAD + 8, argb(mix(acc1, acc2, i / 2f), 0.9f * ga));
        }
        ctx.fill(x + PAD, y + PAD + LINE_H + 3, x + W - PAD, y + PAD + LINE_H + 4, argb(0xFFFFFF, 0.08f * ga));

        // Zeilen
        int bright = hit ? mix(0xFFFFFF, acc1, 0.5f) : 0xFFFFFF;
        for (Anim a : ANIMS.values()) {
            float al = a.a * ga;
            if (al < 0.03f) continue;
            int tx = x + PAD;
            int ty = y + Math.round(a.y);
            boolean emph = a.e > 0.55f;
            Text styled = emph ? Text.literal(a.text).formatted(Formatting.BOLD) : Text.literal(a.text);
            if (emph) {
                ctx.drawTextWithShadow(tr, styled, tx, ty, argb(0xB4AECB, al * 0.62f));       // noch nicht gesungen: gedimmt
                if (a.fill > 0.01f) {
                    int w = tr.getWidth(styled);
                    if (a.fill >= 0.995f) {
                        ctx.drawTextWithShadow(tr, styled, tx, ty, argb(bright, al));
                    } else {
                        ctx.enableScissor(tx - 1, ty - 2, tx + (int) (w * a.fill), ty + LINE_H + 1);
                        ctx.drawTextWithShadow(tr, styled, tx, ty, argb(bright, al));
                        ctx.disableScissor();
                    }
                }
            } else {
                ctx.drawTextWithShadow(tr, styled, tx, ty, argb(0xDCD8EE, al));
            }
        }

        // Fortschritt unten
        if (showBar) {
            int by = y + h - PAD + 1;
            int x0 = x + PAD, x1 = x + W - PAD;
            ctx.fill(x0, by, x1, by + 2, argb(0xFFFFFF, 0.12f * ga));
            int fw = (int) ((x1 - x0) * Math.max(0L, Math.min(progPos, dur)) / (double) dur);
            for (int i = 0; i < fw; i += 2) {
                ctx.fill(x0 + i, by, Math.min(x0 + i + 2, x0 + fw), by + 2, argb(mix(acc1, acc2, i / (float) (x1 - x0)), 0.95f * ga));
            }
        }
    }

    private static String lineAt(List<Lyrics.Line> lines, int i) {
        if (i < 0 || i >= lines.size()) return null;
        String t = lines.get(i).text();
        return t == null || t.isBlank() ? null : t;
    }

    /** Bricht einen Text in Zeilen der Breite maxW um (höchstens maxLines). */
    private static List<String> wrap(TextRenderer tr, String s, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
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
