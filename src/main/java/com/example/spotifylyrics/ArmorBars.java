package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Gegner-Rüstung: zeigt bei anderen Spielern für jedes Rüstungsteil einen Haltbarkeits-Balken
 * (grün = voll, rot = fast kaputt) – als Glanz wie bei Verzauberungen oder als Balken über dem Kopf.
 */
public final class ArmorBars {
    private ArmorBars() {
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Kästen der Rüstungsteile: Mitte-Höhe, halbe Breite, halbe Höhe, halbe Tiefe (Blöcke, stehend). */
    private static final float[][] BOXES = {
        {1.75f, 0.25f, 0.25f, 0.25f},   // Helm (Kopf)
        {1.125f, 0.5f, 0.375f, 0.125f}, // Brust (Körper + Arme)
        {0.55f, 0.25f, 0.22f, 0.125f},  // Hose
        {0.17f, 0.25f, 0.17f, 0.125f}   // Schuhe
    };
    private static final int SUB = 4;

    /** Leuchtende Hülle um ein Rüstungsteil mit wandernden Glanz-Streifen. */
    private static void shell(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float yaw, float hw, float hh, float hd,
                              int col, float time, float ox, float oy, float oz) {
        // Achsen des Körpers: rechts, vorne
        float rX = (float) -Math.cos(yaw), rZ = (float) -Math.sin(yaw);
        float fX = (float) -Math.sin(yaw), fZ = (float) Math.cos(yaw);
        float[] R = {rX * hw, 0, rZ * hw}, U = {0, hh, 0}, F = {fX * hd, 0, fZ * hd};
        float[] C = {cx, cy, cz};
        face(vc, m, add(C, F, 1), R, U, col, time, ox, oy, oz);
        face(vc, m, add(C, F, -1), R, U, col, time, ox, oy, oz);
        face(vc, m, add(C, R, 1), F, U, col, time, ox, oy, oz);
        face(vc, m, add(C, R, -1), F, U, col, time, ox, oy, oz);
        face(vc, m, add(C, U, 1), R, F, col, time, ox, oy, oz);
        face(vc, m, add(C, U, -1), R, F, col, time, ox, oy, oz);
    }

    private static float[] add(float[] a, float[] b, float k) {
        return new float[] {a[0] + b[0] * k, a[1] + b[1] * k, a[2] + b[2] * k};
    }

    private static void face(VertexConsumer vc, Matrix4f m, float[] p, float[] a, float[] b, int col, float time, float ox, float oy, float oz) {
        int r = col >> 16 & 0xFF, g = col >> 8 & 0xFF, bl = col & 0xFF;
        for (int i = 0; i < SUB; i++) {
            for (int j = 0; j < SUB; j++) {
                float u0 = -1 + 2f * i / SUB, u1 = -1 + 2f * (i + 1) / SUB, v0 = -1 + 2f * j / SUB, v1 = -1 + 2f * (j + 1) / SUB;
                float[][] q = {pt(p, a, b, u0, v0), pt(p, a, b, u1, v0), pt(p, a, b, u1, v1), pt(p, a, b, u0, v1)};
                int[] al = new int[4];
                for (int k = 0; k < 4; k++) al[k] = glint(q[k][0] - ox, q[k][1] - oy, q[k][2] - oz, time);
                for (int k = 0; k < 4; k++) vc.vertex(m, q[k][0], q[k][1], q[k][2]).color(r, g, bl, al[k]).normal(0, 1, 0);
                for (int k = 3; k >= 0; k--) vc.vertex(m, q[k][0], q[k][1], q[k][2]).color(r, g, bl, al[k]).normal(0, 1, 0);
            }
        }
    }

    private static float[] pt(float[] p, float[] a, float[] b, float u, float v) {
        return new float[] {p[0] + a[0] * u + b[0] * v, p[1] + a[1] * u + b[1] * v, p[2] + a[2] * u + b[2] * v};
    }

    /** Durchsichtigkeit: schwacher Grundschimmer plus schräge Streifen, die langsam über die Rüstung wandern. */
    private static int glint(float x, float y, float z, float t) {
        double ph = (x + y * 1.4 + z) * 3.2 - t * 3.0;
        double s = Math.max(0, Math.sin(ph));
        return (int) (38 + 120 * s * s * s * s);
    }

    public static void register() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ArmorBars::render);
    }

    private static void render(WorldRenderContext ctx) {
        try {
            Config c = Config.INSTANCE;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!c.armorBars || mc.player == null || mc.world == null) return;
            if (c.abWeapon && HitAssist.kindOf(mc.player.getMainHandStack()) < 0) return;
            float d = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            double yaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
            // Rechts-Vektor der Kamera (waagerecht), damit die Balken immer zu dir zeigen
            float rx = (float) -Math.cos(yaw), rz = (float) -Math.sin(yaw);
            VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer vc = consumers.getBuffer(RenderLayers.debugQuads());
            Matrix4f mat = new Matrix4f().identity();
            double max2 = (double) c.abDist * c.abDist;
            boolean any = false;

            float time = (System.currentTimeMillis() % 100000L) / 1000f;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || p.isInvisible() || p.isSpectator()) continue;
                if (p.squaredDistanceTo(mc.player) > max2) continue;
                Vec3d pos = p.getLastRenderPos().lerp(new Vec3d(p.getX(), p.getY(), p.getZ()), d);
                float px = (float) (pos.x - cam.x), py = (float) (pos.y - cam.y), pz = (float) (pos.z - cam.z);
                float h = p.getHeight();
                if (c.abStyle == 1) {
                    // Balken über dem Kopf gestapelt
                    float len = (float) Math.sqrt(px * px + pz * pz);
                    float tx = len > 1e-3f ? -px / len : 0, tz = len > 1e-3f ? -pz / len : 0;
                    int row = 0;
                    for (int i = 0; i < SLOTS.length; i++) {
                        ItemStack s = p.getEquippedStack(SLOTS[i]);
                        if (s.isEmpty() || !s.isDamageable() || s.getMaxDamage() <= 0) continue;
                        float frac = Math.max(0f, Math.min(1f, 1f - s.getDamage() / (float) s.getMaxDamage()));
                        float y = py + h + 0.14f + (3 - row) * 0.09f;
                        bar(vc, mat, px, y, pz, rx, rz, tx, tz, -0.4f, 0.4f, 0.06f, frac, s.getItemBarColor() & 0xFFFFFF);
                        row++;
                        any = true;
                    }
                    continue;
                }
                // Glanz wie bei Verzauberungen, aber in der Farbe der Haltbarkeit (grün = voll, rot = kaputt)
                float sc = h / 1.8f;
                float by = (float) Math.toRadians(MathHelper.lerpAngleDegrees(d, p.lastBodyYaw, p.bodyYaw));
                float hy = (float) Math.toRadians(MathHelper.lerpAngleDegrees(d, p.lastHeadYaw, p.headYaw));
                for (int i = 0; i < SLOTS.length; i++) {
                    ItemStack s = p.getEquippedStack(SLOTS[i]);
                    if (s.isEmpty() || !s.isDamageable() || s.getMaxDamage() <= 0) continue;
                    int col = s.getItemBarColor() & 0xFFFFFF;
                    float e = 0.07f;
                    float pyaw = i == 0 ? hy : by;
                    float[] b = BOXES[i];
                    shell(vc, mat, px, py + b[0] * sc, pz, pyaw, b[1] + e, b[2] * sc + e, b[3] + e, col, time, px, py, pz);
                    any = true;
                }
            }
            if (any) consumers.draw();
        } catch (Throwable ignored) {
        }
    }

    /** Ein Balken: dunkler Hintergrund, darauf die Füllung in der Haltbarkeits-Farbe. */
    private static void bar(VertexConsumer vc, Matrix4f m, float cx, float y, float cz, float rx, float rz, float tx, float tz,
                            float x0, float x1, float bh, float frac, int col) {
        float pad = 0.018f;
        quad(vc, m, cx, y, cz, rx, rz, x0 - pad, x1 + pad, y - pad, y + bh + pad, 0, 0, 0, 170);
        float bx = cx + tx * 0.006f, bz = cz + tz * 0.006f;
        quad(vc, m, bx, y, bz, rx, rz, x0, x1, y, y + bh, 60, 60, 60, 200);
        if (frac > 0) {
            float fx = cx + tx * 0.012f, fz = cz + tz * 0.012f;
            quad(vc, m, fx, y, fz, rx, rz, x0, x0 + (x1 - x0) * frac, y, y + bh, col >> 16 & 0xFF, col >> 8 & 0xFF, col & 0xFF, 235);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float cx, float yIgnored, float cz, float rx, float rz,
                             float a, float b, float y0, float y1, int r, int g, int bl, int al) {
        float ax = cx + rx * a, az = cz + rz * a, bx = cx + rx * b, bz = cz + rz * b;
        // beide Seiten, damit nichts weggeschnitten wird
        vc.vertex(m, ax, y0, az).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, bx, y0, bz).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, bx, y1, bz).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, ax, y1, az).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, ax, y1, az).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, bx, y1, bz).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, bx, y0, bz).color(r, g, bl, al).normal(0, 1, 0);
        vc.vertex(m, ax, y0, az).color(r, g, bl, al).normal(0, 1, 0);
    }
}
