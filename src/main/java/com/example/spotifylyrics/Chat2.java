package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Zweiter Chat wie in der Mod "SecondChat": ein zweites, ganz normales Minecraft-Chatfenster am rechten Rand.
 * Nachrichten, die zu einer Regel passen, landen dort statt im normalen Chat.
 */
public final class Chat2 {
    private Chat2() {
    }

    /** Regel-Typen wie in SecondChat. */
    static final String[] TYPES = {"EQUALS", "EQUALS_IGNORE_CASE", "STARTS_WITH", "ENDS_WITH", "CONTAINS", "REGEX"};
    static final String[] TYPE_DE = {"Gleich", "Gleich (Groß/klein egal)", "Beginnt mit", "Endet mit", "Enthält", "Regex"};
    static final String[] TYPE_EN = {"Equals", "Equals (ignore case)", "Starts with", "Ends with", "Contains", "Regex"};

    private static ChatHud hud;

    static ChatHud hud() {
        if (hud == null) hud = new ChatHud(MinecraftClient.getInstance());
        return hud;
    }

    static String type(String rule) {
        int i = rule.indexOf('|');
        return i < 0 ? "CONTAINS" : rule.substring(0, i);
    }

    static String value(String rule) {
        int i = rule.indexOf('|');
        return i < 0 ? rule : rule.substring(i + 1);
    }

    static boolean matches(String input) {
        List<String> rules = Config.INSTANCE.chat2Rules;
        for (String r : rules) {
            String v = value(r);
            try {
                boolean m = switch (type(r)) {
                    case "EQUALS" -> input.equals(v);
                    case "EQUALS_IGNORE_CASE" -> input.equalsIgnoreCase(v);
                    case "STARTS_WITH" -> input.startsWith(v);
                    case "ENDS_WITH" -> input.endsWith(v);
                    case "REGEX" -> input.matches(v);
                    default -> input.contains(v);
                };
                if (m) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** true = Nachricht bleibt im normalen Chat. */
    private static boolean take(Text message) {
        try {
            if (!Config.INSTANCE.chat2On || message == null || !matches(message.getString())) return true;
            hud().addMessage(message);
            return false;
        } catch (Throwable e) {
            return true;
        }
    }

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || take(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> take(message));
    }

    static void clear() {
        if (hud != null) hud.clear(false);
    }

    /** Wie SecondChat: den normalen Chat noch einmal zeichnen, nur nach rechts verschoben. */
    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!Config.INSTANCE.chat2On || mc.player == null || mc.options.hudHidden || hud == null) return;
            ChatHud h = hud;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(ctx.getScaledWindowWidth() - h.getWidth(), 0f);
            h.render(ctx, mc.textRenderer, mc.inGameHud.getTicks(), 0, 0, false, mc.currentScreen instanceof ChatScreen);
            ctx.getMatrices().popMatrix();
        } catch (Throwable ignored) {
        }
    }
}
