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
import net.minecraft.particle.ParticleTypes;
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
    public static final String[] COLOR_NAMES = {"Stil-Farben", "Rot", "Türkis", "Lila", "Gold", "Grün", "Regenbogen"};
    /** -1 = eigene Farben je Animation, 0 = Regenbogen. */
    public static final int[] COLORS = {-1, 0xFF4655, 0x2EE6E6, 0xB14BFF, 0xFFC23D, 0x47F58A, 0};

    private static final class Fx {
        int style;
        double x, y, z;
        float h;
        long start;
        long seed;
        int color;
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final long DURATION = 3300;

    private static int targetId = -1;
    private static long targetMs;
    private static boolean fired;
    private static int streak;
    private static long lastKill;
    private static long bannerStart;
    private static String bannerName = "";
    private static int bannerStyle;

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
        for (Fx f : ACTIVE) particles(mc, f, (now - f.start) / (float) DURATION);
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
        bannerStyle = f.style;
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
        f.x = mc.player.getX() + look.x * 9;
        f.y = mc.player.getY();
        f.z = mc.player.getZ() + look.z * 9;
        f.h = 1.8f;
        f.start = System.currentTimeMillis();
        f.seed = f.start;
        f.color = COLORS[Math.floorMod(c.kfColor, COLORS.length)];
        ACTIVE.add(f);
        streak = Math.min(5, streak + 1);
        lastKill = f.start;
        bannerStart = f.start;
        bannerName = Lang.t("Vorschau", "Preview");
        bannerStyle = f.style;
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
        play(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.3f, 0.6f);
        if (style == 0 || style == 4) play(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 1.4f, 0.5f);
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
        return pc(f, k);
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

    // ---------- Paletten & Größe ----------

    /** Eigene Farben je Animation (wenn "Stil-Farben" gewählt ist). */
    private static final int[][] PAL = {
        {0xFFD54A, 0xFF8A1E, 0xFFFFFF, 0xFF4D2E},   // Seelen-Strahl: Gold/Feuer
        {0x7DF9FF, 0x2E8BFF, 0xB46CFF, 0xFFFFFF},   // Kristall: Eis
        {0xB14BFF, 0xFF3DD8, 0x5B2BFF, 0xFFFFFF},   // Wirbel: Violett
        {0x39FF14, 0xFF00E6, 0x00F0FF, 0xFFFFFF},   // Glitch: Neon
        {0xFF2D55, 0xFFC23D, 0xFFFFFF, 0xFF7A00}    // Krone: Rot/Gold
    };

    /** Farbe an Stelle k (0..1) der Palette des Effekts. */
    private static int pc(Fx f, float k) {
        if (f.color == -1) {
            int[] p = PAL[f.style];
            float x = ((k % 1f) + 1f) % 1f * p.length;
            int i = (int) x;
            return mixRgb(p[i % p.length], p[(i + 1) % p.length], x - i);
        }
        if (f.color == 0) return Ui.rainbowAt(k) & 0xFFFFFF;
        return (k % 1f) > 0.75f ? mixRgb(f.color, 0xFFFFFF, 0.6f) : f.color;
    }

    /** Hauptfarbe. */
    private static int main(Fx f) {
        return f.color == -1 ? PAL[f.style][0] : f.color == 0 ? Ui.rainbowAt((System.currentTimeMillis() % 3000) / 3000f) & 0xFFFFFF : f.color;
    }

    private static float S() {
        return Math.max(0.5f, Config.INSTANCE.kfSize / 100f);
    }

    /** Linie mit Leuchten: breiter, schwacher Schein + heller Kern. */
    private static void glowLine(float ax, float ay, float az, float bx, float by, float bz, float w, int rgb, float al) {
        line(ax, ay, az, bx, by, bz, w * 3.2f, rgb, al * 0.22f);
        line(ax, ay, az, bx, by, bz, w * 1.6f, rgb, al * 0.55f);
        line(ax, ay, az, bx, by, bz, w * 0.55f, 0xFFFFFF, al);
    }

    /** Würfel mit Leucht-Hülle. */
    private static void glowCube(float x, float y, float z, float s, float ry, float rx, int rgb, float al) {
        cube(x, y, z, s * 1.9f, ry, rx, rgb, al * 0.16f);
        cube(x, y, z, s, ry, rx, rgb, al);
    }

    /** Zackiger Blitz von oben nach unten. */
    private static void lightning(Random r, float x, float yTop, float yBot, float z, float spread, int rgb, float al, float w) {
        int seg = 14;
        float px = x + (r.nextFloat() - 0.5f) * spread, py = yTop, pz = z + (r.nextFloat() - 0.5f) * spread;
        for (int i = 1; i <= seg; i++) {
            float k = i / (float) seg;
            float qx = i == seg ? x : x + (r.nextFloat() - 0.5f) * spread * (1 - k);
            float qz = i == seg ? z : z + (r.nextFloat() - 0.5f) * spread * (1 - k);
            float qy = yTop + (yBot - yTop) * k;
            glowLine(px, py, pz, qx, qy, qz, w, rgb, al);
            if (r.nextFloat() < 0.25f) {
                float bx = qx + (r.nextFloat() - 0.5f) * spread * 0.8f, bz = qz + (r.nextFloat() - 0.5f) * spread * 0.8f;
                glowLine(qx, qy, qz, bx, qy - (yTop - yBot) / seg * 1.5f, bz, w * 0.5f, rgb, al * 0.8f);
            }
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    // ---------- 1: Seelen-Strahl ----------
    private static void beamFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.03f, 0.3f);
        Random r = new Random(f.seed);
        // Blitze aus dem Himmel am Anfang und nochmal in der Mitte
        if (t < 0.12f || (t > 0.33f && t < 0.4f)) {
            Random lr = new Random(f.seed + (long) (t * 60));
            for (int i = 0; i < 3; i++) lightning(lr, x, y + 30 * S, y, z, 3 * S, pc(f, i / 3f), 1f, 0.22f * S);
        }
        // riesige Lichtsäule
        float w = 1.3f * S * (1 - easeOut(t)) + 0.12f * S;
        float top = y + 4 * S + 45 * S * easeOut(t * 1.3f);
        pillar(x, y, top, z, w * 2.2f, pc(f, 0.1f), a * 0.25f, 0f);
        pillar(x, y, top, z, w, pc(f, 0.3f), a * 0.8f, 0f);
        pillar(x, y, top, z, w * 0.4f, 0xFFFFFF, a, 0.2f);
        // Schockwellen
        for (int k = 0; k < 5; k++) {
            float tk = t * 1.4f - k * 0.1f;
            if (tk <= 0 || tk >= 1) continue;
            float rad = 0.6f * S + 11f * S * easeOut(tk);
            ring(x, y + 0.04f + k * 0.01f, z, rad, 0.9f * S * (1 - tk), 72, pc(f, k / 5f), (1 - tk) * 0.9f);
            ring(x, y + 0.05f + k * 0.01f, z, rad, 0.25f * S * (1 - tk), 72, 0xFFFFFF, (1 - tk));
        }
        // aufsteigende Ringe um die Säule
        for (int k = 0; k < 6; k++) {
            float tk = (t * 2 + k / 6f) % 1f;
            ring(x, y + tk * 12 * S, z, (1.4f - tk) * 1.6f * S, 0.12f * S, 40, pc(f, tk), a * (1 - tk));
        }
        // Splitter in der Spirale
        for (int i = 0; i < 34; i++) {
            float ph = r.nextFloat() * 6.28f, sp = 0.6f + r.nextFloat();
            float ang = ph + t * 7f * sp;
            float rad = (0.8f + easeOut(t) * (2.5f + 2 * r.nextFloat())) * S;
            float yy = y + 0.4f * S + t * (4 + 6 * r.nextFloat()) * S;
            glowCube(x + (float) Math.cos(ang) * rad, yy, z + (float) Math.sin(ang) * rad, (0.12f + 0.16f * r.nextFloat()) * S,
                t * 9 + ph, t * 7, pc(f, i / 34f + t), a);
        }
    }

    // ---------- 2: Kristall-Explosion ----------
    private static void shatterFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.01f, 0.35f);
        float cy = y + f.h * 0.55f;
        if (t < 0.28f) {
            float k = t / 0.28f;
            cube(x, cy, z, (0.4f + 3.2f * easeOut(k)) * S, k * 2, k * 1.5f, 0xFFFFFF, (1 - k) * 0.75f);
            cube(x, cy, z, (0.6f + 4.5f * easeOut(k)) * S, -k * 2, k, pc(f, 0.2f), (1 - k) * 0.4f);
        }
        for (int k = 0; k < 3; k++) {
            float tk = t * 1.7f - k * 0.12f;
            if (tk > 0 && tk < 1) ring(x, y + 0.04f, z, (0.4f + 9f * easeOut(tk)) * S, 0.6f * S * (1 - tk), 64, pc(f, k / 3f), (1 - tk));
        }
        Random r = new Random(f.seed);
        // 90 Kristalle fliegen weg
        for (int i = 0; i < 90; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float out = (4f + r.nextFloat() * 8f) * S, up = (5 + r.nextFloat() * 10) * S;
            float tt = t * 1.4f;
            float px = x + (float) Math.cos(ang) * out * tt * 0.6f, pz = z + (float) Math.sin(ang) * out * tt * 0.6f;
            float py = Math.max(y + 0.08f, cy + up * tt * 0.6f - 7f * S * tt * tt * 0.6f);
            float sz = (0.08f + 0.28f * r.nextFloat()) * S * (1 - t * 0.4f);
            glowCube(px, py, pz, sz, t * 20 * (r.nextFloat() - 0.5f) * 3, t * 12, pc(f, (float) (ang / 6.283)), a);
        }
        // zweite Welle: Kristall-Stacheln brechen im Kreis aus dem Boden
        if (t > 0.25f) {
            float k = (t - 0.25f) / 0.75f;
            float grow = k < 0.35f ? easeOut(k / 0.35f) : 1f - Math.max(0, (k - 0.7f) / 0.3f);
            for (int i = 0; i < 14; i++) {
                double ang = Math.PI * 2 * i / 14 + 0.2;
                float rad = (3.2f + (i % 2) * 1.3f) * S;
                float px = x + (float) Math.cos(ang) * rad, pz = z + (float) Math.sin(ang) * rad;
                float hgt = (2.2f + (i % 3)) * S * grow;
                spike(px, y, pz, (0.35f + 0.1f * (i % 2)) * S, hgt, (float) Math.cos(ang) * 0.35f, (float) Math.sin(ang) * 0.35f, pc(f, i / 14f), a);
            }
        }
    }

    /** Spitzer Kristall (4 Seiten), leicht nach außen geneigt. */
    private static void spike(float x, float y, float z, float r, float h, float tiltX, float tiltZ, int rgb, float al) {
        if (h <= 0.01f) return;
        float tx = x + tiltX * h, ty = y + h, tz = z + tiltZ * h;
        float[][] b = {{x - r, y, z}, {x, y, z - r}, {x + r, y, z}, {x, y, z + r}};
        for (int i = 0; i < 4; i++) {
            float[] p = b[i], q = b[(i + 1) % 4];
            int c = i % 2 == 0 ? rgb : mixRgb(rgb, 0xFFFFFF, 0.45f);
            quad(p, q, new float[] {tx, ty, tz}, new float[] {tx, ty, tz}, c, al * 0.85f);
        }
        glowLine(x, y, z, tx, ty, tz, 0.06f, rgb, al);
    }

    // ---------- 3: Wirbelsturm ----------
    private static void vortexFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.06f, 0.15f);
        float grow = easeOut(Math.min(1, t / 0.5f));
        float collapse = t > 0.62f ? Math.min(1, (t - 0.62f) / 0.18f) : 0;
        float boom = t > 0.8f ? (t - 0.8f) / 0.2f : -1;
        float hMax = 10f * S * grow;
        int n = 60;
        if (boom < 0) {
            for (int arm = 0; arm < 5; arm++) {
                float px = 0, py = 0, pz = 0;
                for (int i = 0; i <= n; i++) {
                    float k = i / (float) n;
                    float rad = (0.6f + 3.4f * k) * S * (1 - collapse);
                    float ang = (float) (arm * Math.PI * 2 / 5 + k * 9 - t * 16);
                    float qx = x + (float) Math.cos(ang) * rad, qy = y + k * hMax * (1 - collapse * 0.7f), qz = z + (float) Math.sin(ang) * rad;
                    if (i > 0) glowLine(px, py, pz, qx, qy, qz, 0.13f * S * (1 - k * 0.4f), pc(f, k + arm * 0.2f), a * (0.35f + 0.65f * k));
                    px = qx;
                    py = qy;
                    pz = qz;
                }
            }
            Random r = new Random(f.seed);
            for (int i = 0; i < 50; i++) {
                float k = r.nextFloat();
                float ang = (float) (r.nextFloat() * 6.28 + t * 14 * (0.6 + k));
                float rad = (0.6f + 3.5f * k) * S * (1 - collapse);
                glowCube(x + (float) Math.cos(ang) * rad, y + k * hMax * (1 - collapse * 0.7f), z + (float) Math.sin(ang) * rad,
                    (0.08f + 0.12f * r.nextFloat()) * S, t * 10, t * 6, pc(f, k), a);
            }
            // Schwarzes Loch in der Mitte beim Zusammenziehen
            if (collapse > 0) {
                float cy = y + 3 * S;
                cube(x, cy, z, 1.4f * S * collapse, t * 8, t * 5, 0x05000A, 0.95f);
                cube(x, cy, z, 2.4f * S * collapse, -t * 6, t * 3, pc(f, 0.3f), 0.3f);
                ring(x, cy, z, 3.2f * S * collapse, 0.4f * S, 64, pc(f, 0.6f), 0.8f);
            }
        } else {
            // Explosion: Ringe in alle Richtungen + Strahl
            float k = easeOut(boom);
            float cy = y + 3 * S;
            for (int i = 0; i < 4; i++) {
                ring(x, cy + (i - 1.5f) * 0.8f * S * k, z, (0.5f + 12 * k) * S, 0.7f * S * (1 - boom), 72, pc(f, i / 4f), 1 - boom);
            }
            pillar(x, y, y + 50 * S * k, z, 0.9f * S * (1 - boom) + 0.1f, pc(f, 0.1f), 1 - boom, 0);
            pillar(x, y, y + 50 * S * k, z, 0.35f * S, 0xFFFFFF, 1 - boom, 0);
            cube(x, cy, z, 2.5f * S * k, k * 3, k * 2, 0xFFFFFF, (1 - boom) * 0.5f);
        }
    }

    // ---------- 4: Glitch-Würfel ----------
    private static final int[][] EDGES = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private static void glitchFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.04f, 0.25f);
        float cy = y + f.h * 0.55f + 0.5f * S;
        Random r = new Random(f.seed);
        boolean jitter = ((int) (t * 40)) % 5 == 0;
        float jx = jitter ? (r.nextFloat() - 0.5f) * 0.6f * S : 0;
        if (t < 0.42f) {
            float k = t / 0.42f;
            float s = (1.5f + 1.3f * easeOut(k)) * S;
            float ry = k * 5, rx = k * 3;
            // RGB-Versatz: drei Kopien in Magenta, Türkis und der Hauptfarbe
            float off = 0.18f * S * (float) Math.sin(t * 60);
            wire(x + jx - off, cy, z, s, ry, rx, 0xFF00E6, a * 0.7f);
            wire(x + jx + off, cy, z, s, ry, rx, 0x00F0FF, a * 0.7f);
            wire(x + jx, cy, z, s, ry, rx, pc(f, 0f), a);
            cube(x + jx, cy, z, s * 0.97f, ry, rx, pc(f, 0f), 0.15f * a);
            wire(x, cy, z, s * 0.45f, -ry * 1.5f, rx, 0xFFFFFF, a);
        } else {
            // zerplatzt in 27 Würfel
            float k = (t - 0.42f) / 0.58f;
            int idx = 0;
            for (int ix = -1; ix <= 1; ix++)
                for (int iy = -1; iy <= 1; iy++)
                    for (int iz = -1; iz <= 1; iz++) {
                        float d = (1.2f + 7f * easeOut(k)) * S;
                        float px = x + ix * d + (ix == 0 && iy == 0 && iz == 0 ? 0 : 0), py = cy + iy * d * 0.7f + k * 3 * S, pz = z + iz * d;
                        glowCube(px, py, pz, 0.45f * S * (1 - k * 0.6f), k * 9 + idx, k * 7, pc(f, idx / 27f), a);
                        idx++;
                    }
            if (k < 0.25f) cube(x, cy, z, 3.5f * S * easeOut(k / 0.25f), 0, 0, 0xFFFFFF, (1 - k / 0.25f) * 0.6f);
        }
        // Scanlinien steigen auf
        for (int i = 0; i < 5; i++) {
            float k = (t * 1.8f + i / 5f) % 1f;
            float hs = 3.5f * S;
            square(x, y + k * 9 * S, z, hs, pc(f, i / 5f), a * (1 - k) * 0.8f);
        }
        // flackernde Pixel
        for (int i = 0; i < 130; i++) {
            float px = x + (r.nextFloat() - 0.5f) * 9 * S, pz = z + (r.nextFloat() - 0.5f) * 9 * S;
            float py = y + r.nextFloat() * 5 * S + t * (2 + 6 * r.nextFloat()) * S;
            if (((int) (t * 30 + i * 7)) % 3 == 0) continue;
            cube(px, py, pz, (0.07f + 0.09f * r.nextFloat()) * S, 0, 0, pc(f, r.nextFloat()), a * 0.95f);
        }
    }

    private static void wire(float x, float y, float z, float s, float ry, float rx, int rgb, float al) {
        float[][] p = corners(x, y, z, s, ry, rx);
        for (int[] e : EDGES) glowLine(p[e[0]][0], p[e[0]][1], p[e[0]][2], p[e[1]][0], p[e[1]][1], p[e[1]][2], 0.08f * S(), rgb, al);
    }

    /** Waagerechter Rahmen (Scanlinie). */
    private static void square(float x, float y, float z, float hs, int rgb, float al) {
        float th = 0.12f * S();
        quad(new float[] {x - hs, y, z - hs}, new float[] {x + hs, y, z - hs}, new float[] {x + hs, y, z - hs + th}, new float[] {x - hs, y, z - hs + th}, rgb, al);
        quad(new float[] {x - hs, y, z + hs - th}, new float[] {x + hs, y, z + hs - th}, new float[] {x + hs, y, z + hs}, new float[] {x - hs, y, z + hs}, rgb, al);
        quad(new float[] {x - hs, y, z - hs}, new float[] {x - hs + th, y, z - hs}, new float[] {x - hs + th, y, z + hs}, new float[] {x - hs, y, z + hs}, rgb, al);
        quad(new float[] {x + hs - th, y, z - hs}, new float[] {x + hs, y, z - hs}, new float[] {x + hs, y, z + hs}, new float[] {x + hs - th, y, z + hs}, rgb, al);
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
        float S = S(), a = fade(t, 0.04f, 0.25f);
        float R = 3.6f * S;
        // Siegel am Boden: zwei Ringe + Stern, dreht sich
        float sig = easeOut(Math.min(1, t / 0.25f));
        ring(x, y + 0.04f, z, R * sig, 0.22f * S, 72, pc(f, 0f), a);
        ring(x, y + 0.045f, z, R * 0.7f * sig, 0.14f * S, 72, pc(f, 0.3f), a);
        for (int i = 0; i < 8; i++) {
            double a1 = Math.PI * 2 * i / 8 + t * 1.5, a2 = Math.PI * 2 * (i + 3) / 8 + t * 1.5;
            glowLine(x + (float) Math.cos(a1) * R * sig, y + 0.06f, z + (float) Math.sin(a1) * R * sig,
                x + (float) Math.cos(a2) * R * sig, y + 0.06f, z + (float) Math.sin(a2) * R * sig, 0.09f * S, pc(f, i / 8f), a);
        }
        // 8 Säulen steigen nacheinander
        float shoot = t > 0.72f ? (t - 0.72f) / 0.28f : 0;
        for (int i = 0; i < 8; i++) {
            float k = (t - 0.04f * i) / 0.4f;
            if (k <= 0) continue;
            double ang = Math.PI * 2 * i / 8 + t * 1.5;
            float px = x + (float) Math.cos(ang) * R, pz = z + (float) Math.sin(ang) * R;
            float hgt = 7.5f * S * easeOut(k);
            float lift = shoot * 25 * S;
            pillar(px, y + lift, y + hgt + lift * 1.6f, pz, 0.55f * S, pc(f, i / 8f), a * 0.3f, 0f);
            pillar(px, y + lift, y + hgt + lift * 1.6f, pz, 0.25f * S, pc(f, i / 8f), a * 0.9f, a * 0.2f);
            pillar(px, y + lift, y + hgt + lift * 1.6f, pz, 0.08f * S, 0xFFFFFF, a, a * 0.4f);
        }
        // große Krone über dem Kopf
        float k = easeOut(Math.min(1, t / 0.35f));
        float cy = y + f.h + 1.2f * S + t * 2.5f * S;
        float rad = (0.9f + 0.5f * k) * S;
        int c = pc(f, t);
        ring(x, cy, z, rad, 0.22f * S, 48, c, a);
        ring(x, cy + 0.01f, z, rad, 0.07f * S, 48, 0xFFFFFF, a);
        ring(x, cy, z, rad * 1.8f, 0.5f * S, 48, c, a * 0.15f);
        for (int i = 0; i < 12; i++) {
            double ang = Math.PI * 2 * i / 12 + t * 4;
            float px = x + (float) Math.cos(ang) * rad, pz = z + (float) Math.sin(ang) * rad;
            glowLine(px, cy, pz, px, cy + (i % 2 == 0 ? 1.1f : 0.65f) * S * k, pz, 0.1f * S, i % 2 == 0 ? 0xFFFFFF : c, a);
            if (i % 2 == 0) glowCube(px, cy + 1.15f * S * k, pz, 0.12f * S, t * 6, t * 4, pc(f, 0.5f), a);
        }
        // Blitze von den Säulen zur Krone
        if (t > 0.25f && t < 0.75f) {
            Random r = new Random(f.seed + (long) (t * 25));
            for (int i = 0; i < 8; i++) {
                double ang = Math.PI * 2 * i / 8 + t * 1.5;
                float px = x + (float) Math.cos(ang) * R, pz = z + (float) Math.sin(ang) * R, py = y + 7.5f * S;
                float lx = px, ly = py, lz = pz;
                for (int sgm = 1; sgm <= 5; sgm++) {
                    float q = sgm / 5f;
                    float nx = px + (x - px) * q + (sgm < 5 ? (r.nextFloat() - 0.5f) * S : 0);
                    float ny = py + (cy - py) * q + (sgm < 5 ? (r.nextFloat() - 0.5f) * S : 0);
                    float nz = pz + (z - pz) * q + (sgm < 5 ? (r.nextFloat() - 0.5f) * S : 0);
                    glowLine(lx, ly, lz, nx, ny, nz, 0.08f * S, pc(f, i / 8f), a);
                    lx = nx;
                    ly = ny;
                    lz = nz;
                }
            }
        }
        // Schlusswelle
        if (shoot > 0) ring(x, y + 0.05f, z, (1 + 12 * easeOut(shoot)) * S, 0.8f * S * (1 - shoot), 72, 0xFFFFFF, 1 - shoot);
    }

    // ---------- Partikel (zusätzlich, für mehr Wucht) ----------

    private static void particles(MinecraftClient mc, Fx f, float t) {
        if (mc.world == null || !Config.INSTANCE.kfParticles) return;
        Random r = new Random();
        float S = S();
        double x = f.x, y = f.y, z = f.z;
        int n = t < 0.15f ? 26 : t < 0.6f ? 8 : 3;
        try {
            for (int i = 0; i < n; i++) {
                double vx = (r.nextDouble() - 0.5) * 0.9 * S, vy = r.nextDouble() * 0.9 * S, vz = (r.nextDouble() - 0.5) * 0.9 * S;
                double px = x + (r.nextDouble() - 0.5) * 2 * S, py = y + r.nextDouble() * 2.5 * S, pz = z + (r.nextDouble() - 0.5) * 2 * S;
                switch (f.style) {
                    case 0 -> mc.world.addParticleClient(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, px, py, pz, vx * 0.4, vy, vz * 0.4);
                    case 1 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
                    case 2 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, px, py, pz, vx, vy, vz);
                    case 3 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.GLOW : ParticleTypes.ELECTRIC_SPARK, px, py, pz, vx, vy, vz);
                    default -> mc.world.addParticleClient(i % 3 == 0 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.END_ROD, px, py, pz, vx, vy * 1.5, vz);
                }
            }
            if (t < 0.06f) {
                for (int i = 0; i < 40; i++) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    mc.world.addParticleClient(ParticleTypes.FIREWORK, x, y + 1, z, Math.cos(ang) * 0.6 * S, (r.nextDouble() - 0.2) * 0.5, Math.sin(ang) * 0.6 * S);
                }
                mc.world.addParticleClient(ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 0, 0, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------- Kill-Banner (HUD) ----------

    public static void hud(DrawContext ctx, RenderTickCounter tick) {
        Config c = Config.INSTANCE;
        if (!c.kfOn || bannerStart == 0) return;
        long el = System.currentTimeMillis() - bannerStart;
        if (el > 3000) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        int base = COLORS[Math.floorMod(c.kfColor, COLORS.length)];
        int rgb = base == -1 ? PAL[bannerStyle][0] : base == 0 ? Ui.rainbowAt(el / 3000f) & 0xFFFFFF : base;
        int rgb2 = base == -1 ? PAL[bannerStyle][1] : mixRgb(rgb, 0xFFFFFF, 0.5f);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        // Bildschirm-Blitz und farbiger Rand
        if (c.kfFlash && el < 700) {
            float k = el / 700f;
            int fa = (int) ((1 - k) * (1 - k) * 110);
            ctx.fill(0, 0, sw, sh, fa << 24 | rgb);
            int va = (int) ((1 - k) * 200);
            int edge = Math.max(20, sh / 5);
            ctx.fillGradient(0, 0, sw, edge, va << 24 | rgb, rgb);
            ctx.fillGradient(0, sh - edge, sw, sh, rgb, va << 24 | rgb);
        }
        if (!c.kfBanner) return;

        TextRenderer tr = mc.textRenderer;
        float t = el / 3000f;
        float pop = el < 220 ? 2.4f - 1.4f * easeOut(el / 220f) : 1f + 0.04f * (float) Math.sin(el / 90.0) * Math.max(0, 1 - el / 900f);
        float alpha = t > 0.82f ? (1 - t) / 0.18f : 1f;
        int a = (int) (alpha * 255) << 24;
        int cx = sw / 2, cy = sh - 92;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.scale(pop * 1.4f, pop * 1.4f);
        // Strahlen hinter dem Emblem
        m.pushMatrix();
        m.rotate(el / 900f);
        for (int i = 0; i < 8; i++) {
            m.rotate((float) (Math.PI / 4));
            ctx.fill(-1, 14, 1, 14 + (int) (16 * Math.min(1, el / 300f)), (int) (alpha * 160) << 24 | (i % 2 == 0 ? rgb : rgb2));
        }
        m.popMatrix();
        // zwei Rauten übereinander
        m.pushMatrix();
        m.rotate((float) (Math.PI / 4) + (el < 400 ? (1 - el / 400f) * 1.5f : 0));
        ctx.fill(-17, -17, 17, 17, (int) (alpha * 120) << 24 | rgb2);
        ctx.fill(-15, -15, 15, 15, a | 0x0B0D12);
        ctx.fill(-15, -15, 15, -12, a | rgb);
        ctx.fill(-15, 12, 15, 15, a | rgb);
        ctx.fill(-15, -15, -12, 15, a | rgb);
        ctx.fill(12, -15, 15, 15, a | rgb);
        ctx.fill(-8, -8, 8, 8, (int) (alpha * 110) << 24 | rgb2);
        m.popMatrix();
        for (int i = 0; i < streak; i++) {
            int bx = -((streak - 1) * 6) / 2 + i * 6;
            ctx.fill(bx - 2, -7, bx + 2, 7, a | 0xFFFFFF);
        }
        m.popMatrix();
        // Flügel
        int wl = (int) (110 * easeOut(Math.min(1, el / 300f)));
        ctx.fill(cx - 34 - wl, cy - 2, cx - 34, cy + 2, a | rgb);
        ctx.fill(cx + 34, cy - 2, cx + 34 + wl, cy + 2, a | rgb);
        ctx.fill(cx - 34 - wl * 2 / 3, cy + 5, cx - 34, cy + 6, a | rgb2);
        ctx.fill(cx + 34, cy + 5, cx + 34 + wl * 2 / 3, cy + 6, a | rgb2);
        String title = streak >= 5 ? "ACE" : streak == 4 ? "QUADRA KILL" : streak == 3 ? "TRIPLE KILL" : streak == 2 ? "DOUBLE KILL" : Lang.t("ELIMINIERT", "ELIMINATED");
        m.pushMatrix();
        m.translate(cx, cy + 34);
        m.scale(2f, 2f);
        int tw = tr.getWidth(title);
        ctx.drawTextWithShadow(tr, title, -tw / 2, 0, a | 0xFFFFFF);
        m.popMatrix();
        String nm = bannerName;
        int nw = tr.getWidth(nm);
        ctx.drawTextWithShadow(tr, nm, cx - nw / 2, cy + 56, a | rgb);
    }
}
