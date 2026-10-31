package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Startfläche der Taste K: zehn Kacheln (5 nebeneinander, 2 Reihen). */
public class HubScreen extends Screen {
    private static final int HEAD = 34;
        private static final int N = 10;

    private int px0, px1, py0, py1, cardW, CARD_H = 80, COLS = 5, ROWS = 2, skinCount = 0;

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
        int panelW = Math.min(590, this.width - 16);
        // Kachel-Höhe an kleine Fenster / große GUI-Skalierung anpassen, damit nichts abgeschnitten wird
        COLS = panelW >= 520 ? 5 : panelW >= 300 ? 4 : 3;
        ROWS = (N + COLS - 1) / COLS;
        CARD_H = Math.max(30, Math.min(80, (this.height - 8 - HEAD - 12 - (ROWS - 1) * 8 - 12) / ROWS));
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
    }

    private static void open(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (i == 0) mc.setScreen(SoundboardScreen.create(false));
        else if (i == 1) mc.setScreen(SoundboardScreen.create(true));
        else if (i == 2) mc.setScreen(new BuildMenuScreen());
        else if (i == 3) mc.setScreen(new MacroScreen());
        else if (i == 4) mc.setScreen(new PackSetsScreen());
        else if (i == 5) mc.setScreen(new PvpScreen());
        else if (i == 6) mc.setScreen(new LyricsScreen());
        else if (i == 7) mc.setScreen(new AuctionScreen());
        else if (i == 8) mc.setScreen(new SkinScreen());
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
        String hint = Lang.t("Taste ", "Key ") + Macros.keyName(cfg.menuKey);
        int hw = tr.getWidth(hint) + 12;
        Ui.card(ctx, px1 - 12 - hw, py0 + 9, px1 - 12, py0 + 25, Ui.BORDER, Ui.SURFACE);
        Ui.txt(ctx, tr, hint, px1 - 12 - hw + 6, py0 + 13, Ui.MUTED);

        boolean playing = Soundboard.nowPlaying != null;
        int build = BuildHelper.activeCount();
        int packs = Packs.activeCount();
        int mods = Integer.bitCount(cfg.pvpMods);
        boolean lyLive = cfg.enabled && (SpotifyApi.state != null || playing);

        int macros = cfg.macros.size();
        boolean macroRun = Macros.running >= 0;
        String[] names = {"Soundboard", "Tools", Lang.t("Bauen", "Build"), Lang.t("Makros", "Macros"), "Texture Packs", "PvP", "Lyrics",
            Lang.t("Auktionen", "Auctions"), "Skin", Lang.t("Einstellungen", "Settings")};
        String[] sub1 = {Lang.t("Effekte · Musik · Voice", "Sounds · Music · Voice"), Lang.t("Filter · Licht · Klick", "Filter · Light · Click"),
            Lang.t("Kreis · Raster · Lineal", "Circle · Grid · Ruler"), Lang.t("Bausteine · Abläufe", "Blocks · Sequences"), "Pack-Sets",
            Lang.t("Module · Fadenkreuz", "Modules · Crosshair"),
            LyricsHud.STYLES[Math.floorMod(cfg.lyStyle, LyricsHud.STYLES.length)] + " · "
                + LyricsHud.UNITS[Math.floorMod(cfg.lyUnit, LyricsHud.UNITS.length)],
            Lang.t("Bieten per /pay", "Bid with /pay"), Lang.t("Hochladen · Wechseln", "Upload · Switch"),
            Lang.t("Farben · Sprache", "Colors · Language")};
        String[] sub2 = {playing ? Lang.t("Spielt gerade", "Playing") : Lang.t("Bereit", "Ready"),
            AutoClick.active ? Lang.t("Auto-Klick läuft", "Auto-click on") : (cfg.filterOn ? Lang.t("Filter an", "Filter on") : Lang.t("Filter aus", "Filter off")),
            build > 0 ? build + Lang.t(" Helfer an", " helpers on") : Lang.t("Alles aus", "All off"),
            macroRun ? Lang.t("Makro läuft", "Macro running") : macros + Lang.t(" Makros", " macros"),
            packs + Lang.t(" aktiv", " active"), mods + Lang.t(" Module an", " modules on"),
            !cfg.enabled ? Lang.t("Aus", "Off") : lyLive ? Lang.t("Läuft", "Live") : Lang.t("Wartet auf Spotify", "Waiting for Spotify"),
            Auction.running ? Lang.t("Läuft ", "Live ") + Auction.timeLeft() : Lang.t("Keine aktiv", "None active"),
            skinCount + " Skins",
            Ui.ACCENT_NAMES[Math.floorMod(cfg.uiAccent, Ui.ACCENT_NAMES.length)] + " · " + Lang.NAMES[Math.floorMod(cfg.lang, Lang.NAMES.length)]};
        int off = 0xFF4A5164;
        int[] dot = {playing ? Ui.GREEN : off, AutoClick.active ? Ui.GREEN : off, build > 0 ? Ui.GREEN : off, macroRun ? Ui.GREEN : off,
            packs > 0 ? Ui.GREEN : off, mods > 0 || cfg.hitAssist ? Ui.GREEN : off,
            !cfg.enabled ? off : lyLive ? Ui.GREEN : Ui.AMBER, Auction.running ? Ui.GREEN : off, off, Ui.ACCENT};
        int[] accent = {Ui.ACCENT, Ui.AMBER, 0xFFFB923C, 0xFFA78BFA, 0xFF34D399, Ui.RED, 0xFFF472B6, 0xFFFFD166, 0xFF60A5FA, 0xFFE5E7EB};
        String[] icons = {"note", "bolt", "cube", "play", "packs", "sword", "mic", "gavel", "people", "cog"};

        for (int i = 0; i < N; i++) {
            int x0 = cx(i), y0 = cy(i), x1 = x0 + cardW, y1 = y0 + CARD_H;
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, Ui.BG);   // normalen Knopf darunter verdecken
            Ui.card(ctx, x0, y0, x1, y1, hov ? accent[i] : Ui.BORDER, hov ? Ui.CARD_HOV : Ui.CARD);
            if (CARD_H >= 72) {
                // Symbol-Kachel oben links (gezeichnetes Symbol), Pfeil oben rechts
                Ui.round2(ctx, x0 + 8, y0 + 8, x0 + 36, y0 + 36, (accent[i] & 0x00FFFFFF) | 0x30000000);
                Icons.draw(ctx, icons[i], x0 + 10, y0 + 10, 2, hov ? 0xFFFFFFFF : accent[i]);
                Ui.txt(ctx, tr, "›", x1 - 14, y0 + 19, hov ? accent[i] : Ui.MUTED);
                // Name und Beschreibung
                Ui.txt(ctx, tr, tr.trimToWidth(names[i], cardW - 20), x0 + 10, y0 + 43, Ui.TEXT);
                Ui.txt(ctx, tr, tr.trimToWidth(sub1[i], cardW - 20), x0 + 10, y0 + 55, Ui.MUTED);
            } else {
                // kompakt: kleines Symbol neben dem Namen
                Ui.round2(ctx, x0 + 6, y0 + 6, x0 + 22, y0 + 22, (accent[i] & 0x00FFFFFF) | 0x30000000);
                Icons.draw(ctx, icons[i], x0 + 8, y0 + 8, 1, hov ? 0xFFFFFFFF : accent[i]);
                Ui.txt(ctx, tr, tr.trimToWidth(names[i], cardW - 34), x0 + 27, y0 + 10, Ui.TEXT);
            }
            // Statuszeile
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
