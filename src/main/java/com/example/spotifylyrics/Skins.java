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
    /** Früher: alle 30 Minuten ein Pack (nur noch für die Umrechnung). */
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
    /** Wie stark der Finisher ist (Größe, Teilchen, Effekte). */
    private static final float[] QUALITY = {0.55f, 0.7f, 0.85f, 1f, 1.15f};
    /** Seltenheit je Kollektion (Reihenfolge wie WeaponSkins.BUNDLES). */
    private static final int[] TIER = {1, 1, 3, 0, 4, 2, 0, 2, 2, 1, 3, 0, 0, 0, 1, 1, 1, 2, 2, 3, 4,
        2, 1, 3, 4, 1, 2, 3, 1, 1, 2, 2, 2, 0, 3, 4};
    /** Saison-Kollektionen: Index der Kiste, aus der sie kommen (-1 = normale Kisten). */
    private static final int[] SEASON = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        3, 3, 3, 3, 4, 4, 4, 5, 6, 6, -1, 5, -1, -1, -1};
    /** Kollektionen sortiert: seltenste zuerst. */
    public static final int[] ORDER = order();

    private static int[] order() {
        int n = WeaponSkins.BUNDLES.length;
        Integer[] o = new Integer[n];
        for (int i = 0; i < n; i++) o[i] = i;
        java.util.Arrays.sort(o, (a, b) -> tier(b) != tier(a) ? tier(b) - tier(a) : a - b);
        int[] r = new int[n];
        for (int i = 0; i < n; i++) r[i] = o[i];
        return r;
    }

    /** Aus welcher Kiste eine Kollektion kommt (-1 = normale Kisten). */
    public static int season(int b) {
        return b < 0 || b >= SEASON.length ? -1 : SEASON[b];
    }

    /** Kann diese Kiste diese Kollektion enthalten? */
    public static boolean inCase(int k, int b) {
        return k < 3 ? season(b) < 0 : season(b) == k;
    }

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

    // ---------- Kisten ----------
    public static final String[] CASES = {"Bronze-Kiste", "Alpha-Kiste", "Mythos-Kiste", "Halloween-Kiste", "Winter-Kiste", "Frühlings-Kiste", "Sommer-Kiste"};
    public static final int[] CASE_PRICE = {15, 30, 80, 40, 40, 40, 40};
    public static final int[] CASE_COL = {0xFFCD7F32, 0xFFFFC23D, 0xFFB14BFF, 0xFFFF8A00, 0xFF9AD8FF, 0xFF8AE070, 0xFFFF7A5A};
    /** Chancen je Kiste und Seltenheit in Promille. */
    public static final int[][] CASE_W = {
        {700, 220, 60, 17, 3},
        {550, 250, 120, 60, 20},
        {200, 300, 280, 160, 60},
        {0, 450, 330, 160, 60},
        {0, 450, 330, 220, 0},
        {0, 600, 400, 0, 0},
        {0, 600, 400, 0, 0}
    };
    /** Saison: {Startmonat, Starttag, Endmonat, Endtag} (über den Jahreswechsel möglich). */
    private static final int[][] SEASON_DATES = {{10, 10, 11, 10}, {12, 1, 1, 10}, {3, 15, 4, 30}, {6, 15, 8, 31}};

    /** Ist die Saison-Kiste gerade im Shop? (Normale Kisten immer; Admin sieht alle.) */
    public static boolean caseActive(int k) {
        if (k < 3 || adminOn()) return true;
        return inSeason(k);
    }

    public static boolean inSeason(int k) {
        if (k < 3) return true;
        int[] d = SEASON_DATES[k - 3];
        java.time.LocalDate now = java.time.LocalDate.now();
        int v = now.getMonthValue() * 100 + now.getDayOfMonth(), a = d[0] * 100 + d[1], e = d[2] * 100 + d[3];
        return a <= e ? v >= a && v <= e : v >= a || v <= e;
    }

    /** Kisten, die gerade im Shop sind. */
    public static List<Integer> activeCases() {
        List<Integer> l = new ArrayList<>();
        for (int k = 0; k < CASES.length; k++) if (caseActive(k)) l.add(k);
        return l;
    }

    /** Erste Saison-Kiste, die gerade läuft (-1 = keine). */
    public static int currentSeasonCase() {
        for (int k = 3; k < CASES.length; k++) if (inSeason(k)) return k;
        return -1;
    }

    /** Chance als Text, z. B. "5,5%". */
    public static String pct(int permille) {
        return permille % 10 == 0 ? (permille / 10) + "%" : (permille / 10) + "," + (permille % 10) + "%";
    }

    /** Zufällige Seltenheit nach den Chancen einer Kiste. */
    static int rollTier(Random r, int k) {
        int[] w = CASE_W[Math.max(0, Math.min(CASES.length - 1, k))];
        int x = r.nextInt(1000), acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i];
            if (x < acc) return i;
        }
        return 0;
    }

    /** Zufällige Kollektion einer Seltenheit aus einer Kiste (gibt es die Seltenheit dort nicht, die nächste passende). */
    static int bundleOfTier(int tier, Random r, int k) {
        for (int d = 0; d < 5; d++) {
            for (int t : new int[] {tier - d, tier + d}) {
                List<Integer> l = new ArrayList<>();
                for (int b = 0; b < bundles(); b++) if (tier(b) == t && inCase(k, b)) l.add(b);
                if (!l.isEmpty()) return l.get(r.nextInt(l.size()));
            }
        }
        return r.nextInt(bundles());
    }

    // ---------- Admin ----------
    /** SHA-256 des Admin-Codes (der Code selbst steht nicht im Mod). */
    private static final String ADMIN_SHA = "bf2c0ba5bc0889e6883ec82fd9c98b1436e25bf3f366d52c37dd328557692cca";

    /** Admin-Code wurde eingelöst. */
    public static boolean admin() {
        return Config.INSTANCE.admin;
    }

    /** Admin-Modus ist gerade an (unendlich Coins). */
    public static boolean adminOn() {
        return Config.INSTANCE.admin && Config.INSTANCE.adminOn;
    }

    public static void toggleAdmin() {
        Config c = Config.INSTANCE;
        if (!c.admin) return;
        c.adminOn = !c.adminOn;
        c.save();
    }

    /** Code einlösen: richtig = Admin (unendlich Coins, an/aus schaltbar). */
    public static boolean redeem(String code) {
        try {
            String n = code == null ? "" : code.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "");
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(n.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            if (!sb.toString().equals(ADMIN_SHA)) return false;
            Config c = Config.INSTANCE;
            c.admin = true;
            c.adminOn = true;
            c.save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Coins aus Spielzeit ----------
    /** 1 Coin je Minute Spielzeit. */
    public static final long COIN_MS = 60L * 1000;

    public static int coins() {
        return adminOn() ? Integer.MAX_VALUE : Config.INSTANCE.coins;
    }

    public static String coinsText() {
        return adminOn() ? "∞" : String.valueOf(Config.INSTANCE.coins);
    }

    /** Coins aus Spielzeit, die noch umgewandelt werden können. */
    public static int pending() {
        Config c = Config.INSTANCE;
        return (int) Math.max(0, (c.playMs - c.convertedMs) / COIN_MS);
    }

    /** Millisekunden bis zum nächsten Coin. */
    public static long msToNextCoin() {
        Config c = Config.INSTANCE;
        return COIN_MS - Math.floorMod(c.playMs - c.convertedMs, COIN_MS);
    }

    /** Spielzeit in Coins umwandeln; gibt die Anzahl zurück. */
    public static int convert() {
        int n = pending();
        if (n <= 0) return 0;
        Config c = Config.INSTANCE;
        c.coins += n;
        c.convertedMs += n * COIN_MS;
        c.save();
        return n;
    }

    public static boolean canOpen(int k) {
        return caseActive(k) && (adminOn() || Config.INSTANCE.coins >= CASE_PRICE[k]);
    }

    /** Für das PvP-Menü: wie viele Alpha-Kisten man sich leisten kann. */
    public static int packsAvailable() {
        return adminOn() ? 999 : (Config.INSTANCE.coins + pending()) / CASE_PRICE[1];
    }

    /** Kiste öffnen: {Kollektion, Typ, 1 = doppelt}. null, wenn zu wenig Coins. */
    public static int[] openCase(int k) {
        if (!canOpen(k)) return null;
        Config c = Config.INSTANCE;
        Random r = new Random();
        int tier = tier(bundleOfTier(rollTier(r, k), r, k));
        List<int[]> free = new ArrayList<>();
        for (int b = 0; b < bundles(); b++)
            if (tier(b) == tier && inCase(k, b))
                for (int t = 0; t < TYPES.length; t++)
                    if (!owns(b, t)) free.add(new int[] {b, t});
        int[] res;
        if (free.isEmpty()) res = new int[] {bundleOfTier(tier, r, k), r.nextInt(TYPES.length), 1};
        else {
            int[] p = free.get(r.nextInt(free.size()));
            res = new int[] {p[0], p[1], 0};
            c.skinsOwned.add(key(p[0], p[1]));
            if (equipped(p[1]) < 0) c.skinsEquipped.put(TYPES[p[1]], p[0]); // erster Skin für die Waffe: gleich ausrüsten
        }
        if (!adminOn()) c.coins -= CASE_PRICE[k];
        c.packsOpened++;
        c.save();
        return res;
    }

    private static int ticks;

    public static void register() {
        Config cfg = Config.INSTANCE;
        // Einmalig: Skins, die der alte Admin-Code alle auf einmal gegeben hat, wieder wegnehmen.
        if (cfg.skinsRev < 1) {
            if (cfg.admin) {
                cfg.skinsOwned.clear();
                cfg.skinsEquipped.clear();
            }
            cfg.skinsRev = 1;
        }
        // Einmalig: alte Packs in Coins umrechnen (1 Pack = eine Alpha-Kiste).
        if (cfg.skinsRev < 2) {
            int old = cfg.admin ? 0 : Math.max(0, cfg.packsBonus + (int) (cfg.playMs / PACK_MS) - cfg.packsOpened);
            cfg.coins += CASE_PRICE[1] * (1 + old);
            cfg.convertedMs = cfg.playMs;
            cfg.skinsRev = 2;
        }
        cfg.save();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world == null || mc.player == null || mc.isPaused()) return;
            Config c = Config.INSTANCE;
            int before = pending();
            c.playMs += 50;
            int now = pending();
            if (now > before && now % 30 == 0 && mc.player != null) {
                SpotifyLyricsClient.chat(net.minecraft.text.Text.literal("§6§lAlpha Mod: §e" + now + " Coins §7aus Spielzeit bereit – umwandeln unter K → PvP → Sammlung"));
            }
            if (++ticks % 1200 == 0) c.save();
        });
    }
}
