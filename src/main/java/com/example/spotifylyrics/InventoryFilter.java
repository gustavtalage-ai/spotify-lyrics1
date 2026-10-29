package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

/** Inventar-Filter: schiebt Items von allein in die Slots, die du dafür festgelegt hast. */
public class InventoryFilter {
    private static int cooldown = 0;

    public static String idOf(ItemStack s) {
        return Registries.ITEM.getId(s.getItem()).toString();
    }

    /** Inventar-Index (0-8 Hotbar, 9-35 Rucksack) -> Slot-Nummer im Spieler-Inventar-Fenster. */
    static int slotId(int idx) {
        return idx < 9 ? 36 + idx : idx;
    }

    public static void tick(MinecraftClient mc) {
        try {
            Config cfg = Config.INSTANCE;
            if (!cfg.filterOn || cfg.filterRules.isEmpty()) return;
            if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
            if (mc.currentScreen != null || mc.player.isCreative() || mc.player.isSpectator()) return;
            if (--cooldown > 0) return;
            cooldown = cfg.filterStrict ? 4 : 8;
            if (mc.player.currentScreenHandler.syncId != 0) return;
            if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
            PlayerInventory inv = mc.player.getInventory();
            if (cfg.filterStrict) {
                // Nur Items mit Regel behalten: alles andere wegwerfen
                for (int i = 0; i < 36; i++) {
                    ItemStack s = inv.getStack(i);
                    if (s.isEmpty()) continue;
                    Long m = cfg.filterRules.get(idOf(s));
                    if (m != null && m != 0L) continue;
                    mc.interactionManager.clickSlot(0, slotId(i), 1, SlotActionType.THROW, mc.player);
                    return;
                }
            }
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty()) continue;
                Long m = cfg.filterRules.get(idOf(s));
                if (m == null || m == 0L) continue;
                if (((m >> i) & 1L) != 0) continue; // liegt schon richtig
                for (int j = 0; j < 36; j++) {
                    if (((m >> j) & 1L) == 0) continue;
                    if (!inv.getStack(j).isEmpty()) continue;
                    mc.interactionManager.clickSlot(0, slotId(i), 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(0, slotId(j), 0, SlotActionType.PICKUP, mc.player);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // nie das Spiel stören
        }
    }
}
