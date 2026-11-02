package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

/**
 * Eigene Nachlade-Animation für Bogen und Armbrust mit Skin: Energie zieht sich spiralförmig um deine Hand zusammen,
 * am Fadenkreuz füllt sich ein Ring in der Farbe der Kollektion, und wenn voll geladen ist, gibt es einen Blitz + Sound.
 */
public final class BowFx {
    private BowFx() {
    }

    private static boolean wasUsing;
    private static boolean fullPlayed;
    private static int style = -1, type = -1;
    private static float progress;
    private static long fullAt;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(BowFx::tick);
    }

    static ParticleEffect particle(int s, int i) {
        return switch (s) {
            case 0 -> i % 3 == 0 ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
            case 1 -> i % 2 == 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.END_ROD;
            case 2 -> i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL;
            case 3 -> i % 2 == 0 ? ParticleTypes.GLOW : ParticleTypes.ELECTRIC_SPARK;
            case 5 -> i % 2 == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.DAMAGE_INDICATOR;
            case 6 -> i % 2 == 0 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SPORE_BLOSSOM_AIR;
            case 7 -> ParticleTypes.ELECTRIC_SPARK;
            default -> i % 3 == 0 ? ParticleTypes.WAX_OFF : ParticleTypes.END_ROD;
        };
    }

    private static void sound(String name, float pitch, float vol) {
        try {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("spotifylyrics", name)), pitch, vol));
        } catch (Throwable ignored) {
        }
    }

    private static final String[] SND = {"sonnenfeuer", "frostkristall", "leere", "neonglitch", "koenigsglanz", "blutmond", "giftranke", "gewitter", "magmakern", "sternenstaub", "geisterhauch",
        "tiefsee", "kupferrost", "wuestensand", "kirschbluete", "polarlicht", "schattenklinge", "kristallherz", "drachenschuppe", "finsternis", "himmelsthron",
        "kuerbisfluch", "spinnennetz", "knochenmond", "hexenkessel", "zuckerstange", "schneesturm", "nordstern", "fruehlingstau", "tropenwelle",
        "sonnenuntergang", "rubinglut", "osterglanz", "mitternacht", "plasmakern", "goldrausch"};

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            wasUsing = false;
            return;
        }
        boolean using = mc.player.isUsingItem();
        ItemStack active = using ? mc.player.getActiveItem() : ItemStack.EMPTY;
        int t = Skins.typeOf(active.getItem());
        boolean ranged = using && (t == 4 || t == 5);
        if (ranged) {
            type = t;
            style = Config.INSTANCE.wsOn ? Skins.equipped(t) : -1;
            int used = mc.player.getItemUseTime();
            int full = t == 4 ? 20 : 25;
            progress = Math.min(1f, used / (float) full);
            if (style >= 0) {
                // Energie-Spirale um die Hand
                Vec3d eye = mc.player.getEyePos(), look = mc.player.getRotationVec(1f);
                double yaw = Math.toRadians(mc.player.getYaw());
                double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
                Vec3d hand = eye.add(look.multiply(0.8)).add(rx * 0.3, -0.25, rz * 0.3);
                int n = progress < 1 ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    double ang = used * 0.7 + i * Math.PI;
                    double rad = 0.9 * (1 - progress) + 0.15;
                    Vec3d up = new Vec3d(0, 1, 0);
                    Vec3d side = new Vec3d(rx, 0, rz);
                    Vec3d p = hand.add(side.multiply(Math.cos(ang) * rad)).add(up.multiply(Math.sin(ang) * rad));
                    Vec3d v = hand.subtract(p).multiply(0.15);
                    mc.world.addParticleClient(particle(style, used + i), p.x, p.y, p.z, v.x, v.y, v.z);
                }
                if (used % (t == 4 ? 5 : 6) == 0 && progress < 1) sound("kill." + SND[style] + ".k1", 0.6f + progress * 0.8f, 0.25f);
                if (progress >= 1 && !fullPlayed) {
                    fullPlayed = true;
                    fullAt = System.currentTimeMillis();
                    for (int i = 0; i < 16; i++) {
                        double ang = Math.PI * 2 * i / 16;
                        mc.world.addParticleClient(particle(style, i), hand.x, hand.y, hand.z, Math.cos(ang) * 0.25 * rx, Math.sin(ang) * 0.25, Math.cos(ang) * 0.25 * rz);
                    }
                    sound("kill." + SND[style] + ".k3", 1.2f, 0.55f);
                }
            }
        } else {
            if (wasUsing && (type == 4 || type == 5)) {
                // losgelassen = geschossen (Armbrust: beim Laden nicht, aber das Schießen merken wir am Kill-Zeitfenster)
                KillFx.lastShotMs = System.currentTimeMillis();
                KillFx.lastShotStyle = style;
            }
            fullPlayed = false;
            progress = 0;
        }
        // Armbrust geladen in der Hand: Schuss mit Rechtsklick
        if (!using && mc.options.useKey.isPressed() && Skins.typeOf(mc.player.getMainHandStack().getItem()) == 5) {
            KillFx.lastShotMs = System.currentTimeMillis();
            KillFx.lastShotStyle = Config.INSTANCE.wsOn ? Skins.equipped(5) : -1;
        }
        wasUsing = ranged;
    }

    /** Lade-Ring am Fadenkreuz. */
    public static void hud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        boolean flash = System.currentTimeMillis() - fullAt < 300;
        if ((!wasUsing || style < 0) && !flash) return;
        int rgb = SkinGen.PAL[Math.max(0, style)][2];
        int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;
        int segs = 24, on = (int) (segs * progress);
        Matrix3x2fStack m = ctx.getMatrices();
        for (int i = 0; i < segs; i++) {
            m.pushMatrix();
            m.translate(cx, cy);
            m.rotate((float) (Math.PI * 2 * i / segs));
            int col = i < on ? (0xFF000000 | rgb) : 0x55FFFFFF;
            ctx.fill(-1, -14, 1, -10, col);
            m.popMatrix();
        }
        if (flash) {
            float k = (System.currentTimeMillis() - fullAt) / 300f;
            int a = (int) ((1 - k) * 200) << 24;
            int r = (int) (14 + 10 * k);
            ctx.fill(cx - r, cy - 1, cx + r, cy + 1, a | 0xFFFFFF);
            ctx.fill(cx - 1, cy - r, cx + 1, cy + r, a | 0xFFFFFF);
        }
    }
}
