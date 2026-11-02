package com.example.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Item-Designer: legt für gewählte Items/Blöcke ein eigenes Bild als Textur fest (wie ein eigenes Texture Pack)
 * und spielt einen eigenen Sound beim Abbauen, Schlagen oder Benutzen. Die Bilder landen in einem automatisch
 * erstellten Resource Pack "AlphaMod Designer", das die Mod selbst einschaltet und neu lädt.
 */
public final class Designer {
    private Designer() {
    }

    static final String PACK_DIR = "AlphaMod Designer";
    static final String PACK_ID = "file/" + PACK_DIR;
    private static final int SIZE = 64;

    // ---------- Ordner ----------

    public static Path imageFolder() {
        Path p = FabricLoader.getInstance().getGameDir().resolve("alphamod").resolve("bilder");
        try {
            Files.createDirectories(p);
        } catch (Exception ignored) {
        }
        return p;
    }

    public static List<Path> images() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(imageFolder())) {
            s.filter(Designer::isImage)
             .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
             .forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    static boolean isImage(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp") || n.endsWith(".gif");
    }

    static boolean isSound(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    static void openFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
                new ProcessBuilder("explorer.exe", dir.toString()).start();
            }
        } catch (Exception ignored) {
        }
    }

    /** Name eines Items für die Anzeige. */
    static String itemName(String id) {
        try {
            Identifier i = Identifier.of(id);
            if (Registries.ITEM.containsId(i)) return Registries.ITEM.get(i).getName().getString();
        } catch (Exception ignored) {
        }
        return id;
    }

    static Config.Design find(String id) {
        for (Config.Design d : Config.INSTANCE.designs) if (d.item.equals(id)) return d;
        return null;
    }

    // ---------- Sounds im Spiel ----------

    private static BlockPos minePos;
    private static String mineId;
    private static int mineAge;
    private static boolean useWas;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Designer::tick);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            try {
                if (world.isClient() && player == MinecraftClient.getInstance().player) {
                    Config.Design d = find(Registries.ITEM.getId(player.getMainHandStack().getItem()).toString());
                    if (d != null) sound(d);
                }
            } catch (Throwable ignored) {
            }
            return ActionResult.PASS;
        });
    }

    private static void sound(Config.Design d) {
        if (d.sound == null || d.sound.isEmpty()) return;
        Soundboard.playFx(Soundboard.folder().resolve(d.sound), Math.max(0, d.volume) / 100f);
    }

    private static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || Config.INSTANCE.designs.isEmpty()) {
            minePos = null;
            return;
        }
        try {
            // Abbauen: Block merken, auf den du beim Schlagen schaust; ist er kurz danach weg, Sound abspielen
            if (minePos != null) {
                BlockState now = mc.world.getBlockState(minePos);
                String nowId = Registries.BLOCK.getId(now.getBlock()).toString();
                if (!nowId.equals(mineId)) {
                    Config.Design d = find(mineId);
                    if (d != null) sound(d);
                    minePos = null;
                } else if (++mineAge > 10) {
                    minePos = null;
                }
            }
            if (mc.options.attackKey.isPressed() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
                String id = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString();
                if (find(id) != null) {
                    minePos = pos;
                    mineId = id;
                    mineAge = 0;
                }
            }
            // Benutzen (Rechtsklick) mit einem Item aus dem Designer
            boolean use = mc.options.useKey.isPressed();
            if (use && !useWas && mc.currentScreen == null) {
                ItemStack s = mc.player.getMainHandStack();
                if (!s.isEmpty()) {
                    Config.Design d = find(Registries.ITEM.getId(s.getItem()).toString());
                    if (d != null && !(s.getItem() instanceof net.minecraft.item.BlockItem)) sound(d);
                }
            }
            useWas = use;
        } catch (Throwable ignored) {
        }
    }

    // ---------- Resource Pack bauen ----------

    /** Baut das Pack aus allen Designs neu und lädt die Texturen neu. */
    public static void apply() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            Path root = mc.getResourcePackDir().resolve(PACK_DIR);
            deleteTree(root.resolve("assets"));
            Files.createDirectories(root);
            Files.writeString(root.resolve("pack.mcmeta"),
                "{\n  \"pack\": {\n    \"description\": \"Alpha Mod: eigene Bilder\",\n    \"min_format\": 69,\n    \"max_format\": 200\n  }\n}\n");
            ResourceManager rm = mc.getResourceManager();
            int done = 0;
            for (Config.Design d : Config.INSTANCE.designs) {
                if (d.image == null || d.image.isEmpty()) continue;
                Path img = imageFolder().resolve(d.image);
                if (!Files.exists(img)) continue;
                BufferedImage tex = square(ImageIO.read(img.toFile()));
                if (tex == null) continue;
                for (Identifier t : textures(rm, d.item)) {
                    Path out = root.resolve("assets").resolve(t.getNamespace()).resolve("textures").resolve(t.getPath() + ".png");
                    Files.createDirectories(out.getParent());
                    ImageIO.write(tex, "png", out.toFile());
                    done++;
                }
            }
            ResourcePackManager pm = mc.getResourcePackManager();
            pm.scanPacks();
            boolean on = pm.getEnabledIds().contains(PACK_ID);
            if (!on && done > 0) {
                pm.enable(PACK_ID);
                mc.options.refreshResourcePacks(pm);
            } else {
                mc.reloadResources();
            }
            SpotifyLyricsClient.chat(Text.literal("Item-Designer: " + done + " Textur(en) geladen."));
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.warn("Item-Designer: Pack konnte nicht gebaut werden", e);
            SpotifyLyricsClient.chat(Text.literal("Item-Designer: Fehler (" + e.getMessage() + ")"));
        }
    }

    private static void deleteTree(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    /** Mitte des Bildes quadratisch ausschneiden und auf 64x64 bringen. */
    static BufferedImage square(BufferedImage src) {
        if (src == null) return null;
        int s = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - s) / 2, y = (src.getHeight() - s) / 2;
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, SIZE, SIZE, x, y, x + s, y + s, null);
        g.dispose();
        return out;
    }

    /** Alle Texturen, die ein Item/Block benutzt (aus Blockstate, Item-Definition und Modellen gelesen). */
    static Set<Identifier> textures(ResourceManager rm, String itemId) {
        Set<Identifier> models = new LinkedHashSet<>();
        Identifier id = Identifier.of(itemId);
        JsonElement bs = json(rm, Identifier.of(id.getNamespace(), "blockstates/" + id.getPath() + ".json"));
        if (bs != null) collect(bs, "model", models);
        JsonElement it = json(rm, Identifier.of(id.getNamespace(), "items/" + id.getPath() + ".json"));
        if (it != null) collect(it, "model", models);
        Set<Identifier> tex = new LinkedHashSet<>();
        Set<Identifier> seen = new LinkedHashSet<>();
        for (Identifier m : models) modelTextures(rm, m, tex, seen, 0);
        return tex;
    }

    private static void modelTextures(ResourceManager rm, Identifier model, Set<Identifier> out, Set<Identifier> seen, int depth) {
        if (depth > 8 || !seen.add(model)) return;
        JsonElement e = json(rm, Identifier.of(model.getNamespace(), "models/" + model.getPath() + ".json"));
        if (e == null || !e.isJsonObject()) return;
        JsonObject o = e.getAsJsonObject();
        if (o.has("textures") && o.get("textures").isJsonObject()) {
            for (var en : o.getAsJsonObject("textures").entrySet()) {
                JsonElement v = en.getValue();
                String s = null;
                if (v.isJsonPrimitive()) s = v.getAsString();
                else if (v.isJsonObject() && v.getAsJsonObject().has("sprite")) s = v.getAsJsonObject().get("sprite").getAsString();
                if (s != null && !s.startsWith("#") && !en.getKey().equals("particle")) {
                    try {
                        out.add(Identifier.of(s));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (o.has("parent")) {
            try {
                modelTextures(rm, Identifier.of(o.get("parent").getAsString()), out, seen, depth + 1);
            } catch (Exception ignored) {
            }
        }
    }

    /** Sucht rekursiv alle Werte eines Schlüssels (z. B. "model") in einer JSON-Struktur. */
    private static void collect(JsonElement e, String key, Set<Identifier> out) {
        if (e == null) return;
        if (e.isJsonObject()) {
            for (var en : e.getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                if (en.getKey().equals(key) && v.isJsonPrimitive()) {
                    try {
                        out.add(Identifier.of(v.getAsString()));
                    } catch (Exception ignored) {
                    }
                } else {
                    collect(v, key, out);
                }
            }
        } else if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (JsonElement v : a) collect(v, key, out);
        }
    }

    private static JsonElement json(ResourceManager rm, Identifier file) {
        try {
            Optional<Resource> r = rm.getResource(file);
            if (r.isEmpty()) return null;
            try (Reader rd = new InputStreamReader(r.get().getInputStream(), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(rd);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Ins Fenster gezogene Bilder in den Bilder-Ordner kopieren; gibt den Namen des ersten zurück. */
    static String importImages(List<Path> files) {
        String first = null;
        for (Path f : files) {
            if (!isImage(f)) continue;
            try {
                Path t = imageFolder().resolve(f.getFileName().toString());
                if (!t.toAbsolutePath().equals(f.toAbsolutePath())) Files.copy(f, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (first == null) first = t.getFileName().toString();
            } catch (Exception ignored) {
            }
        }
        return first;
    }

    /** Ins Fenster gezogene MP3s in den Soundboard-Ordner kopieren; gibt den Namen des ersten zurück. */
    static String importSounds(List<Path> files) {
        String first = null;
        for (Path f : files) {
            if (!isSound(f)) continue;
            try {
                Path t = Soundboard.folder().resolve(f.getFileName().toString());
                if (!t.toAbsolutePath().equals(f.toAbsolutePath())) Files.copy(f, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (first == null) first = t.getFileName().toString();
            } catch (Exception ignored) {
            }
        }
        return first;
    }
}
