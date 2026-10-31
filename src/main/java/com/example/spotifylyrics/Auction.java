package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auktionen (für HugoSMP): Item reinstellen, die anderen bieten per /pay an dich.
 * Wer am Ende insgesamt am meisten gezahlt hat, gewinnt. Danach gibt es /tpa zum Kopieren
 * und für alle anderen die Rückzahlung (/pay) zum Kopieren.
 */
public final class Auction {
    private Auction() {
    }

    static final int[] MINUTES = {1, 2, 3, 5, 10, 15, 20, 30, 45, 60};
    static final long[] MIN_BIDS = {0, 100, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000, 250000, 1000000};

    static volatile boolean running = false;
    static volatile boolean finished = false;
    static String item = "";
    static long endMs = 0, minBid = 0;
    static final Map<String, Double> BIDS = new LinkedHashMap<>();
    static String winner = "";
    static double winAmount = 0;
    static long finishedAt = 0;
    static String lastPayLine = "";
    static int payCount = 0;

    private static final String[] PAY_IN = {"received", "erhalten", "bekommen", "paid you", "hat dir", "has sent you", "sent you", "überwiesen", "gezahlt", "bezahlt", "gutgeschrieben"};
    private static final String[] PAY_OUT = {"you paid", "you sent", "sent to", "paid to", "du hast", "an dich gesendet?"};
    private static final Pattern NUM = Pattern.compile("(\\d{1,3}(?:[.,']\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?\\s*([kKmM](?![a-zA-Z]))?");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message.getString());
        });
    }

    static String myName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? "" : mc.player.getName().getString();
    }

    /** Namen aller Spieler in der Tab-Liste (auch weit weg). */
    static List<String> online() {
        List<String> out = new ArrayList<>();
        try {
            ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
            if (nh != null) {
                for (PlayerListEntry e : nh.getPlayerList()) {
                    String n = profileName(e.getProfile());
                    if (n != null && !n.isEmpty()) out.add(n);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String profileName(Object gp) {
        for (String m : new String[] {"name", "getName"}) {
            try {
                Object r = gp.getClass().getMethod(m).invoke(gp);
                if (r instanceof String s) return s;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Liest Zahlungen wie "Du hast 500$ von Steve erhalten" / "Steve has sent you $500". */
    static void onMessage(String raw) {
        if (!running || raw == null) return;
        try {
            String msg = raw.replaceAll("§.", "");
            String low = msg.toLowerCase(Locale.ROOT);
            boolean in = false;
            for (String w : PAY_IN) if (low.contains(w)) in = true;
            if (!in) return;
            // eigene Überweisungen ("Du hast ... an X gesendet") nicht zählen
            if ((low.contains("du hast") && (low.contains(" an ") || low.contains("gesendet"))) && !low.contains("erhalten") && !low.contains("bekommen")) return;
            if (low.contains("you paid") || low.contains("you sent") || low.contains("sent to") || low.contains("paid to")) return;
            String me = myName();
            String payer = null;
            List<String> on = online();
            for (String n : on) {
                if (n.equalsIgnoreCase(me)) continue;
                if (Pattern.compile("(?i)(^|[^A-Za-z0-9_])" + Pattern.quote(n) + "([^A-Za-z0-9_]|$)").matcher(msg).find()) {
                    if (payer == null || n.length() > payer.length()) payer = n;
                }
            }
            if (payer == null) {
                // Fallback: ein Wort, das wie ein Spielername aussieht und keine Zahl ist
                Matcher m = NAME.matcher(msg);
                while (m.find()) {
                    String t = m.group();
                    if (t.equalsIgnoreCase(me) || t.chars().allMatch(Character::isDigit)) continue;
                    if (t.chars().anyMatch(Character::isUpperCase) || t.contains("_")) {
                        payer = t;
                        break;
                    }
                }
            }
            if (payer == null) return;
            double amount = amount(msg.replace(payer, " "));
            if (amount <= 0) return;
            synchronized (BIDS) {
                BIDS.merge(payer, amount, Double::sum);
            }
            payCount++;
            lastPayLine = msg.length() > 80 ? msg.substring(0, 80) + "…" : msg;
            String lead = leader();
            local(Text.literal("[Auktion] ").formatted(Formatting.GOLD)
                .append(Text.literal(payer + " +" + fmt(amount)).formatted(Formatting.WHITE))
                .append(Text.literal("  Höchstgebot: " + lead + " (" + fmt(BIDS.getOrDefault(lead, 0.0)) + ")").formatted(Formatting.GRAY)));
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.warn("Auktion: Zahlung nicht lesbar", e);
        }
    }

    static double amount(String s) {
        Matcher m = NUM.matcher(s);
        double best = -1;
        while (m.find()) {
            String whole = m.group(1).replaceAll("[.,']", "");
            double v;
            try {
                v = Double.parseDouble(whole);
            } catch (NumberFormatException e) {
                continue;
            }
            if (m.group(2) != null) v += Double.parseDouble("0." + m.group(2));
            if (m.group(3) != null) v *= m.group(3).equalsIgnoreCase("k") ? 1_000 : 1_000_000;
            if (best < 0) best = v;   // erste Zahl ist meist der Betrag
        }
        return best;
    }

    static String fmt(double v) {
        if (v == Math.rint(v)) return String.format(Locale.GERMANY, "%,.0f", v) + "$";
        return String.format(Locale.GERMANY, "%,.2f", v) + "$";
    }

    static String leader() {
        String best = "";
        double max = -1;
        synchronized (BIDS) {
            for (Map.Entry<String, Double> e : BIDS.entrySet()) {
                if (e.getValue() > max) {
                    max = e.getValue();
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    static List<Map.Entry<String, Double>> sorted() {
        List<Map.Entry<String, Double>> l;
        synchronized (BIDS) {
            l = new ArrayList<>(BIDS.entrySet());
        }
        l.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return l;
    }

    static void local(Text t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(t, false);
    }

    static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatMessage(s);
    }

    static void start(String it, int minutes, long min, boolean announce) {
        synchronized (BIDS) {
            BIDS.clear();
        }
        item = it.isBlank() ? "Item" : it.trim();
        minBid = min;
        endMs = System.currentTimeMillis() + minutes * 60_000L;
        running = true;
        finished = false;
        winner = "";
        winAmount = 0;
        payCount = 0;
        lastPayLine = "";
        if (announce) {
            say("[Auktion] " + item + " | Bieten: /pay " + myName() + " <Betrag>" + (min > 0 ? " | ab " + fmt(min) : "")
                + " | Ende in " + minutes + " Min | Höchste Summe gewinnt");
        }
        local(Text.literal("[Auktion] gestartet: " + item + " (" + minutes + " Min)").formatted(Formatting.GOLD));
    }

    static void extend(int minutes) {
        if (running) endMs += minutes * 60_000L;
    }

    static void cancel() {
        running = false;
        finished = false;
        local(Text.literal("[Auktion] abgebrochen. Gezahltes Geld bitte zurückzahlen:").formatted(Formatting.RED));
        refunds(null);
    }

    static void finish() {
        if (!running) return;
        running = false;
        finished = true;
        finishedAt = System.currentTimeMillis();
        String lead = leader();
        double amt = BIDS.getOrDefault(lead, 0.0);
        if (lead.isEmpty() || amt < minBid) {
            winner = "";
            winAmount = 0;
            local(Text.literal("[Auktion] vorbei – kein gültiges Gebot.").formatted(Formatting.RED));
            refunds(null);
            return;
        }
        winner = lead;
        winAmount = amt;
        MutableText t = Text.literal("[Auktion] ").formatted(Formatting.GOLD)
            .append(Text.literal(winner).formatted(Formatting.GREEN, Formatting.BOLD))
            .append(Text.literal(" gewinnt " + item + " für " + fmt(amt) + "  ").formatted(Formatting.WHITE))
            .append(copyButton("/tpa " + winner, "[/tpa " + winner + " kopieren]"));
        local(t);
        refunds(winner);
    }

    static MutableText copyButton(String copy, String label) {
        return Text.literal(label).setStyle(Style.EMPTY.withColor(Formatting.AQUA).withUnderline(true)
            .withClickEvent(new ClickEvent.CopyToClipboard(copy))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Klicken zum Kopieren"))));
    }

    /** Für alle außer dem Gewinner: /pay zum Zurückzahlen als Kopier-Knopf. */
    static void refunds(String except) {
        for (Map.Entry<String, Double> e : sorted()) {
            if (e.getKey().equals(except)) continue;
            String cmd = "/pay " + e.getKey() + " " + plain(e.getValue());
            local(Text.literal("  Zurück an " + e.getKey() + " (" + fmt(e.getValue()) + "): ").formatted(Formatting.GRAY)
                .append(copyButton(cmd, "[" + cmd + " kopieren]")));
        }
    }

    static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
    }

    static void copy(String s) {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(s);
        } catch (Throwable ignored) {
        }
    }

    public static void tick(MinecraftClient mc) {
        if (running && System.currentTimeMillis() >= endMs) finish();
    }

    static String timeLeft() {
        long s = Math.max(0, (endMs - System.currentTimeMillis()) / 1000);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Anzeige oben in der Mitte: während der Auktion und 60 s nach dem Ende. */
    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            boolean showEnd = finished && System.currentTimeMillis() - finishedAt < 60_000;
            if (!running && !showEnd) return;
            TextRenderer tr = mc.textRenderer;
            int sw = ctx.getScaledWindowWidth();
            String l1, l2, l3;
            int col;
            if (running) {
                String lead = leader();
                l1 = "Auktion: " + item;
                l2 = lead.isEmpty() ? "Noch kein Gebot" + (minBid > 0 ? " (ab " + fmt(minBid) + ")" : "")
                    : "Höchstgebot: " + lead + " – " + fmt(BIDS.getOrDefault(lead, 0.0));
                l3 = "Noch " + timeLeft() + "  ·  /pay " + myName();
                col = 0xFFFFD166;
            } else if (!winner.isEmpty()) {
                l1 = "Gewinner: " + winner;
                l2 = item + " für " + fmt(winAmount);
                l3 = "/tpa " + winner + " (im Chat zum Kopieren)";
                col = 0xFF4ADE80;
            } else {
                l1 = "Auktion vorbei";
                l2 = "Kein gültiges Gebot";
                l3 = "";
                col = 0xFFF87171;
            }
            int w = Math.max(tr.getWidth(l1), Math.max(tr.getWidth(l2), tr.getWidth(l3))) + 16;
            int x0 = sw / 2 - w / 2, y0 = 22, h = l3.isEmpty() ? 28 : 38;
            ctx.fill(x0, y0, x0 + w, y0 + h, 0xB0101218);
            ctx.fill(x0, y0, x0 + w, y0 + 1, col);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(l1), sw / 2, y0 + 4, col);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(l2), sw / 2, y0 + 15, 0xFFFFFFFF);
            if (!l3.isEmpty()) ctx.drawCenteredTextWithShadow(tr, Text.literal(l3), sw / 2, y0 + 26, 0xFF9AA3B5);
        } catch (Throwable ignored) {
        }
    }
}
