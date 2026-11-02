package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/** Pack öffnen: Kiste wackelt, leuchtet immer stärker, platzt auf – und der neue Skin erscheint. */
public class PackScreen extends Screen {
    private static final int HEAD = 28;
    private static final long OPEN_MS = 1700;
    private static int[] result;      // {Kollektion, Typ, doppelt}
    private static long openStart;    // 0 = noch nicht geöffnet
    private int px0, px1, py0, py1;

    public PackScreen() {
        super(Text.literal("Packs"));
    }

    private static void sound(String id, float pitch, float vol) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("spotifylyrics", id)), pitch, vol));
        } catch (Throwable ignored) {
        }
    }

    private static final String[] SND = {"sonnenfeuer", "frostkristall", "leere", "neonglitch", "koenigsglanz", "blutmond", "giftranke", "gewitter"};
    private static boolean revealPlayed;

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new PackScreen());
    }

    private boolean revealed() {
        return result != null && System.currentTimeMillis() - openStart >= OPEN_MS;
    }

    @Override
    protected void init() {
        int pw = Math.min(380, this.width - 16), ph = Math.min(300, this.height - 8);
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 86, py0 + 5, 86, 18, () -> {
            result = null;
            mc.setScreen(new CollectionScreen());
        });
        int by = py1 - 32, bw = (pw - 20 - 8) / 3;
        if (result == null) {
            btn((px0 + px1) / 2 - 70, by, 140, 22, () -> {
                int[] r = Skins.openPack();
                if (r != null) {
                    result = r;
                    openStart = System.currentTimeMillis();
                    revealPlayed = false;
                    sound("pack.open", 1f, 1f);
                    reopen();
                }
            });
        } else {
            btn(px0 + 10, by, bw, 22, () -> {
                if (revealed() && result[2] == 0) Skins.equip(result[1], result[0]);
            });
            btn(px0 + 14 + bw, by, bw, 22, () -> {
                if (!revealed()) return;
                result = null;
                if (Skins.packsAvailable() > 0) {
                    int[] r = Skins.openPack();
                    if (r != null) {
                        result = r;
                        openStart = System.currentTimeMillis();
                        revealPlayed = false;
                        sound("pack.open", 1f, 1f);
                    }
                }
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
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("Sammlung / Packs", "Collection / Packs"));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int packs = Skins.packsAvailable();
        Ui.txt(ctx, tr, Lang.t("Skin-Pack", "Skin pack"), px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, packs + Lang.t(" Packs · nächstes in ", " packs · next in ") + CollectionScreen.time(Skins.msToNext()), px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 96, py0 + 5, px1 - 10, py0 + 23, Lang.t("‹ Sammlung", "‹ Collection"), Ui.in(mouseX, mouseY, px1 - 96, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        int cx = (px0 + px1) / 2, cy = py0 + HEAD + (py1 - 40 - py0 - HEAD) / 2 + 4;
        long now = System.currentTimeMillis();
        Matrix3x2fStack m = ctx.getMatrices();
        int by = py1 - 32, bw = (px1 - px0 - 20 - 8) / 3;

        if (result == null) {
            // ruhende Kiste
            crate(ctx, m, cx, cy, 0, 0, 0xFFFFC23D, now);
            String l = packs > 0 ? Lang.t("Klick auf „Öffnen“", "Click “Open”") : Lang.t("Spiel weiter, um Packs zu bekommen", "Keep playing to earn packs");
            Ui.txt(ctx, tr, l, cx - tr.getWidth(l) / 2, cy + 48, 0xFF8B92A5);
            boolean hov = Ui.in(mouseX, mouseY, cx - 70, by, cx + 70, by + 22);
            Ui.btn(ctx, tr, cx - 70, by, cx + 70, by + 22, packs > 0 ? Lang.t("Öffnen", "Open") : Lang.t("Keine Packs", "No packs"), hov,
                packs > 0 ? 0xFF8A5A00 : 0xFF1A1E27, packs > 0 ? 0xFFB07A10 : 0xFF1A1E27, packs > 0 ? 0xFFFFFFFF : 0xFF6F7380);
            return;
        }

        int b = result[0], t = result[1];
        int col = 0xFF000000 | SkinGen.PAL[b][2], col2 = 0xFF000000 | SkinGen.PAL[b][1];
        long el = now - openStart;
        if (el < OPEN_MS) {
            // Kiste wackelt immer stärker und leuchtet in der Farbe der Kollektion
            float k = el / (float) OPEN_MS;
            float shake = k * k * 6;
            float ang = (float) Math.sin(el / 30.0) * 0.08f * k;
            int glow = Ui.mix(0xFFFFC23D, col, k);
            crate(ctx, m, cx + (int) (Math.sin(el / 21.0) * shake), cy + (int) (Math.cos(el / 17.0) * shake), ang, k, glow, now);
            if ((el / 90) % 2 == 0 && k > 0.5f) ctx.fill(px0 + 1, py0 + HEAD, px1 - 1, py1 - 1, (int) (k * 40) << 24 | (col & 0xFFFFFF));
            return;
        }
        if (!revealPlayed) {
            revealPlayed = true;
            sound("pack.reveal", 1f, 1f);
            sound("kill." + SND[b] + ".k5", 1f, 0.9f);
        }
        float k = Math.min(1, (el - OPEN_MS) / 450f);
        // Strahlen
        for (int i = 0; i < 16; i++) {
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) (i * Math.PI / 8 + now / 2500.0));
            int len = (int) (110 * k);
            ctx.fill(-3, 18, 3, 18 + len, (int) (70 * (1 - 0.3 * (i % 2))) << 24 | (i % 2 == 0 ? col : col2) & 0xFFFFFF);
            m.popMatrix();
        }
        // Blitz direkt nach dem Aufplatzen
        if (el - OPEN_MS < 250) ctx.fill(px0 + 1, py0 + HEAD, px1 - 1, py1 - 1, (int) ((1 - (el - OPEN_MS) / 250f) * 180) << 24 | 0xFFFFFF);
        float sc = (1.5f + 4.5f * (1 - (1 - k) * (1 - k))) * (1 + 0.03f * (float) Math.sin(now / 300.0));
        int iw = (int) (16 * sc);
        CollectionScreen.drawBig(ctx, CollectionScreen.stackFor(t, b), cx - iw / 2, cy - iw / 2, sc);
        String title = WeaponSkins.BUNDLES[b] + " " + Skins.TYPE_NAMES[t];
        m.pushMatrix();
        m.translate(cx, cy + 44);
        m.scale(1.6f, 1.6f);
        ctx.drawTextWithShadow(tr, title, -tr.getWidth(title) / 2, 0, col);
        m.popMatrix();
        String sub = result[2] == 0 ? Lang.t("NEU!  ·  Finisher: ", "NEW!  ·  Finisher: ") + KillFx.STYLES[b] : Lang.t("Doppelt – hast du schon", "Duplicate – already owned");
        Ui.txt(ctx, tr, sub, cx - tr.getWidth(sub) / 2, cy + 62, result[2] == 0 ? 0xFF4ADE80 : 0xFF8B92A5);

        boolean eq = Skins.equipped(t) == b;
        Ui.btn(ctx, tr, px0 + 10, by, px0 + 10 + bw, by + 22, eq ? Lang.t("✔ Ausgerüstet", "✔ Equipped") : Lang.t("Ausrüsten", "Equip"),
            Ui.in(mouseX, mouseY, px0 + 10, by, px0 + 10 + bw, by + 22), eq ? 0xFF15301F : Ui.ACCENT_DIM, eq ? 0xFF15301F : Ui.ACCENT_DIM_HOV, 0xFFFFFFFF);
        Ui.btn(ctx, tr, px0 + 14 + bw, by, px0 + 14 + 2 * bw, by + 22, packs > 0 ? Lang.t("Noch eins (", "Another (") + packs + ")" : Lang.t("Fertig", "Done"),
            Ui.in(mouseX, mouseY, px0 + 14 + bw, by, px0 + 14 + 2 * bw, by + 22), 0xFF8A5A00, 0xFFB07A10, 0xFFFFFFFF);
        Ui.btn(ctx, tr, px0 + 18 + 2 * bw, by, px1 - 10, by + 22, Lang.t("Ansehen ›", "View ›"),
            Ui.in(mouseX, mouseY, px0 + 18 + 2 * bw, by, px1 - 10, by + 22), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
    }

    /** Pack-Kiste: zwei gedrehte Kästen mit Band und Leuchten. */
    private static void crate(DrawContext ctx, Matrix3x2fStack m, int x, int y, float ang, float charge, int glow, long now) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 300.0);
        m.pushMatrix();
        m.translate(x, y);
        m.rotate(ang);
        int g = 34 + (int) (8 * charge);
        ctx.fill(-g - 6, -g - 6, g + 6, g + 6, (int) ((0.15f + 0.35f * charge + 0.1f * pulse) * 255) << 24 | (glow & 0xFFFFFF));
        ctx.fill(-g, -g, g, g, 0xFF1B1408);
        ctx.fill(-g, -g, g, -g + 3, glow);
        ctx.fill(-g, g - 3, g, g, glow);
        ctx.fill(-g, -g, -g + 3, g, glow);
        ctx.fill(g - 3, -g, g, g, glow);
        ctx.fill(-3, -g, 3, g, glow);
        ctx.fill(-g, -3, g, 3, glow);
        m.pushMatrix();
        m.rotate((float) (Math.PI / 4));
        ctx.fill(-9, -9, 9, 9, 0xFF0B0D12);
        ctx.fill(-6, -6, 6, 6, glow);
        m.popMatrix();
        m.popMatrix();
    }
}
