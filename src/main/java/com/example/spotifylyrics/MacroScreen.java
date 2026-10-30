package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/** Makros (Tools): Auto-Klicker und eigene Abläufe aus Tasten, Warten und Befehlen. */
public class MacroScreen extends ItemScreen {
    public MacroScreen() {
        super("Makros", "Auto-Klick und eigene Abläufe");
    }

    @Override
    protected ItemScreen fresh() {
        return new MacroScreen();
    }

    @Override
    protected Screen back() {
        return SoundboardScreen.create(true);
    }

    @Override
    protected String backLabel() {
        return "‹ Tools";
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec("Auto-Klicker");
        row(T("Auto-Klick (Taste " + AutoClick.keyName() + ")", AutoClick.active, () -> {
            AutoClick.setActive(!AutoClick.active);
            again();
        }).w(4), G("Einstellungen ›", () -> MinecraftClient.getInstance().setScreen(new AutoClickScreen())).w(3));
        gap(4);
        sec("Meine Makros");
        List<Config.Macro> ms = c.macros;
        if (ms.isEmpty()) {
            text("Noch kein Makro. Unten ein neues anlegen.", 0xFF8B92A5);
        }
        for (int i = 0; i < ms.size(); i++) {
            final int idx = i;
            Config.Macro m = ms.get(i);
            String label = m.name + " · " + m.steps.size() + (m.steps.size() == 1 ? " Schritt" : " Schritte")
                + (m.key > 0 ? " · " + Macros.keyName(m.key) : "");
            row(B(label, () -> MinecraftClient.getInstance().setScreen(new MacroEditScreen(idx))).w(5),
                Macros.isRunning(i)
                    ? R("Stopp", () -> {
                        Macros.stop();
                        again();
                    }).w(2)
                    : G("Start", () -> {
                        Macros.start(idx);
                        MinecraftClient.getInstance().setScreen(null);
                    }).w(2),
                R("×", () -> {
                    if (Macros.isRunning(idx)) Macros.stop();
                    c.macros.remove(idx);
                    c.save();
                    again();
                }).w(1));
        }
        if (ms.size() < 8) {
            row(G("+ Neues Makro", () -> {
                Config.Macro m = new Config.Macro();
                m.name = "Makro " + (c.macros.size() + 1);
                c.macros.add(m);
                c.save();
                MacroEditScreen.openTab(0);
                MinecraftClient.getInstance().setScreen(new MacroEditScreen(c.macros.size() - 1));
            }));
        }
        gap(2);
        text("Im Spiel: Starttaste des Makros drücken = Start / Stopp.", 0xFF8B92A5);
        text("Chat-Schritte nutzen das normale Chatfenster (bei gesperrtem Chat passiert nichts).", 0xFF8B92A5);
    }
}
