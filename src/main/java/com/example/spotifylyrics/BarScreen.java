package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für den Angriffs-Balken. */
public class BarScreen extends ItemScreen {
    public BarScreen() {
        super("Angriffs-Balken", "Dünne Linie unter dem Fadenkreuz");
    }

    @Override
    protected ItemScreen fresh() {
        return new BarScreen();
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
        sec("Balken");
        row(T("Angriffs-Balken", PvpHud.on(PvpHud.BAR), () -> {
            PvpHud.flip(PvpHud.BAR);
            again();
        }));
        stepper("Breite: " + c.barW, () -> {
            c.barW = Math.max(10, c.barW - 2);
            c.save();
            again();
        }, () -> {
            c.barW = Math.min(80, c.barW + 2);
            c.save();
            again();
        });
        stepper("Dicke: " + c.barH, () -> {
            c.barH = Math.max(1, c.barH - 1);
            c.save();
            again();
        }, () -> {
            c.barH = Math.min(4, c.barH + 1);
            c.save();
            again();
        });
        row(T("Nur beim Laden zeigen", c.barOnly, () -> {
            c.barOnly = !c.barOnly;
            c.save();
            again();
        }));
        gap(2);
        text("Grün = bereit. Position: Anzeigen verschieben.", 0xFF8B92A5);
    }
}
