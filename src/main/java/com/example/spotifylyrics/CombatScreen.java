package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Kampf-Timer und Statistik. */
public class CombatScreen extends ItemScreen {
    public CombatScreen() {
        super("Kampf-Timer & Statistik", "Zeit seit dem Kampf, Treffer und Kills");
    }

    @Override
    protected ItemScreen fresh() {
        return new CombatScreen();
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
        Config c = Config.INSTANCE;
        sec("Kampf-Timer");
        row(T("Kampf-Timer anzeigen", PvpHud.on(PvpHud.COMBAT), () -> {
            PvpHud.flip(PvpHud.COMBAT);
            again();
        }));
        stepper("Läuft " + c.combatSecs + " s nach dem letzten Treffer", () -> {
            c.combatSecs = Math.max(5, c.combatSecs - 5);
            c.save();
            again();
        }, () -> {
            c.combatSecs = Math.min(60, c.combatSecs + 5);
            c.save();
            again();
        });
        gap(2);
        sec("Statistik");
        row(T("Statistik anzeigen", PvpHud.on(PvpHud.STATS), () -> {
            PvpHud.flip(PvpHud.STATS);
            again();
        }));
        row(R("Statistik zurücksetzen", () -> {
            PvpHud.resetStats();
            again();
        }));
        gap(2);
        row(G("Anzeigen verschieben ›", () -> MinecraftClient.getInstance().setScreen(new HudEditScreen(new CombatScreen()))));
    }
}
