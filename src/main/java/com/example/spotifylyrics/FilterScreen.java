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
    private static final int W = 320;
    private static final int ROWS = 4;

    private static String search = "";
    private static String selected = "";
    private static long mask = 0L;

    private int left, top, gx, gy;
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

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = Math.max(4, (this.height - 252) / 2);

        TextFieldWidget f = new TextFieldWidget(this.textRenderer, left, top + 14, W, 18, Text.literal("Item suchen"));
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
            addDrawableChild(ButtonWidget.builder(it.getName(), b -> {
                selected = Registries.ITEM.getId(it).toString();
                Long m = Config.INSTANCE.filterRules.get(selected);
                mask = m == null ? 0L : m;
                reopen();
            }).dimensions(left + 22, top + 34 + i * 18, W - 22, 17).build());
        }

        // Slot-Raster: 3 Reihen Rucksack, darunter die Hotbar
        gx = left + (W - 9 * 20) / 2;
        gy = top + 34 + ROWS * 18 + 22;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                final int idx = r < 3 ? 9 + r * 9 + c : c;
                int x = gx + c * 20;
                int y = gy + r * 20 + (r == 3 ? 4 : 0);
                addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    mask ^= 1L << idx;
                    reopen();
                }).dimensions(x, y, 18, 18).build());
            }
        }

        int by = gy + 4 * 20 + 8;
        int bw = (W - 3 * 4) / 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Alle"), b -> {
            mask = (1L << 36) - 1;
            reopen();
        }).dimensions(left, by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Keine"), b -> {
            mask = 0L;
            reopen();
        }).dimensions(left + (bw + 4), by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Freie"), b -> {
            long m = 0L;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) m |= 1L << i;
            }
            mask = m;
            reopen();
        }).dimensions(left + 2 * (bw + 4), by, bw, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Hotbar"), b -> {
            mask = (1L << 9) - 1;
            reopen();
        }).dimensions(left + 3 * (bw + 4), by, bw, 18).build());

        int by2 = by + 22;
        int b5 = (W - 4 * 3) / 5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Speichern"), b -> {
            if (!selected.isEmpty()) {
                if (mask == 0L) Config.INSTANCE.filterRules.remove(selected);
                else Config.INSTANCE.filterRules.put(selected, mask);
                Config.INSTANCE.save();
            }
            reopen();
        }).dimensions(left, by2, b5, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Regel weg"), b -> {
            if (!selected.isEmpty()) {
                Config.INSTANCE.filterRules.remove(selected);
                Config.INSTANCE.save();
                mask = 0L;
            }
            reopen();
        }).dimensions(left + (b5 + 3), by2, b5, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Filter: " + (Config.INSTANCE.filterOn ? "AN" : "AUS")), b -> {
            Config.INSTANCE.filterOn = !Config.INSTANCE.filterOn;
            Config.INSTANCE.save();
            reopen();
        }).dimensions(left + 2 * (b5 + 3), by2, b5, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Nur diese: " + (Config.INSTANCE.filterStrict ? "AN" : "AUS")), b -> {
            Config.INSTANCE.filterStrict = !Config.INSTANCE.filterStrict;
            Config.INSTANCE.save();
            reopen();
        }).dimensions(left + 3 * (b5 + 3), by2, b5, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"), b ->
            MinecraftClient.getInstance().setScreen(new SoundboardScreen(0))
        ).dimensions(left + 4 * (b5 + 3), by2, b5, 18).build());
    }

    // Absichtlich ohne @Override, wie im Soundboard-Menü.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawTextWithShadow(tr, "Item-Filter · Regeln: " + Config.INSTANCE.filterRules.size(), left, top + 2, 0xFFFFFFFF);

        for (int i = 0; i < found.size(); i++) {
            ctx.drawItem(new ItemStack(found.get(i)), left + 3, top + 34 + i * 18);
        }
        if (found.isEmpty()) {
            ctx.drawTextWithShadow(tr, "Tippe oben ein Item (z.B. Schwert, Brot, Fackel)", left + 4, top + 40, 0xFF9AA0AE);
        }

        Item sel = selected.isEmpty() ? null : itemOf(selected);
        int sy = top + 34 + ROWS * 18 + 3;
        if (sel != null) {
            ctx.drawItem(new ItemStack(sel), left, sy - 3);
            ctx.drawTextWithShadow(tr, "Gewählt: " + sel.getName().getString() + "  → grüne Slots", left + 20, sy + 2, 0xFF4ADE80);
        } else {
            ctx.drawTextWithShadow(tr, "Wähle oben ein Item, dann die Slots anklicken.", left, sy + 2, 0xFFFFAA00);
        }

        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = r < 3 ? 9 + r * 9 + c : c;
                int x = gx + c * 20;
                int y = gy + r * 20 + (r == 3 ? 4 : 0);
                if (mc.player != null) {
                    ItemStack s = mc.player.getInventory().getStack(idx);
                    if (!s.isEmpty()) ctx.drawItem(s, x + 1, y + 1);
                }
                if (((mask >> idx) & 1L) != 0) {
                    int g = 0xFF4ADE80;
                    ctx.fill(x, y, x + 18, y + 2, g);
                    ctx.fill(x, y + 16, x + 18, y + 18, g);
                    ctx.fill(x, y, x + 2, y + 18, g);
                    ctx.fill(x + 16, y, x + 18, y + 18, g);
                }
            }
        }
        if (Config.INSTANCE.filterStrict) {
            ctx.drawTextWithShadow(tr, "Nur diese AN: Rest wird weggeworfen!", left + 150, top + 2, 0xFFFF6A6A);
        }
        ctx.drawTextWithShadow(tr, "Rucksack", gx + 9 * 20 + 4, gy + 24, 0xFF7E8494);
        ctx.drawTextWithShadow(tr, "Hotbar", gx + 9 * 20 + 4, gy + 68, 0xFF7E8494);
    }
}
