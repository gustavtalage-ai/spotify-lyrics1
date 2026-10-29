package com.example.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Spotify aus Minecraft steuern: Songs suchen, abspielen, pausieren, weiter, zurück, spulen, Lautstärke. */
public class SpotifyControl {
    private static final String API = "https://api.spotify.com/v1";
    private static final AtomicInteger seq = new AtomicInteger();

    /** Treffer der letzten Suche. */
    public static volatile List<SpotifyApi.Track> results = List.of();
    /** Hinweis oder Fehler für die Anzeige (leer = alles gut). */
    public static volatile String message = "";
    public static volatile boolean busy = false;
    /** Zuletzt bekannte Spotify-Lautstärke in Prozent (-1 = unbekannt). */
    public static volatile int volume = -1;

    private static SpotifyApi.Resp req(String method, String url, String json) throws IOException {
        String tok = SpotifyApi.token();
        if (tok == null) return new SpotifyApi.Resp(401, "", 0);
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("Authorization", "Bearer " + tok);
        c.setRequestProperty("User-Agent", "SpotifyLyricsMod/1.0 (Minecraft Fabric mod)");
        if (!method.equals("GET")) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] b = (json == null ? "" : json).getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = c.getOutputStream()) {
                o.write(b);
            }
        }
        int code = c.getResponseCode();
        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new SpotifyApi.Resp(code, body, c.getHeaderFieldInt("Retry-After", 0));
    }

    private static String describe(SpotifyApi.Resp r) {
        String b = r.body() == null ? "" : r.body().toLowerCase();
        switch (r.code()) {
            case 401:
                return "Nicht verbunden. Im Spiel: /lyrics login";
            case 403:
                if (b.contains("premium")) return "Dafür braucht Spotify Premium.";
                if (b.contains("scope")) return "Neu anmelden: /lyrics login";
                return "Spotify sagt Nein (403). Ist dein Konto freigeschaltet?";
            case 404:
                return "Kein Gerät gefunden. Öffne Spotify am PC oder Handy.";
            case 429:
                return "Zu viele Anfragen. Kurz warten.";
            default:
                return "Spotify-Fehler " + r.code();
        }
    }

    private static void async(String name, Task task) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                message = "Fehler: " + e.getMessage();
                SpotifyLyricsClient.LOGGER.debug("Spotify-Steuerung fehlgeschlagen", e);
            }
        }, name);
        t.setDaemon(true);
        t.start();
    }

    private interface Task {
        void run() throws Exception;
    }

    // ---------- Login aus dem Menü ----------

    public static void login() {
        message = "Login wird gestartet ...";
        SpotifyApi.login(url -> {
            openUrl(url);
            message = "Browser geöffnet. Bei Spotify bestätigen.";
        }, msg -> message = msg);
    }

    private static void openUrl(String url) {
        try {
            new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            return;
        } catch (Throwable ignored) {
            // weiter mit dem nächsten Weg
        }
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(url));
        } catch (Throwable ignored) {
            message = "Browser ließ sich nicht öffnen.";
        }
    }

    // ---------- Suche ----------

    public static void search(String text) {
        final int id = seq.incrementAndGet();
        if (text == null || text.isBlank()) {
            results = List.of();
            message = "";
            return;
        }
        busy = true;
        async("spotifylyrics-search", () -> {
            try {
                String url = API + "/search?" + SpotifyApi.form("q", text, "type", "track", "limit", "5");
                SpotifyApi.Resp r = req("GET", url, null);
                if (id != seq.get()) return; // inzwischen neuer Suchtext
                if (r.code() != 200) {
                    results = List.of();
                    message = describe(r);
                    return;
                }
                List<SpotifyApi.Track> list = new ArrayList<>();
                JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
                JsonArray items = root.getAsJsonObject("tracks").getAsJsonArray("items");
                for (JsonElement e : items) {
                    if (e == null || e.isJsonNull()) continue;
                    JsonObject it = e.getAsJsonObject();
                    String artist = "";
                    if (it.has("artists") && it.getAsJsonArray("artists").size() > 0) {
                        artist = it.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
                    }
                    String album = it.has("album") && !it.get("album").isJsonNull()
                            ? it.getAsJsonObject("album").get("name").getAsString() : "";
                    list.add(new SpotifyApi.Track(it.get("id").getAsString(), it.get("name").getAsString(), artist, album,
                            it.get("duration_ms").getAsLong()));
                }
                results = list;
                message = list.isEmpty() ? "Nichts gefunden." : "";
            } finally {
                if (id == seq.get()) busy = false;
            }
        });
    }

    // ---------- Steuerung ----------

    public static void play(SpotifyApi.Track t) {
        message = "Starte: " + t.title();
        async("spotifylyrics-play", () -> {
            JsonObject body = new JsonObject();
            JsonArray uris = new JsonArray();
            uris.add("spotify:track:" + t.id());
            body.add("uris", uris);
            String json = body.toString();
            SpotifyApi.Resp r = req("PUT", API + "/me/player/play", json);
            if (r.code() == 404) { // kein aktives Gerät: erstes verfügbares nehmen
                SpotifyApi.Resp d = req("GET", API + "/me/player/devices", null);
                if (d.code() == 200) {
                    JsonArray devs = JsonParser.parseString(d.body()).getAsJsonObject().getAsJsonArray("devices");
                    if (devs != null && devs.size() > 0) {
                        String devId = devs.get(0).getAsJsonObject().get("id").getAsString();
                        r = req("PUT", API + "/me/player/play?device_id=" + devId, json);
                    }
                }
            }
            message = (r.code() >= 200 && r.code() < 300) ? "" : describe(r);
        });
    }

    public static void toggle() {
        SpotifyApi.State s = SpotifyApi.state;
        boolean playing = s != null && s.playing();
        simple(playing ? "PUT" : "PUT", playing ? "/me/player/pause" : "/me/player/play");
    }

    public static void next() {
        simple("POST", "/me/player/next");
    }

    public static void previous() {
        simple("POST", "/me/player/previous");
    }

    /** Springt im Lied an die Stelle (Millisekunden). */
    public static void seek(long ms) {
        final long pos = Math.max(0, ms);
        SpotifyApi.State s = SpotifyApi.state;
        if (s != null) SpotifyApi.state = new SpotifyApi.State(s.track(), s.playing(), pos, System.nanoTime());
        async("spotifylyrics-seek", () -> {
            SpotifyApi.Resp r = req("PUT", API + "/me/player/seek?position_ms=" + pos, null);
            message = (r.code() >= 200 && r.code() < 300) ? "" : describe(r);
        });
    }

    /** Lautstärke von Spotify (0 bis 100). Nicht jedes Gerät erlaubt das (Handy meist nicht). */
    public static void setVolume(int pct) {
        final int v = Math.max(0, Math.min(100, pct));
        volume = v;
        Config.INSTANCE.playerVolume = v;
        async("spotifylyrics-vol", () -> {
            SpotifyApi.Resp r = req("PUT", API + "/me/player/volume?volume_percent=" + v, null);
            if (r.code() >= 200 && r.code() < 300) message = "";
            else if (r.code() == 403) message = "Dieses Gerät lässt keine Lautstärke zu. Am Gerät regeln.";
            else message = describe(r);
        });
    }

    /** Liest die aktuelle Lautstärke des Spotify-Geräts. */
    public static void loadVolume() {
        async("spotifylyrics-getvol", () -> {
            SpotifyApi.Resp r = req("GET", API + "/me/player", null);
            if (r.code() != 200 || r.body() == null || r.body().isBlank()) return;
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("device") && !o.get("device").isJsonNull()) {
                JsonObject d = o.getAsJsonObject("device");
                if (d.has("volume_percent") && !d.get("volume_percent").isJsonNull()) {
                    volume = d.get("volume_percent").getAsInt();
                }
            }
        });
    }

    /** Pausiert und wartet kurz, bis Spotify geantwortet hat (für Server verlassen / Spiel beenden). */
    public static void pauseAndWait() {
        try {
            SpotifyApi.State s = SpotifyApi.state;
            if (s == null || !s.playing() || Config.INSTANCE.refreshToken.isBlank()) return;
            Thread t = new Thread(() -> {
                try {
                    req("PUT", API + "/me/player/pause", null);
                } catch (Exception ignored) {
                    // egal
                }
            }, "spotifylyrics-leave");
            t.setDaemon(true);
            t.start();
            t.join(2500);
            SpotifyApi.State n = SpotifyApi.state;
            if (n != null) SpotifyApi.state = new SpotifyApi.State(n.track(), false, n.position(), System.nanoTime());
        } catch (Throwable ignored) {
            // egal
        }
    }

    private static void simple(String method, String path) {
        async("spotifylyrics-ctl", () -> {
            SpotifyApi.Resp r = req(method, API + path, null);
            message = (r.code() >= 200 && r.code() < 300) ? "" : describe(r);
        });
    }
}
