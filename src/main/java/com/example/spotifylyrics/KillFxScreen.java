package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Kollektionen: Finisher + passende Waffen-Skins, wie Skin-Bundles. Farben sind fest je Kollektion. */
public class KillFxScreen extends ItemScreen {
    public KillFxScreen() {
        super(Lang.t("Finisher & Skins", "Finisher & skins"), Lang.t("Kollektion wählen", "Pick a collection"));
    }

    @Override
    protected ItemScreen fresh() {
        return new KillFxScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    @Override
    protected int panelWidth() {
        return 380;
    }

    private Cell t(String label, boolean on, Runnable flip) {
        return T(label, on, () -> {
            flip.run();
            Config.INSTANCE.save();
            again();
        });
    }

    /** Farben der Kollektion als kleine Vorschau-Kästen. */
    private static final int[][] SHOW = {
        {0xFFFF6A00, 0xFFFFD54A, 0xFFFFF6C8},
        {0xFF2E8BFF, 0xFF7DF9FF, 0xFFB46CFF},
        {0xFF5B2BFF, 0xFFB14BFF, 0xFFFF3DD8},
        {0xFFFF00E6, 0xFF00F0FF, 0xFF39FF14},
        {0xFFC4102F, 0xFFFFC23D, 0xFFFFF3D6}
    };

    private static final net.minecraft.item.Item[] SHOW_ITEMS = {net.minecraft.item.Items.NETHERITE_SWORD, net.minecraft.item.Items.DIAMOND_AXE,
        net.minecraft.item.Items.MACE, net.minecraft.item.Items.IRON_SWORD};
    /** Zeile -> Kollektion (für die Waffen-Vorschau). */
    private final java.util.Map<Integer, Integer> previewRows = new java.util.HashMap<>();
    private int bigRow = -1;

    private void bundle(int i) {
        Config c = Config.INSTANCE;
        boolean sel = c.kfStyle == i;
        String label = (sel ? "✔ " : "") + WeaponSkins.BUNDLES[i] + "  ·  " + KillFx.STYLES[i];
        Runnable pick = () -> {
            c.kfStyle = i;
            c.save();
            again();
        };
        row(sel ? S(label).w(6) : G(label, pick).w(6), sel ? S("").w(5) : C("", 0xFF12151B, 0xFF1A1E27, 0, pick).w(5));
        previewRows.put(lastRow(), i);
    }

    @Override
    protected void build() {
        previewRows.clear();
        bigRow = -1;
        Config c = Config.INSTANCE;
        row(t(Lang.t("Finisher an", "Finisher on"), c.kfOn, () -> c.kfOn = !c.kfOn),
            t(Lang.t("Waffen-Skins an", "Weapon skins on"), c.wsOn, () -> c.wsOn = !c.wsOn));
        gap(4);
        Config cc = Config.INSTANCE;
        if (cc.kfStyle < KillFx.STYLES.length) {
            sec(Lang.t("Ausgerüstet: ", "Equipped: ") + WeaponSkins.BUNDLES[cc.kfStyle]);
            row(X("").w(1));
            bigRow = lastRow();
            gap(18);
        }
        sec(Lang.t("Kollektion (Skin + Finisher)", "Collection (skin + finisher)"));
        for (int i = 0; i < KillFx.STYLES.length; i++) bundle(i);
        row(c.kfStyle >= KillFx.STYLES.length ? S("✔ " + Lang.t("Zufälliger Finisher (ohne Skin)", "Random finisher (no skin)"))
            : G(Lang.t("Zufälliger Finisher (ohne Skin)", "Random finisher (no skin)"), () -> {
                c.kfStyle = KillFx.STYLES.length;
                c.save();
                again();
            }));
        text(Lang.t("Skins für alle Schwerter, Äxte, Speere und den Streitkolben –", "Skins for all swords, axes, spears and the mace –"), 0xFF8A93A6);
        text(Lang.t("nur deine Waffen, Gegner sehen bei dir normale Waffen.", "only your weapons, enemies look normal to you."), 0xFF8A93A6);
        gap(4);
        sec(Lang.t("Extras", "Extras"));
        row(t(Lang.t("Kill-Banner (Serie 1–5)", "Kill banner (streak 1–5)"), c.kfBanner, () -> c.kfBanner = !c.kfBanner),
            t("Sound", c.kfSound, () -> c.kfSound = !c.kfSound));
        row(t(Lang.t("Partikel", "Particles"), c.kfParticles, () -> c.kfParticles = !c.kfParticles),
            t(Lang.t("Bildschirm-Blitz", "Screen flash"), c.kfFlash, () -> c.kfFlash = !c.kfFlash));
        row(t(Lang.t("Auch bei Mobs", "Also for mobs"), c.kfMobs, () -> c.kfMobs = !c.kfMobs));
        stepper(Lang.t("Größe: ", "Size: ") + c.kfSize + "%", () -> {
            c.kfSize = Math.max(50, c.kfSize - 25);
            c.save();
            again();
        }, () -> {
            c.kfSize = Math.min(300, c.kfSize + 25);
            c.save();
            again();
        });
        gap(6);
        row(B(Lang.t("▶ Finisher ansehen", "▶ Preview finisher"), () -> {
            MinecraftClient.getInstance().setScreen(null);
            KillFx.preview();
        }));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(net.minecraft.client.gui.DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        try {
            for (var en : previewRows.entrySet()) {
                int[] r = rect(en.getKey(), 1);
                if (r[1] < py0 + HEAD || r[1] > py1 - 20) continue;
                int n = SHOW_ITEMS.length, step = Math.min(20, r[2] / n);
                int x0 = r[0] + (r[2] - step * n) / 2;
                for (int k = 0; k < n; k++) ctx.drawItem(WeaponSkins.preview(SHOW_ITEMS[k], en.getValue()), x0 + k * step + (step - 16) / 2, r[1] + 2);
            }
            Config c = Config.INSTANCE;
            if (bigRow >= 0 && c.kfStyle < KillFx.STYLES.length) {
                int[] r = rect(bigRow, 0);
                if (r[1] >= py0 + HEAD && r[1] < py1 - 40) {
                    int n = SHOW_ITEMS.length, size = 36, gapX = 10;
                    int total = n * size + (n - 1) * gapX, x0 = r[0] + (r[2] - total) / 2;
                    for (int k = 0; k < n; k++) {
                        int x = x0 + k * (size + gapX), y = r[1] - 2;
                        ctx.fill(x, y, x + size, y + size, 0xFF0B0D12);
                        Ui.round(ctx, x, y, x + size, y + size, 0xFF1A1E27);
                        org.joml.Matrix3x2fStack m = ctx.getMatrices();
                        m.pushMatrix();
                        m.translate(x + 2, y + 2);
                        m.scale(2f, 2f);
                        ctx.drawItem(WeaponSkins.preview(SHOW_ITEMS[k], c.kfStyle), 0, 0);
                        m.popMatrix();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
