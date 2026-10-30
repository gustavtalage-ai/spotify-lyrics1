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
    private int yHit, yHint, yQueue, yWeap, yW0, yMin, yGfx, yQuick;
    /** Schnellschalter: Anzeigename, danach Suchbegriffe (klein, ohne Leerzeichen) im Pack-Namen. */
    private static final String[][] QUICK = {
        {"Low Fire", "lowonfire", "lowfire"},
        {"Low Shield", "lowshield"},
        {"Schild + Totem klein", "smallshieldtotem"},
        {"Totem-Pop klein", "smalltotempop"},
        {"Kurze Schwerter", "shortswords", "shorterswords"},
        {"Crystal PvP", "crystalvanilla"}
    };
    private final java.util.List<String> qLabel = new java.util.ArrayList<>();
    private final java.util.List<ResourcePackProfile> qProf = new java.util.ArrayList<>();

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
        qLabel.clear();
        qProf.clear();
        for (String[] q : QUICK) {
            ResourcePackProfile f = Packs.find(java.util.Arrays.copyOfRange(q, 1, q.length));
            if (f != null && !qProf.contains(f)) {
                qLabel.add(q[0]);
                qProf.add(f);
            }
        }
        int panelH = 227 + 14 + Math.max(1, (qProf.size() + 1) / 2) * 22 + 14;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cx = px0 + 10;
        cw = panelW - 20;

        yHit = py0 + HEAD + 8;           // Überschrift
        yHint = yHit + 14;               // Schalter
        yQueue = yHint + 26;             // Klick merken
        yWeap = yQueue + 38;              // "Waffen"
        yW0 = yWeap + 13;                // 3 Reihen à 22
        yMin = yW0 + 3 * 22 + 4;         // Aufladung
        yGfx = yMin + 30;                // "Packs"
        yQuick = yGfx + 14;

        Config c = Config.INSTANCE;
        invisible(px1 - 10 - 64, py0 + 5, 64, 18, () -> MinecraftClient.getInstance().setScreen(new HubScreen()));

        invisible(cx, yHint, cw, 22, () -> {
            c.hitAssist = !c.hitAssist;
            c.save();
            reopen();
        });
        invisible(cx, yQueue, cw, 22, () -> {
            c.hitQueue = !c.hitQueue;
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
        int qh = (cw - 6) / 2;
        for (int i = 0; i < qProf.size(); i++) {
            final ResourcePackProfile qp = qProf.get(i);
            int x = cx + (i % 2) * (qh + 6);
            int y = yQuick + (i / 2) * 22;
            invisible(x, y, qh, 20, () -> {
                Packs.set(qp, !Packs.isOn(qp));
                reopen();
            });
        }
        invisible(px1 - 10 - 80, yGfx - 3, 80, 14, () -> MinecraftClient.getInstance().setScreen(new TexturePacksScreen()));
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
        ctx.drawTextWithShadow(tr, "Hit-Assistent und Schnell-Packs", px0 + 10, py0 + 17, 0xFF7E8494);
        Ui.btn(ctx, tr, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23, "‹ Menü",
            Ui.in(mouseX, mouseY, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23), 0xFF1F232E, 0xFF2A3040, 0xFFD6D9E0);

        Ui.section(ctx, tr, cx, yHit, "Hit-Assistent", cw);
        Ui.toggle(ctx, tr, cx, yHint, cx + cw, yHint + 22, "Hit-Assistent", c.hitAssist, Ui.in(mouseX, mouseY, cx, yHint, cx + cw, yHint + 22));
        Ui.toggle(ctx, tr, cx, yQueue, cx + cw, yQueue + 22, "Klick merken (perfekter Schlag)", c.hitQueue, Ui.in(mouseX, mouseY, cx, yQueue, cx + cw, yQueue + 22));
        ctx.drawTextWithShadow(tr, "Zu früh geklickt: Schlag kommt bei voller Leiste.", cx + 2, yQueue + 26, 0xFF7E8494);

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

        Ui.section(ctx, tr, cx, yGfx, "Schnell-Packs", cw - 80);
        boolean lh = Ui.in(mouseX, mouseY, px1 - 10 - 80, yGfx - 3, px1 - 10, yGfx + 11);
        String more = "Alle Packs \u203a";
        ctx.drawTextWithShadow(tr, more, px1 - 10 - tr.getWidth(more), yGfx, lh ? 0xFFFFFFFF : 0xFF9AA0AE);
        int qh = (cw - 6) / 2;
        for (int i = 0; i < qProf.size(); i++) {
            int x = cx + (i % 2) * (qh + 6);
            int y = yQuick + (i / 2) * 22;
            Ui.toggle(ctx, tr, x, y, x + qh, y + 20, qLabel.get(i), Packs.isOn(qProf.get(i)), Ui.in(mouseX, mouseY, x, y, x + qh, y + 20));
        }
        if (qProf.isEmpty()) {
            ctx.drawTextWithShadow(tr, "Keine passenden Packs gefunden.", cx + 2, yQuick + 6, 0xFFFF6A6A);
        }
    }
}
