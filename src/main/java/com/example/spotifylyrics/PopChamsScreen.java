package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für Pop-Chams. */
public class PopChamsScreen extends ItemScreen {
    public PopChamsScreen() {
        super("Pop-Chams", Lang.t("Umriss, wenn ein Totem poppt", "Outline when a totem pops"));
    }

    @Override
    protected ItemScreen fresh() {
        return new PopChamsScreen();
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
        sec("Pop-Chams");
        row(c.popChams ? B(Lang.t("An", "On"), () -> {
            c.popChams = false;
            c.save();
            again();
        }) : G(Lang.t("Aus", "Off"), () -> {
            c.popChams = true;
            c.save();
            again();
        }));
        text(Lang.t("Gesehene Pops: ", "Pops seen: ") + PopChams.seen, 0xFF8B92A5);
        gap(2);
        sec(Lang.t("Farbe", "Color"));
        Cell[] cells = new Cell[PopChams.COLORS.length];
        for (int i = 0; i < cells.length; i++) {
            final int k = i;
            int col = PopChams.COLORS[i];
            cells[i] = C(c.popColor == i ? "✔" : "", col, col | 0x202020, 0xFF101010, () -> {
                c.popColor = k;
                c.save();
                again();
            });
        }
        row(cells);
        gap(2);
        sec(Lang.t("Dauer", "Duration"));
        stepper(Lang.t("Sekunden: ", "Seconds: ") + c.popTime, () -> {
            c.popTime = Math.max(1, c.popTime - 1);
            c.save();
        }, () -> {
            c.popTime = Math.min(10, c.popTime + 1);
            c.save();
        });
        row(c.popRise ? B(Lang.t("Schwebt nach oben", "Floats up"), () -> {
            c.popRise = false;
            c.save();
            again();
        }) : G(Lang.t("Bleibt stehen", "Stays in place"), () -> {
            c.popRise = true;
            c.save();
            again();
        }), c.popSelf ? B(Lang.t("Auch bei mir", "Also for me"), () -> {
            c.popSelf = false;
            c.save();
            again();
        }) : G(Lang.t("Nur Gegner", "Only enemies"), () -> {
            c.popSelf = true;
            c.save();
            again();
        }));
    }
}
