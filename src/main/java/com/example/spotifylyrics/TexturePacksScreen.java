package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

import java.util.List;

/** Alle Texture-Packs mit Ein/Aus-Schalter. */
public class TexturePacksScreen extends Screen {
    private static final int HEAD = 28;
    private static final int ROW = 24;
    private static int scrollPos = 0;

    private int px0, px1, py0, py1, cx, cw, rowsY, shown;
    private List<ResourcePackProfile> packs = List.of();

    public TexturePacksScreen() {
        super(Text.literal("Texture Packs"));
    }

    private void invisible(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        packs = Packs.list();
        int panelW = Math.min(320, this.width - 20);
        int maxRows = Math.max(3, (this.height - 24 - HEAD - 30) / ROW);
        shown = Math.max(1, Math.min(Math.max(packs.size(), 1), Math.min(8, maxRows)));
        scrollPos = Math.max(0, Math.min(scrollPos, Math.max(0, packs.size() - shown)));
        int panelH = HEAD + 10 + shown * ROW + 24;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cx = px0 + 10;
        cw = panelW - 20;
        rowsY = py0 + HEAD + 8;

        invisible(px1 - 10 - 64, py0 + 5, 64, 18, () -> MinecraftClient.getInstance().setScreen(new PackSetsScreen()));
        for (int v = 0; v < shown && scrollPos + v < packs.size(); v++) {
            final ResourcePackProfile p = packs.get(scrollPos + v);
            invisible(cx, rowsY + v * ROW, cw - 8, ROW - 3, () -> {
                Packs.set(p, !Packs.isOn(p));
                MinecraftClient.getInstance().setScreen(new TexturePacksScreen());
            });
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, packs.size() - shown);
        if (max > 0) {
            int next = Math.max(0, Math.min(max, scrollPos - (int) Math.signum(vertical)));
            if (next != scrollPos) {
                scrollPos = next;
                MinecraftClient.getInstance().setScreen(new TexturePacksScreen());
            }
            return true;
        }
        return false;
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
        if (packs.size() > shown) {
            int trackH = shown * ROW - 3;
            int thumbH = Math.max(14, trackH * shown / packs.size());
            int max = packs.size() - shown;
            int thumbY = rowsY + (trackH - thumbH) * scrollPos / max;
            Ui.round(ctx, px1 - 9, rowsY, px1 - 5, rowsY + trackH, 0x30FFFFFF);
            Ui.round(ctx, px1 - 9, thumbY, px1 - 5, thumbY + thumbH, 0xFF4ADE80);
        }
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        Ui.txt(ctx, tr, "Texture Packs", px0 + 10, py0 + 6, 0xFFFFFFFF);
        Ui.txt(ctx, tr, packs.size() + " Packs · " + Packs.activeCount() + " aktiv", px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23, "‹ Sets",
            Ui.in(mouseX, mouseY, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        if (packs.isEmpty()) {
            Ui.txt(ctx, tr, "Keine Packs gefunden (Ordner resourcepacks).", cx + 2, rowsY + 6, 0xFFFFAA00);
            return;
        }
        for (int v = 0; v < shown && scrollPos + v < packs.size(); v++) {
            ResourcePackProfile p = packs.get(scrollPos + v);
            int y = rowsY + v * ROW;
            Ui.toggle(ctx, tr, cx, y, cx + cw - 8, y + ROW - 3, p.getDisplayName().getString(), Packs.isOn(p),
                Ui.in(mouseX, mouseY, cx, y, cx + cw - 8, y + ROW - 3));
        }
        Ui.txt(ctx, tr, "Klick = an/aus · Texturen laden danach neu", cx + 2, py1 - 14, 0xFF8B92A5);
    }
}
