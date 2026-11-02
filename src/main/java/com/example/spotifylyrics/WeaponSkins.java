package com.example.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Waffen-Skins passend zum Finisher (wie Skin-Kollektionen).
 * Die Skins liegen als eigene Modelle im Pack ("spotifylyrics:skin/sN/<waffe>") und ersetzen die Original-Texturen NICHT.
 * Nur Waffen, die DU hältst (Hand, Inventar, deine Figur), bekommen den Skin – Gegner sehen bei dir normale Waffen,
 * und deine Gegner haben bei dir ihre normalen Waffen.
 */
public final class WeaponSkins {
    private WeaponSkins() {
    }

    /** Name der Kollektion je Finisher (gleiche Reihenfolge wie KillFx.STYLES). */
    public static final String[] BUNDLES = {"Sonnenfeuer", "Frostkristall", "Leere", "Neon-Glitch", "Königsglanz", "Blutmond", "Giftranke", "Gewittersturm", "Magmakern", "Sternenstaub", "Geisterhauch",
        "Tiefsee", "Kupferrost", "Wüstensand", "Kirschblüte", "Polarlicht", "Schattenklinge", "Kristallherz", "Drachenschuppe", "Sonnenfinsternis", "Himmelsthron"};
    static final String VERSION = "v5";
    private static final String NS = "spotifylyrics";

    /** Waffen (Pfad), für die Skins im Pack liegen. */
    private static volatile Set<String> available = new HashSet<>();

    static boolean isWeapon(Item it) {
        return Skins.typeOf(it) >= 0;
    }

    /** Für die Vorschau: Waffe im Skin einer Kollektion. */
    static ItemStack preview(Item item, int style) {
        ItemStack s = new ItemStack(item);
        String p = Registries.ITEM.getId(item).getPath();
        if (available.contains(p)) s.set(DataComponentTypes.ITEM_MODEL, Identifier.of(NS, "skin/s" + style + "/" + p));
        return s;
    }

    /** Vom Mixin gerufen: Skin nur für Waffen, die der eigene Spieler hält. */
    public static ItemStack skinned(ItemStack stack, HeldItemContext held) {
        try {
            Config c = Config.INSTANCE;
            if (!c.wsOn || stack == null || stack.isEmpty() || held == null) return stack;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || held.getEntity() != mc.player) return stack;
            Identifier cur = stack.get(DataComponentTypes.ITEM_MODEL);
            if (cur != null && cur.getNamespace().equals(NS)) return stack;
            int b = Skins.equipped(Skins.typeOf(stack.getItem()));
            if (b < 0) return stack;
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (!id.getNamespace().equals("minecraft") || !available.contains(id.getPath())) return stack;
            ItemStack s = stack.copy();
            s.set(DataComponentTypes.ITEM_MODEL, Identifier.of(NS, "skin/s" + b + "/" + id.getPath()));
            return s;
        } catch (Throwable e) {
            return stack;
        }
    }

    /** Liest, welche Skins im Pack schon da sind. */
    static void scan(Path root) {
        Set<String> s = new HashSet<>();
        Path dir = root.resolve("assets").resolve(NS).resolve("items").resolve("skin").resolve("s0");
        try (Stream<Path> st = Files.list(dir)) {
            st.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".json")) s.add(n.substring(0, n.length() - 5));
            });
        } catch (Exception ignored) {
        }
        available = s;
    }

    /** Schreibt alle 5 Kollektionen als eigene Modelle ins Pack; gibt die Anzahl der Waffen zurück. */
    static int writeInto(Path root, ResourceManager rm) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Map<String, BufferedImage> srcCache = new HashMap<>();
        Set<String> made = new LinkedHashSet<>();
        for (Item it : Registries.ITEM) {
            if (!isWeapon(it)) continue;
            String path = Registries.ITEM.getId(it).getPath();
            JsonElement def = Designer.json(rm, Identifier.of("minecraft", "items/" + path + ".json"));
            if (def == null) continue;
            Set<Identifier> models = new LinkedHashSet<>();
            collectModels(def, models);
            if (models.isEmpty()) continue;
            boolean any = false;
            for (int k = 0; k < BUNDLES.length; k++) {
                Map<String, String> map = new HashMap<>();
                int idx = 0;
                for (Identifier m : models) {
                    JsonElement mj = Designer.json(rm, Identifier.of(m.getNamespace(), "models/" + m.getPath() + ".json"));
                    if (mj == null || !mj.isJsonObject() || !mj.getAsJsonObject().has("textures")) continue;
                    JsonObject tex = mj.getAsJsonObject().getAsJsonObject("textures");
                    JsonObject nt = new JsonObject();
                    for (var en : tex.entrySet()) {
                        if (!en.getValue().isJsonPrimitive()) continue;
                        String v = en.getValue().getAsString();
                        if (v.startsWith("#") || en.getKey().equals("particle")) continue;
                        Identifier t = Identifier.of(v);
                        String skinTex = "item/skin/s" + k + "/" + t.getNamespace() + "/" + t.getPath();
                        if (writeTexture(mc, root, t, skinTex, k, srcCache, path.equals("bow") || path.equals("crossbow"))) nt.addProperty(en.getKey(), NS + ":" + skinTex);
                    }
                    if (nt.size() == 0) continue;
                    String modelPath = "item/skin/s" + k + "/" + path + "_" + idx++;
                    JsonObject model = new JsonObject();
                    model.addProperty("parent", m.toString());
                    model.add("textures", nt);
                    if (!write(root, "models/" + modelPath + ".json", model.toString())) continue;
                    map.put(m.toString(), NS + ":" + modelPath);
                }
                if (map.isEmpty()) continue;
                JsonElement copy = def.deepCopy();
                replaceModels(copy, map);
                if (write(root, "items/skin/s" + k + "/" + path + ".json", copy.toString())) any = true;
            }
            if (any) made.add(path);
        }
        write(root, "../../skin_style.txt", VERSION);
        available = made;
        return made.size();
    }

    private static boolean writeTexture(MinecraftClient mc, Path root, Identifier t, String skinTex, int style, Map<String, BufferedImage> cache, boolean woodBody) {
        try {
            Path png = root.resolve("assets").resolve(NS).resolve("textures").resolve(skinTex + ".png");
            if (Files.exists(png)) return true;
            BufferedImage src = cache.get(t.toString());
            if (src == null) {
                InputSupplier<InputStream> sup = mc.getDefaultResourcePack().open(ResourceType.CLIENT_RESOURCES,
                    Identifier.of(t.getNamespace(), "textures/" + t.getPath() + ".png"));
                if (sup == null) return false;
                try (InputStream in = sup.get()) {
                    src = ImageIO.read(in);
                }
                if (src == null) return false;
                if (src.getHeight() > src.getWidth()) src = src.getSubimage(0, 0, src.getWidth(), src.getWidth()); // schon animiert: erstes Bild
                cache.put(t.toString(), src);
            }
            BufferedImage out = SkinGen.make(src, style, t.hashCode(), woodBody);
            Files.createDirectories(png.getParent());
            ImageIO.write(out, "png", png.toFile());
            Files.writeString(png.resolveSibling(png.getFileName() + ".mcmeta"), "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
            return true;
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.debug("Skin-Textur " + t + " ging nicht", e);
            return false;
        }
    }

    private static boolean write(Path root, String rel, String content) {
        try {
            Path p = rel.startsWith("../../") ? root.resolve(rel.substring(6)) : root.resolve("assets").resolve(NS).resolve(rel);
            Files.createDirectories(p.getParent());
            Files.writeString(p, content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void collectModels(JsonElement e, Set<Identifier> out) {
        if (e == null) return;
        if (e.isJsonObject()) {
            for (var en : e.getAsJsonObject().entrySet()) {
                if (en.getKey().equals("model") && en.getValue().isJsonPrimitive()) {
                    try {
                        out.add(Identifier.of(en.getValue().getAsString()));
                    } catch (Exception ignored) {
                    }
                } else {
                    collectModels(en.getValue(), out);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement v : e.getAsJsonArray()) collectModels(v, out);
        }
    }

    private static void replaceModels(JsonElement e, Map<String, String> map) {
        if (e == null) return;
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (var en : o.entrySet()) {
                if (en.getKey().equals("model") && en.getValue().isJsonPrimitive()) {
                    String v = en.getValue().getAsString();
                    String full = v.contains(":") ? v : "minecraft:" + v;
                    if (map.containsKey(full)) en.setValue(new JsonPrimitive(map.get(full)));
                } else {
                    replaceModels(en.getValue(), map);
                }
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (JsonElement v : a) replaceModels(v, map);
        }
    }
}
