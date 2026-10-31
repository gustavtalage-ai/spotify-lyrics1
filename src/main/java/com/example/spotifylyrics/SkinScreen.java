package com.example.spotifylyrics;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Skin wechseln im Spiel: PNG-Dateien liegen im Ordner "alphamod-skins". Hochladen geht über die offizielle
 * Minecraft-Schnittstelle mit deiner eigenen Anmeldung (wie im Launcher). Danach neu joinen, damit du ihn siehst.
 */
public class SkinScreen extends ItemScreen {
    private static int sel = 0;
    private static int page = 0;
    static volatile String status = "";
    static volatile boolean busy = false;
    static volatile boolean uploaded = false;

    public SkinScreen() {
        super(Lang.t("Skin wechseln", "Change skin"), Lang.t("Hochladen, wechseln, neu joinen", "Upload, switch, rejoin"));
    }

    @Override
    protected ItemScreen fresh() {
        return new SkinScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return Lang.t("‹ Menü", "‹ Menu");
    }

    @Override
    protected int panelWidth() {
        return 340;
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

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        List<Path> list = skins();
        if (sel >= list.size()) sel = Math.max(0, list.size() - 1);
        sec(Lang.t("Deine Skins", "Your skins") + " (" + list.size() + ")");
        if (list.isEmpty()) {
            text(Lang.t("Noch keine Skins. Leg PNG-Dateien in den Ordner:", "No skins yet. Put PNG files into the folder:"), 0xFF8B92A5);
            text("config/alphamod-skins", Ui.TEXT);
        } else {
            int per = 4;
            int pages = (list.size() + per - 1) / per;
            page = Math.max(0, Math.min(page, pages - 1));
            for (int i = page * per; i < Math.min(list.size(), page * per + per); i++) {
                final int k = i;
                String name = list.get(i).getFileName().toString();
                name = name.substring(0, name.length() - 4);
                row(k == sel ? S("✔ " + name) : G(name, () -> {
                    sel = k;
                    again();
                }));
            }
            if (pages > 1) {
                row(G("‹", () -> {
                    page = Math.max(0, page - 1);
                    again();
                }), X((page + 1) + " / " + pages).w(4), G("›", () -> {
                    page = Math.min(pages - 1, page + 1);
                    again();
                }));
            }
        }
        row(G(Lang.t("Ordner öffnen", "Open folder"), () -> {
            try {
                if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                    new ProcessBuilder("explorer.exe", folder().toString()).start();
                }
            } catch (Exception ignored) {
            }
        }), G(Lang.t("Liste neu laden", "Reload list"), this::again));
        gap(2);
        sec(Lang.t("Modell", "Model"));
        row(c.skinSlim ? G(Lang.t("Klassisch (4px)", "Classic (4px)"), () -> {
            c.skinSlim = false;
            c.save();
            again();
        }) : S(Lang.t("Klassisch (4px)", "Classic (4px)")), c.skinSlim ? S(Lang.t("Schmal (3px)", "Slim (3px)")) : G(Lang.t("Schmal (3px)", "Slim (3px)"), () -> {
            c.skinSlim = true;
            c.save();
            again();
        }));
        gap(2);
        sec(Lang.t("Wechseln", "Switch"));
        row(B(busy ? Lang.t("Lade hoch …", "Uploading …") : Lang.t("Skin hochladen und anziehen", "Upload and wear skin"), () -> {
            if (!list.isEmpty()) upload(list.get(Math.min(sel, list.size() - 1)), c.skinSlim);
            again();
        }));
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean server = mc.getCurrentServerEntry() != null;
        if (server) {
            row(G(Lang.t("Neu joinen (Server verlassen)", "Rejoin (leave server)"), SkinScreen::rejoin));
        }
        if (!status.isEmpty()) text(status, uploaded ? Ui.GREEN : 0xFFFFD166);
        if (!server) text(Lang.t("Einzelspieler: Welt verlassen und neu laden.", "Singleplayer: leave and reload the world."), 0xFF8B92A5);
    }
}
