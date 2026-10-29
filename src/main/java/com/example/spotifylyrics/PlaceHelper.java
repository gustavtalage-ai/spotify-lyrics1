package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Platzhilfe: markiert ab einem Startblock in einer Reihe jeden n-ten Block mit leuchtenden Punkten. */
public class PlaceHelper {
    public static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public static final String[] DIR_NAMES = {"Norden", "Osten", "Süden", "Westen", "Hoch", "Runter"};
    private static final int MARKS = 60;
    private static final double RANGE = 48;

    /** Wird im Menü gesetzt; der nächste Tick nimmt den anvisierten Block als Start. */
    public static volatile boolean pendingStart = false;
    private static BlockPos start = null;
    private static Direction dir = Direction.NORTH;
    private static int counter = 0;

    public static void clear() {
        start = null;
    }

    public static String describe() {
        if (start == null) return "Noch kein Startpunkt";
        return "Start: " + start.getX() + " " + start.getY() + " " + start.getZ() + " Richtung " + nameOf(dir);
    }

    private static String nameOf(Direction d) {
        for (int i = 0; i < DIRS.length; i++) if (DIRS[i] == d) return DIR_NAMES[i];
        return "?";
    }

    /** Zeichnet einen Kasten um den Block: Ecken, und bei "full" auch Punkte auf den Kanten. */
    private static void box(MinecraftClient mc, BlockPos p, DustParticleEffect e, boolean full, boolean sparkle) {
        double[] c = {-0.02, 1.02};
        double[] m = {0.34, 0.66};
        double bx = p.getX(), by = p.getY(), bz = p.getZ();
        for (int u = 0; u < 2; u++) {
            for (int v = 0; v < 2; v++) {
                for (int w = 0; w < 2; w++) {
                    mc.particleManager.addParticle(e, bx + c[u], by + c[v], bz + c[w], 0, 0, 0);
                    if (sparkle) mc.particleManager.addParticle(ParticleTypes.END_ROD, bx + c[u], by + c[v], bz + c[w], 0, 0, 0);
                }
                if (!full) continue;
                for (double q : m) {
                    mc.particleManager.addParticle(e, bx + q, by + c[u], bz + c[v], 0, 0, 0);
                    mc.particleManager.addParticle(e, bx + c[u], by + q, bz + c[v], 0, 0, 0);
                    mc.particleManager.addParticle(e, bx + c[u], by + c[v], bz + q, 0, 0, 0);
                }
            }
        }
    }

    public static void tick(MinecraftClient mc) {
        try {
            Config cfg = Config.INSTANCE;
            if (mc.world == null || mc.player == null) return;
            if (pendingStart && mc.currentScreen == null) {
                pendingStart = false;
                HitResult hr = mc.crosshairTarget;
                if (hr instanceof BlockHitResult bhr && hr.getType() == HitResult.Type.BLOCK) {
                    start = bhr.getBlockPos();
                } else {
                    start = mc.player.getBlockPos().down();
                }
                dir = cfg.placeDir >= 0 && cfg.placeDir < DIRS.length ? DIRS[cfg.placeDir] : mc.player.getHorizontalFacing();
            }
            if (!cfg.placeHelper || start == null || mc.isPaused()) return;
            if (++counter % 2 != 0) return;
            if (cfg.placeDir >= 0 && cfg.placeDir < DIRS.length) dir = DIRS[cfg.placeDir];
            int step = Math.max(1, cfg.placeSpacing);
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            int missing = 0;
            for (int k = 0; k < MARKS; k++) {
                BlockPos p = start.offset(dir, k * step);
                double dx = p.getX() + 0.5 - px, dy = p.getY() + 0.5 - py, dz = p.getZ() + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
                if (k == 0) {
                    box(mc, p, new DustParticleEffect(0x33FF66, 0.9f), true, false);
                    continue;
                }
                if (!mc.world.getBlockState(p).isReplaceable()) continue; // hier steht schon ein Block
                missing++;
                if (missing == 1) {
                    box(mc, p, new DustParticleEffect(0x55CCFF, 1.3f), true, true);   // hier muss der nächste Block hin
                } else {
                    box(mc, p, new DustParticleEffect(0x2A7BFF, 1.0f), missing <= 8, false);
                }
            }
        } catch (Throwable ignored) {
            // nie das Spiel stören
        }
    }
}
