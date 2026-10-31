package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Pop-Chams: wenn bei einem anderen Spieler ein Totem poppt, bleibt an der Stelle ein leuchtender Umriss
 * von ihm stehen, der langsam nach oben schwebt und verblasst.
 */
public final class PopChams {
    private PopChams() {
    }

    static final int[] COLORS = {0xFF4ADE80, 0xFFFFD166, 0xFFF87171, 0xFF60A5FA, 0xFFA78BFA, 0xFFFFFFFF};
    static final String[] COLOR_NAMES = {"Grün", "Gelb", "Rot", "Blau", "Lila", "Weiß"};

    private record Ghost(double x, double y, double z, float yaw, long start, boolean sneak) {
    }

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final Map<Integer, Integer> TOTEMS = new HashMap<>();
    private static final Map<Integer, Float> HEALTH = new HashMap<>();
    private static long ticks = 0;

    /** Gesamtzahl der gesehenen Pops (für die Anzeige im Menü). */
    static int seen = 0;

    private static int totems(AbstractClientPlayerEntity p) {
        int n = 0;
        if (p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) n++;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) n++;
        return n;
    }

    public static void tick(MinecraftClient mc) {
        ticks++;
        Config c = Config.INSTANCE;
        if (mc.world == null || mc.player == null) {
            GHOSTS.clear();
            TOTEMS.clear();
            HEALTH.clear();
            return;
        }
        if (!c.popChams) {
            GHOSTS.clear();
            return;
        }
        long life = Math.max(20, c.popTime * 20L);
        synchronized (GHOSTS) {
            GHOSTS.removeIf(g -> ticks - g.start > life);
        }
        Map<Integer, Integer> now = new HashMap<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player && !c.popSelf) continue;
            int id = p.getId();
            int t = totems(p);
            now.put(id, t);
            Integer before = TOTEMS.get(id);
            Float hp = HEALTH.get(id);
            // Totem weg + gerade getroffen (oder Leben plötzlich wieder hoch) = gepoppt
            boolean lost = before != null && t < before;
            boolean hurt = p.hurtTime > 0 || (hp != null && hp < 2.5f);
            if (lost && hurt && p.isAlive()) {
                synchronized (GHOSTS) {
                    GHOSTS.add(new Ghost(p.getX(), p.getY(), p.getZ(), p.getBodyYaw(), ticks, p.isSneaking()));
                    while (GHOSTS.size() > 12) GHOSTS.remove(0);
                }
                seen++;
            }
            HEALTH.put(id, p.getHealth());
        }
        TOTEMS.clear();
        TOTEMS.putAll(now);
        HEALTH.keySet().retainAll(now.keySet());
    }

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Config c = Config.INSTANCE;
            if (!c.popChams || mc.world == null || mc.player == null || mc.options.hudHidden) return;
            List<Ghost> list;
            synchronized (GHOSTS) {
                if (GHOSTS.isEmpty()) return;
                list = new ArrayList<>(GHOSTS);
            }
            float td = tc.getTickProgress(false);
            if (!Overlay3D.begin(mc, td)) return;
            int rgb = COLORS[Math.floorMod(c.popColor, COLORS.length)] & 0xFFFFFF;
            double life = Math.max(20, c.popTime * 20.0);
            for (Ghost g : list) {
                double age = (ticks - g.start) + td;
                double f = 1 - age / life;
                if (f <= 0) continue;
                double rise = c.popRise ? age / life * 1.2 : 0;
                drawBody(ctx, g, rise, rgb, (int) (0xF0 * f));
            }
        } catch (Throwable ignored) {
        }
    }

    /** Einfacher Spieler-Umriss: Kopf, Körper, Arme, Beine als Kästen aus Linien. */
    private static void drawBody(DrawContext ctx, Ghost g, double rise, int rgb, int alpha) {
        double yaw = Math.toRadians(g.yaw);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double by = g.y + rise;
        double drop = g.sneak ? 0.2 : 0;
        // {links-rechts, unten, vorne-hinten, Breite, Höhe, Tiefe} in Blöcken (Spieler-Maße / 16)
        double[][] parts = {
            {0, 1.5 - drop, 0, 0.5, 0.5, 0.5},        // Kopf
            {0, 0.75 - drop, 0, 0.5, 0.75, 0.25},      // Körper
            {-0.375, 0.75 - drop, 0, 0.25, 0.75, 0.25}, // Arm rechts
            {0.375, 0.75 - drop, 0, 0.25, 0.75, 0.25},  // Arm links
            {-0.125, 0, 0, 0.25, 0.75 - drop, 0.25},    // Bein rechts
            {0.125, 0, 0, 0.25, 0.75 - drop, 0.25}      // Bein links
        };
        for (double[] p : parts) box(ctx, g.x, by, g.z, sin, cos, p, rgb, alpha);
    }

    private static void box(DrawContext ctx, double ox, double oy, double oz, double sin, double cos, double[] p, int rgb, int alpha) {
        double[][] v = new double[8][];
        int i = 0;
        for (int yi = 0; yi < 2; yi++) {
            for (int zi = 0; zi < 2; zi++) {
                for (int xi = 0; xi < 2; xi++) {
                    double lx = p[0] + (xi == 0 ? -p[3] / 2 : p[3] / 2);
                    double ly = p[1] + (yi == 0 ? 0 : p[4]);
                    double lz = p[2] + (zi == 0 ? -p[5] / 2 : p[5] / 2);
                    // drehen nach Blickrichtung des Körpers
                    double wx = ox + lx * cos - lz * sin;
                    double wz = oz + lx * sin + lz * cos;
                    v[i++] = Overlay3D.view(wx, oy + ly, wz);
                }
            }
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) Overlay3D.line(ctx, v[e[0]], v[e[1]], rgb, alpha, 1);
    }

    static void clear() {
        synchronized (GHOSTS) {
            GHOSTS.clear();
        }
    }
}
