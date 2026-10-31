package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

import java.util.Locale;

/** Einstellungen für die Reichweite: Zahl am Fadenkreuz und Kreis um dich. */
public class ReachScreen extends ItemScreen {
    public ReachScreen() {
        super(Lang.t("Abstand-Zahl", "Distance"), Lang.t("Meter zum Gegner am Fadenkreuz", "Meters to the enemy at the crosshair"));
    }

    @Override
    protected ItemScreen fresh() {
        return new ReachScreen();
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
    protected void build() {
        sec("Abstand-Zahl am Fadenkreuz");
        row(T("Abstand zum Gegner anzeigen", PvpHud.on(PvpHud.REACH), () -> {
            PvpHud.flip(PvpHud.REACH);
            again();
        }));
    }
}
