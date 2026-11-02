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

    private void bundle(int i) {
        Config c = Config.INSTANCE;
        boolean sel = c.kfStyle == i;
        String label = WeaponSkins.BUNDLES[i] + "  ·  " + Lang.t("Finisher: ", "Finisher: ") + KillFx.STYLES[i];
        Runnable pick = () -> {
            if (c.kfStyle == i) return;
            c.kfStyle = i;
            c.save();
            if (c.wsOn) Designer.apply();
            again();
        };
        row(sel ? S("✔ " + label).w(7) : G(label, pick).w(7),
            Sw(SHOW[i][0], "", false, pick), Sw(SHOW[i][1], "", false, pick), Sw(SHOW[i][2], "", false, pick));
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(t(Lang.t("Finisher an", "Finisher on"), c.kfOn, () -> c.kfOn = !c.kfOn),
            t(Lang.t("Waffen-Skins an", "Weapon skins on"), c.wsOn, () -> {
                c.wsOn = !c.wsOn;
                c.save();
                Designer.apply();
            }));
        gap(4);
        sec(Lang.t("Kollektion (Skin + Finisher)", "Collection (skin + finisher)"));
        for (int i = 0; i < KillFx.STYLES.length; i++) bundle(i);
        row(c.kfStyle >= KillFx.STYLES.length ? S("✔ " + Lang.t("Zufälliger Finisher (ohne Skin)", "Random finisher (no skin)"))
            : G(Lang.t("Zufälliger Finisher (ohne Skin)", "Random finisher (no skin)"), () -> {
                c.kfStyle = KillFx.STYLES.length;
                c.save();
                if (c.wsOn) Designer.apply();
                again();
            }));
        text(Lang.t("Skins gelten für alle Schwerter, Äxte, Speere und den Streitkolben.", "Skins apply to all swords, axes, spears and the mace."), 0xFF8A93A6);
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
}
