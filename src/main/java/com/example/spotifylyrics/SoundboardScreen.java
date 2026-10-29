package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Das Soundboard-Menü (Taste K oder /soundboard) mit den Untertabs "Sounds" und "Voice Changer". */
public class SoundboardScreen extends Screen {
    private static final int ROW_H = 24;
    private static final int HEADER_H = 34;
    private static final int SEARCH_H = 28;
    private static final int TAB_H = 24;
    private static final int CARD_H = 30;
    private static final int CARD_ROWS = 5;
    private static final int FOOTER_H = 36;
    private static final int SLIDER_W = 72;
    private static final int SELF_W = 118;

    private static final int ACCENT = 0xFFAA00FF;
    private static final int PANEL = 0xFF0F1117;
    private static final int HEADER = 0xFF181B24;
    private static final int GREEN = 0xFF4ADE80;

    /** Suchtext; bleibt beim Neuaufbau des Menüs erhalten. */
    private static String searchText = "";
    /** Gewählter Untertab: 0 = Sounds, 1 = Voice Changer. */
    private static int tab = 0;

    private int scroll;                 // Index der ersten sichtbaren Zeile
    private List<Path> files = List.of();
    private int shown;                  // Anzahl sichtbarer Zeilen
    private int px0, px1, py0, py1, rowY0, half;

    public SoundboardScreen(int scroll) {
        super(Text.literal("Soundboard"));
        this.scroll = scroll;
    }

    @Override
    protected void init() {
        files = filter(Soundboard.list(), searchText);
        int panelW = Math.min(360, this.width - 20);
        int panelH;
        if (tab == 0) {
            int maxRows = Math.max(3, (this.height - 24 - HEADER_H - TAB_H - SEARCH_H - FOOTER_H - 8) / ROW_H);
            shown = Math.max(2, Math.min(files.size(), maxRows));
            int maxScroll = Math.max(0, files.size() - shown);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            panelH = HEADER_H + TAB_H + SEARCH_H + shown * ROW_H + FOOTER_H + 4;
        } else {
            shown = 0;
            panelH = HEADER_H + TAB_H + 8 + CARD_ROWS * (CARD_H + 4) + FOOTER_H + 2;
        }
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = (this.height - panelH) / 2;
        py1 = py0 + panelH;
        rowY0 = py0 + HEADER_H + TAB_H + SEARCH_H + 2;
        half = (px1 - px0 - 24) / 2;

        // Untertabs (die Knöpfe erledigen das Klicken, gezeichnet werden sie in render())
        for (int i = 0; i < 2; i++) {
            final int target = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                if (tab != target) {
                    tab = target;
                    MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
                }
            }).dimensions(tabX(i), tabY(), TAB_W, 18).build());
        }

        if (tab == 0) initSounds(); else initVoice();
    }

    private static final int TAB_W = 104;

    private int tabX(int i) {
        return px0 + 8 + i * (TAB_W + 6);
    }

    private int tabY() {
        return py0 + HEADER_H + 4;
    }

    private void initSounds() {
        // Zeilen: Play-Knopf links, Regler rechts
        for (int v = 0; v < shown && scroll + v < files.size(); v++) {
            Path f = files.get(scroll + v);
            int ry = rowY0 + v * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> Soundboard.play(f))
                .dimensions(px0 + 9, ry + 2, 20, 20).build());
            addDrawableChild(new VolumeSlider(sliderX(), ry + 2, SLIDER_W, 20, f));
        }

        // Suchleiste unter den Tabs
        TextFieldWidget search = new TextFieldWidget(this.textRenderer, px0 + 8, py0 + HEADER_H + TAB_H + 5, px1 - px0 - 16, 18, Text.literal("Suche"));
        search.setMaxLength(60);
        search.setText(searchText);
        search.setChangedListener(t -> {
            if (!t.equals(searchText)) {
                searchText = t;
                MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
            }
        });
        addDrawableChild(search);
        setFocused(search);
        search.setFocused(true);

        // Kopfzeile: Selbst hören
        boolean self = Soundboard.isListenSelf();
        addDrawableChild(ButtonWidget.builder(Text.literal("Selbst hören: " + (self ? "Ein" : "Aus")), b -> {
            Soundboard.setListenSelf(!Soundboard.isListenSelf());
            MinecraftClient.getInstance().setScreen(new SoundboardScreen(scroll));
        }).dimensions(px1 - 8 - SELF_W, py0 + 7, SELF_W, 20).build());

        // Fußzeile: Stopp, Ordner öffnen
        addDrawableChild(ButtonWidget.builder(Text.literal("Stopp"), b -> Soundboard.stop())
            .dimensions(px0 + 8, py1 - 28, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ordner öffnen"), b -> Soundboard.openFolder())
            .dimensions(px0 + 16 + half, py1 - 28, half, 20).build());
    }

    private int cardW() {
        return (px1 - px0 - 22) / 2;
    }

    private int cardX(int i) {
        return px0 + 8 + (i % 2) * (cardW() + 6);
    }

    private int cardY(int i) {
        return py0 + HEADER_H + TAB_H + 8 + (i / 2) * (CARD_H + 4);
    }

    private void initVoice() {
        for (int i = 0; i < VoiceChanger.PRESETS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                VoiceChanger.setPreset(idx);
                MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
            }).dimensions(cardX(i), cardY(i), cardW(), CARD_H).build());
        }
        // Fußzeile: selbst hören, Voice Changer ausschalten
        addDrawableChild(ButtonWidget.builder(Text.literal("Mich selbst hören"), b -> {
            VoiceChanger.setSelfHear(!VoiceChanger.isSelfHear());
            MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
        }).dimensions(px0 + 8, py1 - 28, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Voice Changer aus"), b -> {
            VoiceChanger.setPreset(0);
            MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
        }).dimensions(px0 + 16 + half, py1 - 28, half, 20).build());
    }

    private int sliderX() {
        return px1 - 16 - SLIDER_W;
    }

    // Absichtlich ohne @Override: Passt die Signatur, ersetzt das den Standard-Hintergrund durch das dunkle Design.
    // Passt sie nicht, bleibt der normale Hintergrund und alles funktioniert trotzdem.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xB0050609);
        Ui.round2(ctx, px0 - 5, py0 - 5, px1 + 5, py1 + 5, 0x20AA00FF);   // Leuchten
        Ui.round2(ctx, px0 - 3, py0 - 3, px1 + 3, py1 + 3, 0x28AA00FF);
        Ui.round2(ctx, px0 - 1, py0 - 1, px1 + 1, py1 + 1, ACCENT);       // Rand
        Ui.round2(ctx, px0, py0, px1, py1, PANEL);
        Ui.round2(ctx, px0, py0, px1, py0 + HEADER_H, HEADER);
        ctx.fill(px0, py0 + HEADER_H - 4, px1, py0 + HEADER_H, HEADER);
        ctx.fill(px0 + 1, py0 + HEADER_H, px1 - 1, py0 + HEADER_H + 1, ACCENT);
        ctx.fill(px0 + 1, py0 + HEADER_H + 1, px1 - 1, py0 + HEADER_H + 3, 0x20AA00FF);
        ctx.fill(px0 + 1, py1 - FOOTER_H, px1 - 1, py1 - FOOTER_H + 1, 0x28FFFFFF);
        ctx.fill(px0 + 1, py1 - FOOTER_H + 1, px1 - 1, py1 - 3, 0xFF0B0D12);

        // Scrollbalken, nur wenn nicht alles auf einmal passt
        if (tab == 0 && files.size() > shown) {
            int trackTop = rowY0;
            int trackH = shown * ROW_H - 1;
            int thumbH = Math.max(14, trackH * shown / files.size());
            int maxScroll = files.size() - shown;
            int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
            Ui.round(ctx, px1 - 9, trackTop, px1 - 5, trackTop + trackH, 0x30FFFFFF);
            Ui.round(ctx, px1 - 9, thumbY, px1 - 5, thumbY + thumbH, ACCENT);
        }
    }

    // Absichtlich ohne @Override: Stimmt die Signatur, werden die Texte und Symbole gezeichnet.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Kopfzeile
        ctx.drawTextWithShadow(tr, "♪", px0 + 10, py0 + 11, 0xFFC77DFF);
        ctx.drawTextWithShadow(tr, "Soundboard", px0 + 22, py0 + 7, 0xFFFFFFFF);
        if (tab == 0) {
            int count = Soundboard.list().size();
            ctx.drawTextWithShadow(tr, count + (count == 1 ? " Sound" : " Sounds") + " · Voice Chat", px0 + 22, py0 + 19, 0xFF7E8494);
        } else {
            ctx.drawTextWithShadow(tr, "Verändert dein Mikrofon", px0 + 22, py0 + 19, 0xFF7E8494);
        }

        // Untertabs
        String[] tabNames = {"Sounds", "Voice Changer"};
        for (int i = 0; i < 2; i++) {
            int tx = tabX(i), ty = tabY();
            boolean sel = tab == i;
            boolean hov = Ui.in(mouseX, mouseY, tx, ty, tx + TAB_W, ty + 18);
            Ui.round2(ctx, tx, ty, tx + TAB_W, ty + 18, sel ? 0xFF3A1A5C : (hov ? 0xFF232736 : 0xFF181B24));
            if (sel) ctx.fill(tx + 6, ty + 16, tx + TAB_W - 6, ty + 18, ACCENT);
            int tw = tr.getWidth(tabNames[i]);
            ctx.drawTextWithShadow(tr, tabNames[i], tx + (TAB_W - tw) / 2, ty + 5, sel ? 0xFFFFFFFF : 0xFF9AA0AE);
        }

        if (tab == 0) renderSounds(ctx, tr, mouseX, mouseY); else renderVoice(ctx, tr, mouseX, mouseY);
    }

    private void renderVoice(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int active = VoiceChanger.getPreset();
        // Anzeige oben rechts
        String st = active == 0 ? "Aus" : "Aktiv: " + VoiceChanger.PRESETS[active].name();
        int sw = tr.getWidth(st) + 22;
        int sx0 = px1 - 8 - sw, sy0 = py0 + 7;
        Ui.round2(ctx, sx0, sy0, sx0 + sw, sy0 + 20, 0xFF1F232E);
        Ui.round(ctx, sx0 + 8, sy0 + 7, sx0 + 14, sy0 + 13, active == 0 ? 0xFF5A5F6E : GREEN);
        ctx.drawTextWithShadow(tr, st, sx0 + 18, sy0 + 6, active == 0 ? 0xFF9AA0AE : 0xFFFFFFFF);

        for (int i = 0; i < VoiceChanger.PRESETS.length; i++) {
            VoiceChanger.Preset p = VoiceChanger.PRESETS[i];
            int cx = cardX(i), cy = cardY(i), cw = cardW();
            boolean sel = i == active;
            boolean hov = Ui.in(mouseX, mouseY, cx, cy, cx + cw, cy + CARD_H);
            int bg = sel ? 0xFF3A1A5C : (hov ? 0xFF232736 : 0xFF1A1D26);
            Ui.round2(ctx, cx, cy, cx + cw, cy + CARD_H, bg);
            if (sel) ctx.fill(cx, cy + 5, cx + 2, cy + CARD_H - 5, GREEN);
            ctx.drawTextWithShadow(tr, p.name(), cx + 9, cy + 6, sel ? 0xFFFFFFFF : 0xFFE4E7EE);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(p.desc(), cw - 16), cx + 9, cy + 18, sel ? 0xFFD9C2F5 : 0xFF7E8494);
        }

        // Fußzeile
        int fy = py1 - 28;
        int x1 = px0 + 8, x2 = px0 + 16 + half;
        boolean h1 = Ui.in(mouseX, mouseY, x1, fy, x1 + half, fy + 20);
        boolean h2 = Ui.in(mouseX, mouseY, x2, fy, x2 + half, fy + 20);
        boolean self = VoiceChanger.isSelfHear();
        Ui.round2(ctx, x1, fy, x1 + half, fy + 20, h1 ? 0xFF2A3040 : 0xFF1F232E);
        Ui.round(ctx, x1 + 8, fy + 7, x1 + 14, fy + 13, self ? GREEN : 0xFF5A5F6E);
        ctx.drawTextWithShadow(tr, tr.trimToWidth("Mich selbst hören", half - 24), x1 + 20, fy + 6, self ? 0xFFFFFFFF : 0xFF9AA0AE);
        Ui.round2(ctx, x2, fy, x2 + half, fy + 20, h2 ? 0xFF53222D : 0xFF3B1A22);
        String off = "Voice Changer aus";
        ctx.drawTextWithShadow(tr, tr.trimToWidth(off, half - 8), x2 + Math.max(4, (half - tr.getWidth(off)) / 2), fy + 6, 0xFFFF9A9A);
    }

    private void renderSounds(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        // "Selbst hören" als Schalter (liegt über dem echten Knopf, der das Klicken übernimmt)
        boolean self = Soundboard.isListenSelf();
        int sx0 = px1 - 8 - SELF_W, sy0 = py0 + 7;
        boolean hov = Ui.in(mouseX, mouseY, sx0, sy0, sx0 + SELF_W, sy0 + 20);
        Ui.round2(ctx, sx0, sy0, sx0 + SELF_W, sy0 + 20, hov ? 0xFF2A3040 : 0xFF1F232E);
        Ui.round(ctx, sx0 + 8, sy0 + 7, sx0 + 14, sy0 + 13, self ? GREEN : 0xFF5A5F6E);
        ctx.drawTextWithShadow(tr, "Selbst hören", sx0 + 20, sy0 + 6, self ? 0xFFFFFFFF : 0xFF9AA0AE);

        // Suchleiste
        if (searchText.isEmpty()) {
            ctx.drawTextWithShadow(tr, "Suchen …", px0 + 14, py0 + HEADER_H + TAB_H + 10, 0xFF6F7380);
        }
        if (files.isEmpty()) {
            int cy = rowY0 + 6;
            Ui.round2(ctx, px0 + 20, cy, px1 - 20, cy + 54, 0xFF171A22);
            if (!searchText.isEmpty()) {
                centered(ctx, tr, "Nichts gefunden", cy + 12, 0xFFFFFFFF);
                centered(ctx, tr, "für \"" + tr.trimToWidth(searchText, 160) + "\"", cy + 27, 0xFFFFAA00);
            } else {
                centered(ctx, tr, "Noch keine MP3-Dateien", cy + 10, 0xFFFFFFFF);
                centered(ctx, tr, "Auf 'Ordner öffnen' klicken und", cy + 24, 0xFF9AA0AE);
                centered(ctx, tr, "MP3s hineinlegen (oder ins Fenster ziehen).", cy + 35, 0xFF9AA0AE);
            }
        }

        // Zeilen: komplett neu gezeichnet, über den normalen Knöpfen und Reglern (die erledigen nur noch das Klicken)
        int nameX = px0 + 36;
        int nameMax = sliderX() - 36 - nameX;
        for (int v = 0; v < shown && scroll + v < files.size(); v++) {
            Path f = files.get(scroll + v);
            int ry = rowY0 + v * ROW_H;
            boolean playing = f.equals(Soundboard.nowPlaying);
            int rx0 = px0 + 6, rx1 = px1 - 12;
            boolean rowHov = Ui.in(mouseX, mouseY, rx0, ry, rx1, ry + ROW_H - 1);
            int bg = playing ? 0xFF15301F : (rowHov ? 0xFF232736 : (v % 2 == 0 ? 0xFF1A1D26 : 0xFF171A22));
            Ui.round2(ctx, rx0, ry, rx1, ry + ROW_H - 1, bg);
            if (playing) ctx.fill(rx0, ry + 4, rx0 + 2, ry + ROW_H - 5, GREEN);

            // Play-Knopf (rund)
            int bx = px0 + 9, by = ry + 2;
            boolean bHov = Ui.in(mouseX, mouseY, bx, by, bx + 20, by + 20);
            Ui.round2(ctx, bx, by, bx + 20, by + 20, playing ? GREEN : (bHov ? 0xFF4A5068 : 0xFF2C3040));
            if (playing) equalizer(ctx, bx + 5, by + 15);
            else playTriangle(ctx, bx, by, bHov ? 0xFFFFFFFF : 0xFFE4E7EE);

            ctx.drawTextWithShadow(tr, tr.trimToWidth(shortName(f), nameMax), nameX, ry + 8, playing ? 0xFFFFFFFF : 0xFFD6D9E0);

            // Lautstärke: Prozent und Balken
            double vol = Soundboard.getVolume(f);
            int pct = (int) Math.round(vol * 100);
            String ps = pct + " %";
            int vx = sliderX();
            ctx.drawTextWithShadow(tr, ps, vx - 6 - tr.getWidth(ps), ry + 8, vol > 1.0 ? 0xFFFFAA00 : 0xFF9AA0AE);
            Ui.round(ctx, vx, ry + 2, vx + SLIDER_W, ry + 22, bg);                       // alten Regler verdecken
            int tx0 = vx + 3, tx1 = vx + SLIDER_W - 3, ty = ry + 10;
            Ui.round(ctx, tx0, ty, tx1, ty + 4, 0xFF2C3040);                              // Schiene
            int fillX = tx0 + (int) Math.round((tx1 - tx0) * Math.max(0, Math.min(2.0, vol)) / 2.0);
            if (fillX > tx0) Ui.round(ctx, tx0, ty, fillX, ty + 4, vol > 1.0 ? 0xFFFFAA00 : ACCENT);
            ctx.fill((tx0 + tx1) / 2, ty - 1, (tx0 + tx1) / 2 + 1, ty + 5, 0x60FFFFFF);   // Marke bei 100 %
            boolean sHov = Ui.in(mouseX, mouseY, vx, ry + 2, vx + SLIDER_W, ry + 22);
            Ui.round(ctx, fillX - 2, ty - 3, fillX + 3, ty + 7, sHov ? 0xFFFFFFFF : 0xFFE4E7EE); // Griff
        }

        // Fußzeile: Stopp und Ordner öffnen
        int fy = py1 - 28;
        int x1 = px0 + 8, x2 = px0 + 16 + half;
        boolean h1 = Ui.in(mouseX, mouseY, x1, fy, x1 + half, fy + 20);
        boolean h2 = Ui.in(mouseX, mouseY, x2, fy, x2 + half, fy + 20);
        Ui.round2(ctx, x1, fy, x1 + half, fy + 20, h1 ? 0xFF53222D : 0xFF3B1A22);
        ctx.fill(x1 + half / 2 - 22, fy + 7, x1 + half / 2 - 16, fy + 13, 0xFFFF8A8A);
        ctx.drawTextWithShadow(tr, "Stopp", x1 + half / 2 - 10, fy + 6, 0xFFFF9A9A);
        Ui.round2(ctx, x2, fy, x2 + half, fy + 20, h2 ? 0xFF2A3040 : 0xFF1F232E);
        String ot = "Ordner öffnen";
        ctx.drawTextWithShadow(tr, ot, x2 + (half - tr.getWidth(ot)) / 2, fy + 6, 0xFFD6D9E0);
    }

    /** Kleine tanzende Balken bei dem Sound, der gerade läuft. */
    private static void equalizer(DrawContext ctx, int x, int bottom) {
        long t = System.currentTimeMillis() / 110;
        for (int i = 0; i < 3; i++) {
            int h = 3 + (int) ((t * (i + 3) + i * 5) % 8);
            ctx.fill(x + i * 4, bottom - h, x + i * 4 + 3, bottom, 0xFF0B1A10);
        }
    }

    /** Zeichnet ein Play-Dreieck (zeigt nach rechts) in einen 20x20-Knopf. */
    private static void playTriangle(DrawContext ctx, int bx, int by, int color) {
        int cy = by + 10;
        for (int c = 0; c < 6; c++) {
            int h = 11 - 2 * c;
            int top = cy - h / 2;
            ctx.fill(bx + 7 + c, top, bx + 8 + c, top + h, color);
        }
    }

    // Absichtlich ohne @Override (Signatur hat sich zwischen Versionen geändert): Mausrad scrollt die Liste.
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == 0 && files.size() > shown && verticalAmount != 0) {
            int next = scroll + (verticalAmount > 0 ? -1 : 1);
            next = Math.max(0, Math.min(next, files.size() - shown));
            if (next != scroll) MinecraftClient.getInstance().setScreen(new SoundboardScreen(next));
            return true;
        }
        return false;
    }

    // Absichtlich ohne @Override (Name hat sich zwischen Versionen geändert): MP3s per Drag-and-drop ins Fenster ziehen.
    public void filesDragged(List<Path> paths) {
        try {
            Path dir = Soundboard.folder();
            Files.createDirectories(dir);
            for (Path p : paths) {
                if (p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                    Files.copy(p, dir.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Drag-and-drop fehlgeschlagen", e);
        }
        MinecraftClient.getInstance().setScreen(new SoundboardScreen(scroll));
    }

    /** Behält nur Sounds, deren Name den Suchtext enthält (Groß-/Kleinschreibung egal, mehrere Wörter = alle müssen vorkommen). */
    private static List<Path> filter(List<Path> all, String q) {
        String t = q.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return all;
        String[] words = t.split("\\s+");
        List<Path> out = new java.util.ArrayList<>();
        for (Path f : all) {
            String n = shortName(f).toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String w : words) {
                if (!n.contains(w)) { ok = false; break; }
            }
            if (ok) out.add(f);
        }
        return out;
    }

    private static String shortName(Path f) {
        String n = f.getFileName().toString();
        if (n.toLowerCase(Locale.ROOT).endsWith(".mp3")) n = n.substring(0, n.length() - 4);
        return n;
    }

    private void centered(DrawContext ctx, TextRenderer tr, String s, int y, int color) {
        ctx.drawTextWithShadow(tr, s, (this.width - tr.getWidth(s)) / 2, y, color);
    }

    /** Lautstärkeregler für einen Sound: 0 bis 200 %, in 5-%-Schritten, wird gespeichert. */
    private static class VolumeSlider extends SliderWidget {
        private final Path file;

        VolumeSlider(int x, int y, int w, int h, Path file) {
            super(x, y, w, h, Text.literal(""), Soundboard.getVolume(file) / 2.0);
            this.file = file;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(Math.round(Soundboard.snap(this.value * 2.0) * 100) + " %"));
        }

        @Override
        protected void applyValue() {
            Soundboard.setVolume(file, Soundboard.snap(this.value * 2.0));
        }
    }
}
