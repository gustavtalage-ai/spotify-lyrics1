package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** Startfläche der Taste K: vier große Kästen. */
public class HubScreen extends Screen {
    private static final int HEAD = 30;
    private static final int CARD_H = 74;

    private int px0, px1, py0, py1, cardW;

    public HubScreen() {
        super(Text.literal("Menü"));
    }

    private int cx(int i) {
        return px0 + 10 + (i % 2) * (cardW + 10);
    }

    private int cy(int i) {
        return py0 + HEAD + 10 + (i / 2) * (CARD_H + 10);
    }

    @Override
    protected void init() {
        int panelW = Math.min(340, this.width - 20);
        int panelH = HEAD + 10 + 2 * CARD_H + 10 + 10;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cardW = (panelW - 30) / 2;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> open(idx))
                .dimensions(cx(i), cy(i), cardW, CARD_H).build());
        }
    }

    private static void open(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (i == 0) mc.setScreen(SoundboardScreen.create(false));
        else if (i == 1) mc.setScreen(SoundboardScreen.create(true));
        else if (i == 2) mc.setScreen(new TexturePacksScreen());
        else mc.setScreen(new PvpScreen());
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        ctx.drawTextWithShadow(tr, "Spotify Lyrics", px0 + 10, py0 + 6, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, "Wähle einen Bereich", px0 + 10, py0 + 17, 0xFF7E8494);

        Config cfg = Config.INSTANCE;
        String[] names = {"Soundboard", "Tools", "Texture Packs", "PvP"};
        String[] sub1 = {"Effekte · Musik", "Platzhilfe · Filter", "Packs an / aus", "Hit-Assistent"};
        String[] sub2 = {"Voice · Spotify", "Lichtshow", Packs.activeCount() + " aktiv", "Low Fire: " + (Packs.lowFireOn() ? "an" : "aus")};
        int[] accent = {0xFFAA00FF, 0xFF3B82F6, 0xFF4ADE80, 0xFFFF5A5A};
        ItemStack[] icons = {new ItemStack(Items.NOTE_BLOCK), new ItemStack(Items.IRON_PICKAXE),
            new ItemStack(Items.GRASS_BLOCK), new ItemStack(Items.DIAMOND_SWORD)};
        if (cfg.hitAssist) sub1[3] = "Hit-Assistent: an";

        for (int i = 0; i < 4; i++) {
            int x0 = cx(i), y0 = cy(i), x1 = x0 + cardW, y1 = y0 + CARD_H;
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, 0xFF0F1117);   // normalen Knopf darunter verdecken
            if (hov) Ui.round2(ctx, x0 - 2, y0 - 2, x1 + 2, y1 + 2, (accent[i] & 0x00FFFFFF) | 0x40000000);
            Ui.round2(ctx, x0, y0, x1, y1, hov ? accent[i] : 0xFF303546);
            Ui.round2(ctx, x0 + 1, y0 + 1, x1 - 1, y1 - 1, hov ? 0xFF262B3A : 0xFF1B1F2A);
            ctx.fill(x0 + 8, y1 - 5, x1 - 8, y1 - 3, accent[i]);
            ctx.drawItem(icons[i], x0 + 9, y0 + 9);
            ctx.drawTextWithShadow(tr, names[i], x0 + 30, y0 + 13, 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(sub1[i], cardW - 16), x0 + 10, y0 + 32, 0xFF9AA0AE);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(sub2[i], cardW - 16), x0 + 10, y0 + 44, 0xFF9AA0AE);
        }
    }
}
