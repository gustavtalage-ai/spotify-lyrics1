package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Einstellungen der Kill-Animationen. */
public class KillFxScreen extends ItemScreen {
    public KillFxScreen() {
        super(Lang.t("Kill-Animation", "Kill animation"), Lang.t("3D-Effekt beim Kill", "3D effect on kill"));
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

    private Cell t(String label, boolean on, Runnable flip) {
        return T(label, on, () -> {
            flip.run();
            Config.INSTANCE.save();
            again();
        });
    }

    private Cell style(int i, String label) {
        Config c = Config.INSTANCE;
        if (c.kfStyle == i) return S("✔ " + label);
        return G(label, () -> {
            c.kfStyle = i;
            c.save();
            again();
        });
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(t(Lang.t("Kill-Animation an", "Kill animation on"), c.kfOn, () -> c.kfOn = !c.kfOn));
        row(t(Lang.t("Kill-Banner unten (Serie 1–5)", "Kill banner (streak 1–5)"), c.kfBanner, () -> c.kfBanner = !c.kfBanner),
            t("Sound", c.kfSound, () -> c.kfSound = !c.kfSound));
        row(t(Lang.t("Auch bei Mobs", "Also for mobs"), c.kfMobs, () -> c.kfMobs = !c.kfMobs));
        gap(4);
        sec(Lang.t("Animation", "Animation"));
        String[] s = KillFx.STYLES;
        row(style(0, s[0]), style(1, s[1]));
        row(style(2, s[2]), style(3, s[3]));
        row(style(4, s[4]), style(5, Lang.t("Zufällig", "Random")));
        gap(4);
        sec(Lang.t("Farbe", "Color"));
        int[] argb = new int[KillFx.COLORS.length];
        for (int i = 0; i < argb.length; i++) argb[i] = KillFx.COLORS[i] == 0 ? 0 : 0xFF000000 | KillFx.COLORS[i];
        colorPick(null, argb, KillFx.COLOR_NAMES, c.kfColor, k -> c.kfColor = k);
        gap(6);
        row(B(Lang.t("▶ Vorschau ansehen", "▶ Preview"), () -> {
            MinecraftClient.getInstance().setScreen(null);
            KillFx.preview();
        }));
        text(Lang.t("Startet, wenn ein Gegner stirbt, den du gerade getroffen hast.", "Plays when an enemy you just hit dies."), 0xFF8A93A6);
    }
}
