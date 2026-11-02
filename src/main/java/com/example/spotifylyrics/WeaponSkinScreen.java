package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Eine Waffe: großes Vorschaubild, alle Skins (gesperrte grau), ausrüsten und Finisher ansehen. */
public class WeaponSkinScreen extends Screen {
    private static final int HEAD = 28;
    private final int type;
    private int view;
    private int px0, px1, py0, py1, lx0, lx1, rx0, rx1, top;

    public WeaponSkinScreen(int type) {
        super(Text.literal("Skins"));
        this.type = type;
        this.view = Skins.equipped(type);
    }

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int pw = Math.min(470, this.width - 16), ph = Math.min(300, this.height - 8);
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        top = py0 + HEAD + 6;
        lx0 = px0 + 10;
        lx1 = px0 + pw / 2 + 10;
        rx0 = lx1 + 8;
        rx1 = px1 - 10;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 86, py0 + 5, 86, 18, () -> mc.setScreen(new CollectionScreen()));
        int rows = Skins.bundles() + 1, rh = Math.min(22, (py1 - 10 - top) / rows);
        for (int i = -1; i < Skins.bundles(); i++) {
            final int b = i;
            btn(rx0, top + (i + 1) * rh, rx1 - rx0, rh - 2, () -> view = b);
        }
        int by = py1 - 30, bw = (lx1 - lx0 - 6) / 2;
        btn(lx0, by, bw, 20, () -> {
            if (view < 0 || Skins.owns(view, type)) {
                Skins.equip(type, view);
            }
        });
        btn(lx0 + bw + 6, by, bw, 20, () -> {
            if (view >= 0) {
                mc.setScreen(null);
                KillFx.preview(view);
            }
        });
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("Sammlung / ", "Collection / ") + Skins.TYPE_NAMES[type]);
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Ui.txt(ctx, tr, Skins.TYPE_NAMES[type], px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, Skins.ownedCount(type) + "/" + Skins.bundles() + Lang.t(" Skins freigeschaltet", " skins unlocked"), px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 96, py0 + 5, px1 - 10, py0 + 23, Lang.t("‹ Sammlung", "‹ Collection"), Ui.in(mouseX, mouseY, px1 - 96, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        // große Vorschau
        int by = py1 - 30;
        int boxBot = by - 30;
        boolean owned = view < 0 || Skins.owns(view, type);
        int accent = view >= 0 ? 0xFF000000 | SkinGen.PAL[view][2] : 0xFF6A7387;
        Ui.card(ctx, lx0, top, lx1, boxBot, accent, 0xFF0D0F14);
        if (view >= 0) {
            ctx.fillGradient(lx0 + 1, top + 1, lx1 - 1, boxBot - 1, (0x40 << 24) | SkinGen.PAL[view][1], (0x10 << 24) | SkinGen.PAL[view][0]);
            // leuchtende Strahlen hinter der Waffe
            long ms = System.currentTimeMillis();
            int cx = (lx0 + lx1) / 2, cy = (top + boxBot) / 2;
            org.joml.Matrix3x2fStack m = ctx.getMatrices();
            for (int i = 0; i < 10; i++) {
                m.pushMatrix();
                m.translate(cx, cy);
                m.rotate((float) (i * Math.PI / 5 + ms / 4000.0));
                ctx.fill(-2, 20, 2, 70, (0x22 << 24) | SkinGen.PAL[view][2]);
                m.popMatrix();
            }
        }
        float sc = Math.min(8f, Math.min(lx1 - lx0 - 20, boxBot - top - 20) / 16f);
        int iw = (int) (16 * sc);
        float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 3;
        CollectionScreen.drawBig(ctx, CollectionScreen.stackFor(type, view), (lx0 + lx1 - iw) / 2, (int) ((top + boxBot - iw) / 2 + bob), sc);
        if (!owned) {
            ctx.fill(lx0 + 1, top + 1, lx1 - 1, boxBot - 1, 0x88000000);
            String l = Lang.t("Gesperrt – aus Packs", "Locked – from packs");
            Ui.txt(ctx, tr, l, (lx0 + lx1 - tr.getWidth(l)) / 2, (top + boxBot) / 2 - 4, 0xFFFFC23D);
        }
        String nm = view >= 0 ? WeaponSkins.BUNDLES[view] + " " + Skins.TYPE_NAMES[type] : Lang.t("Standard", "Default");
        Ui.txt(ctx, tr, nm, lx0 + 2, boxBot + 5, view >= 0 ? accent : Ui.TEXT);
        if (view >= 0) Ui.txt(ctx, tr, Lang.t("Finisher: ", "Finisher: ") + KillFx.STYLES[view] + (type >= 4 ? Lang.t(" · eigene Nachlade-Animation", " · custom reload") : ""),
            lx0 + 2, boxBot + 16, 0xFF8B92A5);

        int bw = (lx1 - lx0 - 6) / 2;
        boolean eq = Skins.equipped(type) == view;
        Ui.btn(ctx, tr, lx0, by, lx0 + bw, by + 20, eq ? Lang.t("✔ Ausgerüstet", "✔ Equipped") : owned ? Lang.t("Ausrüsten", "Equip") : Lang.t("Gesperrt", "Locked"),
            Ui.in(mouseX, mouseY, lx0, by, lx0 + bw, by + 20), eq ? 0xFF15301F : owned ? Ui.ACCENT_DIM : 0xFF1A1E27, eq ? 0xFF15301F : owned ? Ui.ACCENT_DIM_HOV : 0xFF1A1E27,
            owned ? 0xFFFFFFFF : 0xFF6F7380);
        Ui.btn(ctx, tr, lx0 + bw + 6, by, lx1, by + 20, Lang.t("▶ Finisher ansehen", "▶ View finisher"), Ui.in(mouseX, mouseY, lx0 + bw + 6, by, lx1, by + 20),
            view >= 0 ? 0xFF2A1E40 : 0xFF1A1E27, view >= 0 ? 0xFF3A2A5A : 0xFF1A1E27, view >= 0 ? 0xFFFFFFFF : 0xFF6F7380);

        // Liste aller Skins
        int rows = Skins.bundles() + 1, rh = Math.min(22, (py1 - 10 - top) / rows);
        for (int i = -1; i < Skins.bundles(); i++) {
            int y = top + (i + 1) * rh;
            boolean has = i < 0 || Skins.owns(i, type);
            boolean sel = view == i, hov = Ui.in(mouseX, mouseY, rx0, y, rx1, y + rh - 2);
            int col = i >= 0 ? 0xFF000000 | SkinGen.PAL[i][2] : 0xFF8B92A5;
            Ui.card(ctx, rx0, y, rx1, y + rh - 2, sel ? col : (hov ? 0xFF3A4152 : Ui.BORDER), sel ? Ui.CARD_HOV : Ui.CARD);
            if (i >= 0 && has) ctx.fill(rx0 + 1, y + 1, rx0 + 3, y + rh - 3, col);
            ctx.drawItem(CollectionScreen.stackFor(type, has ? i : -1), rx0 + 5, y + (rh - 2 - 16) / 2);
            String n = i < 0 ? Lang.t("Standard", "Default") : WeaponSkins.BUNDLES[i];
            Ui.txt(ctx, tr, n, rx0 + 25, y + (rh - 2 - 8) / 2, has ? (i >= 0 ? col : Ui.TEXT) : 0xFF5A6070);
            String tag = Skins.equipped(type) == i ? "✔" : has ? "" : "🔒";
            if (!has) tag = Lang.t("gesperrt", "locked");
            if (!tag.isEmpty()) Ui.txt(ctx, tr, tag, rx1 - 6 - tr.getWidth(tag), y + (rh - 2 - 8) / 2, has ? 0xFF4ADE80 : 0xFF5A6070);
            if (!has) ctx.fill(rx0 + 4, y + 2, rx0 + 22, y + rh - 4, 0x99000000);
        }
    }
}
