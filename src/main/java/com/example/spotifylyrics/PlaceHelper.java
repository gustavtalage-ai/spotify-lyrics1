package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Platzhilfe: zeichnet ab einem Startblock in einer Reihe jeden n-ten Block als leuchtenden Kasten (direkt auf den Bildschirm, ohne Partikel). */
public class PlaceHelper {
    public static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public static final String[] DIR_NAMES = {"Norden", "Osten", "Süden", "Westen", "Hoch", "Runter"};
    private static final int MARKS = 60;
    private static final double RANGE = 64;
    private static final int[][] EDGES = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7},
        {0, 2}, {1, 3}, {4, 6}, {5, 7},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    /** Wird im Menü gesetzt; der nächste Tick nimmt den anvisierten Block als Start. */
    public static volatile boolean pendingStart = false;
    private static BlockPos start = null;
    private static Direction dir = Direction.NORTH;
    private static String error = "";

    public static void clear() {
        start = null;
    }

    public static String describe() {
        if (!error.isEmpty()) return "Fehler: " + error;
        if (start == null) return "Noch kein Startpunkt";
        return "Start: " + start.getX() + " " + start.getY() + " " + start.getZ() + " Richtung " + nameOf(dir);
    }

    private static String nameOf(Direction d) {
        for (int i = 0; i < DIRS.length; i++) if (DIRS[i] == d) return DIR_NAMES[i];
        return "?";
    }

    /** Jeden Client-Tick: Startpunkt übernehmen, sobald das Menü zu ist. */
    public static void tick(MinecraftClient mc) {
        try {
            if (mc.world == null || mc.player == null) return;
            if (pendingStart && mc.currentScreen == null) {
                pendingStart = false;
                Config cfg = Config.INSTANCE;
                HitResult hr = mc.crosshairTarget;
                if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
                    start = bhr.getBlockPos();
                } else {
                    start = mc.player.getBlockPos().down();
                }
                dir = cfg.placeDir >= 0 && cfg.placeDir < DIRS.length ? DIRS[cfg.placeDir] : mc.player.getHorizontalFacing();
            }
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    private static void fail(Throwable ex) {
        if (error.isEmpty()) {
            error = String.valueOf(ex);
            SpotifyLyricsClient.LOGGER.warn("Platzhilfe-Fehler", ex);
        }
    }

    /** Zeichnet die Kästen auf den Bildschirm (HUD). */
    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Config cfg = Config.INSTANCE;
            if (!cfg.placeHelper || start == null || mc.world == null || mc.player == null || mc.options.hudHidden) return;
            float td = tc.getTickProgress(false);
            if (!Overlay3D.begin(mc, td)) return;
            if (cfg.placeDir >= 0 && cfg.placeDir < DIRS.length) dir = DIRS[cfg.placeDir];
            int step = Math.max(1, cfg.placeSpacing);
            Vec3d pp = mc.player.getLerpedPos(td);
            float pulse = (float) (0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 180.0));
            int missing = 0;
            for (int k = 0; k < MARKS; k++) {
                BlockPos p = start.offset(dir, k * step);
                double dx = p.getX() + 0.5 - pp.x, dy = p.getY() + 0.5 - pp.y, dz = p.getZ() + 0.5 - pp.z;
                if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
                if (k == 0) {
                    box(ctx, p, 0xCCFFDD, 0x33FF66, 1, 0.8f);
                    continue;
                }
                if (!mc.world.getBlockState(p).isReplaceable()) continue; // hier steht schon ein Block
                missing++;
                if (missing == 1) {
                    // hier muss der nächste Block hin: dicke, pulsierende blaue Outline + Pfeil nach oben
                    box(ctx, p, 0xE8F8FF, 0x2A8CFF, 2, pulse);
                    double[] a = Overlay3D.view(p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5);
                    double[] b = Overlay3D.view(p.getX() + 0.5, p.getY() + 4.5, p.getZ() + 0.5);
                    Overlay3D.beam(ctx, a, b, 0xE8F8FF, 0x2A8CFF, 1, pulse);
                } else {
                    box(ctx, p, 0x9CC8FF, 0x2A6BFF, 1, missing <= 6 ? 0.7f : 0.4f);
                }
            }
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    private static void box(DrawContext ctx, BlockPos p, int core, int glow, int thick, float power) {
        double[] lo = {p.getX() - 0.02, p.getY() - 0.02, p.getZ() - 0.02};
        double[] hi = {p.getX() + 1.02, p.getY() + 1.02, p.getZ() + 1.02};
        double[][] v = new double[8][];
        for (int i = 0; i < 8; i++) {
            v[i] = Overlay3D.view((i & 1) == 0 ? lo[0] : hi[0], (i & 2) == 0 ? lo[1] : hi[1], (i & 4) == 0 ? lo[2] : hi[2]);
        }
        for (int[] e : EDGES) {
            Overlay3D.beam(ctx, v[e[0]], v[e[1]], core, glow, thick, power);
        }
    }
}
