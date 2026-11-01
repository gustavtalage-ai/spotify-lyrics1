package com.example.spotifylyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** Einstellungen des Clients: Farben, Hintergrund, Sprache und Menü-Taste. */
public class SettingsScreen extends ItemScreen {
    private static boolean capturing = false;
    private int previewY = -1;

    public SettingsScreen() {
        super(Lang.t("Einstellungen", "Settings"), Lang.t("Design, Tasten, Zoom und mehr", "Design, keys, zoom and more"));
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

    private static int page = 0;
    /** Welche Taste gerade abgefragt wird: 0 = keine, 1 = Menü, 2 = Zoom. */
    private static int capture = 0;

    private Cell tab(String label, int idx) {
        return idx == page ? S(label) : G(label, () -> {
            page = idx;
            capture = 0;
            again();
        });
    }

    private Cell tg(String label, boolean on, Runnable flip) {
        return T(label, on, () -> {
            flip.run();
            Config.INSTANCE.save();
            again();
        });
    }

    private Cell keyBtn(String label, int key, int which) {
        if (capture == which) return B(Lang.t("Drücke jetzt eine Taste …", "Press a key now …"), () -> {
            capture = 0;
            again();
        });
        return G(label + Macros.keyName(key) + "  ›  " + Lang.t("ändern", "change"), () -> {
            capture = which;
            again();
        });
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        capturing = capture != 0;
        row(tab("Design", 0), tab(Lang.t("Steuerung", "Controls"), 1), tab(Lang.t("Allgemein", "General"), 2));
        gap(4);
        if (page == 0) {
            sec(Lang.t("Stil", "Style"));
            row(c.uiStyle == 0 ? S(Lang.t("Holo (Trailer)", "Holo (trailer)")) : G(Lang.t("Holo (Trailer)", "Holo (trailer)"), () -> {
                c.uiStyle = 0;
                c.save();
                again();
            }), c.uiStyle == 1 ? S(Lang.t("Schlicht", "Plain")) : G(Lang.t("Schlicht", "Plain"), () -> {
                c.uiStyle = 1;
                c.save();
                again();
            }));
            row(tg(Lang.t("Regenbogen bewegt sich", "Animated rainbow"), c.uiAnim, () -> c.uiAnim = !c.uiAnim),
                tg(Lang.t("Pfad unten zeigen", "Show path below"), c.uiPath, () -> c.uiPath = !c.uiPath));
            String[] dims = {Lang.t("Aus", "Off"), "25 %", "50 %", "70 %", "85 %"};
            stepper(Lang.t("Hintergrund abdunkeln: ", "Darken background: ") + dims[Math.max(0, Math.min(4, c.uiDim))], () -> {
                c.uiDim = Math.max(0, c.uiDim - 1);
                c.save();
                again();
            }, () -> {
                c.uiDim = Math.min(4, c.uiDim + 1);
                c.save();
                again();
            });
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
        } else if (page == 1) {
            sec(Lang.t("Menü", "Menu"));
            row(keyBtn(Lang.t("Menü-Taste: ", "Menu key: "), c.menuKey, 1));
            gap(4);
            sec("Zoom");
            row(tg(Lang.t("Zoom an", "Zoom on"), c.zoomOn, () -> c.zoomOn = !c.zoomOn),
                tg(Lang.t("Weiche Kamera", "Smooth camera"), c.zoomSmooth, () -> c.zoomSmooth = !c.zoomSmooth));
            row(keyBtn(Lang.t("Zoom-Taste (halten): ", "Zoom key (hold): "), c.zoomKey, 2));
            stepper(Lang.t("Stärke: ", "Strength: ") + c.zoomLevel + "x", () -> {
                c.zoomLevel = Math.max(2, c.zoomLevel - 1);
                c.save();
                again();
            }, () -> {
                c.zoomLevel = Math.min(10, c.zoomLevel + 1);
                c.save();
                again();
            });
            text(Lang.t("Taste gedrückt halten = ranzoomen, loslassen = normal.", "Hold the key to zoom in, release to go back."), 0xFF8B92A5);
            gap(4);
            sec("Auto-Klick");
            text(Lang.t("Start / Stopp im Spiel: Taste ", "Start / stop in game: key ") + AutoClick.keyName(), 0xFF8B92A5);
        } else {
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
            gap(4);
            sec("Skin");
            row(G(Lang.t("Skins öffnen ›", "Open skins ›"), () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new SkinScreen())));
            gap(4);
            sec(Lang.t("Über", "About"));
            text("Alpha Mod " + modVersion() + "  ·  Minecraft 1.21.11  ·  Fabric", Ui.TEXT);
            text(Lang.t("Kampf-Effekte basieren auf MasEffects (MIT).", "Combat effects are based on MasEffects (MIT)."), 0xFF8B92A5);
            gap(4);
            row(R(Lang.t("Design auf Standard", "Reset design"), () -> {
                c.uiAccent = 0;
                c.uiBg = 0;
                c.uiStyle = 0;
                c.uiAnim = true;
                c.uiDim = 3;
                c.uiPath = true;
                c.save();
                Ui.applyTheme();
                again();
            }), R(Lang.t("Tasten auf Standard", "Reset keys"), () -> {
                c.menuKey = 75;
                c.zoomKey = 67;
                c.save();
                again();
            }));
        }
    }

    private static String modVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("spotifylyrics")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");
        } catch (Throwable t) {
            return "";
        }
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
                if (capture == 2) Config.INSTANCE.zoomKey = k;
                else Config.INSTANCE.menuKey = k;
                Config.INSTANCE.save();
            }
            capture = 0;
            capturing = false;
            again();
            break;
        }
    }
}
