package com.example.spotifylyrics;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;

/** Hit-Assistent: Schlagen nur, wenn der Schlag (fast) voll aufgeladen ist, damit die Leiste nicht zurückgesetzt wird. */
final class HitAssist {
    private HitAssist() {}

    static final String[] NAMES = {"Schwert", "Axt", "Mace", "Speer", "Dreizack"};

    static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            Config c = Config.INSTANCE;
            if (!c.hitAssist || !world.isClient) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity)) return ActionResult.PASS;
            int kind = kindOf(player.getMainHandStack());
            if (kind < 0 || ((c.hitWeapons >> kind) & 1) == 0) return ActionResult.PASS;
            float need = Math.max(0.5f, Math.min(1.0f, c.hitAssistMin / 100f)) - 0.001f;
            if (player.getAttackCooldownProgress(0.5f) < need) return ActionResult.FAIL;
            return ActionResult.PASS;
        });
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
