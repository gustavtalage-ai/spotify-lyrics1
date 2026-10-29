package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Das Soundboard-Menü (Taste K oder /soundboard): links Play, rechts Lautstärke pro Sound. */
public class SoundboardScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int HEADER_H = 28;
    private static final int FOOTER_H = 34;
    private static final int SLIDER_W = 96;

    private static final int ACCENT = 0xFFAA00FF;
    private static final int PANEL = 0xFF12141A;
    private static final int HEADER = 0xFF1B1E27;
    private static final int GREEN = 0xFF4ADE80;

    private int scroll;                 // Index der ersten sichtbaren Zeile
    private List<Path> files = List.of();
    private int shown;                  // Anzahl sichtbarer Zeilen
    private int px0, px1, py0, py1, rowY0;

    public SoundboardScreen(int scroll) {
        super(Text.literal("Soundboard"));
        this.scroll = scroll;
    }

    @Override
    protected void init() {
        files = Soundboard.list();
        int maxRows = Math.max(3, (this.height - 24 - HEADER_H - FOOTER_H - 8) / ROW_H);
        shown = Math.max(2, Math.min(files.size(), maxRows));
        int maxScroll = Math.max(0, files.size() - shown);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int panelW = Math.min(330, this.width - 20);
        int panelH = HEADER_H + shown * ROW_H + FOOTER_H + 4;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = (this.height - panelH) / 2;
        py1 = py0 + panelH;
        rowY0 = py0 + HEADER_H + 2;

        // Zeilen: Play-Knopf links, Regler rechts
        for (int v = 0; v < shown && scroll + v < files.size(); v++) {
            Path f = files.get(scroll + v);
            int ry = rowY0 + v * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> Soundboard.play(f))
                .dimensions(px0 + 6, ry + 1, 20, 20).build());
            addDrawableChild(new VolumeSlider(px1 - 10 - SLIDER_W, ry + 1, SLIDER_W, 20, f));
        }

        // Kopfzeile: Selbst hören
        boolean self = Soundboard.isListenSelf();
        addDrawableChild(ButtonWidget.builder(Text.literal("Selbst hören: " + (self ? "Ein" : "Aus")), b -> {
            Soundboard.setListenSelf(!Soundboard.isListenSelf());
            MinecraftClient.getInstance().setScreen(new SoundboardScreen(scroll));
        }).dimensions(px1 - 6 - 110, py0 + 4, 110, 20).build());

        // Fußzeile: Stopp, Ordner öffnen
        int half = (px1 - px0 - 18) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Stopp"), b -> Soundboard.stop())
            .dimensions(px0 + 6, py1 - 28, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ordner öffnen"), b -> Soundboard.openFolder())
            .dimensions(px0 + 12 + half, py1 - 28, half, 20).build());
    }

    // Absichtlich ohne @Override: Passt die Signatur, ersetzt das den Standard-Hintergrund durch das dunkle Design.
    // Passt sie nicht, bleibt der normale Hintergrund und alles funktioniert trotzdem.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0xC0080A10);
        Ui.round(ctx, px0 - 4, py0 - 4, px1 + 4, py1 + 4, 0x30AA00FF);   // Leuchten
        Ui.round(ctx, px0 - 1, py0 - 1, px1 + 1, py1 + 1, ACCENT);       // Rand
        Ui.round(ctx, px0, py0, px1, py1, PANEL);
        Ui.round(ctx, px0, py0, px1, py0 + HEADER_H, HEADER);
        ctx.fill(px0, py0 + HEADER_H - 3, px1, py0 + HEADER_H, HEADER);
        ctx.fill(px0 + 1, py0 + HEADER_H, px1 - 1, py0 + HEADER_H + 1, ACCENT);
        ctx.fill(px0 + 1, py0 + HEADER_H + 1, px1 - 1, py0 + HEADER_H + 2, 0x40AA00FF);
        ctx.fill(px0 + 1, py1 - FOOTER_H, px1 - 1, py1 - FOOTER_H + 1, 0x30FFFFFF);
        ctx.fill(px0 + 1, py1 - FOOTER_H + 1, px1 - 1, py1 - 1, 0xFF0E1015);

        for (int v = 0; v < shown && scroll + v < files.size(); v++) {
            int ry = rowY0 + v * ROW_H;
            boolean playing = files.get(scroll + v).equals(Soundboard.nowPlaying);
            int bg = playing ? 0x6022C55E : (v % 2 == 0 ? 0x30FFFFFF : 0x18FFFFFF);
            Ui.round(ctx, px0 + 3, ry, px1 - 9, ry + ROW_H - 1, bg);
            if (playing) ctx.fill(px0 + 3, ry + 2, px0 + 5, ry + ROW_H - 3, GREEN);
        }

        // Scrollbalken, nur wenn nicht alles auf einmal passt
        if (files.size() > shown) {
            int trackTop = rowY0;
            int trackH = shown * ROW_H - 1;
            int thumbH = Math.max(12, trackH * shown / files.size());
            int maxScroll = files.size() - shown;
            int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
            Ui.round(ctx, px1 - 7, trackTop, px1 - 3, trackTop + trackH, 0x40FFFFFF);
            Ui.round(ctx, px1 - 7, thumbY, px1 - 3, thumbY + thumbH, ACCENT);
        }
    }

    // Absichtlich ohne @Override: Stimmt die Signatur, werden die Texte und Symbole gezeichnet.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        ctx.drawTextWithShadow(tr, "Soundboard", px0 + 10, py0 + 6, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, "läuft über Simple Voice Chat", px0 + 10, py0 + 17, 0xFF8A8F9C);

        if (files.isEmpty()) {
            centered(ctx, tr, "Keine MP3-Dateien im Ordner.", rowY0 + 6, 0xFFFFAA00);
            centered(ctx, tr, "Auf 'Ordner öffnen' klicken und MP3s hineinlegen.", rowY0 + 20, 0xFFB0B0B0);
            return;
        }

        int nameX = px0 + 32;
        int nameMax = (px1 - 10 - SLIDER_W - 24) - nameX;
        for (int v = 0; v < shown && scroll + v < files.size(); v++) {
            Path f = files.get(scroll + v);
            int ry = rowY0 + v * ROW_H;
            boolean playing = f.equals(Soundboard.nowPlaying);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(shortName(f), nameMax), nameX, ry + 7, playing ? 0xFFFFFFFF : 0xFFD6D9E0);
            playTriangle(ctx, px0 + 6, ry + 1, playing ? 0xFFFFFFFF : GREEN);
            if (playing) equalizer(ctx, px1 - 10 - SLIDER_W - 18, ry + 15);
        }
    }

    /** Kleine tanzende Balken bei dem Sound, der gerade läuft. */
    private static void equalizer(DrawContext ctx, int x, int bottom) {
        long t = System.currentTimeMillis() / 110;
        for (int i = 0; i < 3; i++) {
            int h = 3 + (int) ((t * (i + 3) + i * 5) % 8);
            ctx.fill(x + i * 4, bottom - h, x + i * 4 + 3, bottom, GREEN);
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
        if (files.size() > shown && verticalAmount != 0) {
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
