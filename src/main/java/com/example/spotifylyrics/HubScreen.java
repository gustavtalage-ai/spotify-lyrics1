package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Startfläche der Taste K: vier große Kacheln (2×2), oben rechts Skin und Taste. */
public class HubScreen extends Screen {
    private static final int HEAD = 34;
    private static final int N = 4;

    private int px0, px1, py0, py1, cardW, CARD_H = 96, COLS = 4, ROWS = 1, skinCount = 0;
    private int skX0, skX1, keyX0, keyX1;

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
        int panelW = Math.min(420, this.width - 16);
        COLS = 2;
        ROWS = (N + COLS - 1) / COLS;
        CARD_H = Math.max(30, Math.min(96, (this.height - 8 - HEAD - 12 - (ROWS - 1) * 8 - 12) / ROWS));
        int panelH = HEAD + 12 + ROWS * CARD_H + (ROWS - 1) * 8 + 12;
        skinCount = SkinScreen.skins().size();
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
        // Kopfleiste rechts: [Skin] [Taste K]
        TextRenderer tr = this.textRenderer;
        String hint = Lang.t("Taste ", "Key ") + Macros.keyName(Config.INSTANCE.menuKey);
        keyX1 = px1 - 12;
        keyX0 = keyX1 - (tr.getWidth(hint) + 12);
        skX1 = keyX0 - 6;
        skX0 = skX1 - (tr.getWidth("Skin") + 30);
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(new SkinScreen()))
            .dimensions(skX0, py0 + 9, skX1 - skX0, 16).build());
    }

    private static void open(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (i == 0) mc.setScreen(new SoundScreen());
        else if (i == 1) mc.setScreen(new ToolsScreen());
        else if (i == 2) mc.setScreen(new PvpScreen());
        else mc.setScreen(new SettingsScreen());
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        Config cfg = Config.INSTANCE;
        Ui.txt(ctx, tr, "Alpha Mod", px0 + 12, py0 + 8, Ui.TEXT);
        Ui.txt(ctx, tr, Lang.t("Wähle einen Bereich", "Choose a section"), px0 + 12, py0 + 20, Ui.MUTED);
        // Taste
        String hint = Lang.t("Taste ", "Key ") + Macros.keyName(cfg.menuKey);
        Ui.card(ctx, keyX0, py0 + 9, keyX1, py0 + 25, Ui.BORDER, Ui.SURFACE);
        Ui.txt(ctx, tr, hint, keyX0 + 6, py0 + 13, Ui.MUTED);
        // Skin-Knopf mit kleinem Symbol
        boolean skHov = Ui.in(mouseX, mouseY, skX0, py0 + 9, skX1, py0 + 25);
        ctx.fill(skX0, py0 + 9, skX1, py0 + 25, Ui.HEAD_BG);
        Ui.card(ctx, skX0, py0 + 9, skX1, py0 + 25, skHov ? 0xFF60A5FA : Ui.BORDER, skHov ? Ui.SURFACE_HOV : Ui.SURFACE);
        Icons.draw(ctx, "people", skX0 + 5, py0 + 11, 1, skHov ? 0xFFFFFFFF : 0xFF60A5FA);
        Ui.txt(ctx, tr, "Skin", skX0 + 22, py0 + 13, skHov ? Ui.TEXT : 0xFFD6D9E0);

        boolean playing = Soundboard.nowPlaying != null;
        int build = BuildHelper.activeCount();
        int mods = Integer.bitCount(cfg.pvpMods);
        boolean lyLive = cfg.enabled && (SpotifyApi.state != null || playing);
        int macros = cfg.macros.size();
        boolean macroRun = Macros.running >= 0;
        boolean toolsOn = AutoClick.active || cfg.filterOn || cfg.lightshow || build > 0 || macroRun || Auction.running;

        String[] names = {"Soundboard", "Tools", "PvP", Lang.t("Einstellungen", "Settings")};
        String[] sub1 = {Lang.t("Sounds · Voice · Lyrics", "Sounds · Voice · Lyrics"),
            Lang.t("Makros · Bauen · Auktionen", "Macros · Build · Auctions"),
            Lang.t("Module · Fadenkreuz", "Modules · Crosshair"),
            Lang.t("Farben · Sprache", "Colors · Language")};
        String toolsState = Auction.running ? Lang.t("Auktion ", "Auction ") + Auction.timeLeft()
            : macroRun ? Lang.t("Makro läuft", "Macro running")
            : AutoClick.active ? Lang.t("Auto-Klick läuft", "Auto-click on")
            : toolsOn ? Lang.t("Aktiv", "Active") : macros + Lang.t(" Makros", " macros");
        String soundState = playing ? Lang.t("Spielt gerade", "Playing")
            : lyLive ? Lang.t("Lyrics laufen", "Lyrics live") : Lang.t("Bereit", "Ready");
        String[] sub2 = {soundState, toolsState, mods + Lang.t(" Module an", " modules on"),
            Ui.ACCENT_NAMES[Math.floorMod(cfg.uiAccent, Ui.ACCENT_NAMES.length)] + " · " + Lang.NAMES[Math.floorMod(cfg.lang, Lang.NAMES.length)]};
        int off = 0xFF4A5164;
        int[] dot = {playing || lyLive ? Ui.GREEN : off, toolsOn ? Ui.GREEN : off, mods > 0 || cfg.hitAssist ? Ui.GREEN : off, Ui.ACCENT};
        int[] accent = {Ui.ACCENT, Ui.AMBER, Ui.RED, 0xFFE5E7EB};
        String[] icons = {"note", "bolt", "sword", "cog"};

        for (int i = 0; i < N; i++) {
            int x0 = cx(i), y0 = cy(i), x1 = x0 + cardW, y1 = y0 + CARD_H;
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, Ui.BG);
            Ui.card(ctx, x0, y0, x1, y1, hov ? accent[i] : Ui.BORDER, hov ? Ui.CARD_HOV : Ui.CARD);
            if (CARD_H >= 72) {
                Ui.round2(ctx, x0 + 8, y0 + 8, x0 + 36, y0 + 36, (accent[i] & 0x00FFFFFF) | 0x30000000);
                Icons.draw(ctx, icons[i], x0 + 10, y0 + 10, 2, hov ? 0xFFFFFFFF : accent[i]);
                Ui.txt(ctx, tr, "›", x1 - 14, y0 + 19, hov ? accent[i] : Ui.MUTED);
                int ty = y0 + (CARD_H >= 90 ? 48 : 43);
                Ui.txt(ctx, tr, tr.trimToWidth(names[i], cardW - 20), x0 + 10, ty, Ui.TEXT);
                Ui.txt(ctx, tr, tr.trimToWidth(sub1[i], cardW - 20), x0 + 10, ty + 12, Ui.MUTED);
            } else {
                Ui.round2(ctx, x0 + 6, y0 + 6, x0 + 22, y0 + 22, (accent[i] & 0x00FFFFFF) | 0x30000000);
                Icons.draw(ctx, icons[i], x0 + 8, y0 + 8, 1, hov ? 0xFFFFFFFF : accent[i]);
                Ui.txt(ctx, tr, tr.trimToWidth(names[i], cardW - 34), x0 + 27, y0 + 10, Ui.TEXT);
            }
            boolean on = dot[i] != off;
            if (CARD_H >= 40) {
                Ui.round(ctx, x0 + 10, y1 - 12, x0 + 16, y1 - 6, dot[i]);
                Ui.txt(ctx, tr, tr.trimToWidth(sub2[i], cardW - 30), x0 + 21, y1 - 13, on ? Ui.TEXT : Ui.MUTED);
            } else {
                Ui.round(ctx, x1 - 10, y0 + 12, x1 - 5, y0 + 17, dot[i]);
            }
        }
    }
}
