package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Menü aus Kästen: jedes Modul ein eigener Kasten mit gezeichnetem Symbol, Schalter und Zahnrad (Einstellungen).
 * Optional mit Kategorien links und Suchleiste oben.
 */
abstract class CardGridScreen extends Screen {
    private static final int HEAD = 34, SIDE_W = 52, CARD_H = 46, GAP = 6, FOOT = 26, SEARCH_H = 22;
    private static final Map<String, Integer> SCROLL = new HashMap<>();
    private static final Map<String, String> QUERY = new HashMap<>();

    /** Ein Kasten. flip == null: Kasten öffnet nur (kein Schalter). */
    static final class Card {
        final String name, desc, icon;
        final BooleanSupplier on;
        final Runnable flip;
        final Supplier<Screen> settings;

        Card(String name, String desc, String icon, BooleanSupplier on, Runnable flip, Supplier<Screen> settings) {
            this.name = name;
            this.desc = desc;
            this.icon = icon;
            this.on = on;
            this.flip = flip;
            this.settings = settings;
        }

        boolean matches(String q) {
            return q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || desc.toLowerCase(Locale.ROOT).contains(q);
        }
    }

    private final List<Card> cards = new ArrayList<>();
    private int px0, px1, py0, py1, gx0, gy0, cardW, vis;
    private TextFieldWidget search;

    protected CardGridScreen(String title) {
        super(Text.literal(title));
    }

    // ---------- von Unterklassen ----------

    protected abstract String heading();

    protected abstract String subheading();

    /** Kästen der aktuellen Kategorie (oder bei Suche: alle passenden). */
    protected abstract void cards(List<Card> out, String query);

    protected abstract Screen fresh();

    protected abstract Screen back();

    protected String backLabel() {
        return Lang.t("‹ Menü", "‹ Menu");
    }

    /** Kategorien links (null = keine). */
    protected String[] categories() {
        return null;
    }

    protected String[] categoryIcons() {
        return null;
    }

    protected int category() {
        return 0;
    }

    protected void setCategory(int c) {}

    protected int accent() {
        return Ui.ACCENT;
    }

    protected int categoryAccent(int c) {
        return accent();
    }

    protected boolean searchable() {
        return false;
    }

    /** Knöpfe in der Fußzeile (links), z. B. "Anzeigen verschieben". */
    protected void footer(List<Card> out) {}

    // ---------- Zustand ----------

    private String key() {
        return getClass().getName();
    }

    protected String query() {
        return QUERY.getOrDefault(key(), "");
    }

    private int scroll() {
        return SCROLL.getOrDefault(key(), 0);
    }

    private void setScroll(int s) {
        SCROLL.put(key(), s);
    }

    protected void again() {
        MinecraftClient.getInstance().setScreen(fresh());
    }

    private int rows() {
        return (cards.size() + 2) / 3;
    }

    private int[] card(int i) {
        int r = i / 3 - scroll(), col = i % 3;
        if (r < 0 || r >= vis) return null;
        return new int[] {gx0 + col * (cardW + GAP), gy0 + r * (CARD_H + GAP), cardW, CARD_H};
    }

    private boolean hasSide() {
        return categories() != null;
    }

    private int[] sideBtn(int i) {
        return new int[] {px0 + 8, gy0 + i * 40, SIDE_W - 6, 36};
    }

    private int[] gearRect(int[] r) {
        return new int[] {r[0] + r[2] - 17, r[1] + 3, 14, 14};
    }

    // ---------- Aufbau ----------

    @Override
    protected void init() {
        cards.clear();
        String q = query().trim().toLowerCase(Locale.ROOT);
        cards(cards, q);
        int top = searchable() ? SEARCH_H : 0;
        int panelW = Math.min(460, this.width - 16);
        vis = Math.max(2, Math.min(4, (this.height - 12 - HEAD - 10 - top - FOOT) / (CARD_H + GAP)));
        int panelH = HEAD + 8 + top + vis * (CARD_H + GAP) + FOOT;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        gx0 = px0 + 8 + (hasSide() ? SIDE_W + 4 : 0);
        gy0 = py0 + HEAD + 8 + top;
        cardW = (px1 - 8 - gx0 - 2 * GAP) / 3;
        setScroll(Math.max(0, Math.min(scroll(), Math.max(0, rows() - vis))));

        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(back()))
            .dimensions(px1 - 8 - 64, py0 + 8, 64, 18).build());

        if (searchable()) {
            TextFieldWidget f = new TextFieldWidget(this.textRenderer, gx0 + 20, py0 + HEAD + 8, px1 - 8 - gx0 - 26, 14, Text.literal("Suche"));
            f.setDrawsBackground(false);
            f.setMaxLength(30);
            f.setText(query());
            f.setSuggestion(query().isEmpty() ? Lang.t("Modul suchen …", "Search module …") : "");
            f.setChangedListener(t -> {
                if (!t.equals(query())) {
                    QUERY.put(key(), t);
                    setScroll(0);
                    again();
                }
            });
            addDrawableChild(f);
            search = f;
            if (!query().isEmpty()) {
                setFocused(f);
                f.setFocused(true);
            }
        }

        if (hasSide()) {
            for (int i = 0; i < categories().length; i++) {
                final int k = i;
                int[] r = sideBtn(i);
                addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                    setCategory(k);
                    QUERY.put(key(), "");
                    setScroll(0);
                    again();
                }).dimensions(r[0], r[1], r[2], r[3]).build());
            }
        }
        for (int i = 0; i < cards.size(); i++) {
            int[] r = card(i);
            if (r == null) continue;
            Card c = cards.get(i);
            // Zahnrad zuerst, damit der Klick dort nicht den Schalter trifft
            if (c.settings != null && c.flip != null) {
                int[] g = gearRect(r);
                addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(c.settings.get()))
                    .dimensions(g[0], g[1], g[2], g[3]).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                if (c.flip != null) {
                    c.flip.run();
                    again();
                } else if (c.settings != null) {
                    MinecraftClient.getInstance().setScreen(c.settings.get());
                }
            }).dimensions(r[0], r[1], r[2], r[3]).build());
        }
        int fy = py1 - FOOT + 4;
        if (query().isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                setAll(true);
                again();
            }).dimensions(px1 - 8 - 130, fy, 62, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                setAll(false);
                again();
            }).dimensions(px1 - 8 - 64, fy, 64, 18).build());
        }
        List<Card> foot = new ArrayList<>();
        footer(foot);
        int fx = gx0;
        for (Card c : foot) {
            int w = this.textRenderer.getWidth(c.name) + 16;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(c.settings.get()))
                .dimensions(fx, fy, w, 18).build());
            fx += w + 4;
        }
    }

    private void setAll(boolean value) {
        for (Card c : cards) if (c.flip != null && c.on.getAsBoolean() != value) c.flip.run();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, rows() - vis);
        int ns = Math.max(0, Math.min(max, scroll() - (int) Math.signum(vertical)));
        if (ns != scroll()) {
            setScroll(ns);
            again();
        }
        return true;
    }

    // ---------- Zeichnen ----------

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int acc = hasSide() ? categoryAccent(category()) : accent();
        boolean searching = !query().trim().isEmpty();

        // Kopf
        int active = 0, total = 0;
        for (Card c : cards) {
            if (c.flip == null) continue;
            total++;
            if (c.on.getAsBoolean()) active++;
        }
        String head = heading();
        Ui.txt(ctx, tr, head, px0 + 10, py0 + 8, Ui.TEXT);
        String sub2 = searching ? Lang.t("Suche", "Search") : hasSide() ? categories()[category()] : "";
        if (!sub2.isEmpty()) Ui.txt(ctx, tr, "· " + sub2, px0 + 10 + tr.getWidth(head) + 6, py0 + 8, Ui.MUTED);
        Ui.txt(ctx, tr, tr.trimToWidth(subheading(), px1 - px0 - 170), px0 + 10, py0 + 20, 0xFF6B7385);
        String cnt = active + "/" + total + Lang.t(" an", " on");
        int cw = tr.getWidth(cnt) + 20, cx0 = px1 - 8 - 64 - 8 - cw;
        Ui.card(ctx, cx0, py0 + 9, cx0 + cw, py0 + 25, Ui.BORDER, Ui.SURFACE);
        Ui.round(ctx, cx0 + 6, py0 + 14, cx0 + 11, py0 + 19, active > 0 ? Ui.GREEN : 0xFF4A5164);
        Ui.txt(ctx, tr, cnt, cx0 + 14, py0 + 13, Ui.TEXT);
        int bx0 = px1 - 8 - 64;
        Ui.btn(ctx, tr, bx0, py0 + 8, bx0 + 64, py0 + 26, backLabel(), Ui.in(mouseX, mouseY, bx0, py0 + 8, bx0 + 64, py0 + 26), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        // Suchleiste
        if (searchable()) {
            int sx0 = gx0, sy0 = py0 + HEAD + 4, sx1 = px1 - 8, sy1 = sy0 + 18;
            boolean foc = search != null && search.isFocused();
            Ui.card(ctx, sx0, sy0, sx1, sy1, foc ? acc : Ui.BORDER, 0xFF12151B);
            // Lupe
            int lx = sx0 + 6, ly = sy0 + 4;
            ctx.fill(lx + 1, ly, lx + 5, ly + 1, Ui.MUTED);
            ctx.fill(lx + 1, ly + 6, lx + 5, ly + 7, Ui.MUTED);
            ctx.fill(lx, ly + 1, lx + 1, ly + 6, Ui.MUTED);
            ctx.fill(lx + 5, ly + 1, lx + 6, ly + 6, Ui.MUTED);
            ctx.fill(lx + 6, ly + 7, lx + 7, ly + 8, Ui.MUTED);
            ctx.fill(lx + 7, ly + 8, lx + 9, ly + 10, Ui.MUTED);
            if (search != null) search.render(ctx, mouseX, mouseY, delta);
        }

        // Seitenleiste
        if (hasSide()) {
            String[] cats = categories();
            String[] icons = categoryIcons();
            for (int i = 0; i < cats.length; i++) {
                int[] r = sideBtn(i);
                boolean sel = i == category() && !searching, hov = Ui.in(mouseX, mouseY, r[0], r[1], r[0] + r[2], r[1] + r[3]);
                int ca = categoryAccent(i);
                ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], Ui.BG);
                Ui.card(ctx, r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? ca : hov ? 0xFF3A4152 : Ui.BORDER,
                    sel ? Ui.mix(0xFF141821, ca, 0.18f) : 0xFF141821);
                if (icons != null) Icons.draw(ctx, icons[i], r[0] + (r[2] - 12) / 2, r[1] + 6, 1, sel ? ca : 0xFFAAB2C5);
                String l = cats[i];
                Ui.txt(ctx, tr, l, r[0] + (r[2] - tr.getWidth(l)) / 2, r[1] + 24, sel ? Ui.TEXT : Ui.MUTED);
            }
        }

        // Kästen
        if (cards.isEmpty()) {
            Ui.txt(ctx, tr, Lang.t("Nichts gefunden.", "Nothing found."), gx0 + 4, gy0 + 8, Ui.MUTED);
        }
        for (int i = 0; i < cards.size(); i++) {
            int[] r = card(i);
            if (r == null) continue;
            Card c = cards.get(i);
            boolean on = c.flip != null && c.on.getAsBoolean();
            int x0 = r[0], y0 = r[1], x1 = r[0] + r[2], y1 = r[1] + r[3];
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, Ui.BG);
            if (on) Ui.round2(ctx, x0 - 1, y0 - 1, x1 + 1, y1 + 1, (acc & 0x00FFFFFF) | 0x40000000);
            Ui.card(ctx, x0, y0, x1, y1, on ? acc : hov ? 0xFF3A4152 : Ui.BORDER,
                on ? Ui.mix(Ui.CARD, acc, 0.14f) : hov ? Ui.CARD_HOV : Ui.CARD);
            // Symbol-Kachel mit gezeichnetem Symbol
            Ui.round2(ctx, x0 + 5, y0 + 5, x0 + 25, y0 + 25, on ? (acc & 0x00FFFFFF) | 0x50000000 : 0x18FFFFFF);
            Icons.draw(ctx, c.icon, x0 + 9, y0 + 9, 1, on ? 0xFFFFFFFF : Ui.mix(acc, 0xFFB8C0D0, 0.45f));
            Ui.txt(ctx, tr, tr.trimToWidth(c.name, r[2] - 50), x0 + 29, y0 + 6, on ? 0xFFFFFFFF : Ui.TEXT);
            Ui.txt(ctx, tr, tr.trimToWidth(c.desc, r[2] - 34), x0 + 29, y0 + 17, Ui.MUTED);
            if (c.flip != null) {
                // Zahnrad oben rechts
                if (c.settings != null) {
                    int[] g = gearRect(r);
                    boolean gh = Ui.in(mouseX, mouseY, g[0], g[1], g[0] + g[2], g[1] + g[3]);
                    if (gh) Ui.round2(ctx, g[0], g[1], g[0] + g[2], g[1] + g[3], 0x30FFFFFF);
                    Icons.gear(ctx, g[0] + 3, g[1] + 3, gh ? acc : 0xFF8E97AA);
                }
                // Schalter unten rechts
                int sx1 = x1 - 6, sx0 = sx1 - 20, sy0 = y1 - 13;
                Ui.round2(ctx, sx0, sy0, sx1, sy0 + 10, on ? acc : 0xFF333A4A);
                int kx = on ? sx1 - 9 : sx0 + 1;
                Ui.round(ctx, kx, sy0 + 1, kx + 8, sy0 + 9, on ? 0xFF0B0D12 : 0xFFE8EAF0);
                Ui.txt(ctx, tr, on ? Lang.t("AN", "ON") : Lang.t("AUS", "OFF"), x0 + 6, y1 - 12, on ? acc : 0xFF5E6678);
            } else {
                Ui.txt(ctx, tr, Lang.t("Öffnen ›", "Open ›"), x0 + 6, y1 - 12, hov ? acc : 0xFF7D8598);
            }
        }

        // Fuß
        int fy = py1 - FOOT + 4;
        List<Card> foot = new ArrayList<>();
        footer(foot);
        int fx = gx0;
        for (Card c : foot) {
            int w = tr.getWidth(c.name) + 16;
            Ui.btn(ctx, tr, fx, fy, fx + w, fy + 18, c.name, Ui.in(mouseX, mouseY, fx, fy, fx + w, fy + 18), Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
            fx += w + 4;
        }
        if (rows() > vis) {
            String h = Lang.t("Mausrad (", "Scroll (") + (scroll() + 1) + "/" + (rows() - vis + 1) + ")";
            Ui.txt(ctx, tr, h, fx + 4, fy + 5, 0xFF6B7385);
        }
        if (query().isEmpty()) {
            int ax = px1 - 8 - 130;
            Ui.btn(ctx, tr, ax, fy, ax + 62, fy + 18, Lang.t("Alle an", "All on"), Ui.in(mouseX, mouseY, ax, fy, ax + 62, fy + 18), Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
            int bx = px1 - 8 - 64;
            Ui.btn(ctx, tr, bx, fy, bx + 64, fy + 18, Lang.t("Alle aus", "All off"), Ui.in(mouseX, mouseY, bx, fy, bx + 64, fy + 18), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        }
    }
}
