package com.example.spotifylyrics;

import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Hit-Assistent: Schlagen nur, wenn die Leiste voll ist (sonst wird der Klick ganz verworfen,
 * auch das Schwingen, damit die Leiste nicht zurückgesetzt wird). Optional wird ein zu früher Klick gemerkt
 * und genau bei voller Leiste ausgeführt.
 */
final class HitAssist {
    private HitAssist() {}

    static final String[] NAMES = {"Schwert", "Axt", "Mace", "Speer", "Dreizack"};
    /** Wie viele Ticks ein gemerkter Klick gültig bleibt (15 = 0,75 Sekunden). */
    private static final int QUEUE_TICKS = 15;
    private static int pending = 0;

    static void register() {
        // Hauptsperre: der Klick wird abgebrochen, bevor Minecraft schwingt oder schlägt.
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            Config c = Config.INSTANCE;
            if (!c.hitAssist || client.currentScreen != null) return false;
            if (!weaponSelected(player)) return false;
            HitResult hr = client.crosshairTarget;
            if (hr == null || hr.getType() == HitResult.Type.BLOCK) return false;
            if (hr instanceof EntityHitResult eh && !(eh.getEntity() instanceof LivingEntity)) return false;
            if (ready(player)) return false;
            if (clickCount > 0 && c.hitQueue) pending = QUEUE_TICKS;
            return true;
        });
        // Zusätzliche Absicherung, falls ein Schlag anders ausgelöst wird.
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Config c = Config.INSTANCE;
            if (!c.hitAssist || !world.isClient()) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity)) return ActionResult.PASS;
            if (!weaponSelected(player)) return ActionResult.PASS;
            return ready(player) ? ActionResult.PASS : ActionResult.FAIL;
        });
    }

    /** Jeden Tick: gemerkten Klick bei voller Leiste ausführen. */
    static void tick(MinecraftClient mc) {
        if (pending <= 0) return;
        pending--;
        Config c = Config.INSTANCE;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || !c.hitAssist || !c.hitQueue || mc.currentScreen != null
            || !weaponSelected(p)) {
            pending = 0;
            return;
        }
        if (!ready(p)) return;
        pending = 0;
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity) {
            mc.interactionManager.attackEntity(p, eh.getEntity());
            p.swingHand(Hand.MAIN_HAND);
        }
    }

    private static boolean ready(PlayerEntity p) {
        float need = Math.max(0.5f, Math.min(1.0f, Config.INSTANCE.hitAssistMin / 100f)) - 0.001f;
        return p.getAttackCooldownProgress(0.5f) >= need;
    }

    private static boolean weaponSelected(PlayerEntity p) {
        int kind = kindOf(p.getMainHandStack());
        return kind >= 0 && ((Config.INSTANCE.hitWeapons >> kind) & 1) != 0;
    }

    /** 0 Schwert, 1 Axt, 2 Mace, 3 Speer, 4 Dreizack, -1 = andere Waffe. */
    static int kindOf(ItemStack s) {
        if (s == null || s.isEmpty()) return -1;
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        if (id.endsWith("_sword")) return 0;
        if (id.endsWith("_axe")) return 1;
        if (id.equals("mace")) return 2;
        if (id.endsWith("_spear")) return 3;
        if (id.equals("trident")) return 4;
        return -1;
    }
}
