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
    /** Alle 30 Minuten Spielzeit gibt es eine Kiste. */
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

    // ---------- Seltenheit ----------
    public static final String[] TIER_NAMES = {"Gewöhnlich", "Selten", "Episch", "Legendär", "Mythisch"};
    public static final int[] TIER_COL = {0xFF4B69FF, 0xFF8847FF, 0xFFD32CE6, 0xFFEB4B4B, 0xFFFFD700};
    /** Chance je Seltenheit in Prozent. */
    public static final int[] TIER_W = {55, 25, 12, 6, 2};
    /** Wie stark der Finisher ist (Größe, Teilchen, Effekte). */
    private static final float[] QUALITY = {0.55f, 0.7f, 0.85f, 1f, 1.15f};
    /** Seltenheit je Kollektion (Reihenfolge wie WeaponSkins.BUNDLES). */
    private static final int[] TIER = {1, 1, 3, 0, 4, 2, 0, 2};
    /** Kollektionen sortiert: seltenste zuerst. */
    public static final int[] ORDER = {4, 2, 5, 7, 0, 1, 3, 6};

    public static int tier(int b) {
        return b < 0 || b >= TIER.length ? 0 : TIER[b];
    }

    public static int tierCol(int b) {
        return TIER_COL[tier(b)];
    }

    public static String tierName(int b) {
        return TIER_NAMES[tier(b)];
    }

    public static float quality(int b) {
        return b < 0 ? 0.8f : QUALITY[tier(b)];
    }

    /** Zufällige Seltenheit nach Chancen. */
    static int rollTier(Random r) {
        int x = r.nextInt(100), acc = 0;
        for (int i = 0; i < TIER_W.length; i++) {
            acc += TIER_W[i];
            if (x < acc) return i;
        }
        return 0;
    }

    /** Zufällige Kollektion einer Seltenheit. */
    static int bundleOfTier(int tier, Random r) {
        List<Integer> l = new ArrayList<>();
        for (int b = 0; b < bundles(); b++) if (tier(b) == tier) l.add(b);
        return l.isEmpty() ? r.nextInt(bundles()) : l.get(r.nextInt(l.size()));
    }

    /** Name der Kiste. */
    public static final String CASE = "Alpha-Kiste";
    /** SHA-256 des Admin-Codes (der Code selbst steht nicht im Mod). */
    private static final String ADMIN_SHA = "bf2c0ba5bc0889e6883ec82fd9c98b1436e25bf3f366d52c37dd328557692cca";

    public static boolean admin() {
        return Config.INSTANCE.admin;
    }

    /** Code einlösen: richtig = Admin (unendlich Kisten). */
    public static boolean redeem(String code) {
        try {
            String n = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "");
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(n.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            if (!sb.toString().equals(ADMIN_SHA)) return false;
            Config c = Config.INSTANCE;
            c.admin = true;
            c.save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Anzahl als Text ("∞" für Admin). */
    public static String packsText() {
        return admin() ? "∞" : String.valueOf(packsAvailable());
    }

    public static int packsAvailable() {
        Config c = Config.INSTANCE;
        if (c.admin) return 999;
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
        Random r = new Random();
        int tier = rollTier(r);
        List<int[]> free = new ArrayList<>();
        for (int b = 0; b < bundles(); b++)
            if (tier(b) == tier)
                for (int t = 0; t < TYPES.length; t++)
                    if (!owns(b, t)) free.add(new int[] {b, t});
        int[] res;
        if (free.isEmpty()) res = new int[] {bundleOfTier(tier, r), r.nextInt(TYPES.length), 1};
        else {
            int[] p = free.get(r.nextInt(free.size()));
            res = new int[] {p[0], p[1], 0};
            c.skinsOwned.add(key(p[0], p[1]));
            if (equipped(p[1]) < 0) c.skinsEquipped.put(TYPES[p[1]], p[0]); // erster Skin für die Waffe: gleich ausrüsten
        }
        if (!c.admin) c.packsOpened++;
        c.save();
        return res;
    }

    private static int ticks;

    public static void register() {
        // Einmalig: Skins, die der alte Admin-Code alle auf einmal gegeben hat, wieder wegnehmen.
        Config cfg = Config.INSTANCE;
        if (cfg.skinsRev < 1) {
            if (cfg.admin) {
                cfg.skinsOwned.clear();
                cfg.skinsEquipped.clear();
            }
            cfg.skinsRev = 1;
            cfg.save();
        }
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null || mc.player == null || mc.isPaused()) return;
            Config c = Config.INSTANCE;
            int before = packsAvailable();
            c.playMs += 50;
            if (!c.admin && packsAvailable() > before && mc.player != null) {
                SpotifyLyricsClient.chat(net.minecraft.text.Text.literal("§6§lAlpha Mod: §eNeue " + CASE + "! §7(K → PvP → Sammlung)"));
            }
            if (++ticks % 1200 == 0) c.save();
        });
    }
}
