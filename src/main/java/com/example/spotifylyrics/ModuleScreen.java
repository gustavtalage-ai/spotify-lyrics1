package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für ein einzelnes PvP-Modul (für Module ohne eigenes Menü). */
public class ModuleScreen extends ItemScreen {
    private final int bit;
    private final String desc;

    public ModuleScreen(int bit, String desc) {
        super(PvpHud.NAMES[bit], desc);
        this.bit = bit;
        this.desc = desc;
    }

    @Override
    protected ItemScreen fresh() {
        return new ModuleScreen(bit, desc);
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    private boolean isHud() {
        for (int id : PvpHud.HUD_IDS) if (id == bit) return true;
        return false;
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec(PvpHud.NAMES[bit]);
        row(T(PvpHud.NAMES[bit] + " einschalten", PvpHud.on(bit), () -> {
            PvpHud.flip(bit);
            again();
        }));
        text(desc, 0xFF8B92A5);
        if (isHud()) {
            gap(4);
            sec("Position auf dem Bildschirm");
            row(B("Anzeige verschieben ›", () -> MinecraftClient.getInstance().setScreen(new HudEditScreen(new ModuleScreen(bit, desc)))),
                R("Zurücksetzen", () -> {
                    c.hudPos[2 * bit] = -1;
                    c.hudPos[2 * bit + 1] = -1;
                    c.save();
                    again();
                }));
            text("Zurücksetzen = wieder an den Standard-Platz.", 0xFF8B92A5);
        }
        if (bit == PvpHud.COMBAT || bit == PvpHud.STATS) {
            gap(4);
            row(G("Kampf-Timer & Statistik ›", () -> MinecraftClient.getInstance().setScreen(new CombatScreen())));
        }
        if (bit == PvpHud.BAR) {
            gap(4);
            row(G("Balken-Einstellungen ›", () -> MinecraftClient.getInstance().setScreen(new BarScreen())));
        }
    }
}
