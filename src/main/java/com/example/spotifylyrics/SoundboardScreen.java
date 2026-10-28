package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/** Das Soundboard-Menue (Taste K oder /soundboard). */
public class SoundboardScreen extends Screen {
    private int page;
    private int pages = 1;

    public SoundboardScreen(int page) {
        super(Text.literal("Soundboard"));
        this.page = page;
    }

    @Override
    protected void init() {
        List<Path> files = Soundboard.list();
        int listTop = 36;
        int listBottom = this.height - 100;
        int perPage = Math.max(1, (listBottom - listTop) / 24);
        pages = Math.max(1, (files.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));

        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = listTop;
        for (int i = page * perPage; i < Math.min(files.size(), (page + 1) * perPage); i++) {
            Path f = files.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(shortName(f)), b -> Soundboard.play(f))
                .dimensions(x, y, w, 20).build());
            y += 24;
        }

        int half = 98;
        int right = x + w - half;
        // Lautstaerke
        addDrawableChild(ButtonWidget.builder(Text.literal("Leiser"), b ->
            Soundboard.volume = Math.max(0.1f, Math.round((Soundboard.volume - 0.1f) * 10f) / 10f))
            .dimensions(x, this.height - 82, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Lauter"), b ->
            Soundboard.volume = Math.min(2.0f, Math.round((Soundboard.volume + 0.1f) * 10f) / 10f))
            .dimensions(right, this.height - 82, half, 20).build());
        // Seiten
        if (pages > 1) {
            final int prev = (page + pages - 1) % pages;
            final int next = (page + 1) % pages;
            addDrawableChild(ButtonWidget.builder(Text.literal("< Zurueck"), b ->
                MinecraftClient.getInstance().setScreen(new SoundboardScreen(prev)))
                .dimensions(x, this.height - 58, half, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Weiter >"), b ->
                MinecraftClient.getInstance().setScreen(new SoundboardScreen(next)))
                .dimensions(right, this.height - 58, half, 20).build());
        }
        // Stopp / Ordner
        addDrawableChild(ButtonWidget.builder(Text.literal("Stopp"), b -> Soundboard.stop())
            .dimensions(x, this.height - 34, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ordner oeffnen"), b -> Soundboard.openFolder())
            .dimensions(right, this.height - 34, half, 20).build());
    }

    // Absichtlich ohne @Override: Stimmt die Signatur, wird sie genutzt; sonst fehlen nur die Texte, nicht die Buttons.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        centered(ctx, tr, "Soundboard (laeuft ueber Simple Voice Chat)", 12, 0xFFFFFFFF);
        if (Soundboard.list().isEmpty()) {
            centered(ctx, tr, "Keine MP3-Dateien im Ordner.", 60, 0xFFFFAA00);
            centered(ctx, tr, "Auf 'Ordner oeffnen' klicken und MP3s hineinlegen.", 74, 0xFFB0B0B0);
        }
        String info = "Lautstaerke " + Math.round(Soundboard.volume * 100) + " %   -   Seite " + (page + 1) + "/" + pages
            + (Soundboard.isPlaying() ? "   -   spielt" : "");
        centered(ctx, tr, info, this.height - 96, 0xFFB0B0B0);
    }

    // Absichtlich ohne @Override (Name hat sich zwischen Versionen geaendert): MP3s per Drag-and-drop ins Fenster ziehen.
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
        MinecraftClient.getInstance().setScreen(new SoundboardScreen(page));
    }

    private static String shortName(Path f) {
        String n = f.getFileName().toString();
        if (n.toLowerCase(Locale.ROOT).endsWith(".mp3")) n = n.substring(0, n.length() - 4);
        return n.length() > 30 ? n.substring(0, 29) + "..." : n;
    }

    private void centered(DrawContext ctx, TextRenderer tr, String s, int y, int color) {
        ctx.drawTextWithShadow(tr, s, (this.width - tr.getWidth(s)) / 2, y, color);
    }
}
