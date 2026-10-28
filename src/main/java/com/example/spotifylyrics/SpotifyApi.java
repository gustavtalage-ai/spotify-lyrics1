package com.example.spotifylyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Spotify-Login (PKCE, ohne Client Secret) und Abfrage des aktuell laufenden Liedes. */
public class SpotifyApi {
    public static final int PORT = 8888;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String API = "https://api.spotify.com/v1";

    public record Track(String id, String title, String artist, String album, long durationMs) {}

    public record State(Track track, boolean playing, long progressMs, long fetchedNanos) {
        /** Geschätzte aktuelle Position im Lied. */
        public long position() {
            return progressMs + (playing ? (System.nanoTime() - fetchedNanos) / 1_000_000L : 0);
        }
    }

    record Resp(int code, String body, int retryAfter) {}

    /** null = es läuft gerade nichts. */
    public static volatile State state;

    private static volatile String accessToken = "";
    private static volatile long expiresAtMs = 0;
    private static volatile boolean loginRunning = false;

    // ---------- HTTP ----------

    static Resp http(String method, String url, String formBody, String bearer) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "SpotifyLyricsMod/1.0 (Minecraft Fabric mod)");
        if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
        if (formBody != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream o = c.getOutputStream()) {
                o.write(formBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new Resp(code, body, c.getHeaderFieldInt("Retry-After", 0));
    }

    static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(kv[i], StandardCharsets.UTF_8)).append('=')
              .append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // ---------- Login ----------

    public static void login(Consumer<String> onUrl, Consumer<String> onMessage) {
        Config cfg = Config.INSTANCE;
        if (cfg.clientId.isBlank()) {
            onMessage.accept("Erst die Client-ID setzen: /lyrics clientid <ID>");
            return;
        }
        if (loginRunning) {
            onMessage.accept("Ein Login läuft schon. Warte kurz oder starte das Spiel neu.");
            return;
        }
        loginRunning = true;
        Thread t = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT, 5, InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(300_000);
                SecureRandom rnd = new SecureRandom();
                byte[] vb = new byte[48];
                rnd.nextBytes(vb);
                String verifier = b64(vb);
                String challenge = b64(MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                byte[] sb = new byte[16];
                rnd.nextBytes(sb);
                String stateParam = b64(sb);

                String url = "https://accounts.spotify.com/authorize?" + form(
                        "response_type", "code",
                        "client_id", cfg.clientId,
                        "scope", "user-read-currently-playing user-read-playback-state",
                        "redirect_uri", REDIRECT,
                        "code_challenge_method", "S256",
                        "code_challenge", challenge,
                        "state", stateParam);
                onUrl.accept(url);

                Map<String, String> query = null;
                while (query == null) {
                    try (Socket s = server.accept()) {
                        s.setSoTimeout(5000);
                        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                        String line = r.readLine();
                        String h;
                        while ((h = r.readLine()) != null && !h.isEmpty()) { /* Header überspringen */ }
                        boolean isCallback = line != null && line.contains(" /callback");
                        String page = isCallback
                                ? "<html><body style='font-family:sans-serif'><h2>Fertig! Du kannst dieses Fenster schließen und zurück zu Minecraft gehen.</h2></body></html>"
                                : "";
                        byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
                        OutputStream o = s.getOutputStream();
                        o.write(((isCallback ? "HTTP/1.1 200 OK" : "HTTP/1.1 404 Not Found")
                                + "\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\nContent-Length: "
                                + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        o.write(bytes);
                        o.flush();
                        if (isCallback) {
                            String path = line.split(" ")[1];
                            query = parseQuery(URI.create("http://127.0.0.1" + path).getRawQuery());
                        }
                    } catch (SocketTimeoutException ignored) {
                        // einzelne Verbindung war zu langsam, weiter warten
                    }
                }

                if (query.containsKey("error") || !stateParam.equals(query.get("state")) || !query.containsKey("code")) {
                    onMessage.accept("Login abgebrochen oder fehlgeschlagen: " + query.getOrDefault("error", "ungültige Antwort"));
                    return;
                }
                Resp r = http("POST", TOKEN_URL, form(
                        "grant_type", "authorization_code",
                        "code", query.get("code"),
                        "redirect_uri", REDIRECT,
                        "client_id", cfg.clientId,
                        "code_verifier", verifier), null);
                if (r.code() != 200) {
                    onMessage.accept("Spotify hat den Login abgelehnt (Code " + r.code() + "): " + r.body());
                    return;
                }
                applyToken(JsonParser.parseString(r.body()).getAsJsonObject());
                onMessage.accept("Spotify ist verbunden! Starte ein Lied, dann erscheinen die Lyrics.");
            } catch (SocketTimeoutException e) {
                onMessage.accept("Login-Zeit abgelaufen. Nochmal /lyrics login eingeben.");
            } catch (BindException e) {
                onMessage.accept("Port " + PORT + " ist belegt. Schließe das Programm, das ihn nutzt, und versuche es nochmal.");
            } catch (Exception e) {
                SpotifyLyricsClient.LOGGER.warn("Login-Fehler", e);
                onMessage.accept("Login-Fehler: " + e.getMessage());
            } finally {
                loginRunning = false;
            }
        }, "spotifylyrics-login");
        t.setDaemon(true);
        t.start();
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8),
                      URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    public static void logout() {
        Config.INSTANCE.refreshToken = "";
        Config.INSTANCE.save();
        accessToken = "";
        expiresAtMs = 0;
        state = null;
    }

    // ---------- Token ----------

    private static void applyToken(JsonObject o) {
        accessToken = o.get("access_token").getAsString();
        long expiresIn = o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600;
        expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L;
        if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
            Config.INSTANCE.refreshToken = o.get("refresh_token").getAsString();
            Config.INSTANCE.save();
        }
    }

    private static synchronized boolean ensureToken() throws IOException {
        if (!accessToken.isEmpty() && System.currentTimeMillis() < expiresAtMs - 30_000) return true;
        Config cfg = Config.INSTANCE;
        if (cfg.refreshToken.isBlank() || cfg.clientId.isBlank()) return false;
        Resp r = http("POST", TOKEN_URL, form(
                "grant_type", "refresh_token",
                "refresh_token", cfg.refreshToken,
                "client_id", cfg.clientId), null);
        if (r.code() == 200) {
            applyToken(JsonParser.parseString(r.body()).getAsJsonObject());
            return true;
        }
        if (r.code() == 400 || r.code() == 401) { // Token ungültig -> neu einloggen nötig
            cfg.refreshToken = "";
            cfg.save();
        }
        return false;
    }

    // ---------- Abfrage ----------

    public static void startPolling() {
        Thread t = new Thread(() -> {
            String lastId = "";
            while (true) {
                long sleepMs = 3000;
                try {
                    Config cfg = Config.INSTANCE;
                    if (cfg.enabled && !cfg.refreshToken.isBlank() && ensureToken()) {
                        Resp r = http("GET", API + "/me/player/currently-playing", null, accessToken);
                        if (r.code() == 200) {
                            State s = parseState(r.body());
                            state = s;
                            if (s != null && !s.track().id().equals(lastId)) {
                                lastId = s.track().id();
                                Lyrics.request(s.track());
                            }
                        } else if (r.code() == 204) {
                            state = null;
                        } else if (r.code() == 401) {
                            expiresAtMs = 0;
                        } else if (r.code() == 429) {
                            sleepMs = Math.max(5, r.retryAfter()) * 1000L;
                        }
                    } else {
                        state = null;
                    }
                } catch (Exception e) {
                    SpotifyLyricsClient.LOGGER.debug("Spotify-Abfrage fehlgeschlagen", e);
                }
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "spotifylyrics-poll");
        t.setDaemon(true);
        t.start();
    }

    private static State parseState(String body) {
        if (body == null || body.isBlank()) return null;
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        JsonElement type = o.get("currently_playing_type");
        JsonElement item = o.get("item");
        if (item == null || item.isJsonNull() || type == null || !"track".equals(type.getAsString())) return null;
        JsonObject it = item.getAsJsonObject();
        String artist = "";
        if (it.has("artists") && it.getAsJsonArray("artists").size() > 0) {
            artist = it.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();
        }
        String album = it.has("album") && !it.get("album").isJsonNull()
                ? it.getAsJsonObject("album").get("name").getAsString() : "";
        Track tr = new Track(it.get("id").getAsString(), it.get("name").getAsString(), artist, album,
                it.get("duration_ms").getAsLong());
        boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
        long progress = o.has("progress_ms") && !o.get("progress_ms").isJsonNull() ? o.get("progress_ms").getAsLong() : 0;
        return new State(tr, playing, progress, System.nanoTime());
    }
}
