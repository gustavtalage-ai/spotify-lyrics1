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
        row(t(Lang.t("Ping-Ausgleich", "Ping compensation"), c.rcPing, () -> c.rcPing = !c.rcPing),
            t(Lang.t("Gefüllter Kreis", "Filled circle"), c.rcFilled, () -> c.rcFilled = !c.rcFilled));
        gap(4);

        sec(Lang.t("Anzeige", "Indicators"));
        row(t(Lang.t("Zeigen, wenn du jemanden treffen kannst", "Show when you can reach someone"), c.rcLocalGreen, () -> c.rcLocalGreen = !c.rcLocalGreen));
        row(t(Lang.t("Zeigen, wenn ein Gegner dich treffen kann", "Show when another player can reach you"), c.rcOtherGreen, () -> c.rcOtherGreen = !c.rcOtherGreen));
        row(t(Lang.t("Nur wenn du ihn auch treffen kannst", "Only when you can reach them too"), c.rcOtherGreen2, () -> c.rcOtherGreen2 = !c.rcOtherGreen2));
        gap(4);

        sec(Lang.t("Aussehen", "Look"));
        step(Lang.t("Radius: ", "Radius: ") + String.format(Locale.ROOT, "%.1f", c.rcRadius),
            () -> c.rcRadius = Math.max(0.5f, Math.round((c.rcRadius - 0.1f) * 10) / 10f),
            () -> c.rcRadius = Math.min(10f, Math.round((c.rcRadius + 0.1f) * 10) / 10f));
        step(Lang.t("Dicke: ", "Thickness: ") + String.format(Locale.ROOT, "%.2f", c.rcThick / 100f),
            () -> c.rcThick = Math.max(1, c.rcThick - 1), () -> c.rcThick = Math.min(100, c.rcThick + 1));
        step(Lang.t("Deckkraft: ", "Opacity: ") + c.rcOpacity + "%",
            () -> c.rcOpacity = Math.max(0, c.rcOpacity - 5), () -> c.rcOpacity = Math.min(100, c.rcOpacity + 5));
        step(Lang.t("Segmente: ", "Segments: ") + c.rcSegments,
            () -> c.rcSegments = Math.max(8, c.rcSegments - 8), () -> c.rcSegments = Math.min(256, c.rcSegments + 8));
        step(Lang.t("Max. Entfernung: ", "Max distance: ") + c.rcMaxDist,
            () -> c.rcMaxDist = Math.max(8, c.rcMaxDist - 8), () -> c.rcMaxDist = Math.min(256, c.rcMaxDist + 8));
        step(Lang.t("Glättung: ", "Smoothing: ") + c.rcSmooth,
            () -> c.rcSmooth = Math.max(1, c.rcSmooth - 1), () -> c.rcSmooth = Math.min(100, c.rcSmooth + 1));
        row(t("LOD", c.rcLod, () -> c.rcLod = !c.rcLod));
        step(Lang.t("LOD-Stärke: ", "LOD strength: ") + c.rcLodStrength,
            () -> c.rcLodStrength = Math.max(0, c.rcLodStrength - 5), () -> c.rcLodStrength = Math.min(100, c.rcLodStrength + 5));
        gap(4);

        sec(Lang.t("Farben", "Colors"));
        row(color(Lang.t("Du", "Self"), c.rcSelf, v -> c.rcSelf = v), color(Lang.t("Du triffst", "Self can reach"), c.rcSelfCan, v -> c.rcSelfCan = v));
        row(color(Lang.t("Gegner", "Other"), c.rcOther, v -> c.rcOther = v), color(Lang.t("Gegner trifft", "Other can reach"), c.rcOtherCan, v -> c.rcOtherCan = v));
    }
}
