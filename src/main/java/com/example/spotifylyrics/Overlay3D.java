package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

/** Zeichnet leuchtende 3D-Linien direkt auf den Bildschirm (ohne Minecraft-Partikel). Weltpunkte werden auf den Bildschirm umgerechnet. */
final class Overlay3D {
    private static double cx, cy, cz, fx, fy, fz, rx, ry, rz, ux, uy, uz, focal, halfW, halfH, horizon;

    /** Bildschirmzeile, auf der der Horizont liegt (kann außerhalb des Bildes sein). */
    static double horizonY() {
        return horizon;
    }

    static int screenW() {
        return (int) Math.round(halfW * 2);
    }

    static int screenH() {
        return (int) Math.round(halfH * 2);
    }

    private Overlay3D() {}

    /** Kamera für diesen Frame berechnen. false = gerade nicht zeichnen (z.B. Third-Person). */
    static boolean begin(MinecraftClient mc, float tickDelta) {
        if (mc.player == null || mc.world == null) return false;
        Camera cam = mc.gameRenderer.getCamera();
        if (cam == null || cam.isThirdPerson()) return false;
        Vec3d p = mc.player.getCameraPosVec(tickDelta);
        cx = p.x;
        cy = p.y;
        cz = p.z;
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());
        fx = -Math.sin(yaw) * Math.cos(pitch);
        fy = -Math.sin(pitch);
        fz = Math.cos(yaw) * Math.cos(pitch);
        rx = -Math.cos(yaw);
        ry = 0;
        rz = -Math.sin(yaw);
        ux = ry * fz - rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double fov = mc.options.getFov().getValue();
        focal = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
        halfW = w / 2.0;
        halfH = h / 2.0;
        horizon = halfH - Math.tan(pitch) * focal;
        return true;
    }

    /** Weltpunkt in Kamera-Koordinaten: {rechts, oben, Tiefe}. */
    static double[] view(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return new double[] {dx * rx + dy * ry + dz * rz, dx * ux + dy * uy + dz * uz, dx * fx + dy * fy + dz * fz};
    }

    private static double[] lerp(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** Eine einfarbige Linie mit Dicke (in GUI-Pixeln) zwischen zwei Kamera-Punkten. */
    static void line(DrawContext c, double[] a, double[] b, int rgb, int alpha, int thick) {
        double near = 0.05;
        if (a[2] < near && b[2] < near) return;
        double[] p = a, q = b;
        if (p[2] < near) p = lerp(p, q, (near - p[2]) / (q[2] - p[2]));
        else if (q[2] < near) q = lerp(q, p, (near - q[2]) / (p[2] - q[2]));
        double x0 = halfW + p[0] / p[2] * focal, y0 = halfH - p[1] / p[2] * focal;
        double x1 = halfW + q[0] / q[2] * focal, y1 = halfH - q[1] / q[2] * focal;

        // auf den sichtbaren Bereich zuschneiden (Liang-Barsky), damit nichts riesig wird
        double minX = -60, maxX = halfW * 2 + 60, minY = -60, maxY = halfH * 2 + 60;
        double dx = x1 - x0, dy = y1 - y0;
        double t0 = 0, t1 = 1;
        double[] pp = {-dx, dx, -dy, dy};
        double[] qq = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
        for (int i = 0; i < 4; i++) {
            if (pp[i] == 0) {
                if (qq[i] < 0) return;
            } else {
                double r = qq[i] / pp[i];
                if (pp[i] < 0) {
                    if (r > t1) return;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return;
                    if (r < t1) t1 = r;
                }
            }
        }
        double sx = x0 + dx * t0, sy = y0 + dy * t0;
        double ex = x0 + dx * t1, ey = y0 + dy * t1;
        double len = Math.hypot(ex - sx, ey - sy);
        if (len < 0.5) return;
        int argb = (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
        int h = Math.max(1, thick);
        Matrix3x2fStack m = c.getMatrices();
        m.pushMatrix();
        m.translate((float) sx, (float) sy);
        m.rotate((float) Math.atan2(ey - sy, ex - sx));
        int top = -(h / 2);
        c.fill(0, top, (int) Math.ceil(len), top + h, argb);
        m.popMatrix();
    }

    /** Leuchtender Strahl: heller Kern, weicher farbiger Schein außen herum. power 0..1 = Helligkeit. */
    static void beam(DrawContext c, double[] a, double[] b, int coreRgb, int glowRgb, int thick, float power) {
        float pw = Math.max(0.05f, Math.min(1f, power));
        line(c, a, b, glowRgb, (int) (0x1A * pw), thick * 8);
        line(c, a, b, glowRgb, (int) (0x34 * pw), thick * 5);
        line(c, a, b, glowRgb, (int) (0x70 * pw), thick * 3);
        line(c, a, b, coreRgb, (int) (0xF0 * pw), thick);
    }
}
