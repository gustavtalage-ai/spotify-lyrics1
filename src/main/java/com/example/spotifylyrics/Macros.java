package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Makros: Folgen aus Tasten, Halten, Warten und Chat-Befehlen, mit einer Taste gestartet und gestoppt.
 * Alles läuft über die normalen Tasten des Spiels. Chat-Schritte öffnen das normale Chatfenster:
 * Ist der Chat für dich gesperrt, öffnet es sich nicht, und der Schritt wird übersprungen.
 */
final class Macros {
    private Macros() {}

    static final int T_TAP = 0, T_HOLD = 1, T_WAIT = 2, T_CHAT = 3;
    static final String[] ACTIONS = {"Linksklick", "Rechtsklick", "Springen", "Ducken", "Sprinten", "Vorwärts", "Rückwärts",
        "Links", "Rechts", "Inventar (E)", "Item fallen lassen (Q)", "Hand tauschen (F)", "Hotbar 1", "Hotbar 2", "Hotbar 3",
        "Hotbar 4", "Hotbar 5", "Hotbar 6", "Hotbar 7", "Hotbar 8", "Hotbar 9", "Escape"};
    static final int ESC = 21;
    static final int[] DURATIONS = {50, 100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 10000, 30000, 60000};
    static final int[] COUNTS = {2, 3, 4, 5, 10, 20, 30, 50, 100, 200, 500};
    static final String[] MODES = {"Einmal", "Anzahl", "Endlos bis Taste"};

    // ---------- Laufzeit ----------
    static volatile int running = -1;
    private static int step, loopNo, waitTicks, holdTicks, tapTicks, chatPhase, chatTicks;
    private static KeyBinding heldKb, tapKb;
    private static String chatText = "";
    private static String notice = "";
    private static long noticeUntil = 0;
    private static final boolean[] WAS_DOWN = new boolean[400];

    static String durText(int ms) {
        if (ms < 1000) return ms + " ms";
        if (ms % 1000 == 0) return (ms / 1000) + " s";
        return String.format(Locale.ROOT, "%.1f s", ms / 1000f);
    }

    static String keyName(int code) {
        if (code <= 0) return "keine";
        switch (code) {
            case 32: return "Leertaste";
            case 257: return "Enter";
            case 258: return "Tab";
            case 259: return "Rück";
            case 262: return "Pfeil →";
            case 263: return "Pfeil ←";
            case 264: return "Pfeil ↓";
            case 265: return "Pfeil ↑";
            case 340: case 344: return "Shift";
            case 341: case 345: return "Strg";
            case 342: case 346: return "Alt";
            default:
        }
        if (code >= 290 && code <= 301) return "F" + (code - 289);
        if (code >= 320 && code <= 329) return "Num " + (code - 320);
        try {
            String n = GLFW.glfwGetKeyName(code, 0);
            if (n != null && !n.isEmpty()) return n.toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
        }
        return "Taste " + code;
    }

    static String describe(Config.MacroStep s) {
        switch (s.type) {
            case T_TAP: return "Taste: " + ACTIONS[Math.floorMod(s.arg, ACTIONS.length)];
            case T_HOLD: return "Halten: " + ACTIONS[Math.floorMod(s.arg, ACTIONS.length)] + " " + durText(s.arg2);
            case T_WAIT: return "Warten " + durText(s.arg);
            default: return "Chat: " + s.text;
        }
    }

    private static KeyBinding binding(MinecraftClient mc, int idx) {
        switch (idx) {
            case 0: return mc.options.attackKey;
            case 1: return mc.options.useKey;
            case 2: return mc.options.jumpKey;
            case 3: return mc.options.sneakKey;
            case 4: return mc.options.sprintKey;
            case 5: return mc.options.forwardKey;
            case 6: return mc.options.backKey;
            case 7: return mc.options.leftKey;
            case 8: return mc.options.rightKey;
            case 9: return mc.options.inventoryKey;
            case 10: return mc.options.dropKey;
            case 11: return mc.options.swapHandsKey;
            default:
                if (idx >= 12 && idx <= 20) return mc.options.hotbarKeys[idx - 12];
                return null;
        }
    }

    /** Aktionen, bei denen die Taste wirklich gedrückt sein muss (nicht nur "angetippt"). */
    private static boolean needsPress(int idx) {
        return idx >= 2 && idx <= 8;
    }

    // ---------- Steuerung ----------
    static boolean isRunning(int i) {
        return running == i;
    }

    static void start(int i) {
        List<Config.Macro> ms = Config.INSTANCE.macros;
        if (i < 0 || i >= ms.size() || ms.get(i).steps.isEmpty()) return;
        stop();
        running = i;
        step = 0;
        loopNo = 0;
        waitTicks = 0;
        chatPhase = 0;
    }

    static void stop() {
        running = -1;
        waitTicks = 0;
        chatPhase = 0;
        holdTicks = 0;
        try {
            if (heldKb != null) heldKb.setPressed(false);
            if (tapKb != null) tapKb.setPressed(false);
        } catch (Throwable ignored) {
        }
        heldKb = null;
        tapKb = null;
        tapTicks = 0;
    }

    static void toggle(int i) {
        if (running == i) stop();
        else start(i);
    }

    private static void note(String s) {
        notice = s;
        noticeUntil = System.currentTimeMillis() + 4000;
    }

    // ---------- Tick ----------
    static void tick(MinecraftClient mc) {
        long win = GLFW.glfwGetCurrentContext();
        if (mc.player == null || mc.world == null) {
            if (running >= 0) stop();
            return;
        }
        List<Config.Macro> ms = Config.INSTANCE.macros;
        // Starttasten (nur im Spiel, nicht in Menüs)
        if (win != 0) {
            for (int i = 0; i < ms.size(); i++) {
                int k = ms.get(i).key;
                if (k <= 0 || k >= WAS_DOWN.length) continue;
                boolean down = GLFW.glfwGetKey(win, k) == GLFW.GLFW_PRESS;
                if (down && !WAS_DOWN[k] && mc.currentScreen == null) toggle(i);
                WAS_DOWN[k] = down;
            }
        }
        if (tapTicks > 0 && --tapTicks == 0 && tapKb != null) {
            tapKb.setPressed(false);
            tapKb = null;
        }
        if (running < 0) return;
        if (running >= ms.size()) {
            stop();
            return;
        }
        Config.Macro m = ms.get(running);
        if (m.steps.isEmpty()) {
            stop();
            return;
        }
        if (holdTicks > 0) {
            if (--holdTicks > 0) return;
            if (heldKb != null) heldKb.setPressed(false);
            heldKb = null;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        if (chatPhase > 0) {
            chatTick(mc);
            return;
        }
        if (step >= m.steps.size()) {
            loopNo++;
            step = 0;
            if (m.mode == 0 || (m.mode == 1 && loopNo >= m.count)) {
                stop();
                return;
            }
            waitTicks = 2;
            return;
        }
        Config.MacroStep s = m.steps.get(step);
        switch (s.type) {
            case T_TAP:
                tap(mc, s.arg);
                step++;
                waitTicks = 1;
                break;
            case T_HOLD: {
                KeyBinding kb = binding(mc, s.arg);
                if (kb != null) {
                    kb.setPressed(true);
                    heldKb = kb;
                    holdTicks = Math.max(1, Math.round(s.arg2 / 50f));
                }
                step++;
                break;
            }
            case T_WAIT:
                waitTicks = Math.max(1, Math.round(s.arg / 50f));
                step++;
                break;
            default:
                chatText = s.text == null ? "" : s.text;
                chatPhase = 1;
                chatTicks = 0;
                if (mc.currentScreen == null) {
                    KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(mc.options.chatKey.getBoundKeyTranslationKey()));
                }
                break;
        }
    }

    private static void tap(MinecraftClient mc, int idx) {
        if (idx == ESC) {
            if (mc.currentScreen != null) mc.currentScreen.close();
            else mc.setScreen(new GameMenuScreen(true));
            return;
        }
        KeyBinding kb = binding(mc, idx);
        if (kb == null) return;
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
        if (needsPress(idx)) {
            kb.setPressed(true);
            tapKb = kb;
            tapTicks = 2;
        }
    }

    /** Chat-Schritt: wartet, bis das normale Chatfenster offen ist, schreibt den Text und schickt ihn ab. */
    private static void chatTick(MinecraftClient mc) {
        chatTicks++;
        if (mc.currentScreen instanceof ChatScreen cs) {
            TextFieldWidget tf = null;
            for (Element e : cs.children()) {
                if (e instanceof TextFieldWidget f) {
                    tf = f;
                    break;
                }
            }
            if (tf != null) {
                tf.setText(chatText);
                try {
                    cs.sendMessage(tf.getText(), true);
                } catch (Throwable t) {
                    SpotifyLyricsClient.LOGGER.debug("Makro-Chat-Fehler", t);
                }
                mc.setScreen(null);
            }
            chatPhase = 0;
            step++;
            waitTicks = 3;
        } else if (chatTicks > 12) {
            chatPhase = 0;
            step++;
            note("Chat lässt sich nicht öffnen, Schritt übersprungen");
        }
    }

    // ---------- Anzeige ----------
    static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            TextRenderer tr = mc.textRenderer;
            List<Config.Macro> ms = Config.INSTANCE.macros;
            int y = 22;
            if (running >= 0 && running < ms.size()) {
                Config.Macro m = ms.get(running);
                String rep = m.mode == 0 ? "" : m.mode == 1 ? " · " + (loopNo + 1) + "/" + m.count : " · endlos";
                String s = "Makro " + m.name + rep + " · Stopp: " + keyName(m.key);
                int w = tr.getWidth(s) + 12;
                int x = (ctx.getScaledWindowWidth() - w) / 2;
                Ui.hudBox(ctx, x, y, x + w, y + 14, Ui.ACCENT);
                ctx.drawTextWithShadow(tr, s, x + 6, y + 3, 0xFF7DD3FC);
                y += 17;
            }
            if (!notice.isEmpty() && System.currentTimeMillis() < noticeUntil) {
                int w = tr.getWidth(notice) + 12;
                int x = (ctx.getScaledWindowWidth() - w) / 2;
                Ui.hudBox(ctx, x, y, x + w, y + 14, 0xFFFBBF24);
                ctx.drawTextWithShadow(tr, notice, x + 6, y + 3, 0xFFFBBF24);
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Makro-HUD-Fehler", e);
        }
    }
}
