package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

import java.util.Locale;

/** Einstellungen der Reichweiten-Kreise – dieselben Optionen wie in der Mod "Reach Circles". */
public class ReachCirclesScreen extends ItemScreen {
    static final int[] COLORS = {0x00FF00, 0xFF0000, 0xFFFF00, 0x00FFFF, 0x0080FF, 0xFF00FF, 0xFFFFFF, 0xFF8000};
    static final String[] COLOR_NAMES = {"Grün", "Rot", "Gelb", "Türkis", "Blau", "Pink", "Weiß", "Orange"};

    public ReachCirclesScreen() {
        super(Lang.t("Reichweiten-Kreise", "Reach circles"), "Reach Circles");
    }

    @Override
    protected ItemScreen fresh() {
        return new ReachCirclesScreen();
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

    private void step(String label, Runnable minus, Runnable plus) {
        stepper(label, () -> {
            minus.run();
            Config.INSTANCE.save();
            again();
        }, () -> {
            plus.run();
            Config.INSTANCE.save();
            again();
        });
    }

    private static int next(int color) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == color) return COLORS[(i + 1) % COLORS.length];
        return COLORS[0];
    }

    private static int idx(int color) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == color) return i;
        return 0;
    }

    private static String name(int color) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == color) return COLOR_NAMES[i];
        return String.format("#%06X", color);
    }

    private Cell color(String label, int value, java.util.function.IntConsumer set) {
        return C(label + ": " + name(value), 0xFF000000 | ((value >> 2) & 0x3F3F3F), 0xFF000000 | ((value >> 1) & 0x7F7F7F), 0xFFFFFFFF, () -> {
            set.accept(next(value));
            Config.INSTANCE.save();
            again();
        });
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(t(Lang.t("Kreise an", "Enable circles"), c.rcOn, () -> c.rcOn = !c.rcOn));
        row(t(Lang.t("Gegner leuchten grün, wenn du sie treffen kannst", "Enemies glow green when you can hit them"), c.rcGlow, () -> c.rcGlow = !c.rcGlow));
        row(t(Lang.t("Nur mit Waffe in der Hand", "Only while holding a weapon"), c.rcWeapon, () -> c.rcWeapon = !c.rcWeapon));
        gap(4);
        text(Lang.t("Dein Kreis wird grün, wenn du jemanden treffen kannst,", "Your circle turns green when you can hit someone,"), 0xFF8A93A6);
        text(Lang.t("der Kreis eines Gegners, wenn er dich treffen kann.", "an enemy's circle when they can hit you."), 0xFF8A93A6);
    }
}
