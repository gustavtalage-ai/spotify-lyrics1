package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für den Hit-Assistenten. */
public class HitAssistScreen extends ItemScreen {
    public HitAssistScreen() {
        super("Hit-Assistent", "Nur schlagen, wenn der Schlag voll ist");
    }

    @Override
    protected ItemScreen fresh() {
        return new HitAssistScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    /** Waffen-Knopf: blau = an, grau = aus. */
    private Cell weapon(int i) {
        Config c = Config.INSTANCE;
        boolean on = ((c.hitWeapons >> i) & 1) != 0;
        Runnable r = () -> {
            c.hitWeapons ^= 1 << i;
            c.save();
            again();
        };
        return on ? B(HitAssist.NAMES[i], r) : G(HitAssist.NAMES[i], r);
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec("Hit-Assistent");
        row(T("Hit-Assistent", c.hitAssist, () -> {
            c.hitAssist = !c.hitAssist;
            c.save();
            again();
        }));
        row(T("Klick merken (perfekter Schlag)", c.hitQueue, () -> {
            c.hitQueue = !c.hitQueue;
            c.save();
            again();
        }));
        text("Zu früh geklickt: Schlag kommt bei voller Leiste.", 0xFF8B92A5);
        gap(2);
        sec("Gilt für diese Waffen (blau = an)");
        row(weapon(0), weapon(1), weapon(2));
        row(weapon(3), weapon(4));
        stepper("Aufladung ab: " + c.hitAssistMin + " %", () -> {
            c.hitAssistMin = Math.max(50, c.hitAssistMin - 5);
            c.save();
            again();
        }, () -> {
            c.hitAssistMin = Math.min(100, c.hitAssistMin + 5);
            c.save();
            again();
        });
    }
}
