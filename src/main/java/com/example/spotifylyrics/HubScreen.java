package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** Startfläche der Taste K: vier große Kacheln. */
public class HubScreen extends Screen {
    private static final int HEAD = 34;
    private static final int CARD_H = 76;

    private int px0, px1, py0, py1, cardW;

    public HubScreen() {
        super(Text.literal("Menü"));
    }

    private int cx(int i) {
        return px0 + 12 + (i % 2) * (cardW + 10);
    }

    private int cy(int i) {
        return py0 + HEAD + 12 + (i / 2) * (CARD_H + 10);
    }

    @Override
    protected void init() {
        int panelW = Math.min(372, this.width - 20);
        int panelH = HEAD + 12 + 2 * CARD_H + 10 + 12;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cardW = (panelW - 34) / 2;
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
        else if (i == 2) mc.setScreen(new PackSetsScreen());
        else mc.setScreen(new PvpScreen());
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        Ui.txt(ctx, tr, "Spotify Lyrics", px0 + 12, py0 + 8, Ui.TEXT);
        Ui.txt(ctx, tr, "Wähle einen Bereich", px0 + 12, py0 + 20, Ui.MUTED);
        String hint = "Taste K";
        int hw = tr.getWidth(hint) + 12;
        Ui.card(ctx, px1 - 12 - hw, py0 + 9, px1 - 12, py0 + 25, Ui.BORDER, Ui.SURFACE);
        Ui.txt(ctx, tr, hint, px1 - 12 - hw + 6, py0 + 13, Ui.MUTED);

        Config cfg = Config.INSTANCE;
        String[] names = {"Soundboard", "Tools", "Texture Packs", "PvP"};
        String[] sub1 = {"Effekte & Musik", "Auto-Klick · Filter", "Pack-Sets", cfg.hitAssist ? "Hit-Assistent an" : "Hit-Assistent aus"};
        boolean playing = Soundboard.nowPlaying != null;
        String[] sub2 = {playing ? "Spielt gerade" : "Voice · Spotify", AutoClick.active ? "Auto-Klick läuft" : "Platzhilfe · Licht",
            Packs.activeCount() + " aktiv", Integer.bitCount(cfg.pvpMods) + " Module an"};
        boolean[] live = {playing, AutoClick.active, Packs.activeCount() > 0, Integer.bitCount(cfg.pvpMods) > 0 || cfg.hitAssist};
        int[] accent = {Ui.ACCENT, Ui.AMBER, 0xFF34D399, Ui.RED};
        ItemStack[] icons = {new ItemStack(Items.NOTE_BLOCK), new ItemStack(Items.IRON_PICKAXE),
            new ItemStack(Items.GRASS_BLOCK), new ItemStack(Items.DIAMOND_SWORD)};

        for (int i = 0; i < 4; i++) {
            int x0 = cx(i), y0 = cy(i), x1 = x0 + cardW, y1 = y0 + CARD_H;
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, Ui.BG);   // normalen Knopf darunter verdecken
            Ui.card(ctx, x0, y0, x1, y1, hov ? accent[i] : Ui.BORDER, hov ? 0xFF1A202B : 0xFF151921);
            // Symbol-Kachel
            Ui.round2(ctx, x0 + 10, y0 + 10, x0 + 40, y0 + 40, (accent[i] & 0x00FFFFFF) | 0x30000000);
            ctx.drawItem(icons[i], x0 + 17, y0 + 17);
            Ui.txt(ctx, tr, names[i], x0 + 48, y0 + 13, Ui.TEXT);
            Ui.txt(ctx, tr, tr.trimToWidth(sub1[i], cardW - 58), x0 + 48, y0 + 26, Ui.MUTED);
            Ui.txt(ctx, tr, "›", x1 - 14, y0 + 13, hov ? accent[i] : Ui.MUTED);
            // Statuszeile
            ctx.fill(x0 + 10, y1 - 24, x1 - 10, y1 - 23, 0x18FFFFFF);
            Ui.round(ctx, x0 + 10, y1 - 15, x0 + 16, y1 - 9, live[i] ? Ui.GREEN : 0xFF4A5164);
            Ui.txt(ctx, tr, tr.trimToWidth(sub2[i], cardW - 34), x0 + 22, y1 - 17, live[i] ? Ui.TEXT : Ui.MUTED);
        }
    }
}
