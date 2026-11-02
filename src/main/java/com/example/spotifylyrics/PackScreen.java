package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Random;

/** Kisten-Shop: drei Kisten für Coins. Öffnen wie in CS: Band rollt durch, wird langsamer und bleibt unter dem Strich stehen. */
public class PackScreen extends Screen {
    private static final int HEAD = 28;
    private static final long SPIN_MS = 6200;
    private static final long PAUSE_MS = 450;
    private static final int TILES = 56, WIN = 47, TW = 72, STEP = 78;

    private static int sel = 1;       // gewählte Kiste
    private static int[] result;      // {Kollektion, Typ, 0 neu / 1 doppelt}
    private static int[][] strip;     // {Kollektion, Typ} je Feld
    private static float target;      // Ziel-Verschiebung in Pixeln
    private static long openStart;
    private static int lastIdx;
    private static boolean revealPlayed;
    private int px0, px1, py0, py1;

    private static final String[] SND = {"sonnenfeuer", "frostkristall", "leere", "neonglitch", "koenigsglanz", "blutmond", "giftranke", "gewitter", "magmakern", "sternenstaub", "geisterhauch"};

    public PackScreen() {
        super(Text.literal("Kisten"));
    }

    private static void sound(String ns, String id, float pitch, float vol) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of(ns, id)), pitch, vol));
        } catch (Throwable ignored) {
        }
    }

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new PackScreen());
    }

    private static boolean revealed() {
        return result != null && System.currentTimeMillis() - openStart >= SPIN_MS + PAUSE_MS;
    }

    /** Gewählte Kiste kaufen und das Band mit dem Gewinn an Stelle WIN bauen. */
    private static void start() {
        int[] r = Skins.openCase(sel);
        if (r == null) {
            sound("minecraft", "entity.villager.no", 1f, 0.6f);
            return;
        }
        result = r;
        Random rnd = new Random();
        strip = new int[TILES][];
        for (int i = 0; i < TILES; i++) strip[i] = new int[] {Skins.bundleOfTier(Skins.rollTier(rnd, sel), rnd), rnd.nextInt(Skins.TYPES.length)};
        strip[WIN] = new int[] {r[0], r[1]};
        target = WIN * STEP + (rnd.nextFloat() - 0.5f) * (TW - 14);
        openStart = System.currentTimeMillis();
        lastIdx = 0;
        revealPlayed = false;
        sound("spotifylyrics", "pack.open", 1f, 0.8f);
    }

    private int cardW() {
        return (px1 - px0 - 20 - 16) / 3;
    }

    @Override
    protected void init() {
        int pw = Math.min(440, this.width - 16), ph = Math.min(300, this.height - 8);
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 86, py0 + 5, 86, 18, () -> {
            if (result != null && !revealed()) return;
            result = null;
            mc.setScreen(new CollectionScreen());
        });
        int by = py1 - 32, bw = (pw - 20 - 8) / 3;
        if (result == null) {
            int cw = cardW(), cy0 = py0 + HEAD + 6;
            for (int k = 0; k < Skins.CASES.length; k++) {
                final int kk = k;
                btn(px0 + 10 + k * (cw + 8), cy0, cw, 92, () -> sel = kk);
            }
            btn((px0 + px1) / 2 - 85, by, 170, 22, () -> {
                start();
                reopen();
            });
        } else {
            btn(px0 + 10, by, bw, 22, () -> {
                if (revealed() && Skins.owns(result[0], result[1])) Skins.equip(result[1], result[0]);
            });
            btn(px0 + 14 + bw, by, bw, 22, () -> {
                if (!revealed()) return;
                result = null;
                if (Skins.canOpen(sel)) start();
                reopen();
            });
            btn(px0 + 18 + 2 * bw, by, bw, 22, () -> {
                if (!revealed()) return;
                int t = result[1];
                result = null;
                mc.setScreen(new WeaponSkinScreen(t));
            });
        }
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("Sammlung / Kisten", "Collection / Cases"));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Ui.txt(ctx, tr, result == null ? Lang.t("Kisten", "Cases") : Skins.CASES[sel], px0 + 10, py0 + 6, result == null ? Ui.TEXT : Skins.CASE_COL[sel]);
        Ui.txt(ctx, tr, Skins.coinsText() + " Coins" + (Skins.adminOn() ? "  ·  §6ADMIN" : ""), px0 + 10, py0 + 17, 0xFFFFC23D);
        boolean spinning = result != null && !revealed();
        Ui.btn(ctx, tr, px1 - 96, py0 + 5, px1 - 10, py0 + 23, Lang.t("‹ Sammlung", "‹ Collection"), Ui.in(mouseX, mouseY, px1 - 96, py0 + 5, px1 - 10, py0 + 23),
            0xFF1A1E27, 0xFF252B38, spinning ? 0xFF6F7380 : 0xFFD6D9E0);

        int cx = (px0 + px1) / 2;
        long now = System.currentTimeMillis();
        Matrix3x2fStack m = ctx.getMatrices();
        int by = py1 - 32, bw = (px1 - px0 - 20 - 8) / 3;

        if (result == null) {
            shop(ctx, tr, m, cx, now, mouseX, mouseY);
            boolean can = Skins.canOpen(sel);
            boolean hov = Ui.in(mouseX, mouseY, cx - 85, by, cx + 85, by + 22);
            String l = can ? Lang.t("Öffnen · ", "Open · ") + (Skins.adminOn() ? Lang.t("gratis", "free") : Skins.CASE_PRICE[sel] + " Coins")
                : Lang.t("Zu wenig Coins (", "Not enough coins (") + Skins.CASE_PRICE[sel] + ")";
            Ui.btn(ctx, tr, cx - 85, by, cx + 85, by + 22, l, hov, can ? 0xFF8A5A00 : 0xFF1A1E27, can ? 0xFFB07A10 : 0xFF1A1E27, can ? 0xFFFFFFFF : 0xFF6F7380);
            return;
        }

        long el = now - openStart;
        if (el < SPIN_MS + PAUSE_MS) {
            roulette(ctx, tr, cx, el);
            String l = Lang.t("Kiste wird geöffnet …", "Opening case …");
            Ui.txt(ctx, tr, l, cx - tr.getWidth(l) / 2, by + 7, 0xFF8B92A5);
            return;
        }
        reveal(ctx, tr, m, cx, el - SPIN_MS - PAUSE_MS, now);

        int b = result[0], t = result[1];
        boolean eq = Skins.equipped(t) == b;
        boolean canEq = Skins.owns(b, t);
        boolean again = Skins.canOpen(sel);
        Ui.btn(ctx, tr, px0 + 10, by, px0 + 10 + bw, by + 22, eq ? Lang.t("✔ Ausgerüstet", "✔ Equipped") : Lang.t("Ausrüsten", "Equip"),
            Ui.in(mouseX, mouseY, px0 + 10, by, px0 + 10 + bw, by + 22), eq ? 0xFF15301F : canEq ? Ui.ACCENT_DIM : 0xFF1A1E27, eq ? 0xFF15301F : canEq ? Ui.ACCENT_DIM_HOV : 0xFF1A1E27, 0xFFFFFFFF);
        Ui.btn(ctx, tr, px0 + 14 + bw, by, px0 + 14 + 2 * bw, by + 22,
            again ? Lang.t("Noch eine · ", "Another · ") + (Skins.adminOn() ? Lang.t("gratis", "free") : String.valueOf(Skins.CASE_PRICE[sel])) : Lang.t("Fertig", "Done"),
            Ui.in(mouseX, mouseY, px0 + 14 + bw, by, px0 + 14 + 2 * bw, by + 22), 0xFF8A5A00, 0xFFB07A10, 0xFFFFFFFF);
        Ui.btn(ctx, tr, px0 + 18 + 2 * bw, by, px1 - 10, by + 22, Lang.t("Ansehen ›", "View ›"),
            Ui.in(mouseX, mouseY, px0 + 18 + 2 * bw, by, px1 - 10, by + 22), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
    }

    /** Kleine Kiste in der Farbe der Kiste. */
    private static void crate(DrawContext ctx, Matrix3x2fStack m, float x, float y, float sc, int col, int dark) {
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sc, sc);
        int w = 30, h = 16;
        ctx.fill(-w, -h, w, h, dark);
        ctx.fill(-w, -h, w, -h + 7, Ui.mix(dark, col, 0.25f));
        ctx.fill(-w, -h + 7, w, -h + 9, col);
        ctx.fill(-w, -h, w, -h + 1, col);
        ctx.fill(-w, h - 1, w, h, col);
        ctx.fill(-w, -h, -w + 1, h, col);
        ctx.fill(w - 1, -h, w, h, col);
        ctx.fill(-w + 5, -h + 1, -w + 8, h - 1, Ui.mix(dark, col, 0.5f));
        ctx.fill(w - 8, -h + 1, w - 5, h - 1, Ui.mix(dark, col, 0.5f));
        ctx.fill(-6, -h - 3, 6, -h, Ui.mix(dark, col, 0.5f));
        ctx.fill(-4, -h + 5, 4, -h + 12, col);
        ctx.fill(-1, -h + 7, 1, -h + 11, dark);
        m.popMatrix();
    }

    /** Drei Kisten zur Auswahl, darunter Inhalt und Chancen der gewählten. */
    private void shop(DrawContext ctx, TextRenderer tr, Matrix3x2fStack m, int cx, long now, int mx, int my) {
        int cw = cardW(), cy0 = py0 + HEAD + 6;
        for (int k = 0; k < Skins.CASES.length; k++) {
            int x = px0 + 10 + k * (cw + 8);
            boolean s = sel == k, h = Ui.in(mx, my, x, cy0, x + cw, cy0 + 92);
            int col = Skins.CASE_COL[k];
            Ui.card(ctx, x, cy0, x + cw, cy0 + 92, s ? col : (h ? 0xFF3A4152 : Ui.BORDER), s ? Ui.CARD_HOV : Ui.CARD);
            if (s) ctx.fillGradient(x + 1, cy0 + 1, x + cw - 1, cy0 + 91, 0x05000000, (0x40 << 24) | (col & 0xFFFFFF));
            float bob = s ? (float) Math.sin(now / 400.0) * 2 : 0;
            crate(ctx, m, x + cw / 2f, cy0 + 32 + bob, 1.1f, col, 0xFF1B1408);
            String n = Skins.CASES[k];
            Ui.txt(ctx, tr, n, x + (cw - tr.getWidth(n)) / 2, cy0 + 58, col);
            String p = Skins.adminOn() ? Lang.t("gratis", "free") : Skins.CASE_PRICE[k] + " Coins";
            Ui.txt(ctx, tr, p, x + (cw - tr.getWidth(p)) / 2, cy0 + 72, Skins.canOpen(k) ? 0xFFFFC23D : 0xFF6F7380);
        }

        // Inhalt der gewählten Kiste
        int iy = cy0 + 100;
        String l = Lang.t("Inhalt: ein Skin aus diesen Kollektionen", "Contains one skin from these collections");
        Ui.txt(ctx, tr, l, cx - tr.getWidth(l) / 2, iy, 0xFF8B92A5);
        int n8 = Skins.bundles(), tw = Math.min(34, (px1 - px0 - 20 - (n8 - 1) * 4) / n8), tot = n8 * tw + (n8 - 1) * 4;
        int x0 = cx - tot / 2, ty = iy + 12;
        String tip = null;
        int[] w = Skins.CASE_W[sel];
        for (int j = 0; j < n8; j++) {
            int i = Skins.ORDER[j];
            int x = x0 + j * (tw + 4);
            int col = Skins.tierCol(i);
            Ui.card(ctx, x, ty, x + tw, ty + tw, col, 0xFF07080B);
            ctx.fillGradient(x + 1, ty + 1, x + tw - 1, ty + tw - 1, 0x05000000, (0x50 << 24) | (col & 0xFFFFFF));
            ctx.fill(x + 1, ty + tw - 3, x + tw - 1, ty + tw - 1, col);
            m.pushMatrix();
            m.translate(x + tw / 2f, ty + tw / 2f - 1);
            m.scale(1.6f, 1.6f);
            ctx.drawTextWithShadow(tr, "?", -tr.getWidth("?") / 2, -4, 0xFF8B92A5);
            m.popMatrix();
            if (Ui.in(mx, my, x, ty, x + tw, ty + tw)) {
                int t0 = Skins.tier(i), same = 0;
                for (int q = 0; q < n8; q++) if (Skins.tier(q) == t0) same++;
                tip = WeaponSkins.BUNDLES[i] + " · " + Skins.TIER_NAMES[t0] + " · " + String.format(java.util.Locale.ROOT, "%.1f%%", w[t0] / 10f / same);
            }
        }
        // Chancen
        int lx = 0;
        for (int q = 0; q < Skins.TIER_NAMES.length; q++) lx += tr.getWidth(Skins.TIER_NAMES[q] + " " + Skins.pct(w[q])) + (q > 0 ? 8 : 0);
        int lxx = cx - lx / 2, ly = ty + tw + 6;
        for (int q = 0; q < Skins.TIER_NAMES.length; q++) {
            String s = Skins.TIER_NAMES[q] + " " + Skins.pct(w[q]);
            Ui.txt(ctx, tr, s, lxx, ly, Skins.TIER_COL[q]);
            lxx += tr.getWidth(s) + 8;
        }
        if (tip != null) ctx.drawTooltip(tr, List.of(Text.literal(tip)), mx, my);
    }

    /** Das durchlaufende Band. */
    private void roulette(DrawContext ctx, TextRenderer tr, int cx, long el) {
        int wx0 = px0 + 10, wx1 = px1 - 10, wy0 = py0 + HEAD + 40, wy1 = wy0 + 92;
        float p = Math.min(1f, el / (float) SPIN_MS);
        float e = 1f - (float) Math.pow(1f - p, 4);
        float off = target * e;
        int idx = Math.round(off / STEP);
        if (idx != lastIdx) {
            lastIdx = idx;
            sound("minecraft", "block.note_block.hat", 1.5f + 0.3f * (1 - p), 0.45f);
        }
        Ui.card(ctx, wx0 - 1, wy0 - 1, wx1 + 1, wy1 + 1, 0xFF2A2F3A, 0xFF07080B);
        ctx.enableScissor(wx0, wy0, wx1, wy1);
        int first = Math.max(0, (int) ((off - (cx - wx0)) / STEP) - 1);
        for (int i = first; i < TILES; i++) {
            int x = (int) (cx + i * STEP - off) - TW / 2;
            if (x > wx1) break;
            int b = strip[i][0], t = strip[i][1];
            int col = 0xFF000000 | SkinGen.PAL[b][2], rc = Skins.tierCol(b);
            int y0 = wy0 + 6, y1 = wy1 - 6;
            Ui.card(ctx, x, y0, x + TW, y1, 0xFF2A2F3A, 0xFF12151C);
            ctx.fillGradient(x + 1, y0 + 1, x + TW - 1, y1 - 12, 0x08000000, (0x60 << 24) | (rc & 0xFFFFFF));
            ctx.fill(x + 1, y1 - 4, x + TW - 1, y1 - 1, rc);
            ItemStack s = CollectionScreen.stackFor(t, b);
            CollectionScreen.drawBig(ctx, s, x + TW / 2 - 20, y0 + 6, 2.5f);
            String n = tr.trimToWidth(WeaponSkins.BUNDLES[b], TW - 6);
            ctx.drawTextWithShadow(tr, n, x + (TW - tr.getWidth(n)) / 2, y1 - 15, col);
        }
        // Ränder abdunkeln
        for (int k = 0; k < 8; k++) {
            int a = (int) (200 * (1 - k / 8f)) << 24;
            ctx.fill(wx0 + k * 6, wy0, wx0 + k * 6 + 6, wy1, a);
            ctx.fill(wx1 - k * 6 - 6, wy0, wx1 - k * 6, wy1, a);
        }
        ctx.disableScissor();
        // Strich in der Mitte
        ctx.fill(cx - 1, wy0 - 6, cx + 1, wy1 + 6, 0xFFFFC23D);
        ctx.fill(cx - 5, wy0 - 6, cx + 5, wy0 - 4, 0xFFFFC23D);
        ctx.fill(cx - 3, wy0 - 4, cx + 3, wy0 - 2, 0xFFFFC23D);
        ctx.fill(cx - 5, wy1 + 4, cx + 5, wy1 + 6, 0xFFFFC23D);
        ctx.fill(cx - 3, wy1 + 2, cx + 3, wy1 + 4, 0xFFFFC23D);
        // Gewinner kurz aufleuchten lassen
        if (el >= SPIN_MS) {
            float k = (el - SPIN_MS) / (float) PAUSE_MS;
            int x = (int) (cx + WIN * STEP - target) - TW / 2;
            ctx.fill(x, wy0 + 6, x + TW, wy1 - 6, (int) (120 * (1 - k)) << 24 | (Skins.tierCol(result[0]) & 0xFFFFFF));
        }
    }

    /** Der Gewinn: Strahlen, Blitz, Skin groß. */
    private void reveal(DrawContext ctx, TextRenderer tr, Matrix3x2fStack m, int cx, long rel, long now) {
        int b = result[0], t = result[1];
        int col = 0xFF000000 | SkinGen.PAL[b][2], col2 = Skins.tierCol(b);
        if (!revealPlayed) {
            revealPlayed = true;
            sound("spotifylyrics", "pack.reveal", 1f, 1f);
            sound("spotifylyrics", "kill." + SND[b] + ".k" + (1 + Skins.tier(b)), 1f, 0.9f);
        }
        int cy = py0 + HEAD + (py1 - 40 - py0 - HEAD) / 2;
        float k = Math.min(1, rel / 450f);
        for (int i = 0; i < 16; i++) {
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) (i * Math.PI / 8 + now / 2500.0));
            int len = (int) (110 * k);
            ctx.fill(-3, 18, 3, 18 + len, (int) (70 * (1 - 0.3 * (i % 2))) << 24 | (i % 2 == 0 ? col : col2) & 0xFFFFFF);
            m.popMatrix();
        }
        if (rel < 250) ctx.fill(px0 + 1, py0 + HEAD, px1 - 1, py1 - 1, (int) ((1 - rel / 250f) * 180) << 24 | 0xFFFFFF);
        float sc = (1.5f + 4f * (1 - (1 - k) * (1 - k))) * (1 + 0.03f * (float) Math.sin(now / 300.0));
        int iw = (int) (16 * sc);
        CollectionScreen.drawBig(ctx, CollectionScreen.stackFor(t, b), cx - iw / 2, cy - iw / 2 - 6, sc);
        String title = WeaponSkins.BUNDLES[b] + " " + Skins.TYPE_NAMES[t];
        m.pushMatrix();
        m.translate(cx, cy + 40);
        m.scale(1.6f, 1.6f);
        ctx.drawTextWithShadow(tr, title, -tr.getWidth(title) / 2, 0, col);
        m.popMatrix();
        String rn = Skins.tierName(b).toUpperCase(java.util.Locale.ROOT);
        Ui.txt(ctx, tr, rn, cx - tr.getWidth(rn) / 2, cy - iw / 2 - 18, Skins.tierCol(b));
        String sub = switch (result[2]) {
            case 0 -> Lang.t("NEU!  ·  Finisher: ", "NEW!  ·  Finisher: ") + KillFx.STYLES[b];
            case 2 -> Lang.t("Admin  ·  Finisher: ", "Admin  ·  Finisher: ") + KillFx.STYLES[b];
            default -> Lang.t("Doppelt – hast du schon", "Duplicate – already owned");
        };
        Ui.txt(ctx, tr, sub, cx - tr.getWidth(sub) / 2, cy + 58, result[2] == 1 ? 0xFF8B92A5 : 0xFF4ADE80);
    }
}
