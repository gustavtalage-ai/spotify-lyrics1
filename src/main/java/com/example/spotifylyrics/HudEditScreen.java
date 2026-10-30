package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Anzeigen mit der Maus an die richtige Stelle ziehen. */
public class HudEditScreen extends Screen {
    private int drag = -1, offX, offY;
    private boolean wasDown = false;
    private final int[][] rects = new int[32][];
    private static final int LY = 100;
    private int[] lyRect;
    private boolean snapX, snapY;
    private final Screen backTo;

    public HudEditScreen() {
        this(null);
    }

    public HudEditScreen(Screen backTo) {
        super(Text.literal("Anzeigen verschieben"));
        this.backTo = backTo;
    }

    @Override
    protected void init() {
        int y = this.height - 30;
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(backTo != null ? backTo : new PvpScreen()))
            .dimensions(this.width / 2 - 104, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
            PvpHud.resetPositions();
            LyricsHud.resetPos();
            MinecraftClient.getInstance().setScreen(new HudEditScreen(backTo));
        }).dimensions(this.width / 2 + 4, y, 100, 20).build());
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x9006080C);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = this.width, h = this.height;

        // Mitte des Bildschirms: Kreuz mit Linien (leuchtet, wenn die Anzeige einrastet)
        int mx0 = w / 2, my0 = h / 2;
        ctx.fill(mx0, 0, mx0 + 1, h, snapX ? 0xB038BDF8 : 0x30FFFFFF);
        ctx.fill(0, my0, w, my0 + 1, snapY ? 0xB038BDF8 : 0x30FFFFFF);
        ctx.fill(mx0 - 9, my0, mx0 + 10, my0 + 1, 0xC0FFFFFF);
        ctx.fill(mx0, my0 - 9, mx0 + 1, my0 + 10, 0xC0FFFFFF);

        int shown = 0;
        for (int i = 0; i < rects.length; i++) rects[i] = null;
        for (int id : PvpHud.HUD_IDS) {
            if (!PvpHud.enabled(id)) continue;
            int[] pp = PvpHud.pos(id, w, h);
            int[] sz = PvpHud.elem(id, ctx, pp[0], pp[1], true);
            if (sz[0] <= 0) continue;
            shown++;
            rects[id] = new int[] {pp[0], pp[1], sz[0], sz[1]};
        }
        lyRect = Config.INSTANCE.enabled ? LyricsHud.drawDemo(ctx) : null;
        if (lyRect != null) shown++;
        if (lyRect != null) {
            boolean act = drag == LY || (drag < 0 && Ui.in(mouseX, mouseY, lyRect[0], lyRect[1], lyRect[0] + lyRect[2], lyRect[1] + lyRect[3]));
            int col = act ? 0xFF38BDF8 : 0x70FFFFFF;
            int[] r = lyRect;
            ctx.fill(r[0] - 2, r[1] - 2, r[0] + r[2] + 2, r[1] - 1, col);
            ctx.fill(r[0] - 2, r[1] + r[3] + 1, r[0] + r[2] + 2, r[1] + r[3] + 2, col);
            ctx.fill(r[0] - 2, r[1] - 1, r[0] - 1, r[1] + r[3] + 1, col);
            ctx.fill(r[0] + r[2] + 1, r[1] - 1, r[0] + r[2] + 2, r[1] + r[3] + 1, col);
            if (act) Ui.txt(ctx, tr, "Lyrics", r[0], Math.max(0, r[1] - 12), 0xFF7DD3FC);
        }
        for (int id : PvpHud.HUD_IDS) {
            int[] r = rects[id];
            if (r == null) continue;
            boolean act = drag == id || (drag < 0 && Ui.in(mouseX, mouseY, r[0], r[1], r[0] + r[2], r[1] + r[3]));
            int col = act ? 0xFF38BDF8 : 0x70FFFFFF;
            ctx.fill(r[0] - 2, r[1] - 2, r[0] + r[2] + 2, r[1] - 1, col);
            ctx.fill(r[0] - 2, r[1] + r[3] + 1, r[0] + r[2] + 2, r[1] + r[3] + 2, col);
            ctx.fill(r[0] - 2, r[1] - 1, r[0] - 1, r[1] + r[3] + 1, col);
            ctx.fill(r[0] + r[2] + 1, r[1] - 1, r[0] + r[2] + 2, r[1] + r[3] + 1, col);
            if (act) Ui.txt(ctx, tr, PvpHud.elemName(id), r[0], Math.max(0, r[1] - 12), 0xFF7DD3FC);
        }

        // Maus selbst abfragen: gedrückt = ziehen
        long win = GLFW.glfwGetCurrentContext();
        boolean down = win != 0 && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && mouseY < h - 58) {
            if (lyRect != null && Ui.in(mouseX, mouseY, lyRect[0] - 2, lyRect[1] - 2, lyRect[0] + lyRect[2] + 2, lyRect[1] + lyRect[3] + 2)) {
                drag = LY;
                offX = mouseX - lyRect[0];
                offY = mouseY - lyRect[1];
            }
            for (int i = PvpHud.HUD_IDS.length - 1; i >= 0 && drag < 0; i--) {
                int id = PvpHud.HUD_IDS[i];
                int[] r = rects[id];
                if (r != null && Ui.in(mouseX, mouseY, r[0] - 2, r[1] - 2, r[0] + r[2] + 2, r[1] + r[3] + 2)) {
                    drag = id;
                    offX = mouseX - r[0];
                    offY = mouseY - r[1];
                    break;
                }
            }
        }
        if (down && drag >= 0) {
            int[] dr = drag == LY ? lyRect : rects[drag];
            if (dr != null) {
                int nx = mouseX - offX, ny = mouseY - offY;
                snapX = Math.abs(nx + dr[2] / 2 - w / 2) <= 6;
                snapY = Math.abs(ny + dr[3] / 2 - h / 2) <= 6;
                if (snapX) nx = (w - dr[2]) / 2;
                if (snapY) ny = (h - dr[3]) / 2;
                if (drag == LY) LyricsHud.setPos(nx, ny, dr[2], w, h, snapX);
                else PvpHud.setPos(drag, nx, ny, w, h);
            }
        } else {
            snapX = false;
            snapY = false;
        }
        if (!down && wasDown && drag >= 0) {
            Config.INSTANCE.save();
            drag = -1;
        }
        wasDown = down;

        String hint = shown == 0 ? "Keine Anzeige an. Schalte sie im PvP-Menü unter Anzeigen ein."
            : "Ziehen. Am Kreuz (Mitte) rastet die Anzeige ein. Esc oder Fertig = speichern.";
        Ui.shadow(ctx, w / 2 - 230, h - 56, w / 2 + 230, h - 4);
        Ui.card(ctx, w / 2 - 230, h - 56, w / 2 + 230, h - 4, Ui.BORDER, 0xF00E1015);
        ctx.drawCenteredTextWithShadow(tr, hint, w / 2, h - 51, 0xFFD6D9E0);
        int bx = w / 2 - 104, by = h - 30;
        Ui.btn(ctx, tr, bx, by, bx + 100, by + 20, "Fertig", Ui.in(mouseX, mouseY, bx, by, bx + 100, by + 20), 0xFF123A55, 0xFF1A4E73, 0xFFFFFFFF);
        Ui.btn(ctx, tr, bx + 108, by, bx + 208, by + 20, "Zurücksetzen", Ui.in(mouseX, mouseY, bx + 108, by, bx + 208, by + 20), 0xFF2A141B, 0xFF3D1C26, 0xFFFF9FB0);
    }
}
