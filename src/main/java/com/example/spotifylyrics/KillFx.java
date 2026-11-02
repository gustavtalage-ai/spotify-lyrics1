package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Kill-Animationen: wenn ein Gegner stirbt, den du gerade getroffen hast, spielt an seiner Stelle eine
 * große 3D-Animation (5 Stile) und unten in der Mitte erscheint ein Kill-Banner mit Serien-Zähler (1 bis 5).
 */
public final class KillFx {
    private KillFx() {
    }

    public static final String[] STYLES = {"Seelen-Strahl", "Kristall-Explosion", "Wirbelsturm", "Glitch-Würfel", "Lichtkrone"};
    public static final String[] COLOR_NAMES = {"Rot", "Türkis", "Lila", "Gold", "Grün", "Regenbogen"};
    public static final int[] COLORS = {0xFF4655, 0x2EE6E6, 0xB14BFF, 0xFFC23D, 0x47F58A, 0};

    private static final class Fx {
        int style;
        double x, y, z;
        float h;
        long start;
        long seed;
        int color;
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final long DURATION = 2400;

    private static int targetId = -1;
    private static long targetMs;
    private static boolean fired;
    private static int streak;
    private static long lastKill;
    private static long bannerStart;
    private static String bannerName = "";

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            try {
                if (world.isClient() && player == MinecraftClient.getInstance().player && entity instanceof LivingEntity) {
                    if (entity.getId() != targetId) fired = false;
                    targetId = entity.getId();
                    targetMs = System.currentTimeMillis();
                }
            } catch (Throwable ignored) {
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(KillFx::tick);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(KillFx::render);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            ACTIVE.clear();
            targetId = -1;
            return;
        }
        Config c = Config.INSTANCE;
        long now = System.currentTimeMillis();
        if (now - lastKill > 30000) streak = 0;
        if (!c.kfOn || targetId < 0 || fired || now - targetMs > 4000) return;
        Entity e = mc.world.getEntityById(targetId);
        if (!(e instanceof LivingEntity le)) return;
        if (!(le instanceof PlayerEntity) && !c.kfMobs) return;
        if (le.isDead() || le.getHealth() <= 0f) {
            fired = true;
            trigger(le);
        }
    }

    /** Animation an der Stelle des Gegners starten (auch für die Vorschau). */
    static void trigger(LivingEntity le) {
        Config c = Config.INSTANCE;
        Fx f = new Fx();
        f.style = c.kfStyle >= STYLES.length ? new Random().nextInt(STYLES.length) : Math.max(0, c.kfStyle);
        f.x = le.getX();
        f.y = le.getY();
        f.z = le.getZ();
        f.h = Math.max(1f, le.getHeight());
        f.start = System.currentTimeMillis();
        f.seed = f.start ^ le.getId();
        f.color = COLORS[Math.floorMod(c.kfColor, COLORS.length)];
        if (ACTIVE.size() > 6) ACTIVE.remove(0);
        ACTIVE.add(f);
        streak = Math.min(5, streak + 1);
        lastKill = f.start;
        bannerStart = f.start;
        bannerName = le.getName().getString();
        if (c.kfSound) sounds(f.style);
    }

    /** Vorschau: Animation 3 Blöcke vor dir. */
    static void preview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d look = mc.player.getRotationVec(1f);
        Config c = Config.INSTANCE;
        Fx f = new Fx();
        f.style = c.kfStyle >= STYLES.length ? new Random().nextInt(STYLES.length) : Math.max(0, c.kfStyle);
        f.x = mc.player.getX() + look.x * 4;
        f.y = mc.player.getY();
        f.z = mc.player.getZ() + look.z * 4;
        f.h = 1.8f;
        f.start = System.currentTimeMillis();
        f.seed = f.start;
        f.color = COLORS[Math.floorMod(c.kfColor, COLORS.length)];
        ACTIVE.add(f);
        streak = Math.min(5, streak + 1);
        lastKill = f.start;
        bannerStart = f.start;
        bannerName = Lang.t("Vorschau", "Preview");
        if (c.kfSound) sounds(f.style);
    }

    private static void play(Object sound, float pitch, float vol) {
        try {
            SoundEvent se = sound instanceof SoundEvent s ? s : sound instanceof RegistryEntry<?> r && r.value() instanceof SoundEvent s2 ? s2 : null;
            if (se != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(se, pitch, vol));
        } catch (Throwable ignored) {
        }
    }

    private static void sounds(int style) {
        Object a = SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST;
        switch (style) {
            case 0 -> {
                play(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.6f, 0.9f);
                play(a, 0.8f, 0.8f);
            }
            case 1 -> {
                play(SoundEvents.BLOCK_GLASS_BREAK, 0.7f, 1f);
                play(SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1f);
            }
            case 2 -> {
                play(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.3f, 0.9f);
                play(a, 1.2f, 0.6f);
            }
            case 3 -> {
                play(SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, 0.6f, 1f);
                play(SoundEvents.ENTITY_SHULKER_BULLET_HIT, 1.5f, 0.9f);
            }
            default -> {
                play(SoundEvents.BLOCK_END_PORTAL_SPAWN, 1.8f, 0.5f);
                play(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.4f, 0.8f);
            }
        }
    }

    // ---------- 3D ----------

    private static VertexConsumer vc;
    private static final Matrix4f M = new Matrix4f();

    private static void render(WorldRenderContext ctx) {
        if (ACTIVE.isEmpty()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
            vc = consumers.getBuffer(RenderLayers.debugQuads());
            M.identity();
            long now = System.currentTimeMillis();
            Iterator<Fx> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Fx f = it.next();
                float t = (now - f.start) / (float) DURATION;
                if (t >= 1f) {
                    it.remove();
                    continue;
                }
                float ox = (float) (f.x - cam.x), oy = (float) (f.y - cam.y), oz = (float) (f.z - cam.z);
                switch (f.style) {
                    case 0 -> beamFx(f, t, ox, oy, oz);
                    case 1 -> shatterFx(f, t, ox, oy, oz);
                    case 2 -> vortexFx(f, t, ox, oy, oz);
                    case 3 -> glitchFx(f, t, ox, oy, oz);
                    default -> crownFx(f, t, ox, oy, oz);
                }
            }
            consumers.draw();
        } catch (Throwable ignored) {
        } finally {
            vc = null;
        }
    }

    /** Farbe des Effekts; bei Regenbogen je nach Wert k. */
    private static int col(Fx f, float k) {
        if (f.color != 0) return f.color;
        return Ui.rainbowAt(k) & 0xFFFFFF;
    }

    private static float easeOut(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float fade(float t, float in, float out) {
        float a = t < in ? t / in : 1f;
        if (t > 1 - out) a *= (1 - t) / out;
        return Math.max(0, Math.min(1, a));
    }

    private static void v(float x, float y, float z, int rgb, float a) {
        vc.vertex(M, x, y, z).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (Math.max(0, Math.min(1, a)) * 255)).normal(0, 1, 0);
    }

    /** Viereck (beide Seiten). */
    private static void quad(float[] a, float[] b, float[] c, float[] d, int rgb, float al) {
        v(a[0], a[1], a[2], rgb, al);
        v(b[0], b[1], b[2], rgb, al);
        v(c[0], c[1], c[2], rgb, al);
        v(d[0], d[1], d[2], rgb, al);
        v(d[0], d[1], d[2], rgb, al);
        v(c[0], c[1], c[2], rgb, al);
        v(b[0], b[1], b[2], rgb, al);
        v(a[0], a[1], a[2], rgb, al);
    }

    /** Leuchtender Strich von a nach b, immer zur Kamera gedreht. */
    private static void line(float ax, float ay, float az, float bx, float by, float bz, float w, int rgb, float al) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float mx = (ax + bx) / 2, my = (ay + by) / 2, mz = (az + bz) / 2;
        // Seite = Richtung x Blickrichtung
        float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-5f) return;
        float k = w / 2 / l;
        sx *= k;
        sy *= k;
        sz *= k;
        quad(new float[] {ax - sx, ay - sy, az - sz}, new float[] {ax + sx, ay + sy, az + sz},
            new float[] {bx + sx, by + sy, bz + sz}, new float[] {bx - sx, by - sy, bz - sz}, rgb, al);
    }

    /** Würfel mit Drehung um Y und X. */
    private static void cube(float cx, float cy, float cz, float s, float ry, float rx, int rgb, float al) {
        float[][] p = new float[8][];
        float cY = (float) Math.cos(ry), sY = (float) Math.sin(ry), cX = (float) Math.cos(rx), sX = (float) Math.sin(rx);
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? -s : s, y = (i & 2) == 0 ? -s : s, z = (i & 4) == 0 ? -s : s;
            float y2 = y * cX - z * sX, z2 = y * sX + z * cX;
            float x3 = x * cY + z2 * sY, z3 = -x * sY + z2 * cY;
            p[i] = new float[] {cx + x3, cy + y2, cz + z3};
        }
        int dark = mixRgb(rgb, 0x000000, 0.35f), light = mixRgb(rgb, 0xFFFFFF, 0.35f);
        quad(p[0], p[1], p[3], p[2], dark, al);
        quad(p[4], p[5], p[7], p[6], rgb, al);
        quad(p[0], p[1], p[5], p[4], dark, al);
        quad(p[2], p[3], p[7], p[6], light, al);
        quad(p[0], p[2], p[6], p[4], rgb, al);
        quad(p[1], p[3], p[7], p[5], rgb, al);
    }

    /** Waagerechter Ring. */
    private static void ring(float cx, float cy, float cz, float r, float th, int seg, int rgb, float al) {
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1), c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
            float ro = r + th / 2, ri = Math.max(0, r - th / 2);
            quad(new float[] {cx + c1 * ri, cy, cz + s1 * ri}, new float[] {cx + c1 * ro, cy, cz + s1 * ro},
                new float[] {cx + c2 * ro, cy, cz + s2 * ro}, new float[] {cx + c2 * ri, cy, cz + s2 * ri}, rgb, al);
        }
    }

    /** Senkrechte Säule (6 Seiten). */
    private static void pillar(float cx, float y0, float y1, float cz, float r, int rgb, float al, float alTop) {
        for (int i = 0; i < 6; i++) {
            double a1 = Math.PI * 2 * i / 6, a2 = Math.PI * 2 * (i + 1) / 6;
            float x1 = cx + (float) Math.cos(a1) * r, z1 = cz + (float) Math.sin(a1) * r;
            float x2 = cx + (float) Math.cos(a2) * r, z2 = cz + (float) Math.sin(a2) * r;
            vc.vertex(M, x1, y0, z1).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (al * 255)).normal(0, 1, 0);
            vc.vertex(M, x2, y0, z2).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (al * 255)).normal(0, 1, 0);
            vc.vertex(M, x2, y1, z2).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (alTop * 255)).normal(0, 1, 0);
            vc.vertex(M, x1, y1, z1).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (alTop * 255)).normal(0, 1, 0);
            vc.vertex(M, x1, y1, z1).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (alTop * 255)).normal(0, 1, 0);
            vc.vertex(M, x2, y1, z2).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (alTop * 255)).normal(0, 1, 0);
            vc.vertex(M, x2, y0, z2).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (al * 255)).normal(0, 1, 0);
            vc.vertex(M, x1, y0, z1).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, (int) (al * 255)).normal(0, 1, 0);
        }
    }

    private static int mixRgb(int a, int b, float t) {
        int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }

    // ---------- 1: Seelen-Strahl ----------
    private static void beamFx(Fx f, float t, float x, float y, float z) {
        int c = col(f, t);
        float a = fade(t, 0.05f, 0.35f);
        // Lichtsäule nach oben, wird dünner
        float w = 0.55f * (1 - easeOut(t)) + 0.05f;
        float top = y + 2 + 14 * easeOut(t * 1.4f);
        pillar(x, y, top, z, w, c, a * 0.75f, 0f);
        pillar(x, y, top, z, w * 0.4f, 0xFFFFFF, a * 0.9f, 0f);
        // Schockwellen am Boden
        for (int k = 0; k < 3; k++) {
            float tk = t * 1.3f - k * 0.12f;
            if (tk <= 0 || tk >= 1) continue;
            ring(x, y + 0.03f + k * 0.01f, z, 0.4f + 5.5f * easeOut(tk), 0.35f * (1 - tk), 48, k == 0 ? 0xFFFFFF : c, (1 - tk) * 0.8f);
        }
        // Splitter, die spiralförmig aufsteigen
        Random r = new Random(f.seed);
        for (int i = 0; i < 16; i++) {
            float ph = r.nextFloat() * 6.28f, sp = 0.6f + r.nextFloat();
            float ang = ph + t * 6.28f * sp;
            float rad = 0.5f + easeOut(t) * (1.2f + r.nextFloat());
            float yy = y + 0.3f + t * (2.5f + 3 * r.nextFloat()) * f.h / 1.8f;
            cube(x + (float) Math.cos(ang) * rad, yy, z + (float) Math.sin(ang) * rad, 0.07f + 0.06f * r.nextFloat(),
                t * 9 + ph, t * 7, i % 3 == 0 ? 0xFFFFFF : col(f, (i / 16f + t) % 1f), a);
        }
    }

    // ---------- 2: Kristall-Explosion ----------
    private static void shatterFx(Fx f, float t, float x, float y, float z) {
        int c = col(f, t);
        float a = fade(t, 0.01f, 0.4f);
        float cy = y + f.h * 0.55f;
        // heller Blitz in der Mitte
        if (t < 0.3f) {
            float k = t / 0.3f;
            cube(x, cy, z, 0.2f + 1.1f * easeOut(k), k * 2, k * 1.5f, 0xFFFFFF, (1 - k) * 0.7f);
            cube(x, cy, z, 0.35f + 1.6f * easeOut(k), -k * 2, k, c, (1 - k) * 0.45f);
        }
        ring(x, y + 0.03f, z, 0.3f + 4f * easeOut(t * 1.6f), 0.25f, 40, c, Math.max(0, 1 - t * 1.6f));
        Random r = new Random(f.seed);
        for (int i = 0; i < 46; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float out = 2.5f + r.nextFloat() * 4.5f, up = 3 + r.nextFloat() * 6;
            float vx = (float) Math.cos(ang) * out, vz = (float) Math.sin(ang) * out;
            float tt = t * 1.6f;
            float px = x + vx * tt * 0.6f, pz = z + vz * tt * 0.6f;
            float py = cy + up * tt * 0.6f - 4.9f * tt * tt * 0.6f;
            py = Math.max(y + 0.05f, py);
            float s = (0.05f + 0.12f * r.nextFloat()) * (1 - t * 0.5f);
            int cc = i % 4 == 0 ? 0xFFFFFF : col(f, (float) (ang / 6.283));
            cube(px, py, pz, s, t * 12 * (r.nextFloat() - 0.5f) * 4, t * 9, cc, a);
        }
    }

    // ---------- 3: Wirbelsturm ----------
    private static void vortexFx(Fx f, float t, float x, float y, float z) {
        float a = fade(t, 0.08f, 0.2f);
        float grow = easeOut(Math.min(1, t / 0.6f));
        float collapse = t > 0.7f ? (t - 0.7f) / 0.3f : 0;
        float hMax = 4.5f * grow;
        int n = 70;
        for (int arm = 0; arm < 3; arm++) {
            float px = 0, py = 0, pz = 0;
            for (int i = 0; i <= n; i++) {
                float k = i / (float) n;
                float hh = k * hMax;
                float rad = (0.4f + 1.6f * k) * (1 - collapse);
                float ang = (float) (arm * Math.PI * 2 / 3 + k * 10 - t * 14);
                float qx = x + (float) Math.cos(ang) * rad, qy = y + hh, qz = z + (float) Math.sin(ang) * rad;
                if (i > 0) line(px, py, pz, qx, qy, qz, 0.09f * (1 - k * 0.5f), col(f, (k + t) % 1f), a * (0.4f + 0.6f * k));
                px = qx;
                py = qy;
                pz = qz;
            }
        }
        // kleine Würfel, die im Sog mitfliegen
        Random r = new Random(f.seed);
        for (int i = 0; i < 20; i++) {
            float k = r.nextFloat();
            float ang = (float) (r.nextFloat() * 6.28 + t * 12 * (0.6 + k));
            float rad = (0.5f + 1.7f * k) * (1 - collapse);
            cube(x + (float) Math.cos(ang) * rad, y + k * hMax, z + (float) Math.sin(ang) * rad, 0.06f, t * 10, t * 6, 0xFFFFFF, a);
        }
        // am Ende: Blitz nach oben
        if (collapse > 0) {
            float k = easeOut(collapse);
            pillar(x, y, y + 2 + 12 * k, z, 0.35f * (1 - k) + 0.05f, col(f, t), (1 - collapse), 0);
            ring(x, y + 0.03f, z, 0.5f + 4.5f * k, 0.3f, 48, 0xFFFFFF, 1 - collapse);
        }
    }

    // ---------- 4: Glitch-Würfel ----------
    private static void glitchFx(Fx f, float t, float x, float y, float z) {
        int c = col(f, t);
        float a = fade(t, 0.05f, 0.3f);
        float cy = y + f.h * 0.55f;
        Random r = new Random(f.seed);
        if (t < 0.45f) {
            // Drahtgitter-Würfel um den Körper, dreht sich und wird größer
            float k = t / 0.45f;
            float s = 0.75f + 0.5f * easeOut(k);
            float ry = k * 4, rx = k * 2.5f;
            float[][] p = corners(x, cy, z, s, ry, rx);
            int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] e : edges) {
                line(p[e[0]][0], p[e[0]][1], p[e[0]][2], p[e[1]][0], p[e[1]][1], p[e[1]][2], 0.07f, c, a);
                line(p[e[0]][0], p[e[0]][1], p[e[0]][2], p[e[1]][0], p[e[1]][1], p[e[1]][2], 0.025f, 0xFFFFFF, a);
            }
            cube(x, cy, z, s * 0.98f, ry, rx, c, 0.18f * a);
        } else {
            // zerfällt in 8 kleine Würfel, die wegfliegen
            float k = (t - 0.45f) / 0.55f;
            for (int i = 0; i < 8; i++) {
                float dx = (i & 1) == 0 ? -1 : 1, dy = (i & 2) == 0 ? -1 : 1, dz = (i & 4) == 0 ? -1 : 1;
                float d = 0.6f + 3.5f * easeOut(k);
                cube(x + dx * d, cy + dy * d * 0.7f + k * 1.5f, z + dz * d, 0.4f * (1 - k * 0.6f), k * 8 + i, k * 6, i % 2 == 0 ? c : 0xFFFFFF, a);
            }
        }
        // Pixel, die flackernd nach oben schweben
        for (int i = 0; i < 40; i++) {
            float px = x + (r.nextFloat() - 0.5f) * 3, pz = z + (r.nextFloat() - 0.5f) * 3;
            float py = y + r.nextFloat() * 2.5f + t * (1 + 3 * r.nextFloat());
            boolean on = ((int) (t * 30 + i * 7) % 3) != 0;
            if (!on) continue;
            cube(px, py, pz, 0.05f + 0.05f * r.nextFloat(), 0, 0, i % 3 == 0 ? 0xFFFFFF : col(f, r.nextFloat()), a * 0.9f);
        }
    }

    private static float[][] corners(float cx, float cy, float cz, float s, float ry, float rx) {
        float[][] p = new float[8][];
        float cY = (float) Math.cos(ry), sY = (float) Math.sin(ry), cX = (float) Math.cos(rx), sX = (float) Math.sin(rx);
        for (int i = 0; i < 8; i++) {
            float px = (i & 1) == 0 ? -s : s, py = (i & 2) == 0 ? -s : s, pz = (i & 4) == 0 ? -s : s;
            float y2 = py * cX - pz * sX, z2 = py * sX + pz * cX;
            float x3 = px * cY + z2 * sY, z3 = -px * sY + z2 * cY;
            p[i] = new float[] {cx + x3, cy + y2, cz + z3};
        }
        return p;
    }

    // ---------- 5: Lichtkrone ----------
    private static void crownFx(Fx f, float t, float x, float y, float z) {
        float a = fade(t, 0.05f, 0.3f);
        // 6 Säulen steigen nacheinander aus dem Boden
        for (int i = 0; i < 6; i++) {
            float k = (t - i * 0.05f) / 0.5f;
            if (k <= 0) continue;
            double ang = Math.PI * 2 * i / 6 + t;
            float px = x + (float) Math.cos(ang) * 1.6f, pz = z + (float) Math.sin(ang) * 1.6f;
            float hgt = 3.2f * easeOut(k);
            float shoot = t > 0.75f ? (t - 0.75f) / 0.25f * 10 : 0;
            pillar(px, y + shoot, y + hgt + shoot * 1.5f, pz, 0.14f, col(f, i / 6f), a * 0.8f, a * 0.1f);
        }
        // Krone über dem Kopf: Ring mit Zacken, dreht sich
        float k = easeOut(Math.min(1, t / 0.4f));
        float cy = y + f.h + 0.5f + t * 1.2f;
        float rad = 0.45f + 0.15f * k;
        int c = col(f, t);
        ring(x, cy, z, rad, 0.08f, 32, c, a);
        ring(x, cy + 0.01f, z, rad, 0.03f, 32, 0xFFFFFF, a);
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI * 2 * i / 8 + t * 5;
            float px = x + (float) Math.cos(ang) * rad, pz = z + (float) Math.sin(ang) * rad;
            line(px, cy, pz, px, cy + (i % 2 == 0 ? 0.42f : 0.26f) * k, pz, 0.07f, i % 2 == 0 ? 0xFFFFFF : c, a);
        }
        // Lichtbögen von den Säulen zur Krone
        if (t > 0.3f && t < 0.8f) {
            Random r = new Random(f.seed + (long) (t * 20));
            for (int i = 0; i < 6; i++) {
                double ang = Math.PI * 2 * i / 6 + t;
                float px = x + (float) Math.cos(ang) * 1.6f, pz = z + (float) Math.sin(ang) * 1.6f, py = y + 3f;
                float mx = (px + x) / 2 + (r.nextFloat() - 0.5f) * 0.6f, my = (py + cy) / 2 + 0.4f + (r.nextFloat() - 0.5f) * 0.4f, mz = (pz + z) / 2 + (r.nextFloat() - 0.5f) * 0.6f;
                line(px, py, pz, mx, my, mz, 0.05f, 0xFFFFFF, a * 0.9f);
                line(mx, my, mz, x, cy, z, 0.05f, c, a * 0.9f);
            }
        }
        ring(x, y + 0.03f, z, 1.6f, 0.12f, 48, c, a * 0.8f);
        ring(x, y + 0.03f, z, 0.5f + 4f * easeOut(t), 0.2f, 48, 0xFFFFFF, (1 - t) * 0.6f);
    }

    // ---------- Kill-Banner (HUD) ----------

    public static void hud(DrawContext ctx, RenderTickCounter tick) {
        Config c = Config.INSTANCE;
        if (!c.kfOn || !c.kfBanner || bannerStart == 0) return;
        long el = System.currentTimeMillis() - bannerStart;
        if (el > 2600) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        float t = el / 2600f;
        float pop = el < 180 ? 1.6f - 0.6f * (el / 180f) : 1f;
        float alpha = t > 0.8f ? (1 - t) / 0.2f : 1f;
        int a = (int) (alpha * 255) << 24;
        int base = COLORS[Math.floorMod(c.kfColor, COLORS.length)];
        int rgb = base == 0 ? Ui.rainbowAt(t) & 0xFFFFFF : base;
        int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() - 78;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(pop, pop);
        // Raute mit Rahmen
        m.pushMatrix();
        m.rotate((float) (Math.PI / 4));
        ctx.fill(-15, -15, 15, 15, a | 0x0B0D12);
        ctx.fill(-15, -15, 15, -13, a | rgb);
        ctx.fill(-15, 13, 15, 15, a | rgb);
        ctx.fill(-15, -15, -13, 15, a | rgb);
        ctx.fill(13, -15, 15, 15, a | rgb);
        ctx.fill(-9, -9, 9, 9, (int) (alpha * 90) << 24 | rgb);
        m.popMatrix();
        // Serien-Striche (1 bis 5)
        for (int i = 0; i < streak; i++) {
            int bx = -((streak - 1) * 6) / 2 + i * 6;
            ctx.fill(bx - 2, -6, bx + 2, 6, a | 0xFFFFFF);
        }
        m.popMatrix();
        // Flügel links/rechts
        int wl = (int) (60 * Math.min(1, el / 250f));
        ctx.fill(cx - 26 - wl, cy - 1, cx - 26, cy + 1, a | rgb);
        ctx.fill(cx + 26, cy - 1, cx + 26 + wl, cy + 1, a | rgb);
        String title = streak >= 5 ? "ACE" : Lang.t("ELIMINIERT", "ELIMINATED");
        int tw = tr.getWidth(title);
        ctx.drawTextWithShadow(tr, title, cx - tw / 2, cy + 24, a | 0xFFFFFF);
        String nm = bannerName;
        int nw = tr.getWidth(nm);
        ctx.drawTextWithShadow(tr, nm, cx - nw / 2, cy + 35, a | rgb);
    }
}
