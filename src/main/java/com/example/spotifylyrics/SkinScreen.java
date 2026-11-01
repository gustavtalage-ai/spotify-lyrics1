package com.example.spotifylyrics;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Skin wechseln im Spiel: PNG-Dateien liegen im Ordner "alphamod-skins". Hochladen geht über die offizielle
 * Minecraft-Schnittstelle mit deiner eigenen Anmeldung (wie im Launcher). Danach neu joinen, damit du ihn siehst.
 */
public class SkinScreen extends Screen {
    private static final int HEAD = 34, FOOT = 30;
    /** Gewählter Eintrag: 0 = dein jetziger Skin (wenn im Spiel), danach die Dateien. */
    private static int sel = 0;
    private static boolean back = false;
    static volatile String status = "";
    static volatile boolean busy = false;
    static volatile boolean uploaded = false;

    /** Pixel der Skin-Dateien (ARGB, 64 breit), damit nicht jedes Bild neu gelesen wird. */
    private static final Map<String, int[]> PIX = new HashMap<>();

    private final List<Path> files = new ArrayList<>();
    private boolean hasPlayer;
    private int px0, px1, py0, py1, cx0, cx1, lx0, lx1, rx0, rx1, slotTop, slotBot, sc = 4;

    public SkinScreen() {
        super(Text.literal("Skins"));
    }

    static Path folder() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("alphamod-skins");
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
        }
        return p;
    }

    static List<Path> skins() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Breite und Höhe aus dem PNG-Kopf lesen (null = keine gültige PNG). */
    static int[] pngSize(Path f) {
        try (InputStream in = Files.newInputStream(f)) {
            byte[] h = in.readNBytes(24);
            if (h.length < 24 || (h[1] != 'P' || h[2] != 'N' || h[3] != 'G')) return null;
            int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
            int ht = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
            return new int[] {w, ht};
        } catch (Exception e) {
            return null;
        }
    }

    /** Skin hochladen (läuft im Hintergrund). */
    static void upload(Path file, boolean slim) {
        if (busy) return;
        int[] sz = pngSize(file);
        if (sz == null || sz[0] != 64 || (sz[1] != 64 && sz[1] != 32)) {
            status = Lang.t("Keine gültige Skin-Datei (muss 64x64 PNG sein).", "Not a valid skin (must be a 64x64 PNG).");
            return;
        }
        String token;
        try {
            token = MinecraftClient.getInstance().getSession().getAccessToken();
        } catch (Throwable t) {
            token = "";
        }
        if (token == null || token.isBlank() || token.length() < 20) {
            status = Lang.t("Nicht mit einem Microsoft-Konto angemeldet.", "Not signed in with a Microsoft account.");
            return;
        }
        busy = true;
        uploaded = false;
        status = Lang.t("Lade hoch …", "Uploading …");
        final String tk = token;
        Thread t = new Thread(() -> {
            try {
                byte[] img = Files.readAllBytes(file);
                String b = "----AlphaMod" + System.nanoTime();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic") + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
                out.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
                out.write(img);
                out.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + tk)
                    .header("Content-Type", "multipart/form-data; boundary=" + b)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                    .build();
                HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 == 2) {
                    uploaded = true;
                    status = Lang.t("Fertig! Jetzt neu joinen, dann siehst du den Skin.", "Done! Rejoin to see your new skin.");
                } else if (res.statusCode() == 401) {
                    status = Lang.t("Anmeldung abgelaufen – Launcher neu starten.", "Login expired – restart the launcher.");
                } else if (res.statusCode() == 429) {
                    status = Lang.t("Zu oft gewechselt – kurz warten.", "Too many changes – wait a moment.");
                } else {
                    status = Lang.t("Fehler beim Hochladen (Code ", "Upload failed (code ") + res.statusCode() + ")";
                }
            } catch (Exception e) {
                status = Lang.t("Fehler: ", "Error: ") + e.getClass().getSimpleName();
            } finally {
                busy = false;
            }
        }, "AlphaMod-Skin");
        t.setDaemon(true);
        t.start();
    }

    /** Vom Server trennen, damit man neu joinen kann (der neue Skin wird beim Joinen geladen). */
    static void rejoin() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().getConnection().disconnect(Text.literal(Lang.t(
            "Skin geändert – jetzt einfach wieder joinen.", "Skin changed – just join again.")));
    }

    // ---------- Skin-Bild lesen ----------

    /** {Höhe, Pixel...}: Pixel als ARGB, Breite 64. null = nicht lesbar. */
    private static int[] pixels(Path f) {
        String key;
        try {
            key = f.toString() + "|" + Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            return null;
        }
        if (PIX.containsKey(key)) return PIX.get(key);
        int[] out = null;
        try (InputStream in = Files.newInputStream(f); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth(), h = img.getHeight();
            if (w == 64 && (h == 64 || h == 32)) {
                out = new int[1 + 64 * 64];
                out[0] = h;
                for (int y = 0; y < h; y++) for (int x = 0; x < 64; x++) out[1 + y * 64 + x] = img.getColorArgb(x, y);
            }
        } catch (Throwable ignored) {
        }
        PIX.put(key, out);
        return out;
    }

    private static int px(int[] p, int x, int y) {
        if (p == null || x < 0 || x >= 64 || y < 0 || y >= p[0]) return 0;
        return p[1 + y * 64 + x];
    }

    /** Schmale Arme? (die äußerste Spalte des rechten Arms ist leer) */
    static boolean slim(int[] p) {
        return p != null && p[0] == 64 && (px(p, 54, 20) >>> 24) == 0 && (px(p, 55, 20) >>> 24) == 0 && (px(p, 54, 31) >>> 24) == 0;
    }

    /** Ein Rechteck des Skins zeichnen (u,v Größe w×h), optional gespiegelt. */
    private static void part(DrawContext ctx, int[] p, int u, int v, int w, int h, int x, int y, int s, boolean mirror, boolean overlay) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int c = px(p, mirror ? u + w - 1 - i : u + i, v + j);
                int a = c >>> 24;
                if (a == 0) continue;
                if (!overlay) c |= 0xFF000000;
                ctx.fill(x + i * s, y + j * s, x + (i + 1) * s, y + (j + 1) * s, c);
            }
        }
    }

    /** Ganze Figur (16×32 Skin-Pixel) von vorne oder hinten, mit Außenschicht. */
    private static void figure(DrawContext ctx, int[] p, int x, int y, int s, boolean backSide) {
        boolean legacy = p[0] == 32, thin = slim(p);
        int aw = thin ? 3 : 4;
        // Schatten am Boden
        Ui.round2(ctx, x + 2 * s, y + 32 * s - s, x + 14 * s, y + 32 * s + s, 0x55000000);
        if (!backSide) {
            part(ctx, p, 8, 8, 8, 8, x + 4 * s, y, s, false, false);
            part(ctx, p, 20, 20, 8, 12, x + 4 * s, y + 8 * s, s, false, false);
            part(ctx, p, 44, 20, aw, 12, x + (4 - aw) * s, y + 8 * s, s, false, false);
            if (legacy) part(ctx, p, 44, 20, aw, 12, x + 12 * s, y + 8 * s, s, true, false);
            else part(ctx, p, 36, 52, aw, 12, x + 12 * s, y + 8 * s, s, false, false);
            part(ctx, p, 4, 20, 4, 12, x + 4 * s, y + 20 * s, s, false, false);
            if (legacy) part(ctx, p, 4, 20, 4, 12, x + 8 * s, y + 20 * s, s, true, false);
            else part(ctx, p, 20, 52, 4, 12, x + 8 * s, y + 20 * s, s, false, false);
            part(ctx, p, 40, 8, 8, 8, x + 4 * s, y, s, false, true);
            if (!legacy) {
                part(ctx, p, 20, 36, 8, 12, x + 4 * s, y + 8 * s, s, false, true);
                part(ctx, p, 44, 36, aw, 12, x + (4 - aw) * s, y + 8 * s, s, false, true);
                part(ctx, p, 52, 52, aw, 12, x + 12 * s, y + 8 * s, s, false, true);
                part(ctx, p, 4, 36, 4, 12, x + 4 * s, y + 20 * s, s, false, true);
                part(ctx, p, 4, 52, 4, 12, x + 8 * s, y + 20 * s, s, false, true);
            }
        } else {
            int rb = thin ? 51 : 52, lb = thin ? 43 : 44;
            part(ctx, p, 24, 8, 8, 8, x + 4 * s, y, s, false, false);
            part(ctx, p, 32, 20, 8, 12, x + 4 * s, y + 8 * s, s, false, false);
            part(ctx, p, rb, 20, aw, 12, x + 12 * s, y + 8 * s, s, false, false);
            if (legacy) part(ctx, p, rb, 20, aw, 12, x + (4 - aw) * s, y + 8 * s, s, true, false);
            else part(ctx, p, lb, 52, aw, 12, x + (4 - aw) * s, y + 8 * s, s, false, false);
            part(ctx, p, 12, 20, 4, 12, x + 8 * s, y + 20 * s, s, false, false);
            if (legacy) part(ctx, p, 12, 20, 4, 12, x + 4 * s, y + 20 * s, s, true, false);
            else part(ctx, p, 28, 52, 4, 12, x + 4 * s, y + 20 * s, s, false, false);
            part(ctx, p, 56, 8, 8, 8, x + 4 * s, y, s, false, true);
            if (!legacy) {
                part(ctx, p, 32, 36, 8, 12, x + 4 * s, y + 8 * s, s, false, true);
                part(ctx, p, rb, 36, aw, 12, x + 12 * s, y + 8 * s, s, false, true);
                part(ctx, p, thin ? 59 : 60, 52, aw, 12, x + (4 - aw) * s, y + 8 * s, s, false, true);
                part(ctx, p, 12, 36, 4, 12, x + 8 * s, y + 20 * s, s, false, true);
                part(ctx, p, 12, 52, 4, 12, x + 4 * s, y + 20 * s, s, false, true);
            }
        }
    }

    // ---------- Einträge ----------

    private int count() {
        return files.size() + (hasPlayer ? 1 : 0);
    }

    /** Datei zu einem Eintrag (null = dein jetziger Skin). */
    private Path file(int i) {
        int k = hasPlayer ? i - 1 : i;
        return k >= 0 && k < files.size() ? files.get(k) : null;
    }

    private String name(int i) {
        Path f = file(i);
        if (f == null) return Lang.t("Dein Skin jetzt", "Your skin now");
        String n = f.getFileName().toString();
        return n.substring(0, n.length() - 4);
    }

    private void go(int d) {
        int n = count();
        if (n == 0) return;
        sel = Math.floorMod(sel + d, n);
        back = false;
        reopen();
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new SkinScreen());
    }

    private static void openFolder() {
        try {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("explorer.exe", folder().toString()).start();
            }
        } catch (Exception ignored) {
        }
    }

    private void copy(Path f) {
        try {
            String n = f.getFileName().toString();
            String base = n.substring(0, n.length() - 4);
            Path t = folder().resolve(base + " Kopie.png");
            for (int i = 2; Files.exists(t); i++) t = folder().resolve(base + " Kopie " + i + ".png");
            Files.copy(f, t);
            status = Lang.t("Kopie erstellt.", "Copy created.");
        } catch (Exception e) {
            status = Lang.t("Kopieren ging nicht.", "Could not copy.");
        }
    }

    /** "Löschen" verschiebt nur in den Unterordner "geloescht" – nichts geht verloren. */
    private void remove(Path f) {
        try {
            Path bin = folder().resolve("geloescht");
            Files.createDirectories(bin);
            Path t = bin.resolve(f.getFileName().toString());
            for (int i = 2; Files.exists(t); i++) t = bin.resolve(i + " " + f.getFileName());
            Files.move(f, t);
            status = Lang.t("In den Ordner \"geloescht\" verschoben.", "Moved to the \"geloescht\" folder.");
            if (sel > 0) sel--;
        } catch (Exception e) {
            status = Lang.t("Löschen ging nicht.", "Could not delete.");
        }
    }

    // ---------- Aufbau ----------

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        files.clear();
        files.addAll(skins());
        hasPlayer = mc.player != null;
        if (count() > 0) sel = Math.floorMod(sel, count());
        else sel = 0;

        int panelW = Math.min(460, this.width - 16), panelH = Math.min(290, this.height - 8);
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        slotTop = py0 + HEAD + 8;
        slotBot = py1 - FOOT - 16;
        int cw = Math.min(170, panelW / 3 + 10), sw = (panelW - cw - 2 * 30) / 2;
        cx0 = (px0 + px1) / 2 - cw / 2;
        cx1 = cx0 + cw;
        lx1 = cx0 - 6;
        lx0 = lx1 - sw;
        rx0 = cx1 + 6;
        rx1 = rx0 + sw;
        // Figur-Größe an die Höhe anpassen (Platz für Name oben und Knöpfe unten)
        sc = Math.max(2, Math.min(4, (slotBot - slotTop - 22 - 50) / 32));

        btn(px1 - 8 - 64, py0 + 8, 64, 18, () -> mc.setScreen(new HubScreen()));
        int n = count();
        if (n > 1) {
            btn(px0 + 6, (slotTop + slotBot) / 2 - 12, 18, 24, () -> go(-1));
            btn(px1 - 24, (slotTop + slotBot) / 2 - 12, 18, 24, () -> go(1));
            btn(lx0, slotTop, sw, slotBot - slotTop, () -> go(-1));
            btn(rx0, slotTop, sw, slotBot - slotTop, () -> go(1));
        }
        if (n > 0) {
            // Drehen: Pfeile links und rechts neben der Figur
            int fy = slotTop + 22 + 32 * sc / 2 - 8;
            btn(cx0 + 6, fy, 16, 16, () -> {
                back = !back;
                reopen();
            });
            btn(cx1 - 22, fy, 16, 16, () -> {
                back = !back;
                reopen();
            });
            Path f = file(sel);
            if (f != null) {
                int by = slotBot - 44, half = (cx1 - cx0 - 20) / 2;
                btn(cx0 + 8, by, cx1 - cx0 - 16, 18, () -> {
                    upload(f, slim(pixels(f)));
                    reopen();
                });
                btn(cx0 + 8, by + 22, half, 16, () -> {
                    copy(f);
                    reopen();
                });
                btn(cx0 + 12 + half, by + 22, half, 16, () -> {
                    remove(f);
                    reopen();
                });
            }
        }
        // Fußleiste
        int fy = py1 - FOOT + 6;
        int third = (px1 - px0 - 24) / 3;
        btn(px0 + 8, fy, third, 18, () -> mc.setScreen(new HubScreen()));
        btn(px0 + 12 + third, fy, third, 18, SkinScreen::openFolder);
        boolean server = mc.getCurrentServerEntry() != null;
        if (server) btn(px0 + 16 + 2 * third, fy, third, 18, SkinScreen::rejoin);
        else btn(px0 + 16 + 2 * third, fy, third, 18, SkinScreen::reopen);
    }

    // ---------- Zeichnen ----------

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    private void slot(DrawContext ctx, TextRenderer tr, int i, int x0, int x1, boolean center, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hov = Ui.in(mouseX, mouseY, x0, slotTop, x1, slotBot);
        ctx.fill(x0, slotTop, x1, slotBot, Ui.BG);
        if (center) {
            Ui.round2(ctx, x0 - 1, slotTop - 1, x1 + 1, slotBot + 1, (Ui.ACCENT & 0x00FFFFFF) | 0x40000000);
            Ui.card(ctx, x0, slotTop, x1, slotBot, Ui.ACCENT, Ui.mix(Ui.CARD, Ui.ACCENT, 0.10f));
        } else {
            Ui.card(ctx, x0, slotTop, x1, slotBot, hov ? 0xFF3A4152 : Ui.BORDER, hov ? Ui.CARD_HOV : Ui.CARD);
        }
        String nm = tr.trimToWidth(name(i), x1 - x0 - 10);
        Ui.txt(ctx, tr, nm, (x0 + x1 - tr.getWidth(nm)) / 2, slotTop + 7, center ? Ui.TEXT : Ui.MUTED);
        int s = center ? sc : Math.max(2, sc - 1);
        int fx = (x0 + x1) / 2 - 8 * s, fy = slotTop + 22;
        Path f = file(i);
        if (f == null) {
            // dein jetziger Skin: echte Figur, dreht sich mit der Maus
            if (mc.player != null) {
                int h = 32 * s;
                net.minecraft.client.gui.screen.ingame.InventoryScreen.drawEntity(ctx, fx - 10, fy, fx + 16 * s + 10, fy + h + 4,
                    (int) (h * 0.55f), 0.0625f, mouseX, mouseY, mc.player);
            }
        } else {
            int[] p = pixels(f);
            if (p == null) {
                Ui.txt(ctx, tr, Lang.t("Kein Skin-Bild", "Not a skin"), (x0 + x1) / 2 - 30, fy + 40, Ui.RED);
            } else {
                figure(ctx, p, fx, fy, s, center && back);
            }
        }
        if (!center) return;
        // Dreh-Pfeile
        int ay = fy + 32 * s / 2 - 8;
        Ui.btn(ctx, tr, x0 + 6, ay, x0 + 22, ay + 16, "◀", Ui.in(mouseX, mouseY, x0 + 6, ay, x0 + 22, ay + 16), Ui.SURFACE, Ui.SURFACE_HOV, Ui.TEXT);
        Ui.btn(ctx, tr, x1 - 22, ay, x1 - 6, ay + 16, "▶", Ui.in(mouseX, mouseY, x1 - 22, ay, x1 - 6, ay + 16), Ui.SURFACE, Ui.SURFACE_HOV, Ui.TEXT);
        if (f == null) {
            String t = Lang.t("Das trägst du gerade", "This is what you wear");
            Ui.txt(ctx, tr, t, (x0 + x1 - tr.getWidth(t)) / 2, slotBot - 18, Ui.MUTED);
            return;
        }
        int by = slotBot - 44, half = (x1 - x0 - 20) / 2;
        boolean thin = slim(pixels(f));
        String wear = busy ? Lang.t("Lade hoch …", "Uploading …") : Lang.t("Anziehen", "Wear") + (thin ? Lang.t(" (schmal)", " (slim)") : "");
        Ui.btn(ctx, tr, x0 + 8, by, x1 - 8, by + 18, wear, Ui.in(mouseX, mouseY, x0 + 8, by, x1 - 8, by + 18), Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFFFFFFF);
        Ui.btn(ctx, tr, x0 + 8, by + 22, x0 + 8 + half, by + 38, Lang.t("Kopie", "Copy"), Ui.in(mouseX, mouseY, x0 + 8, by + 22, x0 + 8 + half, by + 38),
            Ui.SURFACE, Ui.SURFACE_HOV, 0xFFD6D9E0);
        Ui.btn(ctx, tr, x0 + 12 + half, by + 22, x1 - 8, by + 38, Lang.t("Löschen", "Delete"), Ui.in(mouseX, mouseY, x0 + 12 + half, by + 22, x1 - 8, by + 38),
            0xFF2A141B, 0xFF3D1C26, 0xFFFF9FB0);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        MinecraftClient mc = MinecraftClient.getInstance();

        Ui.txt(ctx, tr, "Skins", px0 + 10, py0 + 8, Ui.TEXT);
        Ui.txt(ctx, tr, Lang.t("‹ › blättern · Mitte = gewählt · ◀ ▶ = umdrehen", "‹ › browse · middle = selected · ◀ ▶ = turn"), px0 + 10, py0 + 20, Ui.MUTED);
        int bx0 = px1 - 8 - 64;
        Ui.btn(ctx, tr, bx0, py0 + 8, bx0 + 64, py0 + 26, Lang.t("‹ Menü", "‹ Menu"), Ui.in(mouseX, mouseY, bx0, py0 + 8, bx0 + 64, py0 + 26), Ui.SURFACE, Ui.SURFACE_HOV, 0xFFD6D9E0);

        int n = count();
        if (n == 0) {
            String a = Lang.t("Noch keine Skins.", "No skins yet."), b = Lang.t("Leg PNG-Dateien in den Ordner (unten: Ordner öffnen).", "Put PNG files into the folder (below: open folder).");
            Ui.txt(ctx, tr, a, (px0 + px1 - tr.getWidth(a)) / 2, (slotTop + slotBot) / 2 - 10, Ui.TEXT);
            Ui.txt(ctx, tr, b, (px0 + px1 - tr.getWidth(b)) / 2, (slotTop + slotBot) / 2 + 2, Ui.MUTED);
        } else {
            if (n > 1) {
                slot(ctx, tr, Math.floorMod(sel - 1, n), lx0, lx1, false, mouseX, mouseY);
                slot(ctx, tr, Math.floorMod(sel + 1, n), rx0, rx1, false, mouseX, mouseY);
                int my = (slotTop + slotBot) / 2 - 12;
                Ui.btn(ctx, tr, px0 + 6, my, px0 + 24, my + 24, "‹", Ui.in(mouseX, mouseY, px0 + 6, my, px0 + 24, my + 24), Ui.SURFACE, Ui.SURFACE_HOV, Ui.TEXT);
                Ui.btn(ctx, tr, px1 - 24, my, px1 - 6, my + 24, "›", Ui.in(mouseX, mouseY, px1 - 24, my, px1 - 6, my + 24), Ui.SURFACE, Ui.SURFACE_HOV, Ui.TEXT);
            }
            slot(ctx, tr, sel, cx0, cx1, true, mouseX, mouseY);
            if (status.isEmpty()) {
                String pos = (sel + 1) + " / " + n;
                Ui.txt(ctx, tr, pos, (px0 + px1 - tr.getWidth(pos)) / 2, slotBot + 4, Ui.MUTED);
            }
        }
        // Status
        if (!status.isEmpty()) {
            String st = tr.trimToWidth(status, px1 - px0 - 20);
            Ui.txt(ctx, tr, st, px0 + 10, slotBot + 4, uploaded ? Ui.GREEN : 0xFFFFD166);
        }
        // Fußleiste
        ctx.fill(px0 + 1, py1 - FOOT, px1 - 1, py1 - FOOT + 1, 0x28FFFFFF);
        int fy = py1 - FOOT + 6, third = (px1 - px0 - 24) / 3;
        Ui.btn(ctx, tr, px0 + 8, fy, px0 + 8 + third, fy + 18, Lang.t("Abbrechen", "Cancel"), Ui.in(mouseX, mouseY, px0 + 8, fy, px0 + 8 + third, fy + 18), Ui.SURFACE, Ui.SURFACE_HOV, 0xFFD6D9E0);
        Ui.btn(ctx, tr, px0 + 12 + third, fy, px0 + 12 + 2 * third, fy + 18, Lang.t("Ordner öffnen", "Open folder"), Ui.in(mouseX, mouseY, px0 + 12 + third, fy, px0 + 12 + 2 * third, fy + 18), Ui.SURFACE, Ui.SURFACE_HOV, 0xFFD6D9E0);
        boolean server = mc.getCurrentServerEntry() != null;
        String third3 = server ? Lang.t("Neu joinen", "Rejoin") : Lang.t("Neu laden", "Reload");
        Ui.btn(ctx, tr, px0 + 16 + 2 * third, fy, px0 + 16 + 3 * third, fy + 18, third3, Ui.in(mouseX, mouseY, px0 + 16 + 2 * third, fy, px0 + 16 + 3 * third, fy + 18),
            server && uploaded ? Ui.ACCENT_DIM : Ui.SURFACE, server && uploaded ? Ui.ACCENT_DIM_HOV : Ui.SURFACE_HOV, 0xFFD6D9E0);
    }
}
