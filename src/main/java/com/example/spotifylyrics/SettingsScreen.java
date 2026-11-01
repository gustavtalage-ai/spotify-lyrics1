package com.example.spotifylyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** Einstellungen des Clients: Farben, Hintergrund, Sprache und Menü-Taste. */
public class SettingsScreen extends ItemScreen {
    private static boolean capturing = false;
    private int previewY = -1;

    public SettingsScreen() {
        super(Lang.t("Einstellungen", "Settings"), Lang.t("Farben, Sprache und Taste", "Colors, language and key"));
    }

    @Override
    protected ItemScreen fresh() {
        return new SettingsScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return Lang.t("‹ Menü", "‹ Menu");
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(G(Lang.t("Skin wechseln ›", "Change skin ›"), () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new SkinScreen())));
        gap(2);
        sec(Lang.t("Akzentfarbe", "Accent color"));
        int[] acc = new int[Ui.ACCENT_NAMES.length];
        for (int i = 0; i < acc.length; i++) acc[i] = Ui.accentPreview(i);
        colorPick(Lang.t("Gewählt", "Selected"), acc, Ui.ACCENT_NAMES, c.uiAccent, k -> {
            c.uiAccent = k;
            Ui.applyTheme();
        });
        gap(2);
        sec(Lang.t("Hintergrund", "Background"));
        int bsel = Math.floorMod(c.uiBg, Ui.BG_NAMES.length);
        Cell[] bgs = new Cell[Ui.BG_NAMES.length];
        for (int i = 0; i < bgs.length; i++) {
            final int k = i;
            bgs[i] = Sw(Ui.mix(Ui.bgPreview(i), 0xFFFFFFFF, 0.06f), Ui.BG_NAMES[i], k == bsel, () -> {
                c.uiBg = k;
                c.save();
                Ui.applyTheme();
                again();
            });
        }
        row(bgs);
        gap(2);
        sec(Lang.t("Vorschau", "Preview"));
        previewY = area(26);
        gap(2);
        sec(Lang.t("Sprache", "Language"));
        row(c.lang == 0 ? S("Deutsch") : G("Deutsch", () -> {
            c.lang = 0;
            c.save();
            again();
        }), c.lang == 1 ? S("English") : G("English", () -> {
            c.lang = 1;
            c.save();
            again();
        }));
        gap(2);
        sec(Lang.t("Taste für das Menü", "Menu key"));
        row(capturing ? B(Lang.t("Drücke jetzt eine Taste …", "Press a key now …"), () -> {
            capturing = false;
            again();
        }) : G(Lang.t("Taste: ", "Key: ") + Macros.keyName(c.menuKey) + "  ›  " + Lang.t("ändern", "change"), () -> {
            capturing = true;
            again();
        }));
        gap(2);
        row(R(Lang.t("Alles auf Standard", "Reset to default"), () -> {
            c.uiAccent = 0;
            c.uiBg = 0;
            c.menuKey = 75;
            c.save();
            Ui.applyTheme();
            again();
        }));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (previewY >= 0) {
            int y = py0 + previewY - scrollY();
            if (y > py0 + HEAD && y + 22 < py1) {
                var tr = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
                int half = (cw - 4) / 2;
                Ui.toggle(ctx, tr, cx, y, cx + half, y + 20, Lang.t("Schalter an", "Switch on"), true, false);
                Ui.btn(ctx, tr, cx + half + 4, y, cx + cw, y + 20, Lang.t("Knopf", "Button"), false, Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
            }
        }
        if (!capturing) return;
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0) return;
        for (int k = 32; k <= 348; k++) {
            if (GLFW.glfwGetKey(win, k) != GLFW.GLFW_PRESS) continue;
            if (k != GLFW.GLFW_KEY_ESCAPE) {
                Config.INSTANCE.menuKey = k;
                Config.INSTANCE.save();
            }
            capturing = false;
            again();
            break;
        }
    }
}
