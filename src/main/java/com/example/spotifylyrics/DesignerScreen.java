package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Item-Designer: Item oder Block wählen, ein eigenes Bild drauflegen (wie beim eigenen Texture Pack)
 * und einen Sound aussuchen, der beim Abbauen, Schlagen oder Benutzen spielt.
 */
public class DesignerScreen extends ItemScreen {
    /** Gerade bearbeitetes Item (null = Liste). */
    private static String editing;
    private static boolean dirty;

    public DesignerScreen() {
        super(Lang.t("Item-Designer", "Item designer"), Lang.t("Eigene Bilder & Sounds", "Own pictures & sounds"));
    }

    static DesignerScreen open(String item) {
        editing = item;
        return new DesignerScreen();
    }

    @Override
    protected ItemScreen fresh() {
        return new DesignerScreen();
    }

    @Override
    protected Screen back() {
        if (editing != null) {
            editing = null;
            return new DesignerScreen();
        }
        if (dirty) {
            dirty = false;
            Designer.apply();
        }
        return new ToolsScreen();
    }

    @Override
    protected String backLabel() {
        return editing != null ? Lang.t("‹ Liste", "‹ List") : "‹ Tools";
    }

    @Override
    protected int panelWidth() {
        return 380;
    }

    private static String shortName(String s, int max) {
        if (s == null || s.isEmpty()) return "–";
        int dot = s.lastIndexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private void add(String id) {
        if (id == null || id.endsWith(":air")) return;
        Config c = Config.INSTANCE;
        if (Designer.find(id) == null) {
            Config.Design d = new Config.Design();
            d.item = id;
            c.designs.add(d);
            c.save();
        }
        editing = id;
        again();
    }

    @Override
    protected void build() {
        if (editing != null && Designer.find(editing) != null) {
            buildEdit(Designer.find(editing));
            return;
        }
        editing = null;
        Config c = Config.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();

        sec(Lang.t("So geht's", "How it works"));
        text(Lang.t("1. Item in die Hand nehmen oder Block anschauen", "1. Hold an item or look at a block"), Ui.TEXT);
        text(Lang.t("2. Hinzufügen, dann Bild und Sound wählen", "2. Add it, then pick a picture and sound"), Ui.TEXT);
        text(Lang.t("3. Fertig – das Bild ist sofort im Spiel", "3. Done – the picture shows up right away"), Ui.TEXT);
        gap(4);
        row(B(Lang.t("+ Item in der Hand", "+ Item in hand"), () -> {
                if (mc.player == null) return;
                ItemStack s = mc.player.getMainHandStack();
                if (!s.isEmpty()) add(Registries.ITEM.getId(s.getItem()).toString());
            }),
            B(Lang.t("+ Block im Visier", "+ Block you look at"), () -> {
                if (mc.world == null || mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return;
                var st = mc.world.getBlockState(((BlockHitResult) mc.crosshairTarget).getBlockPos());
                add(Registries.ITEM.getId(st.getBlock().asItem()).toString());
            }));
        gap(4);

        sec(Lang.t("Deine Items", "Your items") + " (" + c.designs.size() + ")");
        if (c.designs.isEmpty()) {
            text(Lang.t("Noch nichts – füge oben ein Item hinzu.", "Nothing yet – add an item above."), 0xFF8A93A6);
        }
        for (Config.Design d : new ArrayList<>(c.designs)) {
            String info = Designer.itemName(d.item) + "  ·  " + Lang.t("Bild: ", "Pic: ") + shortName(d.image, 12)
                + "  ·  " + Lang.t("Sound: ", "Sound: ") + shortName(d.sound, 12);
            row(X(info).w(5), G(Lang.t("Ändern", "Edit"), () -> {
                editing = d.item;
                again();
            }).w(2), R("✕", () -> {
                c.designs.remove(d);
                c.save();
                dirty = true;
                again();
            }));
        }
        gap(4);
        row(B(Lang.t("Anwenden / neu laden", "Apply / reload"), () -> {
            dirty = false;
            Designer.apply();
        }), G(Lang.t("Bilder-Ordner", "Pictures folder"), () -> Designer.openFolder(Designer.imageFolder())),
            G(Lang.t("Sound-Ordner", "Sounds folder"), () -> Designer.openFolder(Soundboard.folder())));
    }

    private void buildEdit(Config.Design d) {
        Config c = Config.INSTANCE;
        text(Designer.itemName(d.item) + "  (" + d.item + ")", Ui.TEXT);
        gap(4);

        sec(Lang.t("Bild", "Picture"));
        text(Lang.t("Bild einfach ins Minecraft-Fenster ziehen – oder hier wählen:", "Drag a picture into the Minecraft window – or pick one:"), 0xFF8A93A6);
        List<Path> imgs = Designer.images();
        List<Cell> cells = new ArrayList<>();
        cells.add(pick(Lang.t("Kein Bild", "No picture"), d.image == null || d.image.isEmpty(), () -> d.image = ""));
        for (Path p : imgs) {
            String n = p.getFileName().toString();
            cells.add(pick(shortName(n, 14), n.equals(d.image), () -> d.image = n));
        }
        rows(cells, 3);
        row(G(Lang.t("Bilder-Ordner öffnen", "Open pictures folder"), () -> Designer.openFolder(Designer.imageFolder())));
        gap(4);

        sec(Lang.t("Sound (beim Abbauen, Schlagen, Benutzen)", "Sound (on mine, hit, use)"));
        text(Lang.t("MP3 ins Fenster ziehen – oder einen Soundboard-Sound wählen:", "Drag an MP3 into the window – or pick a soundboard sound:"), 0xFF8A93A6);
        cells = new ArrayList<>();
        cells.add(pick(Lang.t("Kein Sound", "No sound"), d.sound == null || d.sound.isEmpty(), () -> d.sound = ""));
        for (Path p : Soundboard.list()) {
            String n = p.getFileName().toString();
            cells.add(pick(shortName(n, 14), n.equals(d.sound), () -> d.sound = n));
        }
        rows(cells, 3);
        stepper(Lang.t("Lautstärke: ", "Volume: ") + d.volume + "%", () -> {
            d.volume = Math.max(0, d.volume - 10);
            c.save();
            again();
        }, () -> {
            d.volume = Math.min(200, d.volume + 10);
            c.save();
            again();
        });
        row(G(Lang.t("▶ Sound testen", "▶ Test sound"), () -> {
            if (d.sound != null && !d.sound.isEmpty()) Soundboard.playFx(Soundboard.folder().resolve(d.sound), d.volume / 100f);
        }), G(Lang.t("■ Stopp", "■ Stop"), Soundboard::stopFx));
        gap(6);
        row(B(Lang.t("✔ Fertig & anwenden", "✔ Done & apply"), () -> {
            editing = null;
            dirty = false;
            c.save();
            Designer.apply();
            again();
        }));
    }

    private Cell pick(String label, boolean sel, Runnable set) {
        if (sel) return S("✔ " + label);
        return G(label, () -> {
            set.run();
            dirty = true;
            Config.INSTANCE.save();
            again();
        });
    }

    private void rows(List<Cell> cells, int per) {
        for (int i = 0; i < cells.size(); i += per) {
            int n = Math.min(per, cells.size() - i);
            Cell[] r = new Cell[per];
            for (int k = 0; k < per; k++) r[k] = k < n ? cells.get(i + k) : X("");
            row(r);
        }
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        Config.Design d = editing == null ? null : Designer.find(editing);
        if (d == null) return;
        String img = Designer.importImages(paths);
        String snd = Designer.importSounds(paths);
        if (img != null) d.image = img;
        if (snd != null) d.sound = snd;
        if (img != null || snd != null) {
            dirty = true;
            Config.INSTANCE.save();
            again();
        }
    }
}
