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

    public static final String[] STYLES = {"Seelen-Strahl", "Kristall-Explosion", "Wirbelsturm", "Glitch-Würfel", "Lichtkrone", "Blutmond", "Rankenfluch", "Donnerschlag", "Vulkanausbruch", "Sternschnuppe", "Seelenernte",
        "Flutwelle", "Rostsplitter", "Sandsturm", "Blütenregen", "Nordlicht", "Schattensprung", "Amethystbruch", "Drachenatem", "Finsternis", "Himmelsurteil",
        "Kürbisexplosion", "Netzfalle", "Totentanz", "Hexensud", "Zuckersturm", "Eiswirbel", "Sternennacht", "Tauregen", "Brandung", "Abendglut",
        "Rubinsplitter", "Osterwunder", "Mitternachtsschlag", "Plasmasturm", "Goldregen"};
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
    /** Zeichen der Kollektionen 12–21: {Anzahl Zacken, Länge, halbe Breite, Drehung*100, Kern 0/1/2}. */
    private static final int[][] EMB = {
        {8, 7, 2, 60, 0},     // Tiefsee
        {10, 4, 3, -120, 1},  // Kupferrost
        {12, 6, 1, 200, 0},   // Wüstensand
        {5, 8, 3, 40, 0},     // Kirschblüte
        {7, 9, 1, 30, 1},     // Polarlicht
        {6, 10, 2, -250, 2},  // Schattenklinge
        {6, 9, 3, 50, 1},     // Kristallherz
        {9, 6, 2, -80, 1},    // Drachenschuppe
        {16, 8, 1, 20, 2},    // Sonnenfinsternis
        {12, 11, 1, 35, 1},   // Himmelsthron
        {8, 6, 3, 90, 2},     // Kürbisfluch
        {16, 10, 1, 0, 2},    // Spinnennetz
        {10, 7, 2, -60, 0},   // Knochenmond
        {7, 8, 3, 150, 1},    // Hexenkessel
        {6, 9, 3, 120, 0},    // Zuckerstange
        {6, 10, 1, 60, 1},    // Schneesturm
        {5, 11, 2, 25, 1},    // Nordstern
        {8, 5, 3, 45, 0},     // Frühlingstau
        {9, 7, 2, -90, 0},    // Tropenwelle
        {14, 7, 1, 15, 0},    // Sonnenuntergang
        {6, 8, 3, 70, 1},     // Rubinglut
        {6, 6, 4, 50, 0},     // Osterglanz
        {12, 6, 1, 100, 2},   // Mitternacht
        {9, 9, 1, -300, 1},   // Plasmakern
        {16, 7, 2, 40, 1}     // Goldrausch
    };
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
        // Bogen/Armbrust: stirbt kurz nach deinem Schuss ein Spieler in der Nähe, zählt das als dein Kill
        if (c.kfOn && now - lastShotMs < 5000) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                if (p.deathTime >= 1 && p.deathTime <= 2 && DEAD_SEEN.add(p.getId()) && p.squaredDistanceTo(mc.player) < 100 * 100) {
                    if (p.getId() == targetId) fired = true;
                    trigger(p, lastShotStyle);
                }
            }
        }
        if (DEAD_SEEN.size() > 0) DEAD_SEEN.removeIf(id -> {
            Entity e2 = mc.world.getEntityById(id);
            return !(e2 instanceof LivingEntity l2) || l2.deathTime == 0;
        });
        if (!c.kfOn || targetId < 0 || fired || now - targetMs > 4000) return;
        Entity e = mc.world.getEntityById(targetId);
        if (!(e instanceof LivingEntity le)) return;
        if (!(le instanceof PlayerEntity) && !c.kfMobs) return;
        if (le.isDead() || le.getHealth() <= 0f) {
            fired = true;
            trigger(le);
        }
    }

    /** Kollektion der Waffe in deiner Hand (-1 = kein Skin). */
    static int heldStyle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !Config.INSTANCE.wsOn) return -1;
        return Skins.equipped(Skins.typeOf(mc.player.getMainHandStack().getItem()));
    }

    static void trigger(LivingEntity le) {
        trigger(le, heldStyle());
    }

    /** Finisher an der Stelle des Gegners starten. style = Kollektion der Waffe, -1 = nur Banner. */
    static void trigger(LivingEntity le, int style) {
        Config c = Config.INSTANCE;
        long now = System.currentTimeMillis();
        if (style >= 0) {
            Fx f = new Fx();
            f.style = style;
            f.x = le.getX();
            f.y = le.getY();
            f.z = le.getZ();
            f.h = Math.max(1f, le.getHeight());
            f.start = now;
            f.seed = now ^ le.getId();
            f.color = -1;
            if (ACTIVE.size() > 6) ACTIVE.remove(0);
            ACTIVE.add(f);
        }
        streak = Math.min(5, streak + 1);
        lastKill = now;
        bannerStart = now;
        bannerName = le.getName().getString();
        bannerStyle = style;
        if (c.kfSound && style >= 0) sounds(style);
    }

    private static void play(Object sound, float pitch, float vol) {
        try {
            SoundEvent se = sound instanceof SoundEvent s ? s : sound instanceof RegistryEntry<?> r && r.value() instanceof SoundEvent s2 ? s2 : null;
            if (se != null) MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(se, pitch, vol));
        } catch (Throwable ignored) {
        }
    }

    private static final String[] SND = {"sonnenfeuer", "frostkristall", "leere", "neonglitch", "koenigsglanz", "blutmond", "giftranke", "gewitter", "magmakern", "sternenstaub", "geisterhauch",
        "tiefsee", "kupferrost", "wuestensand", "kirschbluete", "polarlicht", "schattenklinge", "kristallherz", "drachenschuppe", "finsternis", "himmelsthron",
        "kuerbisfluch", "spinnennetz", "knochenmond", "hexenkessel", "zuckerstange", "schneesturm", "nordstern", "fruehlingstau", "tropenwelle",
        "sonnenuntergang", "rubinglut", "osterglanz", "mitternacht", "plasmakern", "goldrausch"};

    /** Eigene Kill-Sounds: Kill 1 bis 5 werden immer höher (Serie), dazu der Finisher-Sound der Kollektion. */
    private static void sounds(int style) {
        String b = SND[Math.floorMod(style, SND.length)];
        int k = Math.max(1, Math.min(5, streak));
        play(SoundEvent.of(net.minecraft.util.Identifier.of("spotifylyrics", "kill." + b + ".k" + k)), 1f, 1f);
        if (Skins.tier(style) >= 1) play(SoundEvent.of(net.minecraft.util.Identifier.of("spotifylyrics", "kill." + b + ".fin")), 1f, 0.9f);
    }

    /** Vorschau eines Finishers vor dir. */
    static void preview(int style) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || style < 0) return;
        Vec3d look = mc.player.getRotationVec(1f);
        Config c = Config.INSTANCE;
        Fx f = new Fx();
        f.style = style;
        f.x = mc.player.getX() + look.x * 9;
        f.y = mc.player.getY();
        f.z = mc.player.getZ() + look.z * 9;
        f.h = 1.8f;
        f.start = System.currentTimeMillis();
        f.seed = f.start;
        f.color = -1;
        ACTIVE.add(f);
        streak = Math.min(5, streak + 1);
        lastKill = f.start;
        bannerStart = f.start;
        bannerName = Lang.t("Vorschau", "Preview");
        bannerStyle = f.style;
        if (c.kfSound) sounds(f.style);
    }

    /** Für Bogen/Armbrust: zuletzt geschossen (Zeit und Kollektion). */
    static long lastShotMs;
    static int lastShotStyle = -1;
    private static final java.util.Set<Integer> DEAD_SEEN = new java.util.HashSet<>();

    private static VertexConsumer vc;
    private static float camRx = 1, camRz = 0;
    private static final Matrix4f M = new Matrix4f();

    private static void render(WorldRenderContext ctx) {
        if (ACTIVE.isEmpty()) return;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
            VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
            vc = consumers.getBuffer(RenderLayers.debugQuads());
            M.identity();
            double cyaw = Math.toRadians(mc.gameRenderer.getCamera().getYaw());
            camRx = (float) -Math.cos(cyaw);
            camRz = (float) -Math.sin(cyaw);
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
                curQ = Skins.quality(f.style);
                switch (f.style) {
                    case 0 -> beamFx(f, t, ox, oy, oz);
                    case 1 -> shatterFx(f, t, ox, oy, oz);
                    case 2 -> vortexFx(f, t, ox, oy, oz);
                    case 3 -> glitchFx(f, t, ox, oy, oz);
                    case 5 -> bloodFx(f, t, ox, oy, oz);
                    case 6 -> vineFx(f, t, ox, oy, oz);
                    case 7 -> stormFx(f, t, ox, oy, oz);
                    case 8 -> magmaFx(f, t, ox, oy, oz);
                    case 9 -> starFx(f, t, ox, oy, oz);
                    case 10 -> ghostFx(f, t, ox, oy, oz);
                    case 4 -> crownFx(f, t, ox, oy, oz);
                    default -> comboFx(f, t, ox, oy, oz);
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
        {0xFF2D55, 0xFFC23D, 0xFFFFFF, 0xFF7A00},   // Krone: Rot/Gold
        {0xFF1E30, 0x8A0010, 0xFF7A6E, 0x2A0006},   // Blutmond
        {0x7CFF4F, 0x1F8A3A, 0xE8FFB0, 0x2EE6A0},   // Rankenfluch
        {0x5AD1FF, 0xFFF35A, 0xFFFFFF, 0x3A5BFF},   // Donnerschlag
        {0xFF6A00, 0xFFD040, 0xFF2A00, 0xFFF4C0},   // Vulkanausbruch
        {0xFFB8F0, 0xA8C8FF, 0xFFFFFF, 0x7A5CFF},   // Sternschnuppe
        {0x7FFFE0, 0x2EC4B6, 0xE8FFFA, 0x1E5A5A},   // Seelenernte
        {0x2EE6E6, 0x0B6AFF, 0xCFFFFF, 0x063A5A},   // Flutwelle
        {0xE8945A, 0x3FB89A, 0xFFE0C0, 0x7A3A12},   // Rostsplitter
        {0xF4D896, 0xC8A060, 0xFFF6DC, 0x8A5A20},   // Sandsturm
        {0xFFB0D0, 0xFF6FA8, 0xFFFFFF, 0xA0306A},   // Blütenregen
        {0x4AFFA0, 0xC07CFF, 0xE8FFF4, 0x2A6AFF},   // Nordlicht
        {0x2A2A36, 0xFF2A3A, 0x8A8AA0, 0x000000},   // Schattensprung
        {0xD08CFF, 0xFF5AD0, 0xFFFFFF, 0x5A1AA0},   // Amethystbruch
        {0x4AD07A, 0xFFD45A, 0xFF7A2A, 0x0E3A1A},   // Drachenatem
        {0xFFFFFF, 0xFFB040, 0xFF6A2A, 0x000000},   // Finsternis
        {0xFFE9A0, 0x9AD8FF, 0xFFFFFF, 0xFFD45A},   // Himmelsurteil
        {0xFF8A00, 0x5AA020, 0xFFE070, 0x1A0800},   // Kürbisexplosion
        {0xB8B8C8, 0x9A5AFF, 0xFFFFFF, 0x1A1024},   // Netzfalle
        {0xE8E6D0, 0xB6FF8A, 0xFFFFFF, 0x2A3A1A},   // Totentanz
        {0x7CFF4F, 0xB14BFF, 0xE0FFB0, 0x2A0A3A},   // Hexensud
        {0xE8102A, 0xFFFFFF, 0x2ECC71, 0x6A0010},   // Zuckersturm
        {0xFFFFFF, 0x7AA8E0, 0xBFEFFF, 0x1A3A6A},   // Eiswirbel
        {0xFFD45A, 0x5A7AFF, 0xFFFFFF, 0x0A1440},   // Sternennacht
        {0x8AE070, 0xFFF59A, 0xFFB0E0, 0x2A5A20},   // Tauregen
        {0x0FB8C8, 0xFF7A5A, 0xFFFFFF, 0x04404A},   // Brandung
        {0xFFB040, 0xE0508A, 0xFFF0C0, 0x4A1A5A},   // Abendglut
        {0xFF2A3A, 0xFFD45A, 0xFFD0D8, 0x5A0010},   // Rubinsplitter
        {0xFFB0E0, 0xA8E0FF, 0xFFF59A, 0xC8A8FF},   // Osterwunder
        {0xC8D0E0, 0x3A5AFF, 0xFFFFFF, 0x0A1030},   // Mitternachtsschlag
        {0x00E5FF, 0xE600D0, 0xFFFFFF, 0x2A0A4A},   // Plasmasturm
        {0xFFD45A, 0xFFF0A0, 0xFFFFFF, 0xB07A10}    // Goldregen
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

    /** Stärke des gerade gezeichneten Finishers (nach Seltenheit). */
    private static float curQ = 1f;

    private static float S() {
        return Math.max(0.5f, Config.INSTANCE.kfSize / 100f) * curQ;
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

    // ---------- 6: Blutmond ----------
    /** Senkrechter Bogen, immer zur Kamera gedreht (für den Mond). */
    private static void arc(float cx, float cy, float cz, float r, float a0, float a1, float th, int rgb, float al, boolean crescent) {
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float k0 = i / (float) seg, k1 = (i + 1) / (float) seg;
            float b0 = a0 + (a1 - a0) * k0, b1 = a0 + (a1 - a0) * k1;
            float w0 = crescent ? th * (float) Math.sin(Math.PI * k0) : th, w1 = crescent ? th * (float) Math.sin(Math.PI * k1) : th;
            float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
            quad(new float[] {cx + camRx * c0 * r, cy + s0 * r, cz + camRz * c0 * r},
                new float[] {cx + camRx * c0 * (r + w0), cy + s0 * (r + w0), cz + camRz * c0 * (r + w0)},
                new float[] {cx + camRx * c1 * (r + w1), cy + s1 * (r + w1), cz + camRz * c1 * (r + w1)},
                new float[] {cx + camRx * c1 * r, cy + s1 * r, cz + camRz * c1 * r}, rgb, al);
        }
    }

    private static void bloodFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.05f, 0.25f);
        Random r = new Random(f.seed);
        // Mond steigt hinter dem Gegner auf
        float rise = easeOut(Math.min(1, t / 0.4f));
        float my = y + 4 * S + 7 * S * rise, R = 3.2f * S;
        arc(x, my, z, R * 1.1f, (float) (-Math.PI / 2 - 1.2), (float) (Math.PI / 2 + 1.2), 1.6f * S, 0x8A0010, a * 0.25f, true);
        arc(x, my, z, R, (float) (-Math.PI / 2 - 1.1), (float) (Math.PI / 2 + 1.1), 1.0f * S, 0xFF1E30, a * 0.9f, true);
        arc(x, my, z, R + 0.25f * S, (float) (-Math.PI / 2 - 0.9), (float) (Math.PI / 2 + 0.9), 0.35f * S, 0xFF7A6E, a, true);
        // Blutregen
        for (int i = 0; i < 80; i++) {
            float px = x + (r.nextFloat() - 0.5f) * 10 * S, pz = z + (r.nextFloat() - 0.5f) * 10 * S;
            float sp = 0.6f + r.nextFloat();
            float k = (t * 2.2f * sp + r.nextFloat()) % 1f;
            float py = y + 12 * S * (1 - k);
            line(px, py, pz, px, py + 0.6f * S, pz, 0.05f * S, i % 4 == 0 ? 0xFF7A6E : 0xC4102F, a * 0.8f);
        }
        // Risse im Boden
        float crack = easeOut(Math.min(1, t / 0.3f));
        for (int i = 0; i < 12; i++) {
            double ang = Math.PI * 2 * i / 12 + r.nextFloat() * 0.3;
            float px = x, pz = z, len = (2.5f + r.nextFloat() * 4) * S * crack;
            int segs = 6;
            for (int k = 1; k <= segs; k++) {
                float d = len * k / segs;
                float jit = (r.nextFloat() - 0.5f) * 0.6f * S;
                float qx = x + (float) Math.cos(ang) * d + (float) -Math.sin(ang) * jit, qz = z + (float) Math.sin(ang) * d + (float) Math.cos(ang) * jit;
                glowLine(px, y + 0.05f, pz, qx, y + 0.05f, qz, 0.1f * S, 0xFF1E30, a);
                px = qx;
                pz = qz;
            }
        }
        // Herzschlag-Wellen
        for (int k = 0; k < 2; k++) {
            float tk = (t - k * 0.25f) * 2.5f;
            if (tk > 0 && tk < 1) ring(x, y + 0.06f, z, (0.5f + 7 * easeOut(tk)) * S, 0.5f * S * (1 - tk), 64, 0xFF1E30, 1 - tk);
        }
        pillar(x, y, y + 3 * S, z, 0.9f * S * (1 - t), 0x8A0010, a * 0.5f, 0);
    }

    // ---------- 7: Rankenfluch ----------
    private static void vineFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.04f, 0.25f);
        Random r = new Random(f.seed);
        float grow = easeOut(Math.min(1, t / 0.55f));
        float cy = y + f.h * 0.6f;
        for (int v = 0; v < 8; v++) {
            double a0 = Math.PI * 2 * v / 8 + r.nextFloat() * 0.3;
            float R0 = (3 + r.nextFloat() * 1.5f) * S;
            int n = 40;
            int shown = (int) (n * grow);
            float px = x + (float) Math.cos(a0) * R0, py = y, pz = z + (float) Math.sin(a0) * R0;
            for (int i = 1; i <= shown; i++) {
                float k = i / (float) n;
                double ang = a0 + k * 3.2;
                float rad = R0 * (1 - k * 0.85f);
                float qx = x + (float) Math.cos(ang) * rad, qz = z + (float) Math.sin(ang) * rad;
                float qy = y + (cy + 1.2f * S - y) * (float) Math.sin(k * Math.PI / 2) + (float) Math.sin(k * 12 + v) * 0.25f * S;
                glowLine(px, py, pz, qx, qy, qz, 0.16f * S * (1 - k * 0.6f), pc(f, k * 0.5f), a);
                if (i % 6 == 0) cube(qx, qy, qz, 0.13f * S, k * 5 + v, 0.6f, pc(f, 0.6f), a);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
        // Dornenkreis
        for (int i = 0; i < 18; i++) {
            double ang = Math.PI * 2 * i / 18;
            float k = Math.max(0, Math.min(1, (t - 0.1f - i * 0.01f) * 4));
            spike(x + (float) Math.cos(ang) * 4.5f * S, y, z + (float) Math.sin(ang) * 4.5f * S, 0.3f * S, (1.4f + (i % 3) * 0.6f) * S * k,
                (float) -Math.cos(ang) * 0.3f, (float) -Math.sin(ang) * 0.3f, pc(f, 0.2f), a);
        }
        // Gift-Blüte am Ende
        if (t > 0.55f) {
            float k = (t - 0.55f) / 0.45f;
            for (int i = 0; i < 40; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                float d = (0.5f + 5 * easeOut(k) * r.nextFloat()) * S;
                glowCube(x + (float) Math.cos(ang) * d, cy + (r.nextFloat() * 4 - 1) * S * k, z + (float) Math.sin(ang) * d,
                    0.1f * S, k * 8, k * 5, pc(f, r.nextFloat()), (1 - k));
            }
            ring(x, y + 0.05f, z, (1 + 8 * easeOut(k)) * S, 0.5f * S * (1 - k), 64, pc(f, 0.3f), 1 - k);
        }
    }

    // ---------- 8: Donnerschlag ----------
    private static void stormFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.05f, 0.25f);
        Random r = new Random(f.seed);
        float top = y + 16 * S;
        // Gewitterwolke dreht sich
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2 + t * 1.5;
            float d = (float) Math.sqrt(r.nextFloat()) * 6 * S;
            cube(x + (float) Math.cos(ang) * d, top + (r.nextFloat() - 0.5f) * 1.5f * S, z + (float) Math.sin(ang) * d,
                (0.6f + r.nextFloat() * 0.9f) * S, 0, 0, i % 5 == 0 ? 0x3A4660 : 0x1A2030, a * 0.55f);
        }
        // mehrere Einschläge
        float[] hits = {0.03f, 0.18f, 0.33f, 0.5f};
        for (int h = 0; h < hits.length; h++) {
            float dt = t - hits[h];
            if (dt < 0 || dt > 0.08f) continue;
            Random lr = new Random(f.seed + h * 31 + (long) (t * 80));
            float hx = x + (h == 0 ? 0 : (lr.nextFloat() - 0.5f) * 5 * S), hz = z + (h == 0 ? 0 : (lr.nextFloat() - 0.5f) * 5 * S);
            for (int b = 0; b < 2; b++) lightning(lr, hx, top, y, hz, 3 * S, pc(f, b * 0.3f), 1f, 0.26f * S);
            ring(hx, y + 0.05f, hz, (0.5f + 20 * dt) * S, 0.5f * S, 48, 0xFFFFFF, 1 - dt / 0.08f);
        }
        // Strom-Bögen am Boden
        Random ar = new Random(f.seed + (long) (t * 30));
        for (int i = 0; i < 10; i++) {
            double ang = ar.nextDouble() * Math.PI * 2;
            float d0 = ar.nextFloat() * 4 * S, len = (0.8f + ar.nextFloat() * 1.5f) * S;
            float px = x + (float) Math.cos(ang) * d0, pz = z + (float) Math.sin(ang) * d0, py = y + 0.1f;
            for (int k = 0; k < 4; k++) {
                float qx = px + (ar.nextFloat() - 0.5f) * len, qz = pz + (ar.nextFloat() - 0.5f) * len, qy = y + 0.1f + ar.nextFloat() * 0.6f * S;
                glowLine(px, py, pz, qx, qy, qz, 0.05f * S, pc(f, ar.nextFloat()), a);
                px = qx;
                py = qy;
                pz = qz;
            }
        }
        // geladene Säule um den Gegner
        float k = easeOut(Math.min(1, t / 0.3f));
        pillar(x, y, y + 3 * S * k, z, 1.2f * S, pc(f, 0f), a * 0.25f, 0);
        ring(x, y + 1.5f * S, z, (1.4f + 0.2f * (float) Math.sin(t * 40)) * S, 0.12f * S, 40, pc(f, 0.5f), a);
        ring(x, y + 0.05f, z, (1 + 9 * easeOut(t)) * S, 0.35f * S, 64, pc(f, 0.2f), (1 - t));
    }

    // ---------- Partikel (zusätzlich, für mehr Wucht) ----------

    private static void particles(MinecraftClient mc, Fx f, float t) {
        if (mc.world == null || !Config.INSTANCE.kfParticles) return;
        Random r = new Random();
        curQ = Skins.quality(f.style);
        float S = S();
        double x = f.x, y = f.y, z = f.z;
        int n = t < 0.15f ? 26 : t < 0.6f ? 8 : 3;
        n = Math.max(1, Math.round(n * curQ * curQ));
        try {
            for (int i = 0; i < n; i++) {
                double vx = (r.nextDouble() - 0.5) * 0.9 * S, vy = r.nextDouble() * 0.9 * S, vz = (r.nextDouble() - 0.5) * 0.9 * S;
                double px = x + (r.nextDouble() - 0.5) * 2 * S, py = y + r.nextDouble() * 2.5 * S, pz = z + (r.nextDouble() - 0.5) * 2 * S;
                switch (f.style) {
                    case 0 -> mc.world.addParticleClient(i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, px, py, pz, vx * 0.4, vy, vz * 0.4);
                    case 1 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
                    case 2 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, px, py, pz, vx, vy, vz);
                    case 3 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.GLOW : ParticleTypes.ELECTRIC_SPARK, px, py, pz, vx, vy, vz);
                    case 5 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.DAMAGE_INDICATOR, px, py, pz, vx, vy, vz);
                    case 6 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SPORE_BLOSSOM_AIR, px, py, pz, vx, vy, vz);
                    case 7 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD, px, py, pz, vx * 1.5, vy, vz * 1.5);
                    case 8 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, px, py, pz, vx, vy * 1.4, vz);
                    case 9 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, px, py + 2, pz, vx * 0.5, -Math.abs(vy) * 0.4, vz * 0.5);
                    case 10 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx * 0.4, vy * 0.8, vz * 0.4);
                    case 11 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.GLOW : ParticleTypes.NAUTILUS, px, py, pz, vx * 0.5, vy, vz * 0.5);
                    case 12 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.WAX_OFF : ParticleTypes.CRIT, px, py, pz, vx, vy, vz);
                    case 13 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.CRIT, px, py, pz, vx * 1.6, vy * 0.3, vz * 1.6);
                    case 14 -> mc.world.addParticleClient(i % 3 == 0 ? ParticleTypes.HEART : ParticleTypes.CHERRY_LEAVES, px, py + 1, pz, vx * 0.3, -Math.abs(vy) * 0.2, vz * 0.3);
                    case 15 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.GLOW : ParticleTypes.END_ROD, px, py + 2, pz, vx * 0.4, vy * 0.6, vz * 0.4);
                    case 16 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, px, py, pz, vx * 0.5, vy * 0.4, vz * 0.5);
                    case 17 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.END_ROD, px, py, pz, vx, vy, vz);
                    case 18 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.HAPPY_VILLAGER, px, py, pz, vx * 1.2, vy * 0.6, vz * 1.2);
                    case 19 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD, px, py, pz, vx, vy * 0.5, vz);
                    case 20, 35 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, vx * 0.6, vy * 1.4, vz * 0.6);
                    case 21 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMOKE, px, py, pz, vx, vy, vz);
                    case 22, 33 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
                    case 23 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.WHITE_ASH, px, py, pz, vx * 0.5, vy, vz * 0.5);
                    case 24 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.PORTAL, px, py, pz, vx, vy, vz);
                    case 25, 32 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.FIREWORK : ParticleTypes.HEART, px, py, pz, vx * 0.6, vy * 0.6, vz * 0.6);
                    case 26 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.END_ROD, px, py, pz, vx * 1.4, vy * 0.5, vz * 1.4);
                    case 27 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, px, py + 2, pz, vx * 0.4, vy * 0.4, vz * 0.4);
                    case 28 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CHERRY_LEAVES, px, py, pz, vx * 0.5, vy * 0.5, vz * 0.5);
                    case 29 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.NAUTILUS : ParticleTypes.GLOW, px, py, pz, vx, vy, vz);
                    case 30 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD, px, py, pz, vx * 0.5, vy, vz * 0.5);
                    case 31 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.ENCHANTED_HIT, px, py, pz, vx, vy, vz);
                    case 34 -> mc.world.addParticleClient(i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.GLOW, px, py, pz, vx * 1.3, vy, vz * 1.3);
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

    // ---------- 9: Vulkanausbruch ----------
    private static void magmaFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.04f, 0.25f);
        Random r = new Random(f.seed);
        // glühende Risse breiten sich aus
        float crack = easeOut(Math.min(1, t / 0.25f));
        for (int i = 0; i < 10; i++) {
            double ang = Math.PI * 2 * i / 10 + r.nextFloat() * 0.4;
            float px = x, pz = z, len = (3 + r.nextFloat() * 3) * S * crack;
            for (int k = 1; k <= 5; k++) {
                float d = len * k / 5, jit = (r.nextFloat() - 0.5f) * 0.7f * S;
                float qx = x + (float) Math.cos(ang) * d - (float) Math.sin(ang) * jit, qz = z + (float) Math.sin(ang) * d + (float) Math.cos(ang) * jit;
                glowLine(px, y + 0.05f, pz, qx, y + 0.05f, qz, 0.12f * S, k < 3 ? 0xFFD040 : 0xFF6A00, a);
                px = qx;
                pz = qz;
            }
        }
        // Lava-Säule schießt hoch
        float erupt = easeOut(Math.max(0, Math.min(1, (t - 0.12f) / 0.25f)));
        float top = y + 11 * S * erupt;
        pillar(x, y, top, z, 1.1f * S * (1 - t * 0.6f), 0xFF6A00, a * 0.8f, 0.1f);
        pillar(x, y, top + 1.5f * S * erupt, z, 0.5f * S * (1 - t * 0.6f), 0xFFE070, a, 0.2f);
        // Lavabrocken fliegen im Bogen
        for (int i = 0; i < 36; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float sp = (2 + r.nextFloat() * 5) * S, up = (6 + r.nextFloat() * 7) * S;
            float k = Math.max(0, t - 0.15f - r.nextFloat() * 0.1f) * 1.6f;
            if (k <= 0 || k > 1) continue;
            float bx = x + (float) Math.cos(ang) * sp * k, bz = z + (float) Math.sin(ang) * sp * k;
            float by = y + up * k * (1 - k) * 4 * 0.5f + 1;
            glowCube(bx, Math.max(y + 0.2f, by), bz, (0.16f + r.nextFloat() * 0.14f) * S, k * 9 + i, k * 6, i % 3 == 0 ? 0xFFE070 : 0xFF6A00, a);
        }
        // Gesteinszacken
        for (int i = 0; i < 12; i++) {
            double ang = Math.PI * 2 * i / 12 + 0.2;
            float k = Math.max(0, Math.min(1, (t - 0.1f - (i % 4) * 0.03f) * 5));
            spike(x + (float) Math.cos(ang) * 3.2f * S, y, z + (float) Math.sin(ang) * 3.2f * S, 0.45f * S, (1.6f + (i % 3) * 0.8f) * S * k,
                (float) Math.cos(ang) * 0.35f, (float) Math.sin(ang) * 0.35f, i % 2 == 0 ? 0x3A0E06 : 0xFF2A00, a);
        }
        // Druckwelle
        float tk = (t - 0.15f) * 2.2f;
        if (tk > 0 && tk < 1) ring(x, y + 0.08f, z, (1 + 9 * easeOut(tk)) * S, 0.6f * S * (1 - tk), 64, 0xFF6A00, 1 - tk);
    }

    // ---------- 10: Sternschnuppe ----------
    private static void starFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.04f, 0.3f);
        Random r = new Random(f.seed);
        // Sternschnuppen fallen schräg herab
        for (int i = 0; i < 26; i++) {
            float ox = (r.nextFloat() - 0.5f) * 12 * S, oz = (r.nextFloat() - 0.5f) * 12 * S;
            float k = (t * 1.8f + r.nextFloat()) % 1f;
            float sx = x + ox + 6 * S * (1 - k), sy = y + 14 * S * (1 - k) + 0.5f, sz = z + oz;
            int c = i % 3 == 0 ? 0xFFB8F0 : i % 3 == 1 ? 0xA8C8FF : 0xFFFFFF;
            glowLine(sx, sy, sz, sx + 1.6f * S, sy + 3.2f * S, sz, 0.07f * S, c, a * 0.8f);
            glowCube(sx, sy, sz, 0.12f * S, k * 10, k * 7, 0xFFFFFF, a);
        }
        // großer Stern über dem Gegner (5 Zacken)
        float pop = easeOut(Math.min(1, t / 0.3f));
        float cy = y + f.h + 2.5f * S, R = 2.6f * S * pop, rr = R * 0.42f;
        double rot = t * 1.5;
        for (int i = 0; i < 10; i++) {
            double a0 = rot + Math.PI * i / 5, a1 = rot + Math.PI * (i + 1) / 5;
            float r0 = i % 2 == 0 ? R : rr, r1 = i % 2 == 0 ? rr : R;
            glowLine(x + camRx * (float) Math.cos(a0) * r0, cy + (float) Math.sin(a0) * r0, z + camRz * (float) Math.cos(a0) * r0,
                x + camRx * (float) Math.cos(a1) * r1, cy + (float) Math.sin(a1) * r1, z + camRz * (float) Math.cos(a1) * r1, 0.14f * S, 0xFFB8F0, a);
        }
        glowCube(x, cy, z, 0.35f * S * pop, t * 6, t * 4, 0xFFFFFF, a);
        // Sternenstaub-Spirale
        for (int i = 0; i < 60; i++) {
            float k = i / 60f;
            double ang = k * Math.PI * 6 + t * 4;
            float rad = (0.6f + 3.5f * k) * S * pop;
            cube(x + (float) Math.cos(ang) * rad, y + 0.3f + k * (f.h + 2 * S), z + (float) Math.sin(ang) * rad, 0.07f * S, (float) ang, k * 3,
                i % 2 == 0 ? 0xA8C8FF : 0xFFB8F0, a * (1 - k * 0.5f));
        }
        // Lichtringe
        for (int k = 0; k < 3; k++) {
            float tk = (t - k * 0.15f) * 2;
            if (tk > 0 && tk < 1) ring(x, y + 0.06f, z, (0.5f + 7 * easeOut(tk)) * S, 0.35f * S * (1 - tk), 64, k == 1 ? 0xA8C8FF : 0xFFB8F0, 1 - tk);
        }
    }

    // ---------- 11: Seelenernte ----------
    private static void ghostFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.05f, 0.3f);
        Random r = new Random(f.seed);
        float cy = y + f.h * 0.6f;
        // Sense schwingt einmal um den Gegner
        float swing = easeOut(Math.min(1, t / 0.35f));
        double start = -Math.PI * 0.2, end = start + Math.PI * 2 * swing;
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double a0 = start + (end - start) * i / segs, a1 = start + (end - start) * (i + 1) / segs;
            float R = 3.2f * S, w = 0.9f * S * (float) Math.sin(Math.PI * i / segs);
            quad(new float[] {x + (float) Math.cos(a0) * R, cy, z + (float) Math.sin(a0) * R},
                new float[] {x + (float) Math.cos(a0) * (R + w), cy + 0.1f, z + (float) Math.sin(a0) * (R + w)},
                new float[] {x + (float) Math.cos(a1) * (R + w), cy + 0.1f, z + (float) Math.sin(a1) * (R + w)},
                new float[] {x + (float) Math.cos(a1) * R, cy, z + (float) Math.sin(a1) * R}, i % 2 == 0 ? 0x7FFFE0 : 0xE8FFFA, a * (0.3f + 0.7f * i / segs) * (1 - t));
        }
        // Seelen steigen in Spiralen auf
        for (int s = 0; s < 6; s++) {
            double off = Math.PI * 2 * s / 6;
            float k0 = Math.max(0, t - 0.2f - s * 0.04f) * 1.5f;
            if (k0 <= 0) continue;
            float px = x, py = cy, pz = z;
            for (int i = 1; i <= 16; i++) {
                float k = Math.min(1, k0) * i / 16f;
                double ang = off + k * Math.PI * 3;
                float rad = (0.4f + 2.2f * k) * S;
                float qx = x + (float) Math.cos(ang) * rad, qy = cy + k * 9 * S, qz = z + (float) Math.sin(ang) * rad;
                glowLine(px, py, pz, qx, qy, qz, 0.1f * S * (1 - k * 0.5f), pc(f, k), a * (1 - k * 0.6f));
                px = qx;
                py = qy;
                pz = qz;
            }
            glowCube(px, py, pz, 0.28f * S, t * 8 + s, t * 5, 0xE8FFFA, a);
        }
        // Geisternebel am Boden
        for (int i = 0; i < 40; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            float d = (1 + r.nextFloat() * 5) * S, k = (t * 0.8f + r.nextFloat()) % 1f;
            cube(x + (float) Math.cos(ang) * d, y + 0.2f + k * 2 * S, z + (float) Math.sin(ang) * d, 0.18f * S * (1 - k), (float) ang, k, 0x2EC4B6, a * 0.35f * (1 - k));
        }
        pillar(x, y, y + 6 * S, z, 0.7f * S * (1 - t), 0x7FFFE0, a * 0.4f, 0f);
        float tk = (t - 0.3f) * 2;
        if (tk > 0 && tk < 1) ring(x, y + 0.06f, z, (0.5f + 6 * easeOut(tk)) * S, 0.45f * S * (1 - tk), 64, 0x7FFFE0, 1 - tk);
    }

    // ---------- Bausteine für die Finisher 12–21 ----------

    /** Striche fallen (oder steigen) mit Leuchtkopf – Regen, Blüten, Sand, Licht. */
    private static void rain(Fx f, float t, float x, float y, float z, int n, float spread, float h, float dx, float dz, float len, float w,
                             int c1, int c2, float a, float speed, boolean up) {
        float S = S();
        Random r = new Random(f.seed + 7);
        for (int i = 0; i < n; i++) {
            float ox = (r.nextFloat() - 0.5f) * spread * S, oz = (r.nextFloat() - 0.5f) * spread * S;
            float k = (t * speed + r.nextFloat()) % 1f;
            float q = up ? k : 1 - k;
            float px = x + ox + dx * S * q, py = y + 0.2f + h * S * q, pz = z + oz + dz * S * q;
            float ly = up ? -len : len;
            int c = i % 2 == 0 ? c1 : c2;
            glowLine(px, py, pz, px - dx * len * 0.25f * S, py + ly * S, pz - dz * len * 0.25f * S, w * S, c, a * 0.8f);
            cube(px, py, pz, w * 1.6f * S, k * 9 + i, k * 5, c, a);
        }
    }

    /** Wellenringe am Boden. */
    private static void rings(float t, float x, float y, float z, int n, float delay, float maxR, float th, int c1, int c2) {
        float S = S();
        for (int k = 0; k < n; k++) {
            float tk = (t - k * delay) * 2;
            if (tk > 0 && tk < 1) ring(x, y + 0.06f + k * 0.01f, z, (0.5f + maxR * easeOut(tk)) * S, th * S * (1 - tk), 64, k % 2 == 0 ? c1 : c2, 1 - tk);
        }
    }

    /** Spiralen um den Gegner (Wirbel, Tornado, Feuer). */
    private static void helix(float t, float x, float y, float z, int strands, float h, float r0, float r1, float turns, float w, int c1, int c2, float a) {
        float S = S();
        float grow = easeOut(Math.min(1, t / 0.4f));
        for (int s = 0; s < strands; s++) {
            double off = Math.PI * 2 * s / strands + t * 6;
            float px = 0, py = 0, pz = 0;
            int segs = 30;
            for (int i = 0; i <= segs; i++) {
                float k = i / (float) segs * grow;
                double ang = off + k * Math.PI * 2 * turns;
                float rad = (r0 + (r1 - r0) * k) * S;
                float qx = x + (float) Math.cos(ang) * rad, qy = y + 0.1f + k * h * S, qz = z + (float) Math.sin(ang) * rad;
                if (i > 0) glowLine(px, py, pz, qx, qy, qz, w * S * (1 - k * 0.5f), s % 2 == 0 ? c1 : c2, a * (1 - k * 0.4f));
                px = qx;
                py = qy;
                pz = qz;
            }
        }
    }

    /** Zacken im Kreis (Kristalle, Zähne, Dornen); tilt > 0 nach außen, < 0 nach innen. */
    private static void spikes(float t, float x, float y, float z, int n, float R, float base, float hgt, float tilt, int c1, int c2, float a) {
        float S = S();
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n + 0.15;
            float k = Math.max(0, Math.min(1, (t - 0.05f - (i % 4) * 0.03f) * 5));
            float cx = (float) Math.cos(ang), cz = (float) Math.sin(ang);
            spike(x + cx * R * S, y, z + cz * R * S, base * S, (hgt + (i % 3) * hgt * 0.35f) * S * k, cx * tilt, cz * tilt, i % 2 == 0 ? c1 : c2, a);
        }
    }

    /** Würfel kreisen um den Gegner. */
    private static void orbit(float t, float x, float y, float z, int n, float R, float yOff, float size, float speed, int c1, int c2, float a) {
        float S = S();
        float pop = easeOut(Math.min(1, t / 0.3f));
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n + t * speed;
            float yy = y + yOff * S + (float) Math.sin(t * 6 + i) * 0.3f * S;
            glowCube(x + (float) Math.cos(ang) * R * S * pop, yy, z + (float) Math.sin(ang) * R * S * pop, size * S, t * 7 + i, t * 4, i % 2 == 0 ? c1 : c2, a);
        }
    }

    /** Strahlen schießen aus einem Punkt in alle Richtungen. */
    private static void burst(Fx f, float t, float x, float y, float z, int n, float len, float w, int c1, int c2, float a) {
        float S = S();
        Random r = new Random(f.seed + 3);
        float k = easeOut(Math.min(1, t / 0.35f));
        for (int i = 0; i < n; i++) {
            double th = r.nextDouble() * Math.PI * 2, ph = (r.nextDouble() - 0.3) * Math.PI * 0.8;
            float dx = (float) (Math.cos(th) * Math.cos(ph)), dy = (float) Math.sin(ph), dz = (float) (Math.sin(th) * Math.cos(ph));
            float l0 = len * S * k * 0.3f, l1 = len * S * k * (0.6f + 0.4f * r.nextFloat());
            glowLine(x + dx * l0, y + dy * l0, z + dz * l0, x + dx * l1, y + dy * l1, z + dz * l1, w * S * (1 - t), i % 2 == 0 ? c1 : c2, a);
        }
    }

    /** Wachsende Wand im Kreis (Flutwelle, Schattenkuppel). */
    private static void wall(float t, float x, float y, float z, float maxR, float hgt, int c1, int c2, float a) {
        float S = S();
        float k = easeOut(Math.min(1, t / 0.6f));
        float R = (0.8f + maxR * k) * S, H = hgt * S * (float) Math.sin(Math.PI * Math.min(1, t * 1.4f));
        int segs = 48;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            float wob0 = (float) Math.sin(i * 0.8 + t * 12) * 0.25f * S, wob1 = (float) Math.sin((i + 1) * 0.8 + t * 12) * 0.25f * S;
            float x0 = x + (float) Math.cos(a0) * R, z0 = z + (float) Math.sin(a0) * R, x1 = x + (float) Math.cos(a1) * R, z1 = z + (float) Math.sin(a1) * R;
            quad(new float[] {x0, y, z0}, new float[] {x0, y + H + wob0, z0}, new float[] {x1, y + H + wob1, z1}, new float[] {x1, y, z1}, i % 2 == 0 ? c1 : c2, a * 0.45f * (1 - t));
            line(x0, y + H + wob0, z0, x1, y + H + wob1, z1, 0.12f * S, 0xFFFFFF, a * (1 - t));
        }
    }

    /** Lichtvorhang hoch oben (Polarlicht). */
    private static void curtain(float t, float x, float y, float z, float R, float y0, float hgt, int c1, int c2, float a) {
        float S = S();
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            float k0 = i / (float) segs, k1 = (i + 1) / (float) segs;
            double a0 = Math.PI * 1.6 * k0 - Math.PI * 0.8, a1 = Math.PI * 1.6 * k1 - Math.PI * 0.8;
            float w0 = (float) Math.sin(k0 * 9 + t * 5) * 0.8f * S, w1 = (float) Math.sin(k1 * 9 + t * 5) * 0.8f * S;
            float x0 = x + (float) Math.cos(a0) * (R * S + w0), z0 = z + (float) Math.sin(a0) * (R * S + w0);
            float x1 = x + (float) Math.cos(a1) * (R * S + w1), z1 = z + (float) Math.sin(a1) * (R * S + w1);
            float yb = y + y0 * S, yt = yb + hgt * S * (0.7f + 0.3f * (float) Math.sin(k0 * 13 + t * 7));
            int c = mixRgb(c1, c2, 0.5f + 0.5f * (float) Math.sin(k0 * 6 + t * 3));
            quad(new float[] {x0, yb, z0}, new float[] {x0, yt, z0}, new float[] {x1, yt, z1}, new float[] {x1, yb, z1}, c, a * 0.5f);
            line(x0, yb, z0, x1, yb, z1, 0.15f * S, c, a);
        }
    }

    // ---------- 12–21: zusammengesetzte Finisher ----------
    private static void comboFx(Fx f, float t, float x, float y, float z) {
        float S = S(), a = fade(t, 0.04f, 0.28f);
        int[] p = PAL[f.style];
        float cy = y + f.h * 0.6f;
        switch (f.style) {
            case 11 -> { // Flutwelle
                wall(t, x, y, z, 7, 3.5f, p[0], p[1], a);
                helix(t, x, y, z, 3, 6, 0.6f, 2.2f, 1.5f, 0.12f, p[0], p[2], a);
                rain(f, t, x, y, z, 50, 9, 7, 0, 0, 0.4f, 0.06f, p[2], p[0], a, 1.3f, true);
                rings(t, x, y, z, 3, 0.12f, 8, 0.5f, p[0], p[1]);
            }
            case 12 -> { // Rostsplitter
                orbit(t, x, y, z, 14, 3.2f, f.h * 0.6f, 0.28f, 3.5f, p[0], p[1], a);
                spikes(t, x, y, z, 10, 2.6f, 0.35f, 1.6f, 0.4f, p[0], p[1], a);
                burst(f, t, x, cy, z, 30, 6, 0.08f, p[0], p[2], a);
                rings(t, x, y, z, 2, 0.15f, 7, 0.45f, p[0], p[1]);
            }
            case 13 -> { // Sandsturm
                helix(t, x, y, z, 6, 9, 0.6f, 4.5f, 2.2f, 0.14f, p[0], p[1], a);
                rain(f, t, x, y, z, 70, 10, 4, 7, 2, 0.6f, 0.05f, p[0], p[2], a, 2.2f, false);
                rings(t, x, y, z, 3, 0.1f, 8, 0.6f, p[1], p[0]);
            }
            case 14 -> { // Blütenregen
                rain(f, t, x, y, z, 80, 10, 10, 2, 1, 0.25f, 0.09f, p[0], p[2], a, 0.9f, false);
                orbit(t, x, y, z, 10, 2.4f, f.h + 0.6f, 0.2f, 2, p[0], p[1], a);
                burst(f, t, x, y + f.h + 1.5f * S, z, 10, 3, 0.18f, p[0], p[2], a);
                rings(t, x, y, z, 3, 0.15f, 6, 0.4f, p[0], p[1]);
            }
            case 15 -> { // Nordlicht
                curtain(t, x, y, z, 6, 7, 5, p[0], p[1], a);
                rain(f, t, x, y, z, 40, 8, 8, 0, 0, 0.6f, 0.06f, p[0], p[1], a, 0.8f, true);
                pillar(x, y, y + 8 * S, z, 0.6f * S * (1 - t), p[0], a * 0.5f, 0f);
                rings(t, x, y, z, 2, 0.2f, 7, 0.45f, p[0], p[1]);
            }
            case 16 -> { // Schattensprung
                wall(t, x, y, z, 3, 5, p[0], p[3], a);
                spikes(t, x, y, z, 12, 4.5f, 0.3f, 2.4f, -0.5f, p[0], p[1], a);
                burst(f, t, x, cy, z, 26, 7, 0.07f, p[1], p[2], a);
                orbit(t, x, y, z, 8, 1.8f, f.h * 0.5f, 0.22f, 9, p[1], p[0], a);
            }
            case 17 -> { // Amethystbruch
                spikes(t, x, y, z, 14, 2.2f, 0.5f, 2.6f, 0.45f, p[0], p[1], a);
                pillar(x, y, y + 6 * S, z, 0.8f * S * (1 - t), p[0], a * 0.6f, 0.1f);
                orbit(t, x, y, z, 12, 3.4f, f.h + 0.4f, 0.25f, 2.5f, p[0], p[2], a);
                burst(f, t, x, cy, z, 34, 6.5f, 0.07f, p[0], p[1], a);
                rings(t, x, y, z, 3, 0.12f, 8, 0.5f, p[0], p[1]);
            }
            case 18 -> { // Drachenatem
                helix(t, x, y, z, 2, 10, 0.8f, 3.2f, 2.5f, 0.3f, p[2], p[1], a);
                rain(f, t, x, y, z, 60, 6, 9, 0, 0, 0.7f, 0.08f, p[2], p[1], a, 1.6f, true);
                spikes(t, x, y, z, 16, 3.6f, 0.28f, 1.4f, 0.2f, p[0], p[3], a);
                rings(t, x, y, z, 3, 0.1f, 9, 0.6f, p[2], p[0]);
            }
            case 19 -> { // Finsternis
                float pop = easeOut(Math.min(1, t / 0.3f));
                float sy = y + f.h + 3 * S;
                for (int i = 0; i < 6; i++) arc(x, sy, z, i * 0.38f * S * pop, 0f, (float) (Math.PI * 2), 0.42f * S * pop, 0x000000, a, false);
                arc(x, sy, z, 2.3f * S * pop, 0f, (float) (Math.PI * 2), 0.35f * S * pop, p[0], a, false);
                arc(x, sy, z, 2.65f * S * pop, 0f, (float) (Math.PI * 2), 0.6f * S * pop, p[1], a * 0.4f, false);
                burst(f, t, x, sy, z, 40, 6, 0.06f, p[0], p[1], a);
                wall(t, x, y, z, 5, 2.5f, p[3], p[2], a);
                rings(t, x, y, z, 3, 0.15f, 8, 0.5f, p[0], p[1]);
            }
            case 21 -> { // Kürbisexplosion
                burst(f, t, x, cy, z, 40, 7, 0.1f, p[0], p[2], a);
                orbit(t, x, y, z, 8, 2.8f, f.h + 0.5f, 0.45f, 2, p[0], p[3], a);
                spikes(t, x, y, z, 10, 3.2f, 0.3f, 1.6f, 0.4f, p[1], p[0], a);
                rings(t, x, y, z, 3, 0.12f, 8, 0.6f, p[0], p[2]);
            }
            case 22 -> { // Netzfalle
                for (int i = 0; i < 12; i++) {
                    double ang = Math.PI * 2 * i / 12;
                    float k = easeOut(Math.min(1, t / 0.3f));
                    glowLine(x, y + f.h + 1.5f * S, z, x + (float) Math.cos(ang) * 5 * S * k, y + 0.1f, z + (float) Math.sin(ang) * 5 * S * k, 0.05f * S, p[0], a);
                }
                for (int j = 1; j <= 4; j++) ring(x, y + f.h * (1 - j * 0.2f) + 0.3f, z, j * 1.1f * S * easeOut(Math.min(1, t / 0.4f)), 0.06f * S, 12, p[0], a);
                orbit(t, x, y, z, 6, 1.6f, f.h + 1.2f, 0.25f, 3, p[1], p[3], a);
                rings(t, x, y, z, 2, 0.2f, 6, 0.4f, p[1], p[0]);
            }
            case 23 -> { // Totentanz
                helix(t, x, y, z, 4, 8, 3.5f, 0.4f, 1.5f, 0.12f, p[0], p[1], a);
                orbit(t, x, y, z, 12, 3, f.h * 0.7f, 0.3f, 4, p[0], p[1], a);
                rain(f, t, x, y, z, 40, 8, 7, 0, 0, 0.5f, 0.06f, p[1], p[0], a, 1.2f, true);
                rings(t, x, y, z, 3, 0.12f, 7, 0.5f, p[1], p[0]);
            }
            case 24 -> { // Hexensud
                wall(t, x, y, z, 2.5f, 1.5f, p[1], p[0], a);
                rain(f, t, x, y, z, 70, 4, 8, 0, 0, 0.3f, 0.12f, p[0], p[1], a, 1.5f, true);
                helix(t, x, y, z, 3, 9, 1.5f, 3, 2, 0.16f, p[1], p[0], a);
                burst(f, t, x, y + 8 * S, z, 30, 5, 0.08f, p[0], p[2], a);
                rings(t, x, y, z, 4, 0.1f, 9, 0.6f, p[0], p[1]);
            }
            case 25 -> { // Zuckersturm
                helix(t, x, y, z, 2, 7, 2.5f, 2.5f, 3, 0.25f, p[0], p[1], a);
                rain(f, t, x, y, z, 50, 9, 9, 1, 1, 0.4f, 0.08f, p[0], p[1], a, 1.2f, false);
                orbit(t, x, y, z, 10, 3.4f, 1, 0.3f, 3, p[2], p[1], a);
                rings(t, x, y, z, 2, 0.15f, 7, 0.5f, p[0], p[1]);
            }
            case 26 -> { // Eiswirbel
                helix(t, x, y, z, 6, 8, 0.8f, 4.5f, 1.8f, 0.1f, p[0], p[1], a);
                rain(f, t, x, y, z, 80, 11, 10, 5, 2, 0.3f, 0.06f, p[0], p[2], a, 1.8f, false);
                spikes(t, x, y, z, 12, 2.4f, 0.35f, 1.8f, 0.5f, p[2], p[0], a);
                rings(t, x, y, z, 3, 0.12f, 8, 0.5f, p[0], p[1]);
            }
            case 27 -> { // Sternennacht
                curtain(t, x, y, z, 7, 9, 3, p[3], p[1], a);
                burst(f, t, x, y + f.h + 3 * S, z, 24, 4, 0.12f, p[0], p[2], a);
                rain(f, t, x, y, z, 30, 12, 14, 4, 0, 1.4f, 0.06f, p[0], p[2], a, 1.4f, false);
                rings(t, x, y, z, 3, 0.15f, 8, 0.5f, p[0], p[1]);
            }
            case 28 -> { // Tauregen
                rain(f, t, x, y, z, 70, 9, 9, 0, 0, 0.5f, 0.05f, p[1], p[2], a, 1.6f, false);
                spikes(t, x, y, z, 18, 3, 0.18f, 1.2f, 0.2f, p[0], p[3], a);
                orbit(t, x, y, z, 8, 2, f.h + 0.3f, 0.22f, 2, p[2], p[1], a);
                rings(t, x, y, z, 2, 0.2f, 6, 0.4f, p[0], p[1]);
            }
            case 29 -> { // Brandung
                wall(t, x, y, z, 6, 2.5f, p[0], p[2], a);
                wall(Math.max(0, t - 0.15f), x, y, z, 4, 1.8f, p[1], p[0], a);
                rain(f, t, x, y, z, 50, 8, 5, 0, 0, 0.3f, 0.07f, p[2], p[0], a, 1.8f, true);
                rings(t, x, y, z, 3, 0.1f, 8, 0.5f, p[0], p[1]);
            }
            case 30 -> { // Abendglut
                float pop = easeOut(Math.min(1, t / 0.3f));
                for (int i = 0; i < 6; i++) arc(x, y + f.h + 3 * S, z, i * 0.4f * S * pop, 0f, (float) (Math.PI * 2), 0.42f * S * pop, mixRgb(p[0], p[1], i / 6f), a, false);
                burst(f, t, x, y + f.h + 3 * S, z, 30, 6, 0.08f, p[0], p[1], a);
                curtain(t, x, y, z, 6, 4, 2, p[0], p[1], a);
                rings(t, x, y, z, 2, 0.15f, 7, 0.5f, p[0], p[1]);
            }
            case 31 -> { // Rubinsplitter
                spikes(t, x, y, z, 12, 1.8f, 0.5f, 2.4f, 0.55f, p[0], p[2], a);
                burst(f, t, x, cy, z, 40, 7, 0.08f, p[0], p[1], a);
                orbit(t, x, y, z, 10, 3, f.h, 0.3f, 3, p[0], p[1], a);
                rings(t, x, y, z, 3, 0.12f, 8, 0.5f, p[0], p[1]);
            }
            case 32 -> { // Osterwunder
                orbit(t, x, y, z, 12, 2.6f, f.h * 0.5f, 0.4f, 2, p[0], p[1], a);
                orbit(t, x, y, z, 8, 1.6f, f.h + 0.8f, 0.3f, -3, p[2], p[3], a);
                rain(f, t, x, y, z, 50, 9, 8, 0, 0, 0.3f, 0.1f, p[0], p[2], a, 1.0f, true);
                rings(t, x, y, z, 3, 0.15f, 7, 0.5f, p[1], p[0]);
            }
            case 33 -> { // Mitternachtsschlag
                float pop = easeOut(Math.min(1, t / 0.25f));
                float sy = y + f.h + 2.5f * S;
                arc(x, sy, z, 2.4f * S * pop, 0f, (float) (Math.PI * 2), 0.18f * S, p[0], a, false);
                for (int i = 0; i < 12; i++) {
                    double ang = Math.PI * 2 * i / 12;
                    float r0 = 1.9f * S * pop, r1 = 2.3f * S * pop;
                    glowLine(x + camRx * (float) Math.cos(ang) * r0, sy + (float) Math.sin(ang) * r0, z + camRz * (float) Math.cos(ang) * r0,
                        x + camRx * (float) Math.cos(ang) * r1, sy + (float) Math.sin(ang) * r1, z + camRz * (float) Math.cos(ang) * r1, 0.07f * S, p[0], a);
                }
                double hand = -Math.PI / 2 + t * Math.PI * 4;
                glowLine(x, sy, z, x + camRx * (float) Math.cos(hand) * 1.8f * S, sy - (float) Math.sin(hand) * 1.8f * S, z + camRz * (float) Math.cos(hand) * 1.8f * S, 0.1f * S, p[1], a);
                rings(t, x, y, z, 3, 0.15f, 8, 0.5f, p[0], p[1]);
                wall(t, x, y, z, 4, 3, p[3], p[1], a);
            }
            case 34 -> { // Plasmasturm
                for (int i = 0; i < 5; i++) lightning(new Random(f.seed + i + (long) (t * 12)), x + (i - 2) * 1.5f * S, y + 12 * S, y, z, 2.5f * S, i % 2 == 0 ? p[0] : p[1], a, 0.12f * S);
                helix(t, x, y, z, 3, 7, 1, 2.5f, 2, 0.14f, p[0], p[1], a);
                orbit(t, x, y, z, 10, 3, f.h * 0.6f, 0.25f, 6, p[0], p[1], a);
                rings(t, x, y, z, 3, 0.1f, 9, 0.6f, p[0], p[1]);
            }
            case 35 -> { // Goldregen
                rain(f, t, x, y, z, 90, 10, 12, 0, 0, 0.2f, 0.12f, p[0], p[1], a, 1.4f, false);
                pillar(x, y, y + 10 * S, z, 1.0f * S * (1 - t), p[0], a * 0.6f, 0.1f);
                orbit(t, x, y, z, 14, 3.5f, f.h + 1, 0.3f, 2, p[0], p[2], a);
                burst(f, t, x, y + f.h + 2 * S, z, 30, 7, 0.08f, p[0], p[1], a);
                rings(t, x, y, z, 4, 0.1f, 10, 0.6f, p[0], p[3]);
            }
            default -> { // Himmelsurteil
                float drop = easeOut(Math.min(1, t / 0.25f));
                pillar(x, y, y + 22 * S * drop, z, 1.3f * S * (1 - t * 0.7f), p[0], a * 0.7f, 0.2f);
                pillar(x, y, y + 22 * S * drop, z, 0.5f * S * (1 - t * 0.7f), p[2], a, 0.4f);
                ring(x, y + f.h + 0.6f * S, z, 0.9f * S, 0.15f * S, 48, p[3], a);
                orbit(t, x, y, z, 16, 3.8f, f.h + 1.5f, 0.2f, 1.5f, p[0], p[1], a);
                burst(f, t, x, y + f.h + 2 * S, z, 36, 8, 0.07f, p[0], p[2], a);
                rain(f, t, x, y, z, 40, 10, 14, 0, 0, 1.2f, 0.05f, p[0], p[2], a, 1.4f, false);
                rings(t, x, y, z, 4, 0.1f, 10, 0.6f, p[3], p[1]);
            }
        }
    }

    // ---------- Kill-Banner (HUD) ----------

    /** Emblem der Kollektion im Kill-Banner (eigene Form und Bewegung je Kollektion). */
    private static void emblem(DrawContext ctx, Matrix3x2fStack m, int style, long el, float alpha, int rgb, int rgb2) {
        int a = (int) (alpha * 255) << 24;
        int soft = (int) (alpha * 110) << 24;
        float spin = el / 1000f;
        switch (style) {
            case 0 -> { // Sonne: Strahlenkranz + Kern
                for (int i = 0; i < 12; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 6 + spin));
                    int len = 18 + (i % 2 == 0 ? 8 : 3) + (int) (3 * Math.sin(el / 80.0 + i));
                    ctx.fill(-2, 10, 2, len, a | (i % 2 == 0 ? rgb : rgb2));
                    m.popMatrix();
                }
                for (int i = 0; i < 3; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 6));
                    ctx.fill(-12, -12, 12, 12, a | 0x1A0700);
                    m.popMatrix();
                }
                ctx.fill(-8, -8, 8, 8, a | rgb);
                ctx.fill(-5, -5, 5, 5, a | 0xFFF4C0);
            }
            case 1 -> { // Kristall: Sechseck + wegfliegende Splitter
                for (int i = 0; i < 3; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 3 + spin * 0.4));
                    ctx.fill(-8, -15, 8, 15, a | 0x0A2A5E);
                    ctx.fill(-6, -13, 6, 13, soft | rgb);
                    m.popMatrix();
                }
                ctx.fill(-4, -9, 4, 9, a | 0xEFFFFF);
                float k = Math.min(1, el / 500f);
                for (int i = 0; i < 8; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 4 + 0.3));
                    int d = (int) (16 + 22 * k);
                    ctx.fill(-1, d, 2, d + 6, (int) (alpha * (1 - k) * 255) << 24 | rgb2);
                    m.popMatrix();
                }
            }
            case 2 -> { // Leere: drehender Ring aus Teilchen um ein schwarzes Loch
                for (int i = 0; i < 16; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 8 - spin * 2.2));
                    int r0 = 14 + (i % 3);
                    ctx.fill(-2, r0, 2, r0 + 4, a | (i % 2 == 0 ? rgb : rgb2));
                    m.popMatrix();
                }
                m.pushMatrix();
                m.rotate((float) (Math.PI / 4 + spin));
                ctx.fill(-11, -11, 11, 11, a | 0x05000C);
                ctx.fill(-11, -11, 11, -9, soft | rgb2);
                ctx.fill(-11, 9, 11, 11, soft | rgb2);
                m.popMatrix();
                ctx.fill(-3, -3, 3, 3, soft | rgb);
            }
            case 3 -> { // Glitch: Quadrat mit RGB-Versatz und Zittern
                int jx = (el / 60) % 3 == 0 ? (int) ((el * 7) % 5) - 2 : 0;
                ctx.fill(-14 + jx - 2, -14, 14 + jx - 2, 14, soft | 0xFF00E6);
                ctx.fill(-14 + jx + 2, -14, 14 + jx + 2, 14, soft | 0x00E5FF);
                ctx.fill(-14 + jx, -14, 14 + jx, 14, a | 0x0A0A14);
                for (int y = -12; y < 12; y += 4) ctx.fill(-12 + jx, y, 12 + jx, y + 1, (int) (alpha * 90) << 24 | 0x5CFF3A);
                ctx.fill(-14 + jx, -14, 14 + jx, -12, a | 0x5CFF3A);
                ctx.fill(-14 + jx, 12, 14 + jx, 14, a | 0x5CFF3A);
            }
            case -1 -> { // ohne Skin: schlichte Raute
                m.pushMatrix();
                m.rotate((float) (Math.PI / 4));
                ctx.fill(-12, -12, 12, 12, a | 0x0B0D12);
                ctx.fill(-12, -12, 12, -10, a | rgb);
                ctx.fill(-12, 10, 12, 12, a | rgb);
                ctx.fill(-12, -12, -10, 12, a | rgb);
                ctx.fill(10, -12, 12, 12, a | rgb);
                m.popMatrix();
            }
            case 5 -> { // Blutmond: Sichel + Tropfen
                for (int i = 0; i < 14; i++) {
                    m.pushMatrix();
                    m.rotate((float) (-Math.PI * 0.85 + i * Math.PI * 0.12));
                    int th = 3 + (int) (4 * Math.sin(Math.PI * i / 13.0));
                    ctx.fill(-2, 12, 2, 12 + th, a | (i % 2 == 0 ? rgb : rgb2));
                    m.popMatrix();
                }
                ctx.fill(-5, -5, 5, 5, a | 0x2A0006);
                int drop = (int) ((el / 8) % 20);
                ctx.fill(-1, 6 + drop, 2, 9 + drop, (int) (alpha * (1 - drop / 20f) * 255) << 24 | rgb);
            }
            case 6 -> { // Rankenfluch: Blatt-Kranz
                for (int i = 0; i < 10; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 5 + spin * 0.6));
                    ctx.fill(-3, 9, 3, 18, a | (i % 2 == 0 ? rgb : rgb2));
                    ctx.fill(-1, 18, 1, 22, a | 0xE8FFB0);
                    m.popMatrix();
                }
                ctx.fill(-7, -7, 7, 7, a | 0x0A2A12);
                ctx.fill(-3, -3, 3, 3, a | rgb);
            }
            case 7 -> { // Donnerschlag: Blitz im Kreis
                for (int i = 0; i < 12; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI / 6 - spin));
                    ctx.fill(-1, 15, 1, 19, a | rgb);
                    m.popMatrix();
                }
                ctx.fill(-12, -12, 12, 12, soft | 0x0B1424);
                int fl = (el / 70) % 4 == 0 ? 0xFFFFFF : rgb2;
                ctx.fill(-1, -12, 4, -2, a | fl);
                ctx.fill(-5, -3, 3, 1, a | fl);
                ctx.fill(-4, 0, 1, 12, a | fl);
            }
            case 8 -> { // Vulkan: Berg mit Ausbruch
                for (int i = 0; i < 12; i++) {
                    int w = 2 + i * 2;
                    ctx.fill(-w, -4 + i * 2, w, -2 + i * 2, a | (i < 3 ? rgb2 : 0x3A0E06));
                }
                int up = (int) ((el / 6) % 18);
                for (int i = -1; i <= 1; i++) ctx.fill(i * 4 - 1, -8 - up, i * 4 + 1, -5 - up, (int) (alpha * (1 - up / 18f) * 255) << 24 | rgb);
                ctx.fill(-2, -6, 2, -2, a | rgb);
            }
            case 9 -> { // Stern: fünf Zacken drehen sich
                for (int i = 0; i < 5; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI * 2 / 5 + spin * 0.8));
                    ctx.fill(-3, -18, 3, -4, a | rgb);
                    ctx.fill(-1, -21, 1, -18, a | 0xFFFFFF);
                    m.popMatrix();
                }
                ctx.fill(-5, -5, 5, 5, a | rgb2);
                ctx.fill(-2, -2, 2, 2, a | 0xFFFFFF);
            }
            case 10 -> { // Geist: schwebende Gestalt mit Augen
                int bob = (int) (Math.sin(el / 160.0) * 2);
                ctx.fill(-12, -16, 12, 14, soft | rgb2);
                ctx.fill(-9, -14 + bob, 9, 8 + bob, a | rgb);
                ctx.fill(-7, -16 + bob, 7, -14 + bob, a | rgb);
                for (int i = 0; i < 3; i++) ctx.fill(-9 + i * 6, 8 + bob, -6 + i * 6, 12 + bob, a | rgb);
                ctx.fill(-5, -8 + bob, -2, -3 + bob, a | 0x061A1A);
                ctx.fill(2, -8 + bob, 5, -3 + bob, a | 0x061A1A);
            }
            case 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35 -> { // eigene Zeichen: Kranz aus Blättern/Zacken + Kern
                int[] e = EMB[style - 11];
                ctx.fill(-14, -14, 14, 14, soft | rgb2);
                for (int i = 0; i < e[0]; i++) {
                    m.pushMatrix();
                    m.rotate((float) (i * Math.PI * 2 / e[0] + spin * e[3] / 100.0));
                    ctx.fill(-e[2], -8 - e[1], e[2], -8, a | (i % 2 == 0 ? rgb : rgb2));
                    ctx.fill(-1, -10 - e[1], 1, -8 - e[1], a | 0xFFFFFF);
                    m.popMatrix();
                }
                if (e[4] == 1) {
                    m.pushMatrix();
                    m.rotate((float) (Math.PI / 4 + spin * 0.5));
                    ctx.fill(-6, -6, 6, 6, a | rgb2);
                    ctx.fill(-3, -3, 3, 3, a | rgb);
                    m.popMatrix();
                } else if (e[4] == 2) {
                    ctx.fill(-7, -7, 7, 7, a | 0x000000);
                    ctx.fill(-5, -5, 5, 5, a | rgb);
                    ctx.fill(-3, -3, 3, 3, a | 0x000000);
                } else {
                    ctx.fill(-6, -6, 6, 6, a | rgb);
                    ctx.fill(-3, -3, 3, 3, a | 0xFFFFFF);
                }
            }
            default -> { // Krone: Raute mit drei Zacken und Juwel
                m.pushMatrix();
                m.rotate((float) (Math.PI / 4));
                ctx.fill(-13, -13, 13, 13, a | 0x2A0008);
                ctx.fill(-13, -13, 13, -11, a | rgb2);
                ctx.fill(-13, 11, 13, 13, a | rgb2);
                ctx.fill(-13, -13, -11, 13, a | rgb2);
                ctx.fill(11, -13, 13, 13, a | rgb2);
                m.popMatrix();
                int up = (int) (6 * Math.min(1, el / 300f));
                for (int i = -1; i <= 1; i++) {
                    int x = i * 9;
                    int top = -22 - up - (i == 0 ? 6 : 0);
                    ctx.fill(x - 2, top, x + 2, -15, a | rgb2);
                    ctx.fill(x - 3, top - 3, x + 3, top, a | 0xFFF6DC);
                }
                ctx.fill(-4, -4, 4, 4, a | rgb);
                ctx.fill(-2, -2, 2, 2, a | 0xFFFFFF);
            }
        }
    }

    public static void hud(DrawContext ctx, RenderTickCounter tick) {
        Config c = Config.INSTANCE;
        if (!c.kfOn || bannerStart == 0) return;
        long el = System.currentTimeMillis() - bannerStart;
        if (el > 3000) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        int base = -1;
        int rgb = bannerStyle < 0 ? 0xD6D9E0 : base == -1 ? PAL[bannerStyle][0] : base == 0 ? Ui.rainbowAt(el / 3000f) & 0xFFFFFF : base;
        int rgb2 = bannerStyle < 0 ? 0x8A93A6 : base == -1 ? PAL[bannerStyle][1] : mixRgb(rgb, 0xFFFFFF, 0.5f);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        // Bildschirm-Blitz und farbiger Rand
        if (c.kfFlash && el < 700 && (bannerStyle < 0 || Skins.tier(bannerStyle) >= 2)) {
            float k = el / 700f;
            int fa = (int) ((1 - k) * (1 - k) * 110);
            ctx.fill(0, 0, sw, sh, fa << 24 | rgb);
            int va = (int) ((1 - k) * 200);
            int edge = Math.max(20, sh / 5);
            ctx.fillGradient(0, 0, sw, edge, va << 24 | rgb, rgb);
            ctx.fillGradient(0, sh - edge, sw, sh, rgb, va << 24 | rgb);
        }
        // Kill-Zeichen am Fadenkreuz
        if (el < 450) {
            float k = el / 450f;
            int ca = (int) ((1 - k) * 255) << 24;
            Matrix3x2fStack mm = ctx.getMatrices();
            mm.pushMatrix();
            mm.translate(sw / 2f, sh / 2f);
            mm.scale(1.4f - 0.4f * easeOut(Math.min(1, k * 3)), 1.4f - 0.4f * easeOut(Math.min(1, k * 3)));
            for (int i = 0; i < 4; i++) {
                mm.pushMatrix();
                mm.rotate((float) (Math.PI / 4 + i * Math.PI / 2));
                ctx.fill(-1, 4, 2, 11, ca | rgb);
                ctx.fill(0, 5, 1, 10, ca | 0xFFFFFF);
                mm.popMatrix();
            }
            mm.popMatrix();
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
        emblem(ctx, m, bannerStyle, el, alpha, rgb, rgb2);
        // Serien-Striche erscheinen nacheinander
        for (int i = 0; i < streak; i++) {
            long d = el - 120 - i * 70L;
            if (d < 0) continue;
            float pk = Math.min(1, d / 120f);
            int bx = -((streak - 1) * 6) / 2 + i * 6;
            int hh = (int) (7 * (pk < 1 ? 1.6f - 0.6f * pk : 1));
            ctx.fill(bx - 2, -hh, bx + 2, hh, a | 0xFFFFFF);
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
