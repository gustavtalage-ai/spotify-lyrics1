package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zeichnet die Lyrics in drei Stilen (Kasten, Leiste, Karaoke): verschiebbar, skalierbar,
 * mit wählbarer Farbe, Hintergrund und Textart (ganze Sätze, Wort für Wort, nur ein Wort).
 */
public class LyricsHud {
    private static final int W = 204, PAD = 9, LINE_H = 11;
    /** Spotify meldet die Position etwas zu spät (Netz und Audio-Puffer): die Lyrics laufen darum minimal voraus. */
    private static final long LEAD_SPOTIFY_MS = 280;
    private static final long LEAD_SOUND_MS = 120;

    static final String[] STYLES = {"Kasten", "Leiste", "Karaoke"};
    static final String[] UNITS = {"Ganze Sätze", "Wort für Wort", "Nur ein Wort"};
    static final String[] COLORS = {"Weiß", "Cyan", "Grün", "Gelb", "Orange", "Pink", "Rot", "Lichtshow"};
    private static final int[] COLOR_RGB = {0xFFFFFF, 0x38D6F8, 0x4ADE80, 0xFBE24A, 0xFB923C, 0xF472B6, 0xFF5A5A, -1};
    static final String[] BGS = {"Dunkel", "Glas", "Keiner"};

    /** Eine Textzeile; Position und Helligkeit gleiten weich zum Ziel. */
    private static final class Anim {
        String text = "";
        float y, a, e;          // aktuell: Höhe, Deckkraft, Betonung
        float ty, ta, te;       // Ziel
        float fill;             // 0..1: wie weit die Zeile "gesungen" ist
        boolean live, ctr;
    }

    /** Zustand einer Anzeige (echte Anzeige und Vorschau haben je einen eigenen). */
    private static final class St {
        final Map<Integer, Anim> anims = new LinkedHashMap<>();
        float panelH = -1f, curW = -1f, appear = 0f;
        long lastNanos = 0;
        String lastSource = "";
        int rx, ry, rw, rh;

        void reset() {
            anims.clear();
            panelH = -1f;
            curW = -1f;
            appear = 0f;
        }
    }

    private static final class Data {
        String header = "", status = "", source = "";
        List<Lyrics.Line> lines = List.of();
        long pos, dur, progPos;
        boolean ready, playing;
    }

    private static final St REAL = new St();
    private static final St DEMO = new St();
    private static float enterDy = 9f;

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("HUD-Fehler", e);
        }
    }

    // ---------- kleine Helfer ----------

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

    private static int clampI(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static Text sty(String s, boolean bold) {
        return bold ? Text.literal(s).formatted(Formatting.BOLD) : Text.literal(s);
    }

    private static int tw(TextRenderer tr, String s, boolean bold) {
        return tr.getWidth(sty(s, bold));
    }

    /** Längster Anfang des Textes, der in maxW Pixel passt. */
    private static String prefix(TextRenderer tr, String s, int maxW, boolean bold) {
        int n = 0;
        for (int i = 1; i <= s.length(); i++) {
            if (tr.getWidth(sty(s.substring(0, i), bold)) > maxW) break;
            n = i;
        }
        return s.substring(0, n);
    }

    private static void ring(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        c.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        c.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        c.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        c.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    private static void target(St s, int key, String text, float y, float alpha, float emph, float fill, boolean ctr) {
        Anim a = s.anims.get(key);
        if (a == null) {
            a = new Anim();
            a.y = y + enterDy;      // neue Zeilen schweben von unten herein
            a.a = 0f;
            a.e = emph;
            s.anims.put(key, a);
        }
        a.text = text;
        a.ty = y;
        a.ta = alpha;
        a.te = emph;
        a.fill = fill;
        a.live = true;
        a.ctr = ctr;
    }

    // ---------- Daten ----------

    private static Data realData(Config cfg) {
        // Läuft ein Soundboard-Song mit "Künstler - Titel"-Namen, hat der Vorrang vor Spotify.
        boolean sound = Soundboard.isActive() && !SoundLyrics.key.isEmpty();
        SpotifyApi.State st = SpotifyApi.state;
        if (!sound && st == null) return null;
        Data d = new Data();
        if (sound) {
            d.header = SoundLyrics.header;
            d.lines = SoundLyrics.lines;
            d.status = SoundLyrics.status;
            long sp = Math.max(0, Soundboard.positionMs());
            d.pos = sp + cfg.offsetMs + LEAD_SOUND_MS;
            d.ready = !d.lines.isEmpty();
            d.playing = true;
            d.dur = Soundboard.durationMs();
            d.progPos = sp;
        } else {
            d.header = st.track().artist() + " – " + st.track().title();
            d.lines = Lyrics.lines;
            d.status = Lyrics.status;
            long sp = st.position();
            d.pos = sp + cfg.offsetMs + LEAD_SPOTIFY_MS;
            d.ready = !d.lines.isEmpty() && st.track().id().equals(Lyrics.forTrackId);
            d.playing = st.playing();
            d.dur = st.track().durationMs();
            d.progPos = sp;
        }
        d.source = (sound ? "s:" : "t:") + d.header;
        return d;
    }

    private static final List<Lyrics.Line> DEMO_LINES = List.of(
        new Lyrics.Line(0, "Das ist eine Beispiel-Zeile zum Testen"),
        new Lyrics.Line(4000, "So sieht dein Stil beim Singen aus"),
        new Lyrics.Line(8000, "Und hier kommt die nächste Zeile"),
        new Lyrics.Line(12000, "Noch eine Zeile danach"),
        new Lyrics.Line(16000, "Letzte Zeile der Vorschau"));

    private static Data demoData() {
        Data d = new Data();
        d.header = "Künstler – Titel";
        d.source = "demo";
        d.lines = DEMO_LINES;
        d.ready = true;
        d.playing = true;
        d.dur = 20000;
        d.pos = System.currentTimeMillis() % 20000L;
        d.progPos = d.pos;
        return d;
    }

    /** Beispiel-Lyrics an der eingestellten Stelle (zum Verschieben). Gibt {x, y, Breite, Höhe} zurück. */
    static int[] drawDemo(DrawContext ctx) {
        try {
            paint(ctx, MinecraftClient.getInstance(), DEMO, demoData(), 0, 0, 0, 0f, 0f);
            return new int[] {DEMO.rx, DEMO.ry, DEMO.rw, DEMO.rh};
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Demo-Fehler", e);
            return null;
        }
    }

    /** Vorschau, mittig in ein Feld gezeichnet und passend verkleinert. */
    static void drawPreview(DrawContext ctx, int cx, int cy, int fitW, int fitH) {
        try {
            paint(ctx, MinecraftClient.getInstance(), DEMO, demoData(), 1, cx, cy, fitW, fitH);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Vorschau-Fehler", e);
        }
    }

    // ---------- Position ----------

    private static int[] place(int style, int sw, int sh, float ws, float hs) {
        int[] pos = Config.INSTANCE.lyPos;
        int ix = pos[style * 2], iy = pos[style * 2 + 1];
        int wi = Math.round(ws), hi = Math.round(hs);
        int x, y;
        if (style == 0) {
            x = ix >= 0 ? ix * sw / 1000 : sw - wi - 8;
            y = iy >= 0 ? iy * sh / 1000 : 8;
        } else {
            int c = ix >= 0 ? ix * sw / 1000 : sw / 2;
            x = c - wi / 2;
            y = iy >= 0 ? iy * sh / 1000 : (style == 1 ? sh - 82 : Math.round(sh * 0.62f));
        }
        x = Math.max(0, Math.min(x, Math.max(0, sw - wi)));
        y = Math.max(0, Math.min(y, Math.max(0, sh - hi)));
        return new int[] {x, y};
    }

    /** Neue Stelle speichern. rx, ry = linke obere Ecke der Anzeige, rw = ihre Breite. */
    static void setPos(int rx, int ry, int rw, int sw, int sh, boolean centerX) {
        Config c = Config.INSTANCE;
        int style = clampI(c.lyStyle, 0, 2);
        int ax = style == 0 ? rx : rx + rw / 2;
        c.lyPos[style * 2] = clampI(Math.round(ax * 1000f / Math.max(1, sw)), 0, 1000);
        c.lyPos[style * 2 + 1] = clampI(Math.round(ry * 1000f / Math.max(1, sh)), 0, 1000);
        if (centerX && style != 0) c.lyPos[style * 2] = 500;
    }

    static void resetPos() {
        Arrays.fill(Config.INSTANCE.lyPos, -1);
        Config.INSTANCE.save();
    }

    // ---------- echte Anzeige ----------

    private static void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.INSTANCE;
        if (!cfg.enabled || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditScreen || mc.currentScreen instanceof LyricsScreen) return;
        Data d = realData(cfg);
        if (d == null) {
            REAL.reset();
            REAL.lastSource = "";
            return;
        }
        paint(ctx, mc, REAL, d, 0, 0, 0, 0f, 0f);
    }

    /**
     * Zeichnet die Anzeige. mode 0 = an der eingestellten Stelle, mode 1 = mittig bei (fx, fy),
     * auf fitW x fitH verkleinert (Vorschau).
     */
    private static void paint(DrawContext ctx, MinecraftClient mc, St s, Data d, int mode, int fx, int fy, float fitW, float fitH) {
        Config cfg = Config.INSTANCE;
        TextRenderer tr = mc.textRenderer;
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int style = clampI(cfg.lyStyle, 0, 2);
        int unit = clampI(cfg.lyUnit, 0, 2);
        float sc = Math.max(0.5f, Math.min(3f, cfg.lyScale / 100f));
        if (style == 2) sc *= 1.6f;
        if (unit == 2) sc *= style == 2 ? 1.15f : 1.5f;

        // neuer Song oder neue Einstellung: Animationen zurücksetzen, Anzeige blendet neu ein
        String source = d.source + "#" + style + "#" + unit;
        if (!source.equals(s.lastSource)) {
            s.reset();
            s.lastSource = source;
        }

        long nowN = System.nanoTime();
        float dt = s.lastNanos == 0 ? 0.016f : Math.min(0.1f, (nowN - s.lastNanos) / 1_000_000_000f);
        s.lastNanos = nowN;
        float k = 1f - (float) Math.exp(-dt * 15f);
        float kSlow = 1f - (float) Math.exp(-dt * 8f);
        float t = (float) ((nowN / 1_000_000L) % 3_600_000L) / 1000f;
        s.appear += (1f - s.appear) * kSlow;

        final int maxLocalW = Math.max(110, Math.min(style == 2 ? 300 : 260, (int) (sw * 0.92f / sc) - 24));
        final int innerW = style == 0 ? W - 2 * PAD : maxLocalW;
        final int wrapW = innerW - 16;
        final int padX = style == 0 ? PAD : style == 1 ? 9 : 8;
        final int noteW = style == 1 ? 11 : 0;
        final boolean showTitle = style == 0 && cfg.lyTitle;
        final boolean ctrDefault = style != 0;
        final int maxLines = style == 1 ? 1 : style == 2 ? 2 : 3;
        enterDy = style == 0 ? 9f : 5f;

        // ---- Zielzustand berechnen ----
        for (Anim a : s.anims.values()) a.live = false;
        float ly = style == 0 ? (showTitle ? PAD + LINE_H + 8 : PAD) : style == 1 ? 5 : 7;
        float contentW = 0f;
        boolean hit = false;
        if (!d.ready) {
            List<String> rows = wrap(tr, d.status.isEmpty() ? "♪" : d.status, wrapW, style == 1 ? 1 : 3);
            for (int r = 0; r < rows.size(); r++) {
                target(s, -1000 + r, rows.get(r), ly, 0.8f, 0f, 0f, ctrDefault);
                contentW = Math.max(contentW, tw(tr, rows.get(r), false));
                ly += LINE_H + 1;
            }
        } else {
            List<Lyrics.Line> lines = d.lines;
            int idx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs() <= d.pos) idx = i; else break;
            }
            String c = idx < 0 || lines.get(idx).text().isBlank() ? "♪" : lines.get(idx).text().trim();
            List<String> cur = wrap(tr, c, wrapW, maxLines);
            String[] words = c.split("\\s+");
            int nW = Math.max(1, words.length);

            hit = !cfg.keyword.isBlank() && c.toLowerCase(Locale.ROOT).contains(cfg.keyword.toLowerCase(Locale.ROOT));

            // wie weit ist die aktuelle Zeile schon gesungen?
            float p = 0f;
            if (idx >= 0) {
                long start = lines.get(idx).timeMs();
                long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
                long len = Math.max(400, Math.min(end - start, 5500));
                p = clamp01((d.pos - start) / (float) len * 1.1f);
            }
            int wi = Math.min(nW - 1, (int) (p * nW));
            float wf = clamp01(p * nW - wi);

            if (unit == 2) {
                // nur das gerade gesungene Wort
                for (String wd : words) contentW = Math.max(contentW, tw(tr, wd, true));
                target(s, 5_000_000 + idx * 100 + wi, words[wi], ly, 1f, 1f, wf, true);
                ly += LINE_H + 2;
            } else {
                String prev = style != 1 && cfg.lyPrev ? lineAt(lines, idx - 1) : null;
                if (prev != null) {
                    String pt = tr.trimToWidth(prev, innerW);
                    target(s, (idx - 1) * 10, pt, ly, 0.4f, 0f, 1f, ctrDefault);
                    contentW = Math.max(contentW, tw(tr, pt, false));
                    ly += LINE_H + 1;
                }
                int remaining = wi + 1;
                for (int r = 0; r < cur.size(); r++) {
                    String row = cur.get(r);
                    contentW = Math.max(contentW, tw(tr, row, true));
                    if (unit == 0) {
                        float rf = clamp01(p * cur.size() - r);
                        target(s, idx * 10 + r, row, ly, 1f, 1f, rf, ctrDefault);
                    } else {
                        // Wort für Wort: der Satz baut sich auf
                        String[] rw = row.split(" ");
                        int take = Math.max(0, Math.min(remaining, rw.length));
                        remaining -= take;
                        String vis = String.join(" ", Arrays.copyOf(rw, take));
                        target(s, idx * 10 + r, vis, ly, 1f, 1f, 1f, false);
                    }
                    ly += LINE_H + 2;
                }
                ly += 2;
                if (style != 1) {
                    for (int n = 1; n <= Math.max(0, Math.min(2, cfg.lyNext)); n++) {
                        String nx = lineAt(lines, idx + n);
                        if (nx == null) continue;
                        String nt = tr.trimToWidth(nx, innerW);
                        target(s, (idx + n) * 10, nt, ly, n == 1 ? 0.55f : 0.3f, 0f, 0f, ctrDefault);
                        contentW = Math.max(contentW, tw(tr, nt, false));
                        ly += LINE_H + 1;
                    }
                }
            }
        }

        boolean showBar = cfg.lyProgress && d.dur > 0;
        float wTarget, hTarget;
        if (style == 0) {
            wTarget = W;
            hTarget = ly + PAD + (showBar ? 7 : 1);
        } else if (style == 1) {
            wTarget = Math.max(70f, padX * 2 + noteW + contentW + 2);
            hTarget = 18f;
        } else {
            wTarget = Math.max(80f, contentW + 2 * padX + 2);
            hTarget = ly + 5 + (showBar ? 5 : 0);
        }
        if (style != 0) wTarget = Math.min(wTarget, maxLocalW + 2 * padX + noteW + 2);
        if (s.panelH < 0) s.panelH = hTarget;
        s.panelH += (hTarget - s.panelH) * kSlow;
        if (s.curW < 0) s.curW = wTarget;
        s.curW += (wTarget - s.curW) * kSlow;

        // ---- Animation weiterrechnen ----
        for (Iterator<Anim> it = s.anims.values().iterator(); it.hasNext(); ) {
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

        // ---- Größe und Stelle ----
        int w = Math.max(20, Math.round(s.curW)), h = Math.max(10, Math.round(s.panelH));
        if (mode == 1 && fitW > 0 && fitH > 0) sc = Math.min(sc, Math.min(fitW / w, fitH / h));
        float ws = w * sc, hs = h * sc;
        int x, y;
        if (mode == 1) {
            x = Math.round(fx - ws / 2f);
            y = Math.round(fy - hs / 2f);
        } else {
            int[] pp = place(style, sw, sh, ws, hs);
            x = pp[0];
            y = pp[1];
            if (style == 0) x += Math.round((1f - s.appear) * 16f);
            else y += Math.round((1f - s.appear) * 8f);
        }
        s.rx = x;
        s.ry = y;
        s.rw = Math.round(ws);
        s.rh = Math.round(hs);

        // ---- Farben ----
        float ga = Math.min(1f, s.appear * 1.2f);
        int ci = clampI(cfg.lyColor, 0, COLOR_RGB.length - 1);
        int base = COLOR_RGB[ci];
        int acc1, acc2;
        if (base < 0) {
            acc1 = Lightshow.accent(t, 0f);
            acc2 = Lightshow.accent(t, 1f);
        } else {
            acc1 = base;
            acc2 = mix(base, 0xFFFFFF, 0.35f);
        }
        int sung = base == 0xFFFFFF ? 0xFFFFFF : base < 0 ? mix(0xFFFFFF, acc1, 0.55f) : base;
        if (hit) sung = mix(sung, acc1, 0.5f);
        int dimC = mix(sung, 0x8793A5, 0.5f);
        int lineC = mix(sung, 0xB8C4D4, 0.6f);
        int bg = clampI(cfg.lyBg, 0, 2);
        float bgA = bg == 0 ? 0.84f : bg == 1 ? 0.42f : 0f;
        float pulse = hit ? 0.5f + 0.5f * (float) Math.sin(t * 7f) : 0.35f;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        try {
            m.translate((float) x, (float) y);
            m.scale(sc, sc);

            // Hintergrund und Rand
            if (bg != 2 || hit) {
                if (hit) ring(ctx, -2, -2, w + 2, h + 2, argb(acc1, 0.12f * pulse * ga));
                ring(ctx, -1, -1, w + 1, h + 1, argb(hit ? acc1 : 0xFFFFFF, (hit ? 0.35f + 0.3f * pulse : 0.13f) * ga));
            }
            if (bg != 2) Ui.round2(ctx, 0, 0, w, h, argb(0x0A0C12, bgA * ga));

            if (style == 0) {
                if (bg != 2) {
                    for (int i = 0; i < w - 8; i += 2) {
                        int col = mix(acc1, acc2, i / (float) (w - 8));
                        ctx.fill(4 + i, 1, Math.min(6 + i, w - 4), 2, argb(col, 0.9f * ga));
                    }
                }
                if (showTitle) {
                    String hdr = tr.trimToWidth(d.header, innerW - 26);
                    ctx.drawTextWithShadow(tr, "♪", PAD, PAD, argb(acc1, ga));
                    ctx.drawTextWithShadow(tr, hdr, PAD + 11, PAD, argb(0xEAF4FF, ga));
                    int bx = w - PAD - 11;
                    for (int i = 0; i < 3; i++) {
                        float hh = d.playing ? 2f + 5f * (0.5f + 0.5f * (float) Math.sin(t * (7f + i * 2.3f) + i * 1.7f)) : 2f;
                        int bh = Math.round(hh);
                        ctx.fill(bx + i * 4, PAD + 8 - bh, bx + i * 4 + 3, PAD + 8, argb(mix(acc1, acc2, i / 2f), 0.9f * ga));
                    }
                    ctx.fill(PAD, PAD + LINE_H + 3, w - PAD, PAD + LINE_H + 4, argb(0xFFFFFF, 0.08f * ga));
                }
            } else if (style == 1) {
                ctx.drawTextWithShadow(tr, "♪", padX, 5, argb(acc1, ga));
            }

            // Zeilen
            int baseX = padX + noteW;
            int wC = w - 2 * padX - noteW;
            for (Anim a : s.anims.values()) {
                float al = a.a * ga;
                if (al < 0.03f || a.text.isEmpty()) continue;
                boolean emph = a.e > 0.55f;
                Text styled = sty(a.text, emph);
                int tww = tr.getWidth(styled);
                int tx = baseX + (a.ctr ? Math.max(0, (wC - tww) / 2) : 0);
                int ty = Math.round(a.y);
                if (emph) {
                    ctx.drawTextWithShadow(tr, styled, tx, ty, argb(dimC, al * 0.62f));      // noch nicht gesungen: gedimmt
                    if (a.fill >= 0.995f) {
                        ctx.drawTextWithShadow(tr, styled, tx, ty, argb(sung, al));
                    } else if (a.fill > 0.01f) {
                        String pre = prefix(tr, a.text, (int) (tww * a.fill), true);
                        if (!pre.isEmpty()) ctx.drawTextWithShadow(tr, sty(pre, true), tx, ty, argb(sung, al));
                    }
                } else {
                    ctx.drawTextWithShadow(tr, styled, tx, ty, argb(lineC, al));
                }
            }

            // Fortschritt
            if (showBar || (style == 1 && cfg.lyProgress && d.dur > 0)) {
                int x0, x1, by;
                if (style == 0) {
                    x0 = PAD;
                    x1 = w - PAD;
                    by = h - PAD + 1;
                } else if (style == 1) {
                    x0 = 5;
                    x1 = w - 5;
                    by = h - 3;
                } else {
                    x0 = padX;
                    x1 = w - padX;
                    by = h - 6;
                }
                int span = Math.max(1, x1 - x0);
                ctx.fill(x0, by, x1, by + 2, argb(0xFFFFFF, 0.12f * ga));
                int fw = (int) (span * Math.max(0L, Math.min(d.progPos, d.dur)) / (double) d.dur);
                for (int i = 0; i < fw; i += 2) {
                    ctx.fill(x0 + i, by, Math.min(x0 + i + 2, x0 + fw), by + 2, argb(mix(acc1, acc2, i / (float) span), 0.95f * ga));
                }
            }
        } finally {
            m.popMatrix();
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
