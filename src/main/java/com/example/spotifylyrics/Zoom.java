package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Zoom: Taste gedrückt halten = näher ranzoomen (wie bei OptiFine), mit weicher Kamera. */
public final class Zoom {
    private Zoom() {}

    private static float cur = 1f;
    private static boolean active = false;
    private static Boolean prevSmooth = null;

    public static void tick(MinecraftClient mc) {
        Config c = Config.INSTANCE;
        boolean held = false;
        long win = GLFW.glfwGetCurrentContext();
        if (c.zoomOn && win != 0 && mc.currentScreen == null && mc.player != null && c.zoomKey > 0) {
            held = GLFW.glfwGetKey(win, c.zoomKey) == GLFW.GLFW_PRESS;
        }
        if (held == active) return;
        active = held;
        try {
            if (held && c.zoomSmooth) {
                prevSmooth = mc.options.smoothCameraEnabled;
                mc.options.smoothCameraEnabled = true;
            } else if (!held && prevSmooth != null) {
                mc.options.smoothCameraEnabled = prevSmooth;
                prevSmooth = null;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Faktor für das Sichtfeld (1 = kein Zoom). */
    public static float factor() {
        Config c = Config.INSTANCE;
        float target = active && c != null ? Math.max(1.5f, Math.min(10f, c.zoomLevel)) : 1f;
        cur += (target - cur) * 0.2f;
        if (Math.abs(cur - target) < 0.02f) cur = target;
        return cur;
    }
}
