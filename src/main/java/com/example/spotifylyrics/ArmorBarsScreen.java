package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für die Haltbarkeits-Balken auf der Rüstung von Gegnern. */
public class ArmorBarsScreen extends ItemScreen {
    public ArmorBarsScreen() {
        super(Lang.t("Gegner-Rüstung", "Enemy armor"), "Armor Bars");
    }

    @Override
    protected ItemScreen fresh() {
        return new ArmorBarsScreen();
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

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(t(Lang.t("Balken an", "Enable bars"), c.armorBars, () -> c.armorBars = !c.armorBars));
        row(t(Lang.t("Nur mit Waffe in der Hand", "Only while holding a weapon"), c.abWeapon, () -> c.abWeapon = !c.abWeapon));
        gap(4);
        sec(Lang.t("Position", "Position"));
        row(t(Lang.t("Neben der Rüstung", "Next to the armor"), c.abStyle == 0, () -> c.abStyle = 0),
            t(Lang.t("Über dem Kopf", "Above the head"), c.abStyle == 1, () -> c.abStyle = 1));
        gap(4);
        sec(Lang.t("Reichweite", "Range"));
        stepper(Lang.t("Max. Entfernung: ", "Max distance: ") + c.abDist, () -> {
            c.abDist = Math.max(4, c.abDist - 4);
            c.save();
            again();
        }, () -> {
            c.abDist = Math.min(128, c.abDist + 4);
            c.save();
            again();
        });
        gap(4);
        text(Lang.t("Grün = Rüstung fast neu, Gelb = halb, Rot = gleich kaputt.", "Green = almost new, yellow = half, red = about to break."), 0xFF9AA3B2);
    }
}
