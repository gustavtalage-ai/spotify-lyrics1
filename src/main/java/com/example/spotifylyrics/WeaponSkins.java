package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/**
 * Waffen-Skins passend zum Finisher (wie Skin-Kollektionen): Schwerter, Äxte, Speere und der Streitkolben
 * bekommen die Farben der Kollektion und einen animierten Glanz, der über die Klinge läuft.
 * Die Texturen werden aus den Original-Texturen erzeugt und landen im Pack "AlphaMod Designer".
 */
public final class WeaponSkins {
    private WeaponSkins() {
    }

    /** Name der Kollektion je Finisher (gleiche Reihenfolge wie KillFx.STYLES). */
    public static final String[] BUNDLES = {"Sonnenfeuer", "Frostkristall", "Leere", "Neon-Glitch", "Königsglanz"};

    /** Farbverlauf je Kollektion: dunkel, Hauptfarbe, hell, Glanz. */
    private static final int[][] GRAD = {
        {0x3A0A00, 0xFF6A00, 0xFFD54A, 0xFFF6C8},
        {0x08204A, 0x2E8BFF, 0x7DF9FF, 0xF0FFFF},
        {0x12002A, 0x5B2BFF, 0xB14BFF, 0xFF9CF2},
        {0x0B0B18, 0xFF00E6, 0x00F0FF, 0x39FF14},
        {0x2A0008, 0xC4102F, 0xFFC23D, 0xFFF3D6}
    };
    private static final int FRAMES = 12;

    static boolean isWeapon(Item it) {
        String p = Registries.ITEM.getId(it).getPath();
        return p.endsWith("_sword") || p.endsWith("_axe") || p.endsWith("_spear") || p.equals("mace");
    }

    /** Schreibt die Skins ins Pack; gibt die Anzahl der Texturen zurück. */
    static int writeInto(Path root, ResourceManager rm) {
        Config c = Config.INSTANCE;
        boolean on = c.wsOn && c.kfStyle >= 0 && c.kfStyle < GRAD.length;
        try {
            Files.createDirectories(root);
            Files.writeString(root.resolve("skin_style.txt"), on ? String.valueOf(c.kfStyle) : "none");
        } catch (Exception ignored) {
        }
        if (!on) return 0;
        int[] g = GRAD[c.kfStyle];
        boolean glitch = c.kfStyle == 3;
        Set<Identifier> done = new LinkedHashSet<>();
        int n = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Item it : Registries.ITEM) {
            if (!isWeapon(it)) continue;
            String id = Registries.ITEM.getId(it).toString();
            if (Designer.find(id) != null) continue; // eigenes Bild hat Vorrang
            for (Identifier t : Designer.textures(rm, id)) {
                if (!done.add(t)) continue;
                try {
                    InputSupplier<InputStream> sup = mc.getDefaultResourcePack().open(ResourceType.CLIENT_RESOURCES,
                        Identifier.of(t.getNamespace(), "textures/" + t.getPath() + ".png"));
                    if (sup == null) continue;
                    BufferedImage src;
                    try (InputStream in = sup.get()) {
                        src = ImageIO.read(in);
                    }
                    if (src == null) continue;
                    BufferedImage out = SkinGen.make(src, c.kfStyle, t.hashCode());
                    Path png = root.resolve("assets").resolve(t.getNamespace()).resolve("textures").resolve(t.getPath() + ".png");
                    Files.createDirectories(png.getParent());
                    ImageIO.write(out, "png", png.toFile());
                    Files.writeString(png.resolveSibling(png.getFileName() + ".mcmeta"),
                        "{\"animation\":{\"frametime\":2,\"interpolate\":true}}");
                    n++;
                } catch (Throwable e) {
                    SpotifyLyricsClient.LOGGER.debug("Skin für " + t + " ging nicht", e);
                }
            }
        }
        return n;
    }

    /** Färbt die Textur nach Helligkeit um und baut 12 Bilder mit wanderndem Glanz. */
    private static BufferedImage skin(BufferedImage src, int[] g, boolean glitch, int seed) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h * FRAMES, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(seed);
        for (int f = 0; f < FRAMES; f++) {
            float c = -0.3f + 1.6f * f / FRAMES;
            int shiftRow = glitch && f % 3 == 0 ? r.nextInt(h) : -1;
            for (int y = 0; y < h; y++) {
                int dx = (glitch && Math.abs(y - shiftRow) <= 1) ? (r.nextBoolean() ? 1 : -1) * Math.max(1, w / 16) : 0;
                for (int x = 0; x < w; x++) {
                    int sx = Math.floorMod(x - dx, w);
                    int argb = src.getRGB(sx, y);
                    int a = argb >>> 24;
                    if (a == 0) {
                        out.setRGB(x, f * h + y, 0);
                        continue;
                    }
                    int rr = argb >> 16 & 0xFF, gg = argb >> 8 & 0xFF, bb = argb & 0xFF;
                    float lum = (0.299f * rr + 0.587f * gg + 0.114f * bb) / 255f;
                    lum = (float) Math.pow(lum, 0.85);
                    int col = grad(g, lum);
                    // Glanz-Streifen läuft schräg über die Waffe
                    float p = (x + (h - 1 - y)) / (float) (w + h);
                    float band = Math.max(0, 1 - Math.abs(p - c) * 7);
                    col = mix(col, g[3], band * 0.75f);
                    // leichtes Pulsieren der hellen Stellen
                    float pulse = 0.12f * (float) Math.sin((f / (float) FRAMES) * Math.PI * 2);
                    if (lum > 0.6f) col = mix(col, 0xFFFFFF, Math.max(0, pulse));
                    out.setRGB(x, f * h + y, a << 24 | col);
                }
            }
        }
        return out;
    }

    private static int grad(int[] g, float k) {
        k = Math.max(0, Math.min(1, k));
        float[] stops = {0f, 0.42f, 0.78f, 1f};
        for (int i = 0; i < 3; i++) {
            if (k <= stops[i + 1]) return mix(g[i], g[i + 1], (k - stops[i]) / (stops[i + 1] - stops[i]));
        }
        return g[3];
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) ((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
        int g = (int) ((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return r << 16 | g << 8 | bl;
    }
}
