package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Platzhilfe: markiert ab einem Startblock in einer Reihe jeden n-ten Block mit leuchtenden Punkten. */
public class PlaceHelper {
    public static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    public static final String[] DIR_NAMES = {"Norden", "Osten", "Süden", "Westen", "Hoch", "Runter"};
    private static final int MARKS = 40;
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
            if (++counter % 3 != 0) return;
            if (cfg.placeDir >= 0 && cfg.placeDir < DIRS.length) dir = DIRS[cfg.placeDir];
            int step = Math.max(1, cfg.placeSpacing);
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            for (int k = 0; k < MARKS; k++) {
                BlockPos p = start.offset(dir, k * step);
                double dx = p.getX() + 0.5 - px, dy = p.getY() + 0.5 - py, dz = p.getZ() + 0.5 - pz;
                if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) continue;
                int rgb = k == 0 ? 0x33FF66 : 0x33DDFF;
                DustParticleEffect e = new DustParticleEffect(rgb, 1.0f);
                double y = p.getY() + 1.05;
                mc.particleManager.addParticle(e, p.getX() + 0.12, y, p.getZ() + 0.12, 0, 0, 0);
                mc.particleManager.addParticle(e, p.getX() + 0.88, y, p.getZ() + 0.12, 0, 0, 0);
                mc.particleManager.addParticle(e, p.getX() + 0.12, y, p.getZ() + 0.88, 0, 0, 0);
                mc.particleManager.addParticle(e, p.getX() + 0.88, y, p.getZ() + 0.88, 0, 0, 0);
                mc.particleManager.addParticle(e, p.getX() + 0.5, y + 0.25, p.getZ() + 0.5, 0, 0, 0);
            }
        } catch (Throwable ignored) {
            // nie das Spiel stören
        }
    }
}
