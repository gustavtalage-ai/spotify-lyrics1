package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zweiter Chat: Nachrichten, die zu den Filtern passen (z. B. Privatnachrichten oder eigene Wörter),
 * landen in einem eigenen Fenster am Rand und auf Wunsch nicht mehr im normalen Chat.
 */
public final class Chat2 {
    private Chat2() {
    }

    static final String[] CORNERS = {"Oben rechts", "Mitte rechts", "Oben links", "Mitte links"};
    /** Typische Wörter von Privatnachrichten (deutsch und englisch, verschiedene Server). */
    static final String[] PM_WORDS = {"whispers to you", "flüstert dir", "-> you", "-> mir", "-> me", "[msg]", "[pm]", "flüstert:"};

    private record Line(Text text, long time) {
    }

    private static final List<Line> LINES = new ArrayList<>();
    static int caught = 0;

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || take(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> take(message));
    }

    /** true = Nachricht bleibt im normalen Chat. */
    private static boolean take(Text message) {
        try {
            Config c = Config.INSTANCE;
            if (!c.chat2On || message == null || !matches(message.getString())) return true;
            synchronized (LINES) {
                LINES.add(new Line(message, System.currentTimeMillis()));
                while (LINES.size() > 100) LINES.remove(0);
            }
            caught++;
            return !c.chat2Hide;
        } catch (Throwable e) {
            return true;
        }
    }

    static boolean matches(String s) {
        Config c = Config.INSTANCE;
        String low = s.toLowerCase(Locale.ROOT);
        if (c.chat2Pm) for (String w : PM_WORDS) if (low.contains(w)) return true;
        for (String w : c.chat2Words) if (!w.isBlank() && low.contains(w.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Config c = Config.INSTANCE;
            if (!c.chat2On || mc.player == null || mc.options.hudHidden) return;
            List<Line> copy;
            synchronized (LINES) {
                copy = new ArrayList<>(LINES);
            }
            boolean open = mc.currentScreen instanceof ChatScreen;
            long now = System.currentTimeMillis();
            TextRenderer tr = mc.textRenderer;
            int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
            int w = Math.min(200, sw / 3);
            int max = Math.max(3, Math.min(20, c.chat2Lines));
            // Zeilen von unten nach oben einsammeln (umgebrochen)
            List<OrderedText> rows = new ArrayList<>();
            List<Integer> alphas = new ArrayList<>();
            for (int i = copy.size() - 1; i >= 0 && rows.size() < max; i--) {
                Line l = copy.get(i);
                long age = now - l.time;
                if (!open && age > 12000) break;
                int a = open ? 255 : (int) Math.max(0, Math.min(255, (12000 - age) / 1000.0 * 255));
                List<OrderedText> wrapped = tr.wrapLines(l.text, w - 6);
                for (int k = wrapped.size() - 1; k >= 0 && rows.size() < max; k--) {
                    rows.add(0, wrapped.get(k));
                    alphas.add(0, a);
                }
            }
            if (rows.isEmpty() && !open) return;
            int h = Math.max(1, rows.size()) * 10 + 14;
            boolean right = c.chat2Corner == 0 || c.chat2Corner == 1;
            boolean mid = c.chat2Corner == 1 || c.chat2Corner == 3;
            int x = right ? sw - w - 4 : 4;
            int y = mid ? sh / 2 - h / 2 : 4;
            if (!right && !mid) y = 30;   // oben links nicht über der Infozeile
            int bgA = open ? 0x90 : 0x60;
            ctx.fill(x, y, x + w, y + h, bgA << 24);
            ctx.fill(x, y, x + 2, y + h, Ui.ACCENT);
            ctx.drawTextWithShadow(tr, Text.literal(Lang.t("Zweiter Chat", "Second chat")), x + 5, y + 3, Ui.ACCENT);
            if (rows.isEmpty()) {
                ctx.drawTextWithShadow(tr, Text.literal(Lang.t("(noch leer)", "(empty)")), x + 5, y + 14, 0xFF8B92A5);
            }
            for (int i = 0; i < rows.size(); i++) {
                int a = alphas.get(i);
                if (a < 8) continue;
                ctx.drawTextWithShadow(tr, rows.get(i), x + 5, y + 14 + i * 10, (a << 24) | 0xFFFFFF);
            }
        } catch (Throwable ignored) {
        }
    }
}
