package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für die Reichweiten-Kreise um andere Spieler. */
public class ReachCirclesScreen extends ItemScreen {
    public ReachCirclesScreen() {
        super(Lang.t("Reichweiten-Kreise", "Reach circles"), Lang.t("Kreis um jeden Gegner", "Circle around every enemy"));
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

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec(Lang.t("Reichweiten-Kreise", "Reach circles"));
        row(T(Lang.t("Kreise um Gegner", "Circles around enemies"), c.rcOn, () -> {
            c.rcOn = !c.rcOn;
            c.save();
            again();
        }));
        row(G(Lang.t("Stil: ", "Style: ") + ReachCircles.STYLES[Math.floorMod(c.rcStyle, ReachCircles.STYLES.length)] + " ›", () -> {
            c.rcStyle = (c.rcStyle + 1) % ReachCircles.STYLES.length;
            c.save();
            again();
        }));
        stepper(Lang.t("Bis ", "Up to ") + c.rcRange + Lang.t(" Blöcke weit", " blocks away"), () -> {
            c.rcRange = Math.max(8, c.rcRange - 4);
            c.save();
            again();
        }, () -> {
            c.rcRange = Math.min(64, c.rcRange + 4);
            c.save();
            again();
        });
        row(T(Lang.t("Auch bei Freunden", "Also for friends"), c.rcFriends, () -> {
            c.rcFriends = !c.rcFriends;
            c.save();
            again();
        }));
        gap(2);
        text(Lang.t("Grün = ihr könnt euch gerade treffen.", "Green = you can hit each other right now."), 0xFF8B92A5);
        text(Lang.t("Weiß = noch zu weit weg.", "White = still too far away."), 0xFF8B92A5);
    }
}
