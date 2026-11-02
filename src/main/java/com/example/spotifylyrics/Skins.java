package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Skin-Sammlung: welche Skins du hast, was ausgerüstet ist, und Packs, die man mit Spielzeit öffnet. */
public final class Skins {
    private Skins() {
    }

    /** Waffen-Typen. */
    public static final String[] TYPES = {"sword", "axe", "mace", "spear", "bow", "crossbow"};
    public static final String[] TYPE_NAMES = {"Schwert", "Axt", "Streitkolben", "Speer", "Bogen", "Armbrust"};
    /** Alle 30 Minuten Spielzeit gibt es ein Pack. */
    public static final long PACK_MS = 30L * 60 * 1000;

    public static int bundles() {
        return WeaponSkins.BUNDLES.length;
    }

    public static int typeOf(Item it) {
        if (it == null) return -1;
        Identifier id = Registries.ITEM.getId(it);
        if (!id.getNamespace().equals("minecraft")) return -1;
        String p = id.getPath();
        if (p.endsWith("_sword")) return 0;
        if (p.endsWith("_axe")) return 1;
        if (p.equals("mace")) return 2;
        if (p.endsWith("_spear")) return 3;
        if (p.equals("bow")) return 4;
        if (p.equals("crossbow")) return 5;
        return -1;
    }

    /** Beispiel-Item je Typ (für Vorschau und Karten). */
    public static Item showItem(int type) {
        String[] ids = {"netherite_sword", "netherite_axe", "mace", "netherite_spear", "bow", "crossbow"};
        try {
            Identifier id = Identifier.of("minecraft", ids[type]);
            if (Registries.ITEM.containsId(id)) return Registries.ITEM.get(id);
        } catch (Exception ignored) {
        }
        return type == 3 ? Items.TRIDENT : Items.NETHERITE_SWORD;
    }

    static String key(int b, int t) {
        return b + ":" + TYPES[t];
    }

    public static boolean owns(int b, int t) {
        return Config.INSTANCE.skinsOwned.contains(key(b, t));
    }

    public static int ownedCount(int t) {
        int n = 0;
        for (int b = 0; b < bundles(); b++) if (owns(b, t)) n++;
        return n;
    }

    public static int totalOwned() {
        return Config.INSTANCE.skinsOwned.size();
    }

    /** Ausgerüsteter Skin (Kollektion) für einen Typ, -1 = Standard. */
    public static int equipped(int t) {
        if (t < 0) return -1;
        Integer b = Config.INSTANCE.skinsEquipped.get(TYPES[t]);
        if (b == null || b < 0 || b >= bundles() || !owns(b, t)) return -1;
        return b;
    }

    public static void equip(int t, int b) {
        Config c = Config.INSTANCE;
        if (b < 0) c.skinsEquipped.remove(TYPES[t]);
        else if (owns(b, t)) c.skinsEquipped.put(TYPES[t], b);
        c.save();
    }

    public static int packsAvailable() {
        Config c = Config.INSTANCE;
        return Math.max(0, c.packsBonus + (int) (c.playMs / PACK_MS) - c.packsOpened);
    }

    /** Millisekunden bis zum nächsten Pack. */
    public static long msToNext() {
        return PACK_MS - (Config.INSTANCE.playMs % PACK_MS);
    }

    /** Pack öffnen: {Kollektion, Typ, 1 = doppelt}. null, wenn kein Pack da ist. */
    public static int[] openPack() {
        if (packsAvailable() <= 0) return null;
        Config c = Config.INSTANCE;
        List<int[]> free = new ArrayList<>();
        for (int b = 0; b < bundles(); b++)
            for (int t = 0; t < TYPES.length; t++)
                if (!owns(b, t)) free.add(new int[] {b, t});
        Random r = new Random();
        int[] res;
        if (free.isEmpty()) res = new int[] {r.nextInt(bundles()), r.nextInt(TYPES.length), 1};
        else {
            int[] p = free.get(r.nextInt(free.size()));
            res = new int[] {p[0], p[1], 0};
            c.skinsOwned.add(key(p[0], p[1]));
            if (equipped(p[1]) < 0) c.skinsEquipped.put(TYPES[p[1]], p[0]); // erster Skin für die Waffe: gleich ausrüsten
        }
        c.packsOpened++;
        c.save();
        return res;
    }

    private static int ticks;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null || mc.player == null || mc.isPaused()) return;
            Config c = Config.INSTANCE;
            int before = packsAvailable();
            c.playMs += 50;
            if (packsAvailable() > before && mc.player != null) {
                SpotifyLyricsClient.chat(net.minecraft.text.Text.literal("§6§lAlpha Mod: §eNeues Skin-Pack! §7(K → PvP → Sammlung)"));
            }
            if (++ticks % 1200 == 0) c.save();
        });
    }
}
