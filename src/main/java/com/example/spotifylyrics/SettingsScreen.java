package com.example.spotifylyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** Einstellungen des Clients: Farben, Hintergrund, Sprache und Menü-Taste. */
public class SettingsScreen extends ItemScreen {
    private static boolean capturing = false;

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

    private Cell accent(int i) {
        Config c = Config.INSTANCE;
        int col = Ui.accentPreview(i);
        boolean sel = Math.floorMod(c.uiAccent, Ui.ACCENT_NAMES.length) == i;
        return C(sel ? "✔" : "", col, Ui.mix(col, 0xFFFFFFFF, 0.25f), 0xFF0B0D12, () -> {
            c.uiAccent = i;
            c.save();
            Ui.applyTheme();
            again();
        });
    }

    private Cell bg(int i) {
        Config c = Config.INSTANCE;
        int col = Ui.bgPreview(i);
        boolean sel = Math.floorMod(c.uiBg, Ui.BG_NAMES.length) == i;
        return C((sel ? "✔ " : "") + Ui.BG_NAMES[i], col, Ui.mix(col, 0xFFFFFFFF, 0.12f), sel ? 0xFFFFFFFF : 0xFFB8C0D0, () -> {
            c.uiBg = i;
            c.save();
            Ui.applyTheme();
            again();
        });
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(G(Lang.t("Skin wechseln ›", "Change skin ›"), () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new SkinScreen())));
        gap(2);
        sec(Lang.t("Akzentfarbe", "Accent color") + ": " + Ui.ACCENT_NAMES[Math.floorMod(c.uiAccent, Ui.ACCENT_NAMES.length)]);
        row(accent(0), accent(1), accent(2), accent(3));
        row(accent(4), accent(5), accent(6), accent(7));
        gap(2);
        sec(Lang.t("Hintergrund", "Background"));
        row(bg(0), bg(1), bg(2));
        row(bg(3), bg(4));
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
