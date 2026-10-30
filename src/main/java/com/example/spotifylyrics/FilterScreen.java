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
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Item-Filter einrichten: Item wählen, Slots anklicken, speichern. */
public class FilterScreen extends Screen {
    private static final int ROWS = 4;
    private static final int HEAD = 28;
    private static final int PANEL_H = 293;
    private static final int ACCENT = Ui.ACCENT;
    private static final int PANEL = Ui.BG;
    private static final int HEADER = Ui.HEAD_BG;
    private static final int GREEN = Ui.GREEN;

    private static String search = "";
    private static String selected = "";
    private static long mask = 0L;

    private int px0, px1, py0, py1, cx, cw, gx, gy, rowsY, infoY, botY, bot2Y, qx, qw, searchY;
    private List<Item> found = List.of();

    public FilterScreen() {
        super(Text.literal("Item-Filter"));
    }

    private static Item itemOf(String id) {
        try {
            Item it = Registries.ITEM.get(Identifier.of(id));
            return it == Items.AIR ? null : it;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void reopen() {
        MinecraftClient.getInstance().setScreen(new FilterScreen());
    }

    private List<Item> find() {
        List<Item> out = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            // ohne Suche: die Items aus deinem Inventar
            if (mc.player != null) {
                for (int i = 0; i < 36 && out.size() < ROWS; i++) {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (!s.isEmpty() && !out.contains(s.getItem())) out.add(s.getItem());
                }
            }
            return out;
        }
        String[] words = q.split("\\s+");
        for (Item it : Registries.ITEM) {
            if (it == Items.AIR) continue;
            String id = Registries.ITEM.getId(it).getPath().toLowerCase(Locale.ROOT);
            String name = it.getName().getString().toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String w : words) {
                if (!id.contains(w) && !name.contains(w)) { ok = false; break; }
            }
            if (ok) {
                out.add(it);
                if (out.size() >= ROWS) break;
            }
        }
        return out;
    }

    private int slotX(int c) {
        return gx + c * 20;
    }

    private int slotY(int r) {
        return gy + r * 20 + (r == 3 ? 4 : 0);
    }

    private static int slotIdx(int r, int c) {
        return r < 3 ? 9 + r * 9 + c : c;
    }

    private void invisible(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int panelW = Math.min(340, this.width - 20);
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - PANEL_H) / 2);
        py1 = py0 + PANEL_H;
        cx = px0 + 10;
        cw = panelW - 20;
        searchY = py0 + HEAD + 4;
        rowsY = searchY + 22;
        infoY = rowsY + ROWS * 19 + 3;
        gy = infoY + 16;
        botY = gy + 82 + 8;
        bot2Y = botY + 24;
        gx = cx;
        qx = cx + 9 * 20 + 8;
        qw = cx + cw - qx;

        // Zurück
        invisible(px1 - 10 - 64, py0 + 5, 64, 18, () -> MinecraftClient.getInstance().setScreen(new SoundboardScreen(0)));

        TextFieldWidget f = new TextFieldWidget(this.textRenderer, cx, searchY, cw, 18, Text.literal("Item suchen"));
        f.setMaxLength(40);
        f.setText(search);
        f.setChangedListener(t -> {
            if (!t.equals(search)) {
                search = t;
                reopen();
            }
        });
        addDrawableChild(f);
        setFocused(f);
        f.setFocused(true);

        found = find();
        for (int i = 0; i < found.size(); i++) {
            final Item it = found.get(i);
            invisible(cx, rowsY + i * 19, cw, 18, () -> {
                selected = Registries.ITEM.getId(it).toString();
                Long m = Config.INSTANCE.filterRules.get(selected);
                mask = m == null ? 0L : m;
                reopen();
            });
        }

        // Slot-Raster: 3 Reihen Rucksack, darunter die Hotbar
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                final int idx = slotIdx(r, c);
                invisible(slotX(c), slotY(r), 18, 18, () -> {
                    mask ^= 1L << idx;
                    reopen();
                });
            }
        }

        // Schnellwahl rechts neben dem Raster
        invisible(qx, gy, qw, 19, () -> {
            mask = (1L << 36) - 1;
            reopen();
        });
        invisible(qx, gy + 21, qw, 19, () -> {
            mask = 0L;
            reopen();
        });
        invisible(qx, gy + 42, qw, 19, () -> {
            long m = 0L;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) m |= 1L << i;
            }
            mask = m;
            reopen();
        });
        invisible(qx, gy + 63, qw, 19, () -> {
            mask = (1L << 9) - 1;
            reopen();
        });

        int hw = (cw - 4) / 2;
        invisible(cx, botY, hw, 20, () -> {
            if (!selected.isEmpty()) {
                if (mask == 0L) Config.INSTANCE.filterRules.remove(selected);
                else Config.INSTANCE.filterRules.put(selected, mask);
                Config.INSTANCE.save();
            }
            reopen();
        });
        invisible(cx + hw + 4, botY, hw, 20, () -> {
            if (!selected.isEmpty()) {
                Config.INSTANCE.filterRules.remove(selected);
                Config.INSTANCE.save();
                mask = 0L;
            }
            reopen();
        });
        invisible(cx, bot2Y, hw, 20, () -> {
            Config.INSTANCE.filterOn = !Config.INSTANCE.filterOn;
            Config.INSTANCE.save();
            reopen();
        });
        invisible(cx + hw + 4, bot2Y, hw, 20, () -> {
            Config.INSTANCE.filterStrict = !Config.INSTANCE.filterStrict;
            Config.INSTANCE.save();
            reopen();
        });
    }

    // Absichtlich ohne @Override, wie im Soundboard-Menü.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    // Absichtlich ohne @Override, wie im Soundboard-Menü.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.INSTANCE;

        // Kopf
        Ui.txt(ctx, tr, "Item-Filter", px0 + 10, py0 + 6, 0xFFFFFFFF);
        if (cfg.filterStrict) {
            Ui.txt(ctx, tr, "Nur-diese-Modus: Rest wird weggeworfen!", px0 + 10, py0 + 17, 0xFFFF6A6A);
        } else {
            Ui.txt(ctx, tr, cfg.filterRules.size() + " Regeln · Filter " + (cfg.filterOn ? "an" : "aus"), px0 + 10, py0 + 17, 0xFF8B92A5);
        }
        Ui.btn(ctx, tr, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23, "Zurück", Ui.in(mouseX, mouseY, px1 - 10 - 64, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        if (search.isEmpty()) Ui.txt(ctx, tr, "Item suchen (z.B. Schwert, Brot) …", cx + 5, searchY + 5, 0xFF6F7380);

        // Treffer
        for (int i = 0; i < found.size(); i++) {
            Item it = found.get(i);
            int ry = rowsY + i * 19;
            boolean isSel = Registries.ITEM.getId(it).toString().equals(selected);
            boolean hov = Ui.in(mouseX, mouseY, cx, ry, cx + cw, ry + 18);
            ctx.fill(cx, ry, cx + cw, ry + 18, PANEL);
            Ui.round2(ctx, cx, ry, cx + cw, ry + 18, isSel ? 0xFF15301F : (hov ? 0xFF222733 : 0xFF161A22));
            if (isSel) ctx.fill(cx, ry + 4, cx + 2, ry + 14, GREEN);
            ctx.drawItem(new ItemStack(it), cx + 5, ry + 1);
            Ui.txt(ctx, tr, tr.trimToWidth(it.getName().getString(), cw - 34), cx + 26, ry + 5, isSel ? 0xFFFFFFFF : 0xFFE4E7EE);
        }
        if (found.isEmpty()) {
            Ui.txt(ctx, tr, "Tippe oben ein Item ein.", cx + 4, rowsY + 6, 0xFF9AA0AE);
        }

        // Gewähltes Item
        Item chosen = selected.isEmpty() ? null : itemOf(selected);
        if (chosen != null) {
            Ui.txt(ctx, tr, tr.trimToWidth("Gewählt: " + chosen.getName().getString() + " · grüne Slots = hier darf es liegen", cw), cx, infoY + 4, 0xFF4ADE80);
        } else {
            Ui.txt(ctx, tr, "Wähle oben ein Item, dann die Slots anklicken.", cx, infoY + 4, 0xFFFFAA00);
        }

        // Slot-Raster
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = slotIdx(r, c);
                int x = slotX(c), y = slotY(r);
                boolean on = ((mask >> idx) & 1L) != 0;
                boolean hov = Ui.in(mouseX, mouseY, x, y, x + 18, y + 18);
                ctx.fill(x, y, x + 18, y + 18, PANEL);
                Ui.round(ctx, x, y, x + 18, y + 18, on ? 0xFF1F5A35 : (hov ? 0xFF252B38 : 0xFF161A22));
                if (on) {
                    ctx.fill(x, y + 2, x + 1, y + 16, GREEN);
                    ctx.fill(x + 17, y + 2, x + 18, y + 16, GREEN);
                    ctx.fill(x + 2, y, x + 16, y + 1, GREEN);
                    ctx.fill(x + 2, y + 17, x + 16, y + 18, GREEN);
                }
                if (mc.player != null) {
                    ItemStack s = mc.player.getInventory().getStack(idx);
                    if (!s.isEmpty()) ctx.drawItem(s, x + 1, y + 1);
                }
            }
        }

        // Schnellwahl
        String[] q = {"Alle Slots", "Keine", "Nur freie", "Nur Hotbar"};
        for (int i = 0; i < 4; i++) {
            int y = gy + i * 21;
            Ui.btn(ctx, tr, qx, y, qx + qw, y + 19, q[i], Ui.in(mouseX, mouseY, qx, y, qx + qw, y + 19), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        }

        // Unten
        int hw = (cw - 4) / 2;
        Ui.btn(ctx, tr, cx, botY, cx + hw, botY + 20, "Speichern", Ui.in(mouseX, mouseY, cx, botY, cx + hw, botY + 20), 0xFF123A55, 0xFF1A4E73, 0xFFFFFFFF);
        Ui.btn(ctx, tr, cx + hw + 4, botY, cx + cw, botY + 20, "Regel löschen", Ui.in(mouseX, mouseY, cx + hw + 4, botY, cx + cw, botY + 20), 0xFF2A141B, 0xFF3D1C26, 0xFFFF9FB0);
        Ui.toggle(ctx, tr, cx, bot2Y, cx + hw, bot2Y + 20, "Filter an", cfg.filterOn, Ui.in(mouseX, mouseY, cx, bot2Y, cx + hw, bot2Y + 20));
        Ui.toggle(ctx, tr, cx + hw + 4, bot2Y, cx + cw, bot2Y + 20, "Nur diese behalten", cfg.filterStrict, Ui.in(mouseX, mouseY, cx + hw + 4, bot2Y, cx + cw, bot2Y + 20));
    }
}
