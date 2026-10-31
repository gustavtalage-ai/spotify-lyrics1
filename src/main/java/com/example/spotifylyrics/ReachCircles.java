package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Reichweiten-Kreise um andere Spieler: zeigt am Boden, wie weit jeder Gegner schlagen kann.
 * Grün = ihr seid in Reichweite (du kannst ihn treffen und er dich), sonst weiß.
 */
public final class ReachCircles {
    private ReachCircles() {
    }

    static final String[] STYLES = {"Linie", "Leuchtend", "Dünn"};
    static final double R = 3.0;

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Config c = Config.INSTANCE;
            if (!c.rcOn || mc.world == null || mc.player == null || mc.options.hudHidden) return;
            float td = tc.getTickProgress(false);
            if (!Overlay3D.begin(mc, td)) return;
            Vec3d me = mc.player.getLerpedPos(td);
            double max = Math.max(8, Math.min(64, c.rcRange));
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == mc.player || o.isSpectator() || o.isInvisible() || !o.isAlive()) continue;
                if (!c.rcFriends && c.friends.contains(o.getName().getString())) continue;
                Vec3d q = o.getLerpedPos(td);
                double dist = q.distanceTo(me);
                if (dist > max) continue;
                // Reichweite bis zur Körpermitte: 3 Blöcke + halbe Spielerbreite
                double dx = q.x - me.x, dz = q.z - me.z;
                boolean inReach = Math.sqrt(dx * dx + dz * dz) <= R + 0.3 && Math.abs(q.y - me.y) < 2.5;
                int alpha = (int) (255 * Math.max(0.25, 1 - dist / max));
                circle(ctx, q, inReach, alpha, c.rcStyle);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void circle(DrawContext ctx, Vec3d at, boolean green, int alpha, int style) {
        int rgb = green ? 0x22FF66 : 0xFFFFFF;
        double y = at.y + 0.03;
        int n = 48;
        double[] prev = null;
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            double[] cur = Overlay3D.view(at.x + Math.cos(a) * R, y, at.z + Math.sin(a) * R);
            if (prev != null) {
                if (style == 1) Overlay3D.beam(ctx, prev, cur, green ? 0xC8FFD8 : 0xFFFFFF, rgb, 1, alpha / 255f);
                else Overlay3D.line(ctx, prev, cur, rgb, alpha, style == 2 ? 1 : 2);
            }
            prev = cur;
        }
    }
}
