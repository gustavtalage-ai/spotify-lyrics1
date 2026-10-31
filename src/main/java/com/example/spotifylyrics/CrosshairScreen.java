package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Fadenkreuz-Editor: selbst pixeln (9x9 bis 15x15), Farben, Spiegeln, Vorlagen und Gegner-Erkennung. */
public class CrosshairScreen extends Screen {
    private static final int HEAD = 28;
    static final int[] COLORS = {0xFFFFFFFF, 0xFF111111, 0xFF38BDF8, 0xFF4ADE80, 0xFFFACC15,
        0xFFFB923C, 0xFFFF3B4E, 0xFFF472B6, 0xFFA78BFA, 0xFF2DD4BF};
    private static int paint = 0xFFFFFFFF;
    private static boolean eraser = false;
    private static boolean mirror = true;

    private static final int K_GREY = 0, K_BLUE = 1, K_TOGGLE = 2, K_SWATCH = 3, K_RED = 4, K_LABEL = 5;

    private static final class Btn {
        int x, y, w, h, kind, color;
        String label;
        boolean on;
        Runnable run;
    }

    private final List<Btn> btns = new ArrayList<>();
    private int px0, px1, py0, py1, gx0, gy0, cs, n;
    private boolean wasDown, dirty;

    public CrosshairScreen() {
        super(Text.literal("Fadenkreuz"));
    }

    private static void again() {
        MinecraftClient.getInstance().setScreen(new CrosshairScreen());
    }

    private Btn add(int kind, int x, int y, int w, int h, String label, boolean on, int color, Runnable r) {
        Btn b = new Btn();
        b.kind = kind;
        b.x = x;
        b.y = y;
        b.w = w;
        b.h = h;
        b.label = label;
        b.on = on;
        b.color = color;
        b.run = r;
        btns.add(b);
        return b;
    }

    @Override
    protected void init() {
        btns.clear();
        Config c = Config.INSTANCE;
        n = Math.max(9, Math.min(15, c.crossN | 1));
        cs = Math.max(6, Math.min(16, 150 / n));
        int panelW = Math.min(404, this.width - 20);
        int panelH = HEAD + 8 + 204 + 8;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        gx0 = px0 + 10;
        gy0 = py0 + HEAD + 8;

        int rx0 = gx0 + 150 + 12, rx1 = px1 - 10, w = rx1 - rx0, half = (w - 4) / 2;
        int y = gy0;
        add(K_TOGGLE, rx0, y, w, 18, "Eigenes Fadenkreuz", PvpHud.on(PvpHud.CROSS), 0, () -> {
            PvpHud.flip(PvpHud.CROSS);
            if (PvpHud.on(PvpHud.CROSS)) c.crossPixel = true;
            c.save();
            again();
        });
        y += 22;
        add(K_LABEL, rx0, y, w, 10, "Farbe (Linksklick malt, Rechtsklick löscht)", false, 0, null);
        y += 11;
        int sw = (w - 4 * 3) / 5;
        for (int i = 0; i < COLORS.length; i++) {
            final int col = COLORS[i];
            add(K_SWATCH, rx0 + (i % 5) * (sw + 3), y + (i / 5) * 17, sw, 14, "", !eraser && paint == col, col, () -> {
                paint = col;
                eraser = false;
                again();
            });
        }
        y += 34 + 2;
        add(K_TOGGLE, rx0, y, half, 18, "Radierer", eraser, 0, () -> {
            eraser = !eraser;
            again();
        });
        add(K_TOGGLE, rx0 + half + 4, y, w - half - 4, 18, "Spiegeln", mirror, 0, () -> {
            mirror = !mirror;
            again();
        });
        y += 22;
        add(K_GREY, rx0, y, 20, 18, "‹", false, 0, () -> {
            c.crossN = Math.max(9, n - 2);
            c.save();
            again();
        });
        add(K_LABEL, rx0 + 24, y, w - 48, 18, "Feld: " + n + " × " + n, false, 0, null);
        add(K_GREY, rx1 - 20, y, 20, 18, "›", false, 0, () -> {
            c.crossN = Math.min(15, n + 2);
            c.save();
            again();
        });
        y += 22;
        add(K_GREY, rx0, y, half, 18, "Pixel: " + Math.max(1, c.crossScale) + "×", false, 0, () -> {
            c.crossScale = c.crossScale >= 2 ? 1 : 2;
            c.save();
            again();
        });
        add(K_TOGGLE, rx0 + half + 4, y, w - half - 4, 18, "Schatten", c.crossShadow, 0, () -> {
            c.crossShadow = !c.crossShadow;
            c.save();
            again();
        });
        y += 22;
        String[] pre = {"Kreuz", "Punkt", "Kreis", "X"};
        int pw = (w - 3 * 3) / 4;
        for (int i = 0; i < pre.length; i++) {
            final int k = i;
            add(K_GREY, rx0 + i * (pw + 3), y, i == 3 ? w - 3 * (pw + 3) : pw, 18, pre[i], false, 0, () -> {
                preset(k);
                again();
            });
        }
        y += 22;
        add(K_RED, rx0, y, half, 18, "Alles leeren", false, 0, () -> {
            for (int gy = 0; gy < n; gy++) for (int gx = 0; gx < n; gx++) set(gx, gy, 0);
            c.save();
            again();
        });
        add(K_GREY, rx0 + half + 4, y, w - half - 4, 18, "Einfacher Stil ›", false, 0,
            () -> MinecraftClient.getInstance().setScreen(new CrosshairSimpleScreen()));
        y += 22;
        add(K_TOGGLE, rx0, y, w - 24, 18, "Gegner-Erkennung", c.crossEnemy, 0, () -> {
            c.crossEnemy = !c.crossEnemy;
            c.save();
            again();
        });
        add(K_SWATCH, rx1 - 20, y + 2, 20, 14, "", false, c.crossEnemyColor, () -> {
            int at = 0;
            for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == c.crossEnemyColor) at = i;
            c.crossEnemyColor = COLORS[(at + 1) % COLORS.length];
            c.save();
            again();
        });

        // Zurück-Knopf oben rechts
        add(K_GREY, px1 - 10 - 64, py0 + 5, 64, 18, "‹ PvP", false, 0, () -> MinecraftClient.getInstance().setScreen(new PvpScreen()));

        for (Btn b : btns) {
            if (b.run == null) continue;
            final Runnable r = b.run;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), x -> r.run()).dimensions(b.x, b.y, b.w, b.h).build());
        }
    }

    // ---------- Pixel ----------

    private int idx(int gx, int gy) {
        int off = (15 - n) / 2;
        return (gy + off) * 15 + gx + off;
    }

    private void set(int gx, int gy, int col) {
        int[] p = Config.INSTANCE.crossPix;
        p[idx(gx, gy)] = col;
    }

    private void paintAt(int gx, int gy, int col) {
        set(gx, gy, col);
        if (mirror) {
            set(n - 1 - gx, gy, col);
            set(gx, n - 1 - gy, col);
            set(n - 1 - gx, n - 1 - gy, col);
        }
    }

    private void preset(int k) {
        int c = n / 2, col = paint;
        for (int gy = 0; gy < n; gy++) for (int gx = 0; gx < n; gx++) set(gx, gy, 0);
        if (k == 0) {
            for (int d = 2; d <= Math.min(4, c); d++) {
                set(c - d, c, col);
                set(c + d, c, col);
                set(c, c - d, col);
                set(c, c + d, col);
            }
            set(c, c, col);
        } else if (k == 1) {
            set(c, c, col);
            set(c - 1, c, col);
            set(c + 1, c, col);
            set(c, c - 1, col);
            set(c, c + 1, col);
        } else if (k == 2) {
            double r = Math.min(3, c);
            for (int gy = 0; gy < n; gy++) for (int gx = 0; gx < n; gx++) {
                double d = Math.hypot(gx - c, gy - c);
                if (Math.abs(d - r) < 0.5) set(gx, gy, col);
            }
            set(c, c, col);
        } else {
            for (int d = 1; d <= Math.min(3, c); d++) {
                set(c - d, c - d, col);
                set(c + d, c - d, col);
                set(c - d, c + d, col);
                set(c + d, c + d, col);
            }
        }
        Config.INSTANCE.save();
    }

    // ---------- Zeichnen ----------

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Config c = Config.INSTANCE;
        Ui.txt(ctx, tr, "Fadenkreuz-Editor", px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, "Pixel für Pixel selbst malen", px0 + 10, py0 + 17, Ui.MUTED);

        // Malen mit der Maus (gedrückt halten und ziehen)
        long win = GLFW.glfwGetCurrentContext();
        boolean left = win != 0 && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = win != 0 && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        int gw = n * cs;
        int hx = -1, hy = -1;
        if (Ui.in(mouseX, mouseY, gx0, gy0, gx0 + gw, gy0 + gw)) {
            hx = (mouseX - gx0) / cs;
            hy = (mouseY - gy0) / cs;
            if (left || right) {
                paintAt(hx, hy, right || eraser ? 0 : paint);
                dirty = true;
            }
        }
        if (!left && !right && wasDown && dirty) {
            c.save();
            dirty = false;
        }
        wasDown = left || right;

        // Raster
        Ui.card(ctx, gx0 - 2, gy0 - 2, gx0 + gw + 2, gy0 + gw + 2, Ui.BORDER, 0xFF0A0C10);
        for (int gy = 0; gy < n; gy++) {
            for (int gx = 0; gx < n; gx++) {
                int x0 = gx0 + gx * cs, y0 = gy0 + gy * cs;
                int col = c.crossPix[idx(gx, gy)];
                int bg = ((gx + gy) & 1) == 0 ? 0xFF161A22 : 0xFF1B2029;
                ctx.fill(x0, y0, x0 + cs, y0 + cs, bg);
                if (col != 0) ctx.fill(x0 + 1, y0 + 1, x0 + cs - 1, y0 + cs - 1, col);
            }
        }
        // Mitte markieren
        int mid = n / 2, mx0 = gx0 + mid * cs, my0 = gy0 + mid * cs;
        ctx.fill(mx0, my0, mx0 + cs, my0 + 1, 0x8038BDF8);
        ctx.fill(mx0, my0 + cs - 1, mx0 + cs, my0 + cs, 0x8038BDF8);
        ctx.fill(mx0, my0, mx0 + 1, my0 + cs, 0x8038BDF8);
        ctx.fill(mx0 + cs - 1, my0, mx0 + cs, my0 + cs, 0x8038BDF8);
        if (hx >= 0) {
            int x0 = gx0 + hx * cs, y0 = gy0 + hy * cs;
            int hc = eraser ? 0xFFFF9FB0 : 0xFFFFFFFF;
            ctx.fill(x0, y0, x0 + cs, y0 + 1, hc);
            ctx.fill(x0, y0 + cs - 1, x0 + cs, y0 + cs, hc);
            ctx.fill(x0, y0, x0 + 1, y0 + cs, hc);
            ctx.fill(x0 + cs - 1, y0, x0 + cs, y0 + cs, hc);
        }

        // Vorschau in echter Größe: normal und mit Gegner im Visier
        int vy0 = gy0 + 150 + 6, vy1 = py1 - 8, vx1 = gx0 + 150;
        boolean twoUp = c.crossEnemy;
        int vmid = twoUp ? (gx0 + vx1) / 2 : vx1;
        Ui.card(ctx, gx0 - 2, vy0, vmid - (twoUp ? 2 : 0), vy1, Ui.BORDER, 0xFF2E4A2A);
        PvpHud.drawPixelCross(ctx, (gx0 + vmid) / 2, (vy0 + vy1) / 2 + 3, 0, Math.max(1, c.crossScale));
        Ui.txt(ctx, tr, "Vorschau", gx0 + 3, vy0 + 3, 0xFFB8F0B0);
        if (twoUp) {
            Ui.card(ctx, vmid + 2, vy0, vx1 + 2, vy1, Ui.BORDER, 0xFF3A2228);
            PvpHud.drawPixelCross(ctx, (vmid + vx1) / 2 + 2, (vy0 + vy1) / 2 + 3, c.crossEnemyColor, Math.max(1, c.crossScale));
            Ui.txt(ctx, tr, "Gegner", vmid + 7, vy0 + 3, 0xFFFFB4BE);
        }

        // Knöpfe
        for (Btn b : btns) {
            boolean hov = b.run != null && Ui.in(mouseX, mouseY, b.x, b.y, b.x + b.w, b.y + b.h);
            switch (b.kind) {
                case K_TOGGLE -> Ui.toggle(ctx, tr, b.x, b.y, b.x + b.w, b.y + b.h, b.label, b.on, hov);
                case K_BLUE -> Ui.btn(ctx, tr, b.x, b.y, b.x + b.w, b.y + b.h, b.label, hov, Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
                case K_RED -> Ui.btn(ctx, tr, b.x, b.y, b.x + b.w, b.y + b.h, b.label, hov, 0xFF2A141B, 0xFF3D1C26, 0xFFFF9FB0);
                case K_LABEL -> {
                    if (b.h > 12) Ui.btn(ctx, tr, b.x, b.y, b.x + b.w, b.y + b.h, b.label, false, 0xFF12151B, 0xFF12151B, 0xFFD6D9E0);
                    else Ui.txt(ctx, tr, tr.trimToWidth(b.label, b.w), b.x, b.y, Ui.MUTED);
                }
                case K_SWATCH -> {
                    ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, b.on || hov ? 0xFFFFFFFF : 0xFF2B3140);
                    ctx.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, 0xFF0E1015);
                    ctx.fill(b.x + 2, b.y + 2, b.x + b.w - 2, b.y + b.h - 2, b.color);
                    if (b.on) ctx.fill(b.x + b.w / 2 - 1, b.y + b.h + 1, b.x + b.w / 2 + 1, b.y + b.h + 2, 0xFFFFFFFF);
                }
                default -> Ui.btn(ctx, tr, b.x, b.y, b.x + b.w, b.y + b.h, b.label, hov, Ui.SURFACE, Ui.SURFACE_HOV, 0xFFD6D9E0);
            }
        }
    }
}
