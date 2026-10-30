package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für den Auto-Klick (Tools). */
public class AutoClickScreen extends ItemScreen {
    public AutoClickScreen() {
        super("Auto-Klick", "Schlagen oder halten, z. B. zum Farmen");
    }

    @Override
    protected ItemScreen fresh() {
        return new AutoClickScreen();
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
    protected void build() {
        Config c = Config.INSTANCE;
        sec("Starten");
        row(T("Auto-Klick läuft", AutoClick.active, () -> {
            AutoClick.setActive(!AutoClick.active);
            again();
        }));
        row(B("Starten und Menü schließen", () -> {
            AutoClick.setActive(true);
            MinecraftClient.getInstance().setScreen(null);
        }));
        text("Im Spiel: Taste " + AutoClick.keyName() + " = Start / Stopp. Menü offen = Pause.", 0xFF8B92A5);
        gap(4);

        sec("Was soll geklickt werden?");
        row(T("Angriff (links)", c.autoButton == 0, () -> {
            c.autoButton = 0;
            c.save();
            again();
        }), T("Benutzen (rechts)", c.autoButton == 1, () -> {
            c.autoButton = 1;
            c.save();
            again();
        }));
        gap(4);

        sec("Wie?");
        row(T("Gedrückt halten", c.autoMode == 1, () -> {
            c.autoMode = 1;
            c.save();
            again();
        }), T("Im Abstand", c.autoMode == 0, () -> {
            c.autoMode = 0;
            c.save();
            again();
        }));
        int idx = AutoClick.stepIndex(c.autoIntervalMs);
        stepper(c.autoMode == 0 ? "Alle " + AutoClick.intervalText(AutoClick.STEPS[idx]) : "Abstand (nur bei \"Im Abstand\")", () -> {
            c.autoIntervalMs = AutoClick.STEPS[Math.max(0, idx - 1)];
            c.save();
            again();
        }, () -> {
            c.autoIntervalMs = AutoClick.STEPS[Math.min(AutoClick.STEPS.length - 1, idx + 1)];
            c.save();
            again();
        });
        gap(4);
        text("Farmen/Abbauen: Angriff + Gedrückt halten.", 0xFF9AA0AE);
        text("Mit Hit-Assistent: Schläge nur bei voller Leiste.", 0xFF9AA0AE);
        text("Achtung: Auf manchen Servern gilt Auto-Klick als Cheat.", 0xFFFFAA33);
    }
}
