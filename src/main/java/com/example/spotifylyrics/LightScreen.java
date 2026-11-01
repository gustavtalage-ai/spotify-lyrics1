package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Einstellungen der Lichtshow. */
public class LightScreen extends ItemScreen {
    public LightScreen() {
        super(Lang.t("Lichtshow", "Light show"), Lang.t("Licht passend zur Musik", "Lights to the music"));
    }

    @Override
    protected ItemScreen fresh() {
        return new LightScreen();
    }

    @Override
    protected Screen back() {
        return new ToolsScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ Tools";
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(T(Lang.t("Lichtshow an", "Light show on"), c.lightshow, () -> {
            c.lightshow = !c.lightshow;
            c.save();
            again();
        }));
        gap(4);
        sec(Lang.t("Farbe", "Color"));
        int n = Lightshow.MODES.length;
        Cell[] cells = new Cell[n];
        for (int i = 0; i < n; i++) {
            final int k = i;
            int sel = Math.floorMod(c.lightMode, n);
            cells[i] = k == sel ? S(Lightshow.MODES[i]) : G(Lightshow.MODES[i], () -> {
                c.lightMode = k;
                c.save();
                again();
            });
        }
        for (int i = 0; i < n; i += 3) {
            Cell[] part = new Cell[Math.min(3, n - i)];
            System.arraycopy(cells, i, part, 0, part.length);
            row(part);
        }
        gap(4);
        sec(Lang.t("Testen", "Test"));
        row(B(Lang.t("20 Sekunden testen", "Test for 20 seconds"), () -> {
            Lightshow.testUntilMs = System.currentTimeMillis() + 20000;
            MinecraftClient.getInstance().setScreen(null);
        }));
        text(Lightshow.status(), 0xFF8B92A5);
    }
}
