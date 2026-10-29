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
    /** Gewählter Untertab: 0 = Effekte (unter 30 s), 1 = Musik (ab 30 s), 2 = Voice Changer, 3 = Spotify. */
    private static int tab = 0;
    private static final int DEV_SLOTS = 4;
    /** Spotify-Tab: Suchtext, Zeitpunkt der letzten Änderung und zuletzt gesuchter Text. */
    private static String songText = "";
    private static long songChangedAt = 0;
    private static String searchedText = "";
    /** Spulbalken und Lautstärke: erst kurz nach dem Loslassen/Ziehen an Spotify schicken. */
    private long seekAt = 0, seekHold = 0, volAt = 0, volHold = 0;
    private long seekMs = -1;
    private int volPending = -1;
    private double seekVal = 0, volVal = 0;
    private int tabW = 83;
    private boolean playerConnected = true;
    private int catTotal = 0;

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
        List<Path> cat = categoryOf(Soundboard.list());
        catTotal = cat.size();
        files = filter(cat, searchText);
        int panelW = Math.min(360, this.width - 20);
        int panelH;
        if (tab <= 1) {
            int maxRows = Math.max(3, (this.height - 24 - HEADER_H - TAB_H - SEARCH_H - FOOTER_H - 8) / ROW_H);
            shown = Math.max(2, Math.min(files.size(), maxRows));
            int maxScroll = Math.max(0, files.size() - shown);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            panelH = HEADER_H + TAB_H + SEARCH_H + shown * ROW_H + FOOTER_H + 4;
        } else if (tab == 2) {
            shown = 0;
            panelH = HEADER_H + TAB_H + 8 + CARD_ROWS * (CARD_H + 4) + FOOTER_H + 2;
        } else if (tab == 3) {
            shown = 0;
            panelH = HEADER_H + TAB_H + 8 + 110 + DEV_SLOTS * 22 + FOOTER_H + 2;
        } else {
            shown = 0;
            panelH = HEADER_H + TAB_H + 8 + 180 + FOOTER_H + 2;
        }
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = (this.height - panelH) / 2;
        py1 = py0 + panelH;
        rowY0 = py0 + HEADER_H + TAB_H + SEARCH_H + 2;
        half = (px1 - px0 - 24) / 2;
        tabW = Math.max(40, (px1 - px0 - 16 - 4 * 4) / 5);

        // Untertabs (die Knöpfe erledigen das Klicken, gezeichnet werden sie in render())
        for (int i = 0; i < 5; i++) {
            final int target = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                if (tab != target) {
                    tab = target;
                    MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
                }
            }).dimensions(tabX(i), tabY(), tabW, 18).build());
        }

        if (tab <= 1) initSounds(); else if (tab == 2) initVoice(); else if (tab == 3) initPlayer(); else initTools();
    }

    private int tabX(int i) {
        return px0 + 8 + i * (tabW + 4);
    }

    private int tabY() {
        return py0 + HEADER_H + 4;
    }

    private void initSounds() {
        // Zeilen: Play-Knopf links, Regler rechts
        for (int v = 0; v < shown && scroll + v < files.size(); v++) {
            Path f = files.get(scroll + v);
            int ry = rowY0 + v * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                if (f.equals(Soundboard.nowPlaying)) Soundboard.stop();
                else Soundboard.play(f);
            })
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

    private void initPlayer() {
        int w = px1 - px0 - 16, y0 = spotY();
        int bw = (w - 8) / 3;
        playerConnected = !Config.INSTANCE.refreshToken.isBlank();
        if (!playerConnected) {
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.login())
                .dimensions(px0 + 8, y0 + 38, w, 22).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.previous())
                .dimensions(px0 + 8, y0 + 38, bw, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.toggle())
                .dimensions(px0 + 12 + bw, y0 + 38, bw, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.next())
                .dimensions(px0 + 16 + 2 * bw, y0 + 38, bw, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.login())
                .dimensions(px0 + 8 + w - 76, y0 + 3, 72, 13).build());
            // Spulbalken (im Statusfeld) und Lautstärke
            addDrawableChild(new SeekSlider(px0 + 8 + 10, y0 + 21, w - 20, 12));
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.setVolume(shownVolume() - 10))
                .dimensions(px0 + 8, y0 + 64, 22, 16).build());
            addDrawableChild(new VolSlider(px0 + 8 + 28, y0 + 64, w - 28 - 56, 16));
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> SpotifyControl.setVolume(shownVolume() + 10))
                .dimensions(px0 + 8 + w - 22, y0 + 64, 22, 16).build());
            SpotifyControl.loadVolume();
        }

        TextFieldWidget f = new TextFieldWidget(this.textRenderer, px0 + 8, y0 + 88, w, 18, Text.literal("Song suchen"));
        f.setMaxLength(60);
        f.setText(songText);
        f.setChangedListener(t -> {
            songText = t;
            songChangedAt = System.currentTimeMillis();
        });
        addDrawableChild(f);
        setFocused(f);
        f.setFocused(true);

        for (int i = 0; i < DEV_SLOTS; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                List<SpotifyApi.Track> r = SpotifyControl.results;
                if (idx < r.size()) SpotifyControl.play(r.get(idx));
            }).dimensions(px0 + 8, y0 + 110 + i * 22, w, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            Config.INSTANCE.lightshow = !Config.INSTANCE.lightshow;
            Config.INSTANCE.save();
        }).dimensions(px0 + 8, py1 - 28, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            Config.INSTANCE.lightMode = (Config.INSTANCE.lightMode + 1) % Lightshow.MODES.length;
            Config.INSTANCE.save();
        }).dimensions(px0 + 16 + half, py1 - 28, half, 20).build());
    }

    private void renderPlayer(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        int x0 = px0 + 8, w = px1 - px0 - 16, y0 = spotY();

        // Suche mit kleiner Pause nach dem Tippen starten
        if (!songText.equals(searchedText) && System.currentTimeMillis() - songChangedAt > 450) {
            searchedText = songText;
            SpotifyControl.search(songText.trim());
        }

        // Nach dem Login Menü neu aufbauen (Verbinden-Knopf -> Steuerung)
        if (playerConnected != !Config.INSTANCE.refreshToken.isBlank()) {
            MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (seekMs >= 0 && nowMs - seekAt > 250) {
            SpotifyControl.seek(seekMs);
            seekMs = -1;
        }
        if (volPending >= 0 && nowMs - volAt > 250) {
            SpotifyControl.setVolume(volPending);
            volPending = -1;
        }
        String msg = SpotifyControl.message;
        // Aktueller Song mit Fortschrittsbalken
        Ui.round2(ctx, x0, y0, x0 + w, y0 + 34, 0xFF1A1D26);
        SpotifyApi.State st = SpotifyApi.state;
        String playingId = "";
        if (st != null) {
            playingId = st.track().id();
            ctx.drawTextWithShadow(tr, "♪", x0 + 8, y0 + 11, 0xFF1ED760);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(st.track().title(), w - 112), x0 + 22, y0 + 5, 0xFFFFFFFF);
            long dur = Math.max(1, st.track().durationMs());
            double frac = nowMs < seekHold ? seekVal : Math.min(1.0, st.position() / (double) dur);
            long posMs = (long) (frac * dur);
            String time = mmss(posMs) + " / " + mmss(dur);
            int timeW = tr.getWidth(time);
            if (msg.isEmpty()) ctx.drawTextWithShadow(tr, tr.trimToWidth(st.track().artist(), w - 40 - timeW), x0 + 22, y0 + 16, 0xFF9AA0AE);
            else ctx.drawTextWithShadow(tr, tr.trimToWidth(msg, w - 34), x0 + 22, y0 + 16, 0xFFFFAA00);
            if (msg.isEmpty()) ctx.drawTextWithShadow(tr, time, x0 + w - 10 - timeW, y0 + 16, 0xFF7E8494);
            boolean sh = Ui.in(mouseX, mouseY, x0 + 10, y0 + 21, x0 + w - 10, y0 + 33);
            int bar = (int) ((w - 20) * Math.max(0.0, Math.min(1.0, frac)));
            int by0 = sh ? y0 + 26 : y0 + 27, by1 = sh ? y0 + 31 : y0 + 30;
            Ui.round(ctx, x0 + 10, by0, x0 + w - 10, by1, 0xFF2E3342);
            if (bar > 0) Ui.round(ctx, x0 + 10, by0, x0 + 10 + bar, by1, 0xFF1ED760);
            Ui.round(ctx, x0 + 10 + bar - 2, y0 + 25, x0 + 10 + bar + 3, y0 + 32, sh ? 0xFFFFFFFF : 0xFFE4E7EE);
        } else {
            ctx.drawTextWithShadow(tr, "Es läuft nichts", x0 + 10, y0 + 7, 0xFFFFFFFF);
            if (msg.isEmpty()) ctx.drawTextWithShadow(tr, tr.trimToWidth("Spotify ist noch nicht verbunden.", w - 20), x0 + 10, y0 + 19, 0xFF9AA0AE);
            else ctx.drawTextWithShadow(tr, tr.trimToWidth(msg, w - 20), x0 + 10, y0 + 19, 0xFFFFAA00);
        }

        // Neu verbinden (klein, oben rechts im Statusfeld)
        if (playerConnected) {
            int rx = x0 + w - 76;
            boolean hr = Ui.in(mouseX, mouseY, rx, y0 + 3, rx + 72, y0 + 16);
            Ui.round2(ctx, rx, y0 + 3, rx + 72, y0 + 16, hr ? 0xFF2A3040 : 0xFF232736);
            String rt = "Neu verbinden";
            ctx.drawTextWithShadow(tr, rt, rx + (72 - tr.getWidth(rt)) / 2, y0 + 6, hr ? 0xFF1ED760 : 0xFF9AA0AE);
        }

        // Steuerung
        int bw = (w - 8) / 3;
        String[] labels = {"<< Zurück", (st != null && st.playing()) ? "Pause" : "Abspielen", "Weiter >>"};
        if (!playerConnected) {
            boolean hc = Ui.in(mouseX, mouseY, x0, y0 + 38, x0 + w, y0 + 60);
            Ui.round2(ctx, x0, y0 + 38, x0 + w, y0 + 60, hc ? 0xFF1FB455 : 0xFF1ED760);
            String lt = "Mit Spotify verbinden";
            ctx.drawTextWithShadow(tr, lt, x0 + (w - tr.getWidth(lt)) / 2, y0 + 45, 0xFF06140B);
        }
        for (int i = 0; i < 3 && playerConnected; i++) {
            int bx = x0 + i * (bw + 4);
            boolean h = Ui.in(mouseX, mouseY, bx, y0 + 38, bx + bw, y0 + 60);
            Ui.round2(ctx, bx, y0 + 38, bx + bw, y0 + 60, i == 1 ? (h ? 0xFF1FB455 : 0xFF1ED760) : (h ? 0xFF2A3040 : 0xFF1F232E));
            ctx.drawTextWithShadow(tr, labels[i], bx + (bw - tr.getWidth(labels[i])) / 2, y0 + 45, i == 1 ? 0xFF06140B : 0xFFD6D9E0);
        }

        // Lautstärke: [-] Balken [+]
        if (playerConnected) {
            int vy = y0 + 64;
            int vol = shownVolume();
            ctx.fill(x0, vy, x0 + w, vy + 16, PANEL);   // Standard-Regler darunter verdecken
            boolean hm = Ui.in(mouseX, mouseY, x0, vy, x0 + 22, vy + 16);
            boolean hp = Ui.in(mouseX, mouseY, x0 + w - 22, vy, x0 + w, vy + 16);
            Ui.round2(ctx, x0, vy, x0 + 22, vy + 16, hm ? 0xFF2A3040 : 0xFF1F232E);
            Ui.round2(ctx, x0 + w - 22, vy, x0 + w, vy + 16, hp ? 0xFF2A3040 : 0xFF1F232E);
            ctx.fill(x0 + 7, vy + 7, x0 + 15, vy + 9, hm ? 0xFFFFFFFF : 0xFFD6D9E0);
            ctx.fill(x0 + w - 15, vy + 7, x0 + w - 7, vy + 9, hp ? 0xFFFFFFFF : 0xFFD6D9E0);
            ctx.fill(x0 + w - 12, vy + 4, x0 + w - 10, vy + 12, hp ? 0xFFFFFFFF : 0xFFD6D9E0);
            int tx0 = x0 + 28 + 3, tx1 = x0 + w - 56 - 3;
            int ty = vy + 6;
            Ui.round(ctx, tx0, ty, tx1, ty + 4, 0xFF2C3040);
            int fx = tx0 + (int) Math.round((tx1 - tx0) * vol / 100.0);
            if (fx > tx0) Ui.round(ctx, tx0, ty, fx, ty + 4, 0xFF1ED760);
            boolean vh = Ui.in(mouseX, mouseY, x0 + 28, vy, x0 + w - 56, vy + 16);
            Ui.round(ctx, fx - 2, ty - 3, fx + 3, ty + 7, vh ? 0xFFFFFFFF : 0xFFE4E7EE);
            String vt = vol + " %";
            ctx.drawTextWithShadow(tr, vt, x0 + w - 26 - tr.getWidth(vt), vy + 4, 0xFF9AA0AE);
        }

        // Ergebnisse
        List<SpotifyApi.Track> res = SpotifyControl.results;
        if (res.isEmpty()) {
            String hint = songText.isBlank() ? "Tippe oben einen Song oder Künstler" : (SpotifyControl.busy ? "Suche..." : "");
            if (!hint.isEmpty()) ctx.drawTextWithShadow(tr, hint, x0 + 4, y0 + 116, 0xFF7E8494);
        }
        for (int i = 0; i < DEV_SLOTS && i < res.size(); i++) {
            SpotifyApi.Track t = res.get(i);
            int ry = y0 + 110 + i * 22;
            boolean h = Ui.in(mouseX, mouseY, x0, ry, x0 + w, ry + 20);
            boolean cur = t.id().equals(playingId);
            Ui.round2(ctx, x0, ry, x0 + w, ry + 20, cur ? 0xFF15301F : (h ? 0xFF232736 : 0xFF1A1D26));
            if (cur) ctx.fill(x0, ry + 4, x0 + 2, ry + 16, GREEN);
            ctx.drawTextWithShadow(tr, ">", x0 + 7, ry + 6, h ? 0xFF1ED760 : 0xFF5A5F6E);
            ctx.drawTextWithShadow(tr, tr.trimToWidth(t.title() + " · " + t.artist(), w - 28), x0 + 18, ry + 6, cur ? 0xFFFFFFFF : 0xFFE4E7EE);
        }

        // Fußzeile: Lichtshow
        boolean on = Config.INSTANCE.lightshow;
        int fy = py1 - 28;
        int x1 = px0 + 8, x2 = px0 + 16 + half;
        boolean h1 = Ui.in(mouseX, mouseY, x1, fy, x1 + half, fy + 20);
        boolean h2 = Ui.in(mouseX, mouseY, x2, fy, x2 + half, fy + 20);
        Ui.round2(ctx, x1, fy, x1 + half, fy + 20, h1 ? 0xFF2A3040 : 0xFF1F232E);
        Ui.round(ctx, x1 + 8, fy + 7, x1 + 14, fy + 13, on ? GREEN : 0xFF5A5F6E);
        ctx.drawTextWithShadow(tr, tr.trimToWidth("Lichtshow", half - 24), x1 + 20, fy + 6, on ? 0xFFFFFFFF : 0xFF9AA0AE);
        Ui.round2(ctx, x2, fy, x2 + half, fy + 20, h2 ? 0xFF2A3040 : 0xFF1F232E);
        String mode = "Farbe: " + Lightshow.MODES[Config.INSTANCE.lightMode % Lightshow.MODES.length];
        ctx.drawTextWithShadow(tr, tr.trimToWidth(mode, half - 8), x2 + Math.max(4, (half - tr.getWidth(mode)) / 2), fy + 6, 0xFFD6D9E0);
    }

    private void reopenTools() {
        MinecraftClient.getInstance().setScreen(new SoundboardScreen(0));
    }

    // ---------- Tools-Tab: Zeilen-Positionen (gleich für Knöpfe und Zeichnung) ----------
    private int tlX() {
        return px0 + 8;
    }

    private int tlW() {
        return px1 - px0 - 16;
    }

    private int tlHalf() {
        return (tlW() - 4) / 2;
    }

    private int tlRight() {
        return tlX() + tlHalf() + 4;
    }

    private int tlY(int rel) {
        return spotY() + rel;
    }

    private static final int R1 = 13, R2 = 35, R3 = 57, R4 = 97, R5 = 137, R6 = 159;

    private void toolBtn(int x, int y, int w, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, 20).build());
    }

    private void initTools() {
        Config cfg = Config.INSTANCE;
        int xl = tlX(), xr = tlRight(), hw = tlHalf(), w = tlW();
        toolBtn(xl, tlY(R1), hw, () -> {
            cfg.placeHelper = !cfg.placeHelper;
            cfg.save();
            reopenTools();
        });
        toolBtn(xr, tlY(R1), hw, () -> {
            cfg.placeHelper = true;
            cfg.save();
            PlaceHelper.pendingStart = true;
            MinecraftClient.getInstance().setScreen(null);
        });
        toolBtn(xl, tlY(R2), 24, () -> {
            cfg.placeSpacing = Math.max(1, cfg.placeSpacing - 1);
            cfg.save();
            reopenTools();
        });
        toolBtn(xl + w - 24, tlY(R2), 24, () -> {
            cfg.placeSpacing = Math.min(32, cfg.placeSpacing + 1);
            cfg.save();
            reopenTools();
        });
        toolBtn(xl, tlY(R3), hw, () -> {
            cfg.placeDir = cfg.placeDir + 1 >= PlaceHelper.DIR_NAMES.length ? -1 : cfg.placeDir + 1;
            cfg.save();
            reopenTools();
        });
        toolBtn(xr, tlY(R3), hw, () -> {
            PlaceHelper.clear();
            reopenTools();
        });
        toolBtn(xl, tlY(R4), hw, () -> MinecraftClient.getInstance().setScreen(new FilterScreen()));
        toolBtn(xr, tlY(R4), hw, () -> {
            cfg.filterOn = !cfg.filterOn;
            cfg.save();
            reopenTools();
        });
        toolBtn(xl, tlY(R5), hw, () -> {
            cfg.lightshow = !cfg.lightshow;
            cfg.save();
            reopenTools();
        });
        toolBtn(xr, tlY(R5), hw, () -> {
            cfg.lightMode = (cfg.lightMode + 1) % Lightshow.MODES.length;
            cfg.save();
            reopenTools();
        });
        toolBtn(xl, tlY(R6), hw, () -> {
            Lightshow.testUntilMs = System.currentTimeMillis() + 20000;
            MinecraftClient.getInstance().setScreen(null);
        });
    }

    private void renderTools(DrawContext ctx, TextRenderer tr, int mx, int my) {
        Config cfg = Config.INSTANCE;
        int xl = tlX(), xr = tlRight(), hw = tlHalf(), w = tlW();
        int sw = px1 - px0 - 20;

        String info = tr.trimToWidth(PlaceHelper.describe(), sw - 90);
        int iw = tr.getWidth(info);
        Ui.section(ctx, tr, xl + 2, tlY(0), "Platzhilfe", sw - iw - 8);
        ctx.drawTextWithShadow(tr, info, px1 - 12 - iw, tlY(0), 0xFF7E8494);
        Ui.toggle(ctx, tr, xl, tlY(R1), xl + hw, tlY(R1) + 20, "Platzhilfe", cfg.placeHelper, Ui.in(mx, my, xl, tlY(R1), xl + hw, tlY(R1) + 20));
        Ui.btn(ctx, tr, xr, tlY(R1), xr + hw, tlY(R1) + 20, "Startpunkt setzen", Ui.in(mx, my, xr, tlY(R1), xr + hw, tlY(R1) + 20), 0xFF3A1A5C, 0xFF4B2373, 0xFFFFFFFF);

        boolean hm = Ui.in(mx, my, xl, tlY(R2), xl + 24, tlY(R2) + 20);
        boolean hp = Ui.in(mx, my, xl + w - 24, tlY(R2), xl + w, tlY(R2) + 20);
        Ui.btn(ctx, tr, xl, tlY(R2), xl + 24, tlY(R2) + 20, "-", hm, 0xFF1F232E, 0xFF2A3040, 0xFFFFFFFF);
        Ui.btn(ctx, tr, xl + w - 24, tlY(R2), xl + w, tlY(R2) + 20, "+", hp, 0xFF1F232E, 0xFF2A3040, 0xFFFFFFFF);
        Ui.btn(ctx, tr, xl + 28, tlY(R2), xl + w - 28, tlY(R2) + 20, "Abstand: jeder " + cfg.placeSpacing + ". Block", false, 0xFF171A22, 0xFF171A22, 0xFFD6D9E0);

        String dn = cfg.placeDir < 0 || cfg.placeDir >= PlaceHelper.DIR_NAMES.length ? "Blickrichtung" : PlaceHelper.DIR_NAMES[cfg.placeDir];
        Ui.btn(ctx, tr, xl, tlY(R3), xl + hw, tlY(R3) + 20, "Richtung: " + dn, Ui.in(mx, my, xl, tlY(R3), xl + hw, tlY(R3) + 20), 0xFF1F232E, 0xFF2A3040, 0xFFD6D9E0);
        Ui.btn(ctx, tr, xr, tlY(R3), xr + hw, tlY(R3) + 20, "Markierung löschen", Ui.in(mx, my, xr, tlY(R3), xr + hw, tlY(R3) + 20), 0xFF3B1A22, 0xFF53222D, 0xFFFF9A9A);

        Ui.section(ctx, tr, xl + 2, tlY(84), "Inventar", sw);
        Ui.btn(ctx, tr, xl, tlY(R4), xl + hw, tlY(R4) + 20, "Filter einrichten (" + cfg.filterRules.size() + ")", Ui.in(mx, my, xl, tlY(R4), xl + hw, tlY(R4) + 20), 0xFF3A1A5C, 0xFF4B2373, 0xFFFFFFFF);
        Ui.toggle(ctx, tr, xr, tlY(R4), xr + hw, tlY(R4) + 20, "Item-Filter", cfg.filterOn, Ui.in(mx, my, xr, tlY(R4), xr + hw, tlY(R4) + 20));

        Ui.section(ctx, tr, xl + 2, tlY(124), "Lichtshow", sw);
        Ui.toggle(ctx, tr, xl, tlY(R5), xl + hw, tlY(R5) + 20, "Lichtshow", cfg.lightshow, Ui.in(mx, my, xl, tlY(R5), xl + hw, tlY(R5) + 20));
        Ui.btn(ctx, tr, xr, tlY(R5), xr + hw, tlY(R5) + 20, "Farbe: " + Lightshow.MODES[cfg.lightMode % Lightshow.MODES.length], Ui.in(mx, my, xr, tlY(R5), xr + hw, tlY(R5) + 20), 0xFF1F232E, 0xFF2A3040, 0xFFD6D9E0);
        Ui.btn(ctx, tr, xl, tlY(R6), xl + hw, tlY(R6) + 20, "Testen (20 s)", Ui.in(mx, my, xl, tlY(R6), xl + hw, tlY(R6) + 20), 0xFF3A1A5C, 0xFF4B2373, 0xFFFFFFFF);
        String ls = Lightshow.status();
        ctx.drawTextWithShadow(tr, tr.trimToWidth(ls, hw - 4), xr + 4, tlY(R6) + 6, 0xFF7E8494);

        ctx.drawTextWithShadow(tr, "Zielblock anschauen, dann Startpunkt setzen.", px0 + 10, py1 - FOOTER_H + 8, 0xFF9AA0AE);
        ctx.drawTextWithShadow(tr, "Grün = Start, blau = jeder n-te Block.", px0 + 10, py1 - FOOTER_H + 20, 0xFF9AA0AE);
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private int shownVolume() {
        if (System.currentTimeMillis() < volHold) return (int) Math.round(volVal * 100);
        return SpotifyControl.volume >= 0 ? SpotifyControl.volume : Config.INSTANCE.playerVolume;
    }

    /** Unsichtbarer Regler über dem Fortschrittsbalken: Klicken oder Ziehen spult im Lied. */
    private class SeekSlider extends SliderWidget {
        SeekSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.literal(""), 0.0);
        }

        @Override
        protected void updateMessage() {
        }

        @Override
        protected void applyValue() {
            SpotifyApi.State s = SpotifyApi.state;
            if (s == null) return;
            long now = System.currentTimeMillis();
            seekVal = this.value;
            seekMs = (long) (this.value * s.track().durationMs());
            seekAt = now;
            seekHold = now + 1500;
        }
    }

    /** Unsichtbarer Regler für die Spotify-Lautstärke. */
    private class VolSlider extends SliderWidget {
        VolSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.literal(""), shownVolume() / 100.0);
        }

        @Override
        protected void updateMessage() {
        }

        @Override
        protected void applyValue() {
            long now = System.currentTimeMillis();
            volVal = this.value;
            volPending = (int) Math.round(this.value * 100);
            volAt = now;
            volHold = now + 1500;
        }
    }

    private int spotY() {
        return py0 + HEADER_H + TAB_H + 8;
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
        if (tab <= 1 && files.size() > shown) {
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
        if (tab <= 1) {
            String what = tab == 0 ? " Effekte · unter 30 Sek." : " Lieder · ab 30 Sek.";
            ctx.drawTextWithShadow(tr, catTotal + what, px0 + 22, py0 + 19, 0xFF7E8494);
        } else if (tab == 2) {
            ctx.drawTextWithShadow(tr, "Verändert dein Mikrofon", px0 + 22, py0 + 19, 0xFF7E8494);
        } else if (tab == 3) {
            ctx.drawTextWithShadow(tr, "Songs suchen und abspielen", px0 + 22, py0 + 19, 0xFF7E8494);
        } else {
            ctx.drawTextWithShadow(tr, "Platzhilfe und Item-Filter", px0 + 22, py0 + 19, 0xFF7E8494);
        }

        // Untertabs
        String[] tabNames = {"Effekte", "Musik", "Voice", "Spotify", "Tools"};
        for (int i = 0; i < 5; i++) {
            int tx = tabX(i), ty = tabY();
            boolean sel = tab == i;
            boolean hov = Ui.in(mouseX, mouseY, tx, ty, tx + tabW, ty + 18);
            Ui.round2(ctx, tx, ty, tx + tabW, ty + 18, sel ? 0xFF3A1A5C : (hov ? 0xFF232736 : 0xFF181B24));
            if (sel) ctx.fill(tx + 6, ty + 16, tx + tabW - 6, ty + 18, ACCENT);
            int tw = tr.getWidth(tabNames[i]);
            ctx.drawTextWithShadow(tr, tabNames[i], tx + (tabW - tw) / 2, ty + 5, sel ? 0xFFFFFFFF : 0xFF9AA0AE);
        }

        if (tab <= 1) renderSounds(ctx, tr, mouseX, mouseY); else if (tab == 2) renderVoice(ctx, tr, mouseX, mouseY); else if (tab == 3) renderPlayer(ctx, tr, mouseX, mouseY); else renderTools(ctx, tr, mouseX, mouseY);
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
            } else if (tab == 1) {
                centered(ctx, tr, "Noch keine Lieder", cy + 10, 0xFFFFFFFF);
                centered(ctx, tr, "MP3s ab 30 Sekunden landen hier.", cy + 24, 0xFF9AA0AE);
                centered(ctx, tr, "Auf 'Ordner öffnen' klicken oder ins Fenster ziehen.", cy + 35, 0xFF9AA0AE);
            } else {
                centered(ctx, tr, "Noch keine Soundeffekte", cy + 10, 0xFFFFFFFF);
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
            if (playing) ctx.fill(bx + 6, by + 6, bx + 14, by + 14, 0xFF0B1A10);
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

    /** Nur die Sounds der gewählten Kategorie: Effekte (unter 30 Sekunden) oder Musik (ab 30 Sekunden). */
    private List<Path> categoryOf(List<Path> all) {
        boolean music = tab == 1;
        List<Path> out = new java.util.ArrayList<>();
        for (Path p : all) {
            if (Soundboard.isMusic(p) == music) out.add(p);
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
