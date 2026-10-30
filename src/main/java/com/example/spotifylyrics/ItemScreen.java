package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemeinsame Grundlage für die neuen Menüs: man beschreibt nur die Zeilen (Schalter, Knöpfe, Texte),
 * das Panel, die Größe, die Klickflächen und das Zeichnen übernimmt diese Klasse.
 */
abstract class ItemScreen extends Screen {
    static final int HEAD = 28;
    private static final int K_TOGGLE = 0, K_BTN = 1, K_LABEL = 2, K_SECTION = 3, K_TEXT = 4;

    /** Eine Zelle in einer Zeile. */
    static final class Cell {
        final int kind;
        final String label;
        final boolean on;
        final Runnable act;
        final int c1, c2, fg;
        int weight = 1;

        Cell(int kind, String label, boolean on, Runnable act, int c1, int c2, int fg) {
            this.kind = kind;
            this.label = label;
            this.on = on;
            this.act = act;
            this.c1 = c1;
            this.c2 = c2;
            this.fg = fg;
        }

        Cell w(int n) {
            this.weight = n;
            return this;
        }
    }

    /** Schalter mit Ein/Aus-Schieber. */
    static Cell T(String label, boolean on, Runnable r) {
        return new Cell(K_TOGGLE, label, on, r, 0, 0, 0);
    }

    /** Lila Knopf. */
    static Cell B(String label, Runnable r) {
        return new Cell(K_BTN, label, false, r, Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
    }

    /** Grauer Knopf. */
    static Cell G(String label, Runnable r) {
        return new Cell(K_BTN, label, false, r, Ui.SURFACE, Ui.SURFACE_HOV, 0xFFD6D9E0);
    }

    /** Roter Knopf. */
    static Cell R(String label, Runnable r) {
        return new Cell(K_BTN, label, false, r, 0xFF2A141B, 0xFF3D1C26, 0xFFFF9FB0);
    }

    /** Gewählter Reiter (nicht klickbar). */
    static Cell S(String label) {
        return new Cell(K_BTN, label, false, null, Ui.ACCENT, Ui.ACCENT, 0xFF04121B);
    }

    /** Reiner Text-Kasten. */
    static Cell X(String label) {
        return new Cell(K_LABEL, label, false, null, 0xFF12151B, 0xFF12151B, 0xFFD6D9E0);
    }

    private static final class It {
        int kind, x, y, w, h, c1, c2, fg;
        String s;
        boolean on;
        Runnable act;
    }

    private final List<It> items = new ArrayList<>();
    /** Je Zeile die Kästen der Zellen (x relativ zum Inhalt, y relativ zum Panel, Breite, Höhe). */
    private final List<int[][]> rowRects = new ArrayList<>();
    private final String head, sub;
    protected int px0, px1, py0, py1, cx, cw;
    private int cur;
    private boolean hasBack;

    protected ItemScreen(String head, String sub) {
        super(Text.literal(head));
        this.head = head;
        this.sub = sub;
    }

    /** Zeilen aufbauen (mit sec, row, text, gap, stepper). */
    protected abstract void build();

    /** Neue Instanz desselben Menüs (nach jedem Klick, damit alles neu gezeichnet wird). */
    protected abstract ItemScreen fresh();

    /** Menü, zu dem der Knopf oben rechts zurückführt (null = kein Knopf). */
    protected Screen back() {
        return null;
    }

    protected String backLabel() {
        return "‹ Zurück";
    }

    protected int panelWidth() {
        return 320;
    }

    protected boolean onScroll(int dir) {
        return false;
    }

    protected void again() {
        MinecraftClient.getInstance().setScreen(fresh());
    }

    // ---------- Aufbau-Helfer ----------

    private It add(int kind, int x, int y, int w, int h, String s) {
        It it = new It();
        it.kind = kind;
        it.x = x;
        it.y = y;
        it.w = w;
        it.h = h;
        it.s = s;
        items.add(it);
        return it;
    }

    protected void sec(String text) {
        add(K_SECTION, 0, cur, cw, 12, text);
        cur += 14;
    }

    protected void gap(int n) {
        cur += n;
    }

    protected void text(String s, int color) {
        It it = add(K_TEXT, 0, cur, cw, 10, s);
        it.fg = color;
        cur += 11;
    }

    protected void row(Cell... cs) {
        int gapPx = 4;
        int total = 0;
        for (Cell c : cs) total += c.weight;
        int avail = cw - gapPx * (cs.length - 1);
        int x = 0, used = 0, wsum = 0;
        int[][] rects = new int[cs.length][];
        for (int i = 0; i < cs.length; i++) {
            Cell c = cs[i];
            wsum += c.weight;
            int w = (i == cs.length - 1) ? avail - used : Math.round(avail * (float) c.weight / total);
            if (i < cs.length - 1) used += w;
            rects[i] = new int[] {x, cur, w, 20};
            It it = add(c.kind, x, cur, w, 20, c.label);
            it.on = c.on;
            it.act = c.act;
            it.c1 = c.c1;
            it.c2 = c.c2;
            it.fg = c.fg;
            x += w + gapPx;
        }
        rowRects.add(rects);
        cur += 22;
    }

    /** Nummer der zuletzt gebauten Zeile. */
    protected int lastRow() {
        return rowRects.size() - 1;
    }

    /** Kasten einer Zelle in Bildschirm-Koordinaten {x, y, Breite, Höhe}; erst nach build() gültig. */
    protected int[] rect(int row, int cell) {
        int[] r = rowRects.get(row)[cell];
        return new int[] {cx + r[0], py0 + r[1], r[2], r[3]};
    }

    /** [-]  Text  [+] */
    protected void stepper(String mid, Runnable minus, Runnable plus) {
        row(G("-", minus), X(mid).w(6), G("+", plus));
    }

    // ---------- Screen ----------

    @Override
    protected void init() {
        items.clear();
        rowRects.clear();
        int panelW = Math.min(panelWidth(), this.width - 20);
        cw = panelW - 20;
        cur = HEAD + 8;
        build();
        int panelH = cur + 8;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        cx = px0 + 10;
        for (It it : items) {
            it.x += cx;
            it.y += py0;
        }
        hasBack = back() != null;
        if (hasBack) {
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(back()))
                .dimensions(px1 - 10 - 72, py0 + 5, 72, 18).build());
        }
        for (It it : items) {
            if (it.act == null || it.kind == K_SECTION || it.kind == K_TEXT) continue;
            final Runnable r = it.act;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(it.x, it.y, it.w, it.h).build());
        }
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return onScroll((int) Math.signum(vertical));
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        Ui.txt(ctx, tr, head, px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, tr.trimToWidth(sub, px1 - px0 - 110), px0 + 10, py0 + 17, 0xFF8B92A5);
        if (hasBack) {
            int bx0 = px1 - 10 - 72, by0 = py0 + 5;
            Ui.btn(ctx, tr, bx0, by0, bx0 + 72, by0 + 18, backLabel(), Ui.in(mouseX, mouseY, bx0, by0, bx0 + 72, by0 + 18),
                0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        }
        for (It it : items) {
            boolean hov = it.act != null && Ui.in(mouseX, mouseY, it.x, it.y, it.x + it.w, it.y + it.h);
            switch (it.kind) {
                case K_TOGGLE -> Ui.toggle(ctx, tr, it.x, it.y, it.x + it.w, it.y + it.h, it.s, it.on, hov);
                case K_BTN, K_LABEL -> Ui.btn(ctx, tr, it.x, it.y, it.x + it.w, it.y + it.h, it.s, hov, it.c1, it.c2, it.fg);
                case K_SECTION -> Ui.section(ctx, tr, it.x + 2, it.y, it.s, it.w);
                default -> Ui.txt(ctx, tr, tr.trimToWidth(it.s, it.w - 2), it.x + 2, it.y, it.fg);
            }
        }
    }
}
