package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reichweiten-Kreise wie in der Mod "Reach Circles": ein flacher Ring am Boden um jeden Spieler (auch um dich).
 * Dein Kreis wird grün, wenn du jemanden treffen kannst; der Kreis eines Gegners wird grün, wenn er dich treffen kann.
 * Mit Ping-Ausgleich (Bewegung x Ping) und weicher Anpassung des Radius.
 */
public final class ReachCircles {
    private ReachCircles() {
    }

    private static final class Cache {
        int ping;
        Float radius;
    }

    private static final Map<UUID, Cache> CACHE = new HashMap<>();
    private static int ticks = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ReachCircles::tick);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(ReachCircles::render);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            CACHE.clear();
            return;
        }
        if (++ticks % 20 != 0 || mc.getNetworkHandler() == null) return;
        for (PlayerEntity p : mc.world.getPlayers()) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
            if (e != null) CACHE.computeIfAbsent(p.getUuid(), u -> new Cache()).ping = e.getLatency();
        }
    }

    private static Vec3d lerpPos(PlayerEntity p, float d) {
        return p.getLastRenderPos().lerp(new Vec3d(p.getX(), p.getY(), p.getZ()), d);
    }

    /** Berührt eine Kugel (Reichweite) die Hitbox des Spielers? */
    private static boolean reaches(PlayerEntity target, double sx, double sy, double sz, float r, float d) {
        Vec3d q = lerpPos(target, d);
        double cx = MathHelper.clamp(sx, q.x - 0.3, q.x + 0.3);
        double cy = MathHelper.clamp(sy, q.y, q.y + 1.8);
        double cz = MathHelper.clamp(sz, q.z - 0.3, q.z + 0.3);
        double dx = cx - sx, dy = cy - sy, dz = cz - sz;
        return dx * dx + dy * dy + dz * dz < r * r;
    }

    private static void render(WorldRenderContext ctx) {
        try {
            Config c = Config.INSTANCE;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!c.rcOn || mc.player == null || mc.world == null) return;
            float d = mc.getRenderTickCounter().getTickProgress(false);
            VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer vc = consumers.getBuffer(RenderLayers.debugQuads());
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            Vec3d me = lerpPos(mc.player, d);
            double meEye = me.y + mc.player.getEyeHeight(mc.player.getPose());
            float base = Math.max(0.5f, c.rcRadius);

            boolean meCanReach = false;
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o != mc.player && !o.isSpectator() && reaches(o, me.x, meEye, me.z, base, d)) {
                    meCanReach = true;
                    break;
                }
            }
            int myPing = 0;
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (e != null) myPing = e.getLatency();
            }
            float smooth = Math.max(0.01f, Math.min(1f, c.rcSmooth / 100f));
            int alpha = Math.max(0, Math.min(255, c.rcOpacity * 255 / 100));
            float half = c.rcThick / 200f;
            Matrix4f mat = new Matrix4f().identity();

            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p.isInvisible() || p.isSpectator()) continue;
                boolean self = p == mc.player;
                if (!self && p.squaredDistanceTo(mc.player) > (double) c.rcMaxDist * c.rcMaxDist) continue;
                Cache cache = CACHE.computeIfAbsent(p.getUuid(), u -> new Cache());
                Vec3d pos = lerpPos(p, d);
                float target = base;
                if (!self && c.rcPing) {
                    // Ping-Ausgleich: beide Bewegungen entlang der Verbindungslinie mal gemeinsamer Ping
                    double eye = pos.y + p.getEyeHeight(p.getPose());
                    double dx = me.x - pos.x, dy = meEye - eye, dz = me.z - pos.z;
                    double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (len > 1e-4) {
                        dx /= len;
                        dy /= len;
                        dz /= len;
                        Vec3d lm = mc.player.getLastRenderPos(), lo = p.getLastRenderPos();
                        double vMe = ((mc.player.getX() - lm.x) * dx + (mc.player.getY() - lm.y) * dy + (mc.player.getZ() - lm.z) * dz) * 20;
                        double vOther = ((p.getX() - lo.x) * dx + (p.getY() - lo.y) * dy + (p.getZ() - lo.z) * dz) * 20;
                        double comp = (myPing + cache.ping) / 1000.0 * (vMe + vOther);
                        target = MathHelper.clamp(base + (float) comp, 0.5f, base + 5f);
                    }
                }
                float r = smooth * target + (1 - smooth) * (cache.radius == null ? base : cache.radius);
                cache.radius = r;

                int color;
                if (self) {
                    color = meCanReach && c.rcLocalGreen ? c.rcSelfCan : c.rcSelf;
                } else {
                    boolean can = reaches(mc.player, pos.x, pos.y + p.getEyeHeight(p.getPose()), pos.z, r, d);
                    if (c.rcOtherGreen2) can = can && reaches(p, me.x, meEye, me.z, base, d);
                    color = can && c.rcOtherGreen ? c.rcOtherCan : c.rcOther;
                }
                int cr = color >> 16 & 0xFF, cg = color >> 8 & 0xFF, cb = color & 0xFF;

                int seg = Math.max(8, c.rcSegments);
                if (c.rcLod && !self) {
                    double dist = p.distanceTo(mc.player);
                    if (dist > 10) seg = Math.max(14, (int) (seg * (1 - c.rcLodStrength / 100f / 40f * (dist - 10))));
                }
                // jeder Kreis minimal anders hoch, damit sich überlappende Kreise nicht flackern
                float y = (float) (pos.y - cam.y) + 0.01f + 2.0E-5f * Math.abs(p.getUuid().hashCode() % 1000);
                float px = (float) (pos.x - cam.x), pz = (float) (pos.z - cam.z);
                for (int i = 0; i < seg; i++) {
                    double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                    float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                    float ro = r + half;
                    vc.vertex(mat, px + c1 * ro, y, pz + s1 * ro).color(cr, cg, cb, alpha).normal(0, 1, 0);
                    vc.vertex(mat, px + c2 * ro, y, pz + s2 * ro).color(cr, cg, cb, alpha).normal(0, 1, 0);
                    if (c.rcFilled) {
                        vc.vertex(mat, px, y, pz).color(cr, cg, cb, alpha).normal(0, 1, 0);
                        vc.vertex(mat, px, y, pz).color(cr, cg, cb, alpha).normal(0, 1, 0);
                    } else {
                        float ri = Math.max(0, r - half);
                        vc.vertex(mat, px + c2 * ri, y, pz + s2 * ri).color(cr, cg, cb, alpha).normal(0, 1, 0);
                        vc.vertex(mat, px + c1 * ri, y, pz + s1 * ri).color(cr, cg, cb, alpha).normal(0, 1, 0);
                    }
                }
            }
            consumers.draw();
        } catch (Throwable ignored) {
        }
    }
}
