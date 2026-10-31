package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Kampf-Effekte: zusätzliche Partikel bei Streitkolben-Schlag, Tod, Totem, Enderperle und Windkugel.
 * Alles nur bei dir sichtbar (nichts wird an den Server geschickt).
 */
public final class CombatFx {
    private CombatFx() {
    }

    private static final Map<Integer, Double> PEAK = new HashMap<>();
    private static final Map<Integer, Double> LASTY = new HashMap<>();
    private static final Set<Integer> DEAD = new HashSet<>();
    private static long ticks = 0;

    private static void p(MinecraftClient mc, ParticleEffect e, double x, double y, double z, double vx, double vy, double vz) {
        try {
            mc.particleManager.addParticle(e, x, y, z, vx, vy, vz);
        } catch (Throwable ignored) {
        }
    }

    /** Ring aus Partikeln am Boden, der nach außen fliegt. */
    private static void ring(MinecraftClient mc, ParticleEffect e, double x, double y, double z, int n, double speed) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            p(mc, e, x + Math.cos(a) * 0.4, y, z + Math.sin(a) * 0.4, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
        }
    }

    public static void tick(MinecraftClient mc) {
        ticks++;
        Config c = Config.INSTANCE;
        if (mc.world == null || mc.player == null) {
            PEAK.clear();
            LASTY.clear();
            DEAD.clear();
            return;
        }
        if (!c.fxOn) return;
        try {
            Set<Integer> alive = new HashSet<>();
            for (PlayerEntity pl : mc.world.getPlayers()) {
                int id = pl.getId();
                alive.add(id);
                double y = pl.getY();
                // Streitkolben: Fallhöhe merken, beim Aufkommen mit Treffer in der Nähe -> Schockwelle
                Double last = LASTY.get(id);
                if (last != null && y < last - 0.05) {
                    PEAK.putIfAbsent(id, last);
                } else if (last != null && PEAK.containsKey(id)) {
                    double drop = PEAK.remove(id) - y;
                    if (c.fxMace && drop > 1.5 && pl.getMainHandStack().isOf(Items.MACE) && someoneHurtNear(mc, pl)) mace(mc, pl, drop);
                }
                LASTY.put(id, y);
                // Tod
                boolean dead = pl.isDead() || pl.getHealth() <= 0;
                if (dead && !DEAD.contains(id)) {
                    DEAD.add(id);
                    if (c.fxDeath) death(mc, pl);
                } else if (!dead) {
                    DEAD.remove(id);
                }
            }
            PEAK.keySet().retainAll(alive);
            LASTY.keySet().retainAll(alive);
            // Spuren hinter Enderperlen und Windkugeln
            for (Entity e : mc.world.getEntities()) {
                if (c.fxPearl && e.getType() == EntityType.ENDER_PEARL) {
                    p(mc, ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY(), e.getZ(), 0, 0, 0);
                    if (ticks % 2 == 0) p(mc, ParticleTypes.END_ROD, e.getX(), e.getY(), e.getZ(), 0, 0, 0);
                } else if (c.fxWind && (e.getType() == EntityType.WIND_CHARGE || e.getType() == EntityType.BREEZE_WIND_CHARGE)) {
                    p(mc, ParticleTypes.CLOUD, e.getX(), e.getY(), e.getZ(), 0, 0, 0);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean someoneHurtNear(MinecraftClient mc, PlayerEntity attacker) {
        for (Entity e : mc.world.getOtherEntities(attacker, attacker.getBoundingBox().expand(4.0))) {
            if (e instanceof net.minecraft.entity.LivingEntity le && le.hurtTime >= 8) return true;
        }
        return false;
    }

    private static void mace(MinecraftClient mc, PlayerEntity pl, double drop) {
        double x = pl.getX(), y = pl.getY() + 0.1, z = pl.getZ();
        double speed = Math.min(0.9, 0.25 + drop * 0.04);
        ring(mc, ParticleTypes.CLOUD, x, y, z, 36, speed);
        ring(mc, ParticleTypes.CRIT, x, y + 0.3, z, 24, speed * 1.4);
        for (int i = 0; i < 14; i++) {
            p(mc, ParticleTypes.ELECTRIC_SPARK, x + (Math.random() - 0.5), y + 0.5 + Math.random(), z + (Math.random() - 0.5),
                (Math.random() - 0.5) * 0.8, Math.random() * 0.5, (Math.random() - 0.5) * 0.8);
        }
        p(mc, ParticleTypes.EXPLOSION, x, y + 0.6, z, 0, 0, 0);
        // Schild-Aufprall bei Gegnern, die blocken
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o != pl && o.isBlocking() && o.squaredDistanceTo(pl) < 25) ring(mc, ParticleTypes.ENCHANTED_HIT, o.getX(), o.getY() + 1.0, o.getZ(), 16, 0.3);
        }
    }

    private static void death(MinecraftClient mc, PlayerEntity pl) {
        double x = pl.getX(), y = pl.getY(), z = pl.getZ();
        for (int i = 0; i < 20; i++) {
            p(mc, ParticleTypes.SCULK_SOUL, x + (Math.random() - 0.5) * 0.8, y + Math.random() * 1.8, z + (Math.random() - 0.5) * 0.8,
                0, 0.05 + Math.random() * 0.08, 0);
        }
        for (int i = 0; i < 12; i++) {
            p(mc, ParticleTypes.LARGE_SMOKE, x, y + 1, z, (Math.random() - 0.5) * 0.2, 0.05, (Math.random() - 0.5) * 0.2);
        }
        ring(mc, ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.1, z, 30, 0.18);
    }

    /** Vom Pop-Erkenner aufgerufen, wenn bei jemandem ein Totem poppt. */
    static void totem(PlayerEntity pl) {
        Config c = Config.INSTANCE;
        if (!c.fxOn || !c.fxTotem) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        double x = pl.getX(), y = pl.getY(), z = pl.getZ();
        for (int k = 0; k < 3; k++) ring(mc, ParticleTypes.TOTEM_OF_UNDYING, x, y + 0.4 + k * 0.6, z, 20, 0.35);
        for (int i = 0; i < 10; i++) p(mc, ParticleTypes.END_ROD, x, y + 1, z, (Math.random() - 0.5) * 0.3, 0.2 + Math.random() * 0.2, (Math.random() - 0.5) * 0.3);
    }
}
