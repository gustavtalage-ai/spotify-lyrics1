package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

/** PvP-Einstellungen: Hit-Assistent und Low Fire. */
public class PvpScreen extends Screen {
    private static final int HEAD = 28;
    private int px0, px1, py0, py1, cx, cw;
    private int yHit, yHint, yWeap, yW0, yMin, yGfx, yFire, yFireHint;

    public PvpScreen() {
        super(Text.literal("PvP"));
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new PvpScreen());
    }

    private void invisible(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int panelW = Math.min(320, this.width - 20);
        int panelH = 262;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cx = px0 + 10;
        cw = panelW - 20;

        yHit = py0 + HEAD + 8;           // Überschrift
        yHint = yHit + 14;               // Schalter
        yWeap = yHint + 38;              // "Waffen"
        yW0 = yWeap + 13;                // 3 Reihen à 22
        yMin = yW0 + 3 * 22 + 4;         // Aufladung
        yGfx = yMin + 30;                // "Grafik"
        yFire = yGfx + 13;
        yFireHint = yFire + 24;

        Config c = Config.INSTANCE;
        invisible(px1 - 10 - 64, py0 + 5, 64, 18, () -> MinecraftClient.getInstance().setScreen(new HubScreen()));

        invisible(cx, yHint, cw, 22, () -> {
            c.hitAssist = !c.hitAssist;
            c.save();
            reopen();
        });
        int half = (cw - 6) / 2;
        for (int i = 0; i < 5; i++) {
            final int bit = i;
            int x = cx + (i % 2) * (half + 6);
            int y = yW0 + (i / 2) * 22;
            invisible(x, y, half, 20, () -> {
                c.hitWeapons ^= 1 << bit;
                c.save();
                reopen();
            });
        }
        invisible(cx + cw - 52, yMin + 2, 24, 20, () -> {
            c.hitAssistMin = Math.max(50, c.hitAssistMin - 5);
            c.save();
            reopen();
        });
        invisible(cx + cw - 24, yMin + 2, 24, 20, () -> {
            c.hitAssistMin = Math.min(100, c.hitAssistMin + 5);
            c.save();
            reopen();
        });
        invisible(cx, yFire, cw, 22, () -> {
            ResourcePackProfile p = Packs.lowFire();
            if (p != null) Packs.set(p, !Packs.isOn(p));
            reopen();
        });
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Config c = Config.INSTANCE;

        ctx.drawTextWithShadow(tr, "PvP", px0 + 10, py0 + 6, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, "Hit-Assistent und Low Fire", px0 + 10, py0 + 17, 0xFF7E8494);
        Ui.btn(ctx, tr, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23, "‹ Menü",
            Ui.in(mouseX, mouseY, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23), 0xFF1F232E, 0xFF2A3040, 0xFFD6D9E0);

        Ui.section(ctx, tr, cx, yHit, "Hit-Assistent", cw);
        Ui.toggle(ctx, tr, cx, yHint, cx + cw, yHint + 22, "Hit-Assistent", c.hitAssist, Ui.in(mouseX, mouseY, cx, yHint, cx + cw, yHint + 22));
        ctx.drawTextWithShadow(tr, "Schlagen geht nur, wenn die Leiste voll ist.", cx + 2, yHint + 26, 0xFF7E8494);

        Ui.section(ctx, tr, cx, yWeap, "Gilt für diese Waffen", cw);
        int half = (cw - 6) / 2;
        for (int i = 0; i < 5; i++) {
            int x = cx + (i % 2) * (half + 6);
            int y = yW0 + (i / 2) * 22;
            Ui.toggle(ctx, tr, x, y, x + half, y + 20, HitAssist.NAMES[i], ((c.hitWeapons >> i) & 1) != 0, Ui.in(mouseX, mouseY, x, y, x + half, y + 20));
        }

        ctx.drawTextWithShadow(tr, "Aufladung ab: " + c.hitAssistMin + " %", cx + 2, yMin + 8, 0xFFD6D9E0);
        Ui.btn(ctx, tr, cx + cw - 52, yMin + 2, cx + cw - 28, yMin + 22, "-",
            Ui.in(mouseX, mouseY, cx + cw - 52, yMin + 2, cx + cw - 28, yMin + 22), 0xFF1F232E, 0xFF2A3040, 0xFFFFFFFF);
        Ui.btn(ctx, tr, cx + cw - 24, yMin + 2, cx + cw, yMin + 22, "+",
            Ui.in(mouseX, mouseY, cx + cw - 24, yMin + 2, cx + cw, yMin + 22), 0xFF1F232E, 0xFF2A3040, 0xFFFFFFFF);

        Ui.section(ctx, tr, cx, yGfx, "Grafik", cw);
        ResourcePackProfile p = Packs.lowFire();
        Ui.toggle(ctx, tr, cx, yFire, cx + cw, yFire + 22, "Low Fire", p != null && Packs.isOn(p), Ui.in(mouseX, mouseY, cx, yFire, cx + cw, yFire + 22));
        if (p == null) {
            ctx.drawTextWithShadow(tr, "Pack \"LowOnFire\" nicht gefunden (resourcepacks-Ordner).", cx + 2, yFireHint, 0xFFFF6A6A);
        } else {
            ctx.drawTextWithShadow(tr, "Schaltet dein Pack \"" + tr.trimToWidth(p.getDisplayName().getString(), cw - 90) + "\" an/aus.", cx + 2, yFireHint, 0xFF7E8494);
        }
    }
}
