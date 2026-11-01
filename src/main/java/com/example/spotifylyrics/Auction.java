package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;
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
    static ItemStack stack = ItemStack.EMPTY;
    static int count = 1;
    static long startMs = 0;
    /** Letzte Zahlungen für die Anzeige rechts: {Name, Betrag}. */
    static final List<String[]> PAYS = new ArrayList<>();
    private static final Pattern COUNT = Pattern.compile("^(\\d+)\\s*[xX]\\s+(.+)$");

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
            synchronized (PAYS) {
                PAYS.add(0, new String[]{payer, dollar(amount)});
                if (PAYS.size() > 12) PAYS.remove(PAYS.size() - 1);
            }
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
        synchronized (PAYS) {
            PAYS.clear();
        }
        item = it.isBlank() ? "Item" : it.trim();
        count = 1;
        stack = ItemStack.EMPTY;
        Matcher cm = COUNT.matcher(item);
        if (cm.matches()) {
            try {
                count = Integer.parseInt(cm.group(1));
            } catch (NumberFormatException ignored) {
            }
            item = cm.group(2).trim();
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                ItemStack h = mc.player.getMainHandStack();
                if (!h.isEmpty() && (it.isBlank() || item.equalsIgnoreCase(h.getName().getString()))) {
                    stack = h.copy();
                    count = h.getCount();
                    item = h.getName().getString();
                }
            }
        } catch (Throwable ignored) {
        }
        minBid = min;
        startMs = System.currentTimeMillis();
        endMs = startMs + minutes * 60_000L;
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

    static String dollar(double v) {
        if (v == Math.rint(v)) return "$" + String.format(Locale.GERMANY, "%,.0f", v);
        return "$" + String.format(Locale.GERMANY, "%,.2f", v);
    }

    static double total() {
        double t = 0;
        synchronized (BIDS) {
            for (double v : BIDS.values()) t += v;
        }
        return t;
    }

    private static final int RED = 0xFFE5484D, PANEL = 0xA0141418, WHITE = 0xFFFFFFFF, GRAY = 0xFFB8BCC6, GREEN = 0xFF4ADE80;

    private static void big(DrawContext ctx, TextRenderer tr, String s, int cx, int y, float sc, int col) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) cx, (float) y);
        m.scale(sc, sc);
        ctx.drawTextWithShadow(tr, Text.literal(s), -tr.getWidth(s) / 2, 0, col);
        m.popMatrix();
    }

    private static void frame(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        ctx.fill(x0, y0, x1, y0 + 1, col);
        ctx.fill(x0, y1 - 1, x1, y1, col);
        ctx.fill(x0, y0, x0 + 1, y1, col);
        ctx.fill(x1 - 1, y0, x1, y1, col);
    }

    /** Große Auktions-Anzeige: links Auktion mit Item und Zeit, rechts Gesamteingang und Zahlungen. */
    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            boolean showEnd = finished && System.currentTimeMillis() - finishedAt < 60_000;
            if (!running && !showEnd) return;
            TextRenderer tr = mc.textRenderer;
            int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

            int m = Math.max(6, sw / 40);
            int fx0 = m, fy0 = 14, fx1 = sw - m, fy1 = sh - 26;
            // Titel und roter Rahmen
            big(ctx, tr, Lang.t("Alpha Auktionen", "Alpha Auctions"), sw / 2, 3, 1f, RED);
            frame(ctx, fx0, fy0, fx1, fy1, RED);

            int gapX = 6;
            int lx0 = fx0 + 3, ly0 = fy0 + 3, ly1 = fy1 - 3;
            int lx1 = fx0 + (int) ((fx1 - fx0) * 0.64f);
            int rx0 = lx1 + gapX, rx1 = fx1 - 3;
            ctx.fill(lx0, ly0, lx1, ly1, PANEL);
            ctx.fill(rx0, ly0, rx1, ly1, PANEL);

            // ---------- links: Auktion ----------
            int cx = (lx0 + lx1) / 2, y = ly0 + 8;
            int lh = ly1 - ly0;
            float s2 = lh > 200 ? 2f : 1.5f;
            if (running) {
                String lead = leader();
                big(ctx, tr, Lang.t("LAUFENDE AUKTION", "RUNNING AUCTION"), cx, y, 1f, WHITE);
                y += 16;
                big(ctx, tr, lead.isEmpty() ? Lang.t("Noch kein Gebot", "No bid yet") : Lang.t("Höchstbieter: ", "Top bidder: ") + lead, cx, y, 1f, GRAY);
                y += 12;
                big(ctx, tr, dollar(lead.isEmpty() ? 0 : BIDS.getOrDefault(lead, 0.0)), cx, y, s2, WHITE);
                y += (int) (9 * s2) + 5;
                big(ctx, tr, Lang.t("Mindestgebot: ", "Minimum bid: ") + (minBid > 0 ? dollar(minBid) : Lang.t("keines", "none")), cx, y, 1f, GRAY);
            } else if (!winner.isEmpty()) {
                big(ctx, tr, Lang.t("AUKTION BEENDET", "AUCTION ENDED"), cx, y, 1f, WHITE);
                y += 16;
                big(ctx, tr, Lang.t("Gewinner: ", "Winner: ") + winner, cx, y, 1f, GREEN);
                y += 12;
                big(ctx, tr, dollar(winAmount), cx, y, s2, WHITE);
                y += (int) (9 * s2) + 5;
                big(ctx, tr, "/tpa " + winner + Lang.t(" (im Chat kopieren)", " (copy in chat)"), cx, y, 1f, GRAY);
            } else {
                big(ctx, tr, Lang.t("AUKTION BEENDET", "AUCTION ENDED"), cx, y, 1f, WHITE);
                y += 16;
                big(ctx, tr, Lang.t("Kein gültiges Gebot", "No valid bid"), cx, y, 1f, RED);
                y += 12 + (int) (9 * s2) + 5;
            }
            y += 14;

            // Item groß in der Mitte
            int bottomReserve = 46;
            int space = Math.max(16, (ly1 - bottomReserve) - y - 14);
            float isc = Math.max(1f, Math.min(4f, space / 16f));
            if (!stack.isEmpty()) {
                Matrix3x2fStack mm = ctx.getMatrices();
                mm.pushMatrix();
                mm.translate(cx - 8 * isc, (float) y);
                mm.scale(isc, isc);
                ctx.drawItem(stack, 0, 0);
                mm.popMatrix();
            }
            y += (int) (16 * isc) + 6;
            big(ctx, tr, item, cx, y, 1f, WHITE);

            // unten: Menge, Zeit, Balken
            int by = ly1 - bottomReserve + 6;
            big(ctx, tr, Lang.t("Menge: ", "Amount: ") + count, cx, by, 1f, GRAY);
            long left = running ? Math.max(0, endMs - System.currentTimeMillis()) : 0;
            long secs = left / 1000;
            big(ctx, tr, String.format(Locale.ROOT, "%02d:%02d", secs / 60, secs % 60), cx, by + 11, 1.5f, WHITE);
            float frac = running && endMs > startMs ? Math.min(1f, left / (float) (endMs - startMs)) : 0f;
            int barY = ly1 - 6;
            ctx.fill(lx0 + 4, barY, lx1 - 4, barY + 2, 0x60FFFFFF);
            ctx.fill(lx0 + 4, barY, lx0 + 4 + (int) ((lx1 - lx0 - 8) * frac), barY + 2, WHITE);

            // ---------- rechts: Gesamteingang ----------
            int tx = rx0 + 6, ty = ly0 + 8;
            ctx.drawTextWithShadow(tr, Text.literal(Lang.t("Gesamteingang", "Total received")), tx, ty, WHITE);
            ty += 14;
            big(ctx, tr, dollar(total()), (rx0 + rx1) / 2, ty, 1.5f, GREEN);
            ty += 20;
            ctx.drawTextWithShadow(tr, Text.literal(payCount + Lang.t(" Zahlungen", " payments")), tx, ty, GRAY);
            ty += 14;
            List<String[]> pays;
            synchronized (PAYS) {
                pays = new ArrayList<>(PAYS);
            }
            if (pays.isEmpty()) {
                ctx.drawTextWithShadow(tr, Text.literal(Lang.t("Keine Zahlungen", "No payments")), tx, ty, GRAY);
            } else {
                int maxW = rx1 - tx - 4;
                for (String[] p : pays) {
                    if (ty + 9 > ly1 - 4) break;
                    String amt = "+" + p[1];
                    String name = tr.trimToWidth(p[0], Math.max(10, maxW - tr.getWidth(amt) - 6));
                    ctx.drawTextWithShadow(tr, Text.literal(name), tx, ty, WHITE);
                    ctx.drawTextWithShadow(tr, Text.literal(amt), rx1 - 4 - tr.getWidth(amt), ty, GREEN);
                    ty += 11;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
