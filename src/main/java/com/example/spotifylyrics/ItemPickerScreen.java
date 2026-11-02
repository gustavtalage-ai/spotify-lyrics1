package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Item-Designer: Liste mit allen Items als Bildchen. Anklicken = eigenes Bild und Sound wählen. */
public class ItemPickerScreen extends Screen {
    private static final int HEAD = 28, CELL = 20;
    private static String search = "";
    private static boolean onlyMine = false;
    private static int scroll = 0;

    private int px0, px1, py0, py1, gx, gy, cols, vis, searchY, tabY;
    private List<Item> found = List.of();

    public ItemPickerScreen() {
        super(Text.literal("Item-Designer"));
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new ItemPickerScreen());
    }

    private static String id(Item it) {
        return Registries.ITEM.getId(it).toString();
    }

    private List<Item> find() {
        List<Item> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        for (Item it : Registries.ITEM) {
            if (it == Items.AIR) continue;
            if (onlyMine && Designer.find(id(it)) == null) continue;
            if (words.length > 0) {
                String path = Registries.ITEM.getId(it).getPath().toLowerCase(Locale.ROOT);
                String name = it.getName().getString().toLowerCase(Locale.ROOT);
                boolean ok = true;
                for (String w : words) {
                    if (!path.contains(w) && !name.contains(w)) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;
            }
            out.add(it);
        }
        return out;
    }

    private int rows() {
        return (found.size() + cols - 1) / Math.max(1, cols);
    }

    private void invisible(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int panelW = Math.min(440, this.width - 16), panelH = Math.min(320, this.height - 8);
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        searchY = py0 + HEAD + 4;
        tabY = searchY + 22;
        gx = px0 + 10;
        gy = tabY + 24;
        cols = Math.max(4, (panelW - 28) / CELL);
        vis = Math.max(2, (py1 - 10 - gy) / CELL);
        found = find();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows() - vis)));

        invisible(px1 - 10 - 72, py0 + 5, 72, 18, () -> MinecraftClient.getInstance().setScreen(new DesignerScreen()));

        TextFieldWidget f = new TextFieldWidget(this.textRenderer, gx, searchY, px1 - 10 - gx, 18, Text.literal("Item suchen"));
        f.setMaxLength(40);
        f.setText(search);
        f.setChangedListener(t -> {
            if (!t.equals(search)) {
                search = t;
                scroll = 0;
                reopen();
            }
        });
        addDrawableChild(f);
        setFocused(f);
        f.setFocused(true);

        int tw = (px1 - 10 - gx - 4) / 2;
        invisible(gx, tabY, tw, 18, () -> {
            onlyMine = false;
            scroll = 0;
            reopen();
        });
        invisible(gx + tw + 4, tabY, tw, 18, () -> {
            onlyMine = true;
            scroll = 0;
            reopen();
        });

        for (int r = 0; r < vis; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scroll + r) * cols + c;
                if (i >= found.size()) break;
                final Item it = found.get(i);
                invisible(gx + c * CELL, gy + r * CELL, CELL - 2, CELL - 2, () -> pick(it));
            }
        }
    }

    private void pick(Item it) {
        String id = id(it);
        Config c = Config.INSTANCE;
        if (Designer.find(id) == null) {
            Config.Design d = new Config.Design();
            d.item = id;
            c.designs.add(d);
            c.save();
        }
        MinecraftClient.getInstance().setScreen(DesignerScreen.open(id));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, rows() - vis);
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical) * 2));
        if (ns != scroll) {
            scroll = ns;
            reopen();
        }
        return true;
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("Tools / Item-Designer", "Tools / Item designer"));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        Ui.txt(ctx, tr, Lang.t("Alle Items", "All items"), px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, found.size() + Lang.t(" Items · anklicken = Bild & Sound wählen", " items · click = pick picture & sound"), px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 10 - 72, py0 + 5, px1 - 10, py0 + 23, Lang.t("‹ Designer", "‹ Designer"),
            Ui.in(mouseX, mouseY, px1 - 10 - 72, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        if (search.isEmpty()) Ui.txt(ctx, tr, Lang.t("Item suchen (z.B. Schwert, Debris) …", "Search item (e.g. sword) …"), gx + 5, searchY + 5, 0xFF6F7380);

        int tw = (px1 - 10 - gx - 4) / 2;
        int mine = Config.INSTANCE.designs.size();
        tab(ctx, tr, gx, tabY, gx + tw, Lang.t("Alle", "All"), !onlyMine, mouseX, mouseY);
        tab(ctx, tr, gx + tw + 4, tabY, px1 - 10, Lang.t("Mit eigenem Bild (", "With own picture (") + mine + ")", onlyMine, mouseX, mouseY);

        Item hovered = null;
        for (int r = 0; r < vis; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scroll + r) * cols + c;
                if (i >= found.size()) break;
                Item it = found.get(i);
                int x = gx + c * CELL, y = gy + r * CELL;
                boolean hov = Ui.in(mouseX, mouseY, x, y, x + CELL - 2, y + CELL - 2);
                Config.Design d = Designer.find(id(it));
                ctx.fill(x, y, x + CELL - 2, y + CELL - 2, Ui.BG);
                Ui.round(ctx, x, y, x + CELL - 2, y + CELL - 2, d != null ? 0xFF1F5A35 : hov ? 0xFF2A3140 : 0xFF161A22);
                if (d != null) {
                    ctx.fill(x, y + 2, x + 1, y + CELL - 4, Ui.GREEN);
                    ctx.fill(x + CELL - 3, y + 2, x + CELL - 2, y + CELL - 4, Ui.GREEN);
                }
                ctx.drawItem(new ItemStack(it), x + 1, y + 1);
                if (hov) hovered = it;
            }
        }
        if (found.isEmpty()) {
            Ui.txt(ctx, tr, onlyMine ? Lang.t("Noch kein Item mit eigenem Bild.", "No item with own picture yet.")
                : Lang.t("Nichts gefunden.", "Nothing found."), gx + 4, gy + 6, 0xFF9AA0AE);
        }

        // Leiste rechts
        int max = Math.max(0, rows() - vis);
        if (max > 0) {
            int top = gy, bot = gy + vis * CELL - 2, tx0 = px1 - 7, tx1 = px1 - 3, trackH = bot - top;
            int thumbH = Math.max(16, trackH * vis / rows());
            int thumbY = top + (trackH - thumbH) * scroll / max;
            ctx.fill(tx0, top, tx1, bot, 0x22FFFFFF);
            Ui.round(ctx, tx0, thumbY, tx1, thumbY + thumbH, 0x88FFFFFF);
        }

        if (hovered != null) {
            Config.Design d = Designer.find(id(hovered));
            List<Text> tip = new ArrayList<>();
            tip.add(hovered.getName());
            if (d != null) {
                tip.add(Text.literal("§a" + Lang.t("Bild: ", "Picture: ") + (d.image.isEmpty() ? "–" : d.image)));
                tip.add(Text.literal("§a" + Lang.t("Sound: ", "Sound: ") + (d.sound.isEmpty() ? "–" : d.sound)));
            }
            tip.add(Text.literal("§7" + Lang.t("Klick = Bild & Sound wählen", "Click = pick picture & sound")));
            ctx.drawTooltip(tr, tip, mouseX, mouseY);
        }
    }

    private static void tab(DrawContext ctx, TextRenderer tr, int x0, int y0, int x1, String label, boolean on, int mx, int my) {
        boolean hov = Ui.in(mx, my, x0, y0, x1, y0 + 18);
        if (on) Ui.btn(ctx, tr, x0, y0, x1, y0 + 18, label, hov, Ui.ACCENT, Ui.ACCENT, 0xFF04121B);
        else Ui.btn(ctx, tr, x0, y0, x1, y0 + 18, label, hov, 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
    }
}
