package com.example.spotifylyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Holt zeitlich synchronisierte Lyrics vom freien Dienst LRCLIB (lrclib.net). */
public class Lyrics {
    public record Line(long timeMs, String text) {}

    public static volatile String forTrackId = "";
    public static volatile List<Line> lines = List.of();
    public static volatile String status = "";

    private static final String BASE = "https://lrclib.net/api/";
    private static final Pattern TS = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    public static void request(SpotifyApi.Track t) {
        forTrackId = t.id();
        lines = List.of();
        status = "Lade Lyrics …";
        Thread th = new Thread(() -> {
            List<Line> res = fetch(t);
            if (t.id().equals(forTrackId)) {
                lines = res;
                status = res.isEmpty() ? "Keine Lyrics gefunden" : "";
            }
        }, "spotifylyrics-fetch");
        th.setDaemon(true);
        th.start();
    }

    static List<Line> fetch(SpotifyApi.Track t) {
        try {
            String q = SpotifyApi.form("artist_name", t.artist(), "track_name", t.title(),
                    "album_name", t.album(), "duration", String.valueOf(t.durationMs() / 1000));
            SpotifyApi.Resp r = SpotifyApi.http("GET", BASE + "get?" + q, null, null);
            if (r.code() == 200) {
                List<Line> l = fromObject(JsonParser.parseString(r.body()).getAsJsonObject());
                if (!l.isEmpty()) return l;
            }
            r = SpotifyApi.http("GET", BASE + "search?" + SpotifyApi.form(
                    "track_name", cleanTitle(t.title()), "artist_name", t.artist()), null, null);
            if (r.code() == 200) {
                for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonArray()) {
                    List<Line> l = fromObject(e.getAsJsonObject());
                    if (!l.isEmpty()) return l;
                }
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Lyrics-Abfrage fehlgeschlagen", e);
        }
        return List.of();
    }

    private static String cleanTitle(String title) {
        return title.replaceAll("\\s*[-–].*$", "").replaceAll("\\s*[(\\[].*?[)\\]]", "").trim();
    }

    private static List<Line> fromObject(JsonObject o) {
        JsonElement s = o.get("syncedLyrics");
        if (s == null || s.isJsonNull()) return List.of();
        return parse(s.getAsString());
    }

    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                String f = m.group(3);
                if (f != null) ms = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
                times.add(min * 60_000 + sec * 1000 + ms);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }
}
