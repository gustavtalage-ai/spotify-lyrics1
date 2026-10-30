package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

import java.util.Locale;

/** Einstellungen für die Reichweite: Zahl am Fadenkreuz und Kreis um dich. */
public class ReachScreen extends ItemScreen {
    public ReachScreen() {
        super("Reichweite", "Zahl und Kreis um dich");
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
        Config c = Config.INSTANCE;
        sec("Abstand-Zahl am Fadenkreuz");
        row(T("Abstand zum Gegner anzeigen", PvpHud.on(PvpHud.REACH), () -> {
            PvpHud.flip(PvpHud.REACH);
            again();
        }));
        gap(4);
        sec("Kreis um dich (Reichweite)");
        row(T("Reichweiten-Kreis", PvpHud.on(PvpHud.CIRCLE), () -> {
            PvpHud.flip(PvpHud.CIRCLE);
            again();
        }));
        row(X("Radius: 3.3 Blöcke (normale Reichweite, fest)"));
        row(G("Farbe: " + PvpHud.colorName(c.circleColor) + " ›", () -> {
            c.circleColor = (c.circleColor + 1) % PvpHud.COLOR_NAMES.length;
            c.save();
            again();
        }));
        stepper("Dicke: " + c.circleThick, () -> {
            c.circleThick = Math.max(1, c.circleThick - 1);
            c.save();
            again();
        }, () -> {
            c.circleThick = Math.min(3, c.circleThick + 1);
            c.save();
            again();
        });
        row(T("Spieler im Kreis markieren", c.circleMark, () -> {
            c.circleMark = !c.circleMark;
            c.save();
            again();
        }));
        row(T("Nur mit Waffe in der Hand", c.circleWeapon, () -> {
            c.circleWeapon = !c.circleWeapon;
            c.save();
            again();
        }));
        gap(2);
        text("Der Kreis liegt am Boden und reicht bis zur Mitte des Gegners.", 0xFF8B92A5);
        text("3.3 = Angriffsreichweite 3 plus halbe Gegner-Breite.", 0xFF8B92A5);
    }
}
