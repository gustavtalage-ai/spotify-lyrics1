package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/** Startfläche der Taste K: sechs große Kacheln (3 nebeneinander, 2 Reihen). */
public class HubScreen extends Screen {
    private static final int HEAD = 34;
    private static final int CARD_H = 80;
    private static final int COLS = 3;
    private static final int N = 6;

    private int px0, px1, py0, py1, cardW;

    public HubScreen() {
        super(Text.literal("Menü"));
    }

    private int cx(int i) {
        return px0 + 12 + (i % COLS) * (cardW + 8);
    }

    private int cy(int i) {
        return py0 + HEAD + 12 + (i / COLS) * (CARD_H + 8);
    }

    @Override
    protected void init() {
        int panelW = Math.min(456, this.width - 20);
        int panelH = HEAD + 12 + 2 * CARD_H + 8 + 12;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cardW = (panelW - 24 - (COLS - 1) * 8) / COLS;
        for (int i = 0; i < N; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> open(idx))
                .dimensions(cx(i), cy(i), cardW, CARD_H).build());
        }
    }

    private static void open(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (i == 0) mc.setScreen(SoundboardScreen.create(false));
        else if (i == 1) mc.setScreen(SoundboardScreen.create(true));
        else if (i == 2) mc.setScreen(new BuildScreen());
        else if (i == 3) mc.setScreen(new PackSetsScreen());
        else if (i == 4) mc.setScreen(new PvpScreen());
        else mc.setScreen(new LyricsScreen());
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
        boolean playing = Soundboard.nowPlaying != null;
        int build = BuildHelper.activeCount();
        int packs = Packs.activeCount();
        int mods = Integer.bitCount(cfg.pvpMods);
        boolean lyLive = cfg.enabled && (SpotifyApi.state != null || playing);

        String[] names = {"Soundboard", "Tools", "Bauen", "Texture Packs", "PvP", "Lyrics"};
        String[] sub1 = {"Effekte · Musik · Voice", "Filter · Licht · Makros", "Kreis · Raster · Lineal", "Pack-Sets",
            cfg.hitAssist ? "Hit-Assistent an" : "Hit-Assistent aus",
            LyricsHud.STYLES[Math.floorMod(cfg.lyStyle, LyricsHud.STYLES.length)] + " · "
                + LyricsHud.UNITS[Math.floorMod(cfg.lyUnit, LyricsHud.UNITS.length)]};
        String[] sub2 = {playing ? "Spielt gerade" : "Bereit",
            AutoClick.active ? "Auto-Klick läuft" : (cfg.filterOn ? "Filter an" : "Filter aus"),
            build > 0 ? build + " Helfer an" : "Alles aus",
            packs + " aktiv", mods + " Module an",
            !cfg.enabled ? "Aus" : lyLive ? "Läuft" : "Wartet auf Spotify"};
        int[] dot = {playing ? Ui.GREEN : 0xFF4A5164, AutoClick.active ? Ui.GREEN : 0xFF4A5164, build > 0 ? Ui.GREEN : 0xFF4A5164,
            packs > 0 ? Ui.GREEN : 0xFF4A5164, mods > 0 || cfg.hitAssist ? Ui.GREEN : 0xFF4A5164,
            !cfg.enabled ? 0xFF4A5164 : lyLive ? Ui.GREEN : Ui.AMBER};
        int[] accent = {Ui.ACCENT, Ui.AMBER, 0xFFFB923C, 0xFF34D399, Ui.RED, 0xFFF472B6};
        ItemStack[] icons = {new ItemStack(Items.NOTE_BLOCK), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.BRICKS),
            new ItemStack(Items.GRASS_BLOCK), new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.JUKEBOX)};

        for (int i = 0; i < N; i++) {
            int x0 = cx(i), y0 = cy(i), x1 = x0 + cardW, y1 = y0 + CARD_H;
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, Ui.BG);   // normalen Knopf darunter verdecken
            Ui.card(ctx, x0, y0, x1, y1, hov ? accent[i] : Ui.BORDER, hov ? 0xFF1A202B : 0xFF151921);
            // Symbol-Kachel oben links, Pfeil oben rechts
            Ui.round2(ctx, x0 + 10, y0 + 10, x0 + 36, y0 + 36, (accent[i] & 0x00FFFFFF) | 0x30000000);
            ctx.drawItem(icons[i], x0 + 15, y0 + 15);
            Ui.txt(ctx, tr, "›", x1 - 14, y0 + 19, hov ? accent[i] : Ui.MUTED);
            // Name und Beschreibung
            Ui.txt(ctx, tr, tr.trimToWidth(names[i], cardW - 20), x0 + 10, y0 + 43, Ui.TEXT);
            Ui.txt(ctx, tr, tr.trimToWidth(sub1[i], cardW - 20), x0 + 10, y0 + 55, Ui.MUTED);
            // Statuszeile
            boolean on = dot[i] != 0xFF4A5164;
            Ui.round(ctx, x0 + 10, y1 - 12, x0 + 16, y1 - 6, dot[i]);
            Ui.txt(ctx, tr, tr.trimToWidth(sub2[i], cardW - 30), x0 + 21, y1 - 13, on ? Ui.TEXT : Ui.MUTED);
        }
    }
}
