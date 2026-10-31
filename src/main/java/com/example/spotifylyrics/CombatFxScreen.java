package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für die Kampf-Effekte. */
public class CombatFxScreen extends ItemScreen {
    public CombatFxScreen() {
        super(Lang.t("Kampf-Effekte", "Combat effects"), Lang.t("Extra Partikel im Kampf", "Extra particles in combat"));
    }

    @Override
    protected ItemScreen fresh() {
        return new CombatFxScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    private Cell t(String de, String en, boolean on, Runnable flip) {
        return T(Lang.t(de, en), on, () -> {
            flip.run();
            Config.INSTANCE.save();
            again();
        });
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec(Lang.t("Kampf-Effekte", "Combat effects"));
        row(t("Effekte an", "Effects on", c.fxOn, () -> c.fxOn = !c.fxOn));
        gap(2);
        sec(Lang.t("Welche", "Which"));
        row(t("Streitkolben-Schockwelle", "Mace shockwave", c.fxMace, () -> c.fxMace = !c.fxMace));
        row(t("Tod (Seelen + Rauch)", "Death (souls + smoke)", c.fxDeath, () -> c.fxDeath = !c.fxDeath));
        row(t("Totem-Ring", "Totem ring", c.fxTotem, () -> c.fxTotem = !c.fxTotem));
        row(t("Enderperlen-Spur", "Ender pearl trail", c.fxPearl, () -> c.fxPearl = !c.fxPearl));
        row(t("Windkugel-Spur", "Wind charge trail", c.fxWind, () -> c.fxWind = !c.fxWind));
        gap(2);
        text(Lang.t("Nur du siehst die Effekte.", "Only you can see the effects."), 0xFF8B92A5);
    }
}
