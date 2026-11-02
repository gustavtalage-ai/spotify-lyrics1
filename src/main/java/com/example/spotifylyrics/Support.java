package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Support: Spieler schreiben Ideen, Fragen oder Fehler. Die Nachricht geht mit dem Minecraft-Namen an das
 * Support-Postfach des Entwicklers (Discord-Webhook, Adresse steht in support.txt im GitHub-Projekt).
 * Eine Kopie bleibt auf dem eigenen PC; nur im Admin-Modus sieht man das Postfach im Spiel.
 */
public final class Support {
    private Support() {
    }

    public static final String[] CATS = {"Idee", "Frage", "Fehler"};
    private static final String CONFIG_URL = "https://raw.githubusercontent.com/gustavtalage-ai/spotify-lyrics1/main/support.txt";
    private static volatile String hook = null;
    private static long lastSend;

    /** Webhook-Adresse aus dem GitHub-Projekt holen (einmal im Hintergrund). */
    public static void load() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(CONFIG_URL + "?t=" + System.currentTimeMillis())).timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> res = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
                String s = res.statusCode() == 200 ? res.body().trim() : "";
                hook = s.startsWith("https://discord.com/api/webhooks/") || s.startsWith("https://discordapp.com/api/webhooks/") ? s.split("\\s+")[0] : "";
            } catch (Throwable e) {
                hook = "";
            }
        });
    }

    public static boolean ready() {
        return hook != null && !hook.isEmpty();
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> {
                }
                case '\t' -> b.append(' ');
                default -> {
                    if (c < 0x20) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    /** Nachricht senden. Rückgabe: Text für die Anzeige. */
    public static String send(int cat, String text) {
        text = text == null ? "" : text.trim();
        if (text.length() < 3) return Lang.t("Bitte schreib etwas mehr.", "Please write a bit more.");
        long now = System.currentTimeMillis();
        if (now - lastSend < 60_000) return Lang.t("Bitte warte kurz (1 Nachricht pro Minute).", "Please wait (1 message per minute).");
        lastSend = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = mc.getSession() != null ? mc.getSession().getUsername() : "?";
        String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"));
        String c = CATS[Math.floorMod(cat, CATS.length)];
        Config cfg = Config.INSTANCE;
        cfg.supportMsgs.add(when + "|" + c + "|" + name + "|" + text);
        while (cfg.supportMsgs.size() > 100) cfg.supportMsgs.remove(0);
        cfg.save();
        if (!ready()) return Lang.t("Gespeichert – Support-Postfach ist noch nicht eingerichtet.", "Saved – support inbox is not set up yet.");
        String body = "{\"username\":\"Alpha Mod Support\",\"allowed_mentions\":{\"parse\":[]},\"content\":\"" + json("**" + c + "** von **" + name + "** (" + when + "):\n" + text) + "\"}";
        String url = hook;
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {
            }
        });
        return Lang.t("Gesendet – danke!", "Sent – thanks!");
    }
}
