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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Gegner-Rüstung: zeigt bei anderen Spielern für jedes Rüstungsteil einen Haltbarkeits-Balken
 * (grün = voll, rot = fast kaputt) – direkt auf dem Teil oder gestapelt über dem Kopf.
 */
public final class ArmorBars {
    private ArmorBars() {
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Höhe der Rüstungsteile (Anteil der Körperhöhe). */
    private static final float[] HEIGHTS = {0.86f, 0.62f, 0.38f, 0.10f};

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

            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || p.isInvisible() || p.isSpectator()) continue;
                if (p.squaredDistanceTo(mc.player) > max2) continue;
                Vec3d pos = p.getLastRenderPos().lerp(new Vec3d(p.getX(), p.getY(), p.getZ()), d);
                float px = (float) (pos.x - cam.x), py = (float) (pos.y - cam.y), pz = (float) (pos.z - cam.z);
                // ein Stück zur Kamera hin, damit der Balken nicht im Körper steckt
                float len = (float) Math.sqrt(px * px + pz * pz);
                float tx = len > 1e-3f ? -px / len : 0, tz = len > 1e-3f ? -pz / len : 0;
                float h = p.getHeight();
                int row = 0;
                for (int i = 0; i < SLOTS.length; i++) {
                    ItemStack s = p.getEquippedStack(SLOTS[i]);
                    if (s.isEmpty() || !s.isDamageable() || s.getMaxDamage() <= 0) continue;
                    float frac = 1f - s.getDamage() / (float) s.getMaxDamage();
                    frac = Math.max(0f, Math.min(1f, frac));
                    int col = s.getItemBarColor() & 0xFFFFFF;
                    float x0, x1, y, bh;
                    float fx = px, fz = pz;
                    if (c.abStyle == 1) {
                        // über dem Kopf gestapelt (unter dem Namensschild)
                        bh = 0.06f;
                        y = py + h + 0.14f + (3 - row) * 0.09f;
                        x0 = -0.4f;
                        x1 = 0.4f;
                    } else {
                        // direkt auf dem Rüstungsteil (vor dem Körper, zu dir hin)
                        bh = 0.06f;
                        y = py + h * HEIGHTS[i];
                        x0 = -0.26f;
                        x1 = 0.26f;
                        fx += tx * 0.45f;
                        fz += tz * 0.45f;
                    }
                    bar(vc, mat, fx, y, fz, rx, rz, tx, tz, x0, x1, bh, frac, col);
                    row++;
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
