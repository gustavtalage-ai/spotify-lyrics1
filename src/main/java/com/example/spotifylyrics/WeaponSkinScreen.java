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
        lx1 = px0 + pw * 2 / 5;
        rx0 = lx1 + 8;
        rx1 = px1 - 10;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 86, py0 + 5, 86, 18, () -> mc.setScreen(new CollectionScreen()));
        int n = Skins.bundles() + 1, rows = (n + 1) / 2, rh = Math.min(22, (py1 - 10 - top) / rows), cw = (rx1 - rx0 - 4) / 2;
        for (int e = 0; e < n; e++) {
            final int b = e == 0 ? -1 : Skins.ORDER[e - 1];
            btn(rx0 + (e / rows) * (cw + 4), top + (e % rows) * rh, cw, rh - 2, () -> view = b);
        }
        int by = py1 - 30, bw = (lx1 - lx0 - 6) / 2;
        btn(lx0, by, bw, 20, () -> {
            if (view < 0 || Skins.owns(view, type)) {
                Skins.equip(type, view);
            }
        });
        btn(lx0 + bw + 6, by, bw, 20, () -> {
            if (view >= 0 && Skins.owns(view, type)) {
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
        if (view >= 0 && owned) {
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
        if (owned) {
            CollectionScreen.drawBig(ctx, CollectionScreen.stackFor(type, view), (lx0 + lx1 - iw) / 2, (int) ((top + boxBot - iw) / 2 + bob), sc);
        } else {
            // ausgeschwärzt mit Fragezeichen
            ctx.fill(lx0 + 1, top + 1, lx1 - 1, boxBot - 1, 0xFF050608);
            int cx = (lx0 + lx1) / 2, cy = (int) ((top + boxBot) / 2 + bob);
            int r = iw / 2;
            ctx.fill(cx - r, cy - r, cx + r, cy + r, 0xFF0E1016);
            ctx.fill(cx - r, cy - r, cx + r, cy - r + 2, 0xFF2A2F3A);
            ctx.fill(cx - r, cy + r - 2, cx + r, cy + r, 0xFF2A2F3A);
            ctx.fill(cx - r, cy - r, cx - r + 2, cy + r, 0xFF2A2F3A);
            ctx.fill(cx + r - 2, cy - r, cx + r, cy + r, 0xFF2A2F3A);
            org.joml.Matrix3x2fStack m = ctx.getMatrices();
            float qs = Math.max(2f, iw / 14f);
            m.pushMatrix();
            m.translate(cx, cy);
            m.scale(qs, qs);
            ctx.drawTextWithShadow(tr, "?", -tr.getWidth("?") / 2, -4, 0xFF8B92A5);
            m.popMatrix();
            String l = Lang.t("Gesperrt – aus Kisten", "Locked – from cases");
            Ui.txt(ctx, tr, l, (lx0 + lx1 - tr.getWidth(l)) / 2, boxBot - 14, 0xFFFFC23D);
        }
        String nm = view >= 0 ? WeaponSkins.BUNDLES[view] + " " + Skins.TYPE_NAMES[type] : Lang.t("Standard", "Default");
        Ui.txt(ctx, tr, tr.trimToWidth(nm, lx1 - lx0 - 8 - (view >= 0 ? tr.getWidth(Skins.tierName(view)) : 0)), lx0 + 2, boxBot + 5, view >= 0 ? accent : Ui.TEXT);
        if (view >= 0) Ui.txt(ctx, tr, Skins.tierName(view), lx1 - 2 - tr.getWidth(Skins.tierName(view)), boxBot + 5, Skins.tierCol(view));
        if (view >= 0) Ui.txt(ctx, tr, tr.trimToWidth(Lang.t("Finisher: ", "Finisher: ") + (owned ? KillFx.STYLES[view] : "???") + (type >= 4 ? Lang.t(" · Nachladen", " · reload") : ""), lx1 - lx0 - 4),
            lx0 + 2, boxBot + 16, 0xFF8B92A5);

        int bw = (lx1 - lx0 - 6) / 2;
        boolean eq = Skins.equipped(type) == view;
        Ui.btn(ctx, tr, lx0, by, lx0 + bw, by + 20, eq ? Lang.t("✔ Ausgerüstet", "✔ Equipped") : owned ? Lang.t("Ausrüsten", "Equip") : Lang.t("Gesperrt", "Locked"),
            Ui.in(mouseX, mouseY, lx0, by, lx0 + bw, by + 20), eq ? 0xFF15301F : owned ? Ui.ACCENT_DIM : 0xFF1A1E27, eq ? 0xFF15301F : owned ? Ui.ACCENT_DIM_HOV : 0xFF1A1E27,
            owned ? 0xFFFFFFFF : 0xFF6F7380);
        boolean canFin = view >= 0 && owned;
        Ui.btn(ctx, tr, lx0 + bw + 6, by, lx1, by + 20, Lang.t("▶ Finisher ansehen", "▶ View finisher"), Ui.in(mouseX, mouseY, lx0 + bw + 6, by, lx1, by + 20),
            canFin ? 0xFF2A1E40 : 0xFF1A1E27, canFin ? 0xFF3A2A5A : 0xFF1A1E27, canFin ? 0xFFFFFFFF : 0xFF6F7380);

        // Liste aller Skins (zwei Spalten)
        int n = Skins.bundles() + 1, rows = (n + 1) / 2, rh = Math.min(22, (py1 - 10 - top) / rows), cw = (rx1 - rx0 - 4) / 2;
        for (int e = 0; e < n; e++) {
            int i = e == 0 ? -1 : Skins.ORDER[e - 1];
            int x = rx0 + (e / rows) * (cw + 4), y = top + (e % rows) * rh, x1 = x + cw;
            boolean has = i < 0 || Skins.owns(i, type);
            boolean sel = view == i, hov = Ui.in(mouseX, mouseY, x, y, x1, y + rh - 2);
            int col = i >= 0 ? 0xFF000000 | SkinGen.PAL[i][2] : 0xFF8B92A5;
            Ui.card(ctx, x, y, x1, y + rh - 2, sel ? col : (hov ? 0xFF3A4152 : Ui.BORDER), sel ? Ui.CARD_HOV : Ui.CARD);
            if (i >= 0) ctx.fill(x + 1, y + 1, x + 3, y + rh - 3, Skins.tierCol(i));
            int iy = y + (rh - 2 - 16) / 2;
            if (has) ctx.drawItem(CollectionScreen.stackFor(type, i), x + 5, iy);
            else {
                ctx.fill(x + 5, iy, x + 21, iy + 16, 0xFF050608);
                ctx.drawTextWithShadow(tr, "?", x + 13 - tr.getWidth("?") / 2, iy + 4, 0xFF8B92A5);
            }
            boolean eqd = Skins.equipped(type) == i;
            String nm2 = i < 0 ? Lang.t("Standard", "Default") : WeaponSkins.BUNDLES[i];
            nm2 = tr.trimToWidth(nm2, cw - 26 - (eqd ? 12 : 4));
            Ui.txt(ctx, tr, nm2, x + 24, y + (rh - 2 - 8) / 2, has ? (i >= 0 ? col : Ui.TEXT) : 0xFF5A6070);
            if (eqd) Ui.txt(ctx, tr, "✔", x1 - 10, y + (rh - 2 - 8) / 2, 0xFF4ADE80);
        }
    }
}
