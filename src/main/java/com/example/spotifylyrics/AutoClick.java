package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Auto-Klick: hält die Angriffs- oder Benutzen-Taste gedrückt, oder klickt im festen Abstand.
 * Läuft nur, solange kein Menü offen ist. Start/Stopp mit der Taste aus der Config (Standard J) oder im Menü.
 */
final class AutoClick {
    private AutoClick() {}

    static final int[] STEPS = {100, 200, 300, 500, 800, 1000, 1500, 2000, 3000, 4000, 5000, 10000, 15000, 30000, 60000};

    static volatile boolean active = false;
    private static boolean held = false;
    private static boolean keyWasDown = false;
    private static int counter = 0;

    static String keyName() {
        try {
            String n = GLFW.glfwGetKeyName(Config.INSTANCE.autoKey, 0);
            if (n != null && !n.isEmpty()) return n.toUpperCase(Locale.ROOT);
        } catch (Throwable ignored) {
        }
        return Config.INSTANCE.autoKey == 74 ? "J" : "Taste " + Config.INSTANCE.autoKey;
    }

    static String intervalText(int ms) {
        if (ms < 1000) return ms + " ms";
        if (ms % 1000 == 0) return (ms / 1000) + " s";
        return String.format(Locale.ROOT, "%.1f s", ms / 1000f);
    }

    static int stepIndex(int ms) {
        int best = 0;
        for (int i = 0; i < STEPS.length; i++) {
            if (Math.abs(STEPS[i] - ms) < Math.abs(STEPS[best] - ms)) best = i;
        }
        return best;
    }

    static String describe() {
        Config c = Config.INSTANCE;
        String what = c.autoButton == 0 ? "Angriff" : "Benutzen";
        return c.autoMode == 1 ? what + " gedrückt halten" : what + " alle " + intervalText(c.autoIntervalMs);
    }

    static void setActive(boolean on) {
        active = on;
        counter = 0;
        if (!on) release(MinecraftClient.getInstance());
    }

    private static void release(MinecraftClient mc) {
        if (!held) return;
        held = false;
        try {
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        } catch (Throwable ignored) {
        }
    }

    private static void click(KeyBinding kb) {
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
    }

    static void tick(MinecraftClient mc) {
        Config c = Config.INSTANCE;
        long win = GLFW.glfwGetCurrentContext();
        if (win != 0) {
            boolean down = GLFW.glfwGetKey(win, c.autoKey) == GLFW.GLFW_PRESS;
            if (down && !keyWasDown && mc.currentScreen == null && mc.player != null) setActive(!active);
            keyWasDown = down;
        }
        if (mc.player == null || mc.world == null) {
            active = false;
            held = false;
            return;
        }
        if (!active || mc.currentScreen != null) {
            release(mc);
            return;
        }
        KeyBinding kb = c.autoButton == 0 ? mc.options.attackKey : mc.options.useKey;
        if (c.autoMode == 1) {
            kb.setPressed(true);
            held = true;
        } else {
            release(mc);
            int need = Math.max(1, Math.round(c.autoIntervalMs / 50f));
            counter++;
            if (counter >= need) {
                counter = 0;
                click(kb);
            }
        }
    }

    static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!active || mc.player == null || mc.options.hudHidden) return;
            TextRenderer tr = mc.textRenderer;
            String s = "Auto-Klick AN · " + describe() + " · Stopp: " + keyName();
            int w = tr.getWidth(s) + 12;
            int x = (ctx.getScaledWindowWidth() - w) / 2;
            Ui.round(ctx, x, 4, x + w, 18, 0xB0102014);
            ctx.drawTextWithShadow(tr, s, x + 6, 7, 0xFF7DF29E);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("Auto-Klick-HUD-Fehler", e);
        }
    }
}
