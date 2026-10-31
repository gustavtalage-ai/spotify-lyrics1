package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Bauen: Platzhilfe, Kreis-/Kugel-Vorlage, Raster, Lineal und Symmetrie-Linie. */
public class BuildScreen extends ItemScreen {
    private static int page = 0;
    private static final int HINT = 0xFF8B92A5;

    public BuildScreen() {
        super("Bauen", "Helfer zum Bauen");
    }

    @Override
    protected ItemScreen fresh() {
        return new BuildScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ Menü";
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    private Cell tab(String label, int idx) {
        return idx == page ? S(label) : G(label, () -> {
            page = idx;
            again();
        });
    }

    @Override
    protected void build() {
        row(tab("Platzhilfe", 0), tab("Kreis", 1), tab("Raster", 2), tab("Lineal", 3), tab("Spiegel", 4));
        gap(4);
        if (page == 0) buildPlace();
        else if (page == 1) buildShape();
        else if (page == 2) buildGrid();
        else if (page == 3) buildRuler();
        else buildSym();
    }

    private void buildPlace() {
        Config c = Config.INSTANCE;
        sec("Platzhilfe – jeder n-te Block");
        text(PlaceHelper.describe(), HINT);
        row(T("Platzhilfe", c.placeHelper, () -> {
            c.placeHelper = !c.placeHelper;
            c.save();
            again();
        }), B("Startpunkt setzen", () -> {
            c.placeHelper = true;
            c.save();
            PlaceHelper.pendingStart = true;
            MinecraftClient.getInstance().setScreen(null);
        }));
        stepper("Abstand: jeder " + c.placeSpacing + ". Block", () -> {
            c.placeSpacing = Math.max(1, c.placeSpacing - 1);
            c.save();
            again();
        }, () -> {
            c.placeSpacing = Math.min(32, c.placeSpacing + 1);
            c.save();
            again();
        });
        String dn = c.placeDir < 0 || c.placeDir >= PlaceHelper.DIR_NAMES.length ? "Blickrichtung" : PlaceHelper.DIR_NAMES[c.placeDir];
        row(G("Richtung: " + dn + " ›", () -> {
            c.placeDir = c.placeDir + 1 >= PlaceHelper.DIR_NAMES.length ? -1 : c.placeDir + 1;
            c.save();
            again();
        }), R("Markierung löschen", () -> {
            PlaceHelper.clear();
            again();
        }));
        gap(2);
        text("Zielblock anschauen, dann K → Bauen → Startpunkt setzen.", HINT);
        text("Grün = Start, blau = hier kommt der nächste Block hin.", HINT);
    }

    private void buildShape() {
        Config c = Config.INSTANCE;
        sec("Kreis- und Kugel-Vorlage");
        text(BuildHelper.shapeInfo(), HINT);
        row(T("Vorlage anzeigen", c.shapeOn, () -> {
            c.shapeOn = !c.shapeOn;
            c.save();
            again();
        }), B("Mitte setzen", () -> {
            c.shapeOn = true;
            c.save();
            BuildHelper.pick(BuildHelper.P_SHAPE);
        }));
        row(G("Form: " + BuildHelper.SHAPES[Math.floorMod(c.shapeType, BuildHelper.SHAPES.length)] + " ›", () -> {
            c.shapeType = Math.floorMod(c.shapeType + 1, BuildHelper.SHAPES.length);
            c.save();
            again();
        }));
        int r = BuildHelper.clampR(c.shapeR);
        stepper("Größe: " + (2 * r + 1) + " Blöcke breit", () -> {
            c.shapeR = BuildHelper.clampR(r - 1);
            c.save();
            again();
        }, () -> {
            c.shapeR = BuildHelper.clampR(r + 1);
            c.save();
            again();
        });
        row(G("Mitte runter", () -> {
            BuildHelper.moveCenter(-1);
            again();
        }), G("Mitte hoch", () -> {
            BuildHelper.moveCenter(1);
            again();
        }), R("Löschen", () -> {
            BuildHelper.clearShape();
            again();
        }));
        gap(2);
        text("Mitte = vor dem Block, den du anschaust (bis 64 Blöcke weit).", HINT);
        text("Orange = hier fehlt noch ein Block. Gesetzte verschwinden.", HINT);
    }

    private void buildGrid() {
        Config c = Config.INSTANCE;
        sec("Raster am Boden");
        row(T("Raster anzeigen", c.gridOn, () -> {
            c.gridOn = !c.gridOn;
            c.save();
            again();
        }));
        stepper("Linien alle " + c.gridStep + (c.gridStep == 1 ? " Block" : " Blöcke"), () -> {
            c.gridStep = Math.max(1, c.gridStep - 1);
            c.save();
            again();
        }, () -> {
            c.gridStep = Math.min(16, c.gridStep + 1);
            c.save();
            again();
        });
        stepper("Größe: " + c.gridRange + " Blöcke um dich", () -> {
            c.gridRange = Math.max(4, c.gridRange - 4);
            c.save();
            again();
        }, () -> {
            c.gridRange = Math.min(32, c.gridRange + 4);
            c.save();
            again();
        });
        row(T("Chunk-Grenzen", c.gridChunks, () -> {
            c.gridChunks = !c.gridChunks;
            c.save();
            again();
        }), T("Höhe festhalten", BuildHelper.gridLock, () -> {
            BuildHelper.gridLock = !BuildHelper.gridLock;
            again();
        }));
        gap(2);
        text("Das Raster liegt da, wo du stehst.", HINT);
        text("Höhe festhalten: bleibt unten, auch wenn du fliegst.", HINT);
        text("Orange Linien = Chunk-Grenzen (alle 16 Blöcke).", HINT);
    }

    private void buildRuler() {
        Config c = Config.INSTANCE;
        sec("Lineal – Abstand zwischen zwei Blöcken");
        text(BuildHelper.measure(), Ui.TEXT);
        String air = BuildHelper.airLine();
        if (!air.isEmpty()) text(air, HINT);
        row(T("Lineal anzeigen", c.rulerOn, () -> {
            c.rulerOn = !c.rulerOn;
            c.save();
            again();
        }));
        row(B("Punkt A setzen", () -> {
            c.rulerOn = true;
            c.save();
            BuildHelper.pick(BuildHelper.P_A);
        }), B("Punkt B setzen", () -> {
            c.rulerOn = true;
            c.save();
            BuildHelper.pick(BuildHelper.P_B);
        }));
        row(R("Punkte löschen", () -> {
            BuildHelper.clearRuler();
            again();
        }));
        gap(2);
        text("Block anschauen, dann K → Bauen → Punkt setzen.", HINT);
        text("Grün = A, pink = B, gelber Kasten = ganzer Bereich.", HINT);
    }

    private void buildSym() {
        Config c = Config.INSTANCE;
        sec("Symmetrie-Linie (Spiegelachse)");
        text(BuildHelper.symInfo(), HINT);
        row(T("Linie anzeigen", c.symOn, () -> {
            c.symOn = !c.symOn;
            c.save();
            again();
        }), B("Achse setzen", () -> {
            c.symOn = true;
            c.save();
            BuildHelper.pick(BuildHelper.P_SYM);
        }));
        row(G("Richtung: " + BuildHelper.AXES[Math.floorMod(c.symAxis, BuildHelper.AXES.length)] + " ›", () -> {
            c.symAxis = Math.floorMod(c.symAxis + 1, BuildHelper.AXES.length);
            c.save();
            again();
        }), G("Mitte: " + (c.symEdge ? "Kante" : "Block") + " ›", () -> {
            c.symEdge = !c.symEdge;
            c.save();
            again();
        }));
        row(R("Achse löschen", () -> {
            BuildHelper.clearSym();
            again();
        }));
        gap(2);
        text("Block = Mitte genau auf dem Block (ungerade Breite).", HINT);
        text("Kante = Mitte zwischen zwei Blöcken (gerade Breite).", HINT);
    }
}
