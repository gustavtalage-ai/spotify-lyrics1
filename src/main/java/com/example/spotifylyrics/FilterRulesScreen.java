package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Übersicht: alle Item-Filter-Regeln und gesperrten Items auf einen Blick. Klick = bearbeiten, ✕ = löschen. */
public class FilterRulesScreen extends Screen {
    private static final int HEAD = 28, RH = 24;
    private static int page = 0;
    private int px0, px1, py0, py1, top, rows;

    public FilterRulesScreen() {
        super(Text.literal("Filter-Regeln"));
    }

    /** Alle Einträge: {Item-ID, "rule" oder "block"}. */
    private static List<String[]> entries() {
        Config c = Config.INSTANCE;
        List<String[]> l = new ArrayList<>();
        for (String id : c.filterBlocked) l.add(new String[] {id, "block"});
        for (var e : c.filterRules.entrySet()) if (e.getValue() != null && e.getValue() != 0L) l.add(new String[] {e.getKey(), "rule"});
        return l;
    }

    private static Item itemOf(String id) {
        try {
            Item it = Registries.ITEM.get(Identifier.of(id));
            return it == Items.AIR ? null : it;
        } catch (Throwable e) {
            return null;
        }
    }

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new FilterRulesScreen());
    }

    @Override
    protected void init() {
        int pw = Math.min(360, this.width - 16), ph = Math.min(300, this.height - 8);
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        top = py0 + HEAD + 8;
        rows = Math.max(1, (py1 - 34 - top) / RH);
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 64, py0 + 5, 64, 18, () -> mc.setScreen(new FilterScreen()));
        List<String[]> l = entries();
        int pages = Math.max(1, (l.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < rows; i++) {
            int k = page * rows + i;
            if (k >= l.size()) break;
            String[] e = l.get(k);
            int y = top + i * RH;
            // ✕ löschen
            btn(px1 - 10 - 20, y + 2, 20, RH - 6, () -> {
                Config c = Config.INSTANCE;
                if (e[1].equals("block")) c.filterBlocked.remove(e[0]);
                else c.filterRules.remove(e[0]);
                c.save();
                reopen();
            });
            // Zeile = bearbeiten
            btn(px0 + 10, y, px1 - px0 - 20 - 24, RH - 2, () -> {
                FilterScreen.edit(e[0]);
                mc.setScreen(new FilterScreen());
            });
        }
        int by = py1 - 26;
        btn(px0 + 10, by, 40, 18, () -> {
            page = Math.max(0, page - 1);
            reopen();
        });
        btn(px0 + 54, by, 40, 18, () -> {
            page++;
            reopen();
        });
        btn(px1 - 10 - 110, by, 110, 18, () -> {
            Config.INSTANCE.filterOn = !Config.INSTANCE.filterOn;
            Config.INSTANCE.save();
            reopen();
        });
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("Item-Filter / Alle Regeln", "Item filter / All rules"));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Config c = Config.INSTANCE;
        List<String[]> l = entries();
        Ui.txt(ctx, tr, Lang.t("Aktive Regeln", "Active rules"), px0 + 10, py0 + 6, Ui.TEXT);
        String sub = c.filterRules.size() + Lang.t(" Regeln · ", " rules · ") + c.filterBlocked.size() + Lang.t(" gesperrt", " blocked")
            + (c.filterStrict ? Lang.t(" · Nur-diese-Modus", " · keep-only mode") : "");
        Ui.txt(ctx, tr, sub, px0 + 10, py0 + 17, c.filterStrict ? 0xFFFF6A6A : 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 74, py0 + 5, px1 - 10, py0 + 23, "Zurück", Ui.in(mouseX, mouseY, px1 - 74, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        if (l.isEmpty()) {
            String e = Lang.t("Noch keine Regeln. Lege im Item-Filter welche an.", "No rules yet. Create some in the item filter.");
            Ui.txt(ctx, tr, e, (px0 + px1 - tr.getWidth(e)) / 2, top + 20, 0xFF6F7380);
        }
        for (int i = 0; i < rows; i++) {
            int k = page * rows + i;
            if (k >= l.size()) break;
            String[] e = l.get(k);
            boolean blk = e[1].equals("block");
            int y = top + i * RH, x0 = px0 + 10, x1 = px1 - 10 - 24;
            boolean hov = Ui.in(mouseX, mouseY, x0, y, x1, y + RH - 2);
            Ui.card(ctx, x0, y, x1, y + RH - 2, blk ? 0xFF5A1A22 : (hov ? 0xFF3A4152 : Ui.BORDER), hov ? Ui.CARD_HOV : Ui.CARD);
            if (blk) ctx.fill(x0 + 1, y + 1, x0 + 3, y + RH - 3, 0xFFFF4A5A);
            Item it = itemOf(e[0]);
            if (it != null) ctx.drawItem(new ItemStack(it), x0 + 6, y + 3);
            String name = it != null ? it.getName().getString() : e[0];
            // rechts: Mini-Inventar mit den erlaubten Slots oder "GESPERRT"
            int gx = x1 - 6 - 9 * 4, gy = y + 3;
            if (blk) {
                String g = Lang.t("GESPERRT", "BLOCKED");
                Ui.txt(ctx, tr, g, x1 - 6 - tr.getWidth(g), y + 7, 0xFFFF6A6A);
                gx = x1 - 6 - tr.getWidth(g);
            } else {
                long m = c.filterRules.getOrDefault(e[0], 0L);
                int cnt = 0;
                for (int r = 0; r < 4; r++) {
                    for (int col = 0; col < 9; col++) {
                        int idx = r < 3 ? 9 + r * 9 + col : col;
                        boolean on = ((m >> idx) & 1L) != 0;
                        if (on) cnt++;
                        int sx = gx + col * 4, sy = gy + r * 4 + (r == 3 ? 1 : 0);
                        ctx.fill(sx, sy, sx + 3, sy + 3, on ? Ui.GREEN : 0xFF2A2F3A);
                    }
                }
                String info = cnt + Lang.t(" Slots", " slots");
                Ui.txt(ctx, tr, info, gx - 6 - tr.getWidth(info), y + 7, 0xFF8B92A5);
                gx = gx - 6 - tr.getWidth(info);
            }
            Ui.txt(ctx, tr, tr.trimToWidth(name, gx - (x0 + 26) - 6), x0 + 26, y + 7, blk ? 0xFFFF9FB0 : Ui.TEXT);
            // ✕
            int dx = px1 - 10 - 20;
            Ui.btn(ctx, tr, dx, y + 2, dx + 20, y + RH - 4, "✕", Ui.in(mouseX, mouseY, dx, y + 2, dx + 20, y + RH - 4), 0xFF2A141B, 0xFF3D1C26, 0xFFFF9FB0);
        }
        int pages = Math.max(1, (l.size() + rows - 1) / rows);
        int by = py1 - 26;
        Ui.btn(ctx, tr, px0 + 10, by, px0 + 50, by + 18, "◀", Ui.in(mouseX, mouseY, px0 + 10, by, px0 + 50, by + 18), 0xFF1A1E27, 0xFF252B38, page > 0 ? 0xFFD6D9E0 : 0xFF4A5164);
        Ui.btn(ctx, tr, px0 + 54, by, px0 + 94, by + 18, "▶", Ui.in(mouseX, mouseY, px0 + 54, by, px0 + 94, by + 18), 0xFF1A1E27, 0xFF252B38, page < pages - 1 ? 0xFFD6D9E0 : 0xFF4A5164);
        String pg = (page + 1) + " / " + pages;
        Ui.txt(ctx, tr, pg, px0 + 100, by + 5, 0xFF8B92A5);
        Ui.toggle(ctx, tr, px1 - 120, by, px1 - 10, by + 18, Lang.t("Filter an", "Filter on"), c.filterOn, Ui.in(mouseX, mouseY, px1 - 120, by, px1 - 10, by + 18));
    }
}
