package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/** Bauen: jeder Helfer ein Kasten mit Symbol, Schalter und Zahnrad. */
public class BuildMenuScreen extends CardGridScreen {
    public BuildMenuScreen() {
        super("Bauen");
    }

    @Override
    protected String heading() {
        return Lang.t("Bauen", "Build");
    }

    @Override
    protected String subheading() {
        return Lang.t("Kasten anklicken = an/aus, Zahnrad = einstellen", "Click a box = on/off, gear = settings");
    }

    @Override
    protected Screen fresh() {
        return new BuildMenuScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected int accent() {
        return 0xFFFB923C;
    }

    @Override
    protected void cards(List<Card> out, String query) {
        Config c = Config.INSTANCE;
        out.add(new Card(Lang.t("Platzhilfe", "Place helper"), Lang.t("Jeder n-te Block", "Every n-th block"), "cube", () -> c.placeHelper, () -> {
            c.placeHelper = !c.placeHelper;
            c.save();
        }, () -> new BuildScreen(0)));
        out.add(new Card(Lang.t("Kreis & Kugel", "Circle & sphere"), Lang.t("Vorlage zum Nachbauen", "Template to build"), "ring", () -> c.shapeOn, () -> {
            c.shapeOn = !c.shapeOn;
            c.save();
        }, () -> new BuildScreen(1)));
        out.add(new Card(Lang.t("Raster", "Grid"), Lang.t("Linien am Boden", "Lines on the ground"), "grid", () -> c.gridOn, () -> {
            c.gridOn = !c.gridOn;
            c.save();
        }, () -> new BuildScreen(2)));
        out.add(new Card(Lang.t("Lineal", "Ruler"), Lang.t("Abstand messen", "Measure distance"), "ruler", () -> c.rulerOn, () -> {
            c.rulerOn = !c.rulerOn;
            c.save();
        }, () -> new BuildScreen(3)));
        out.add(new Card(Lang.t("Spiegel-Linie", "Mirror line"), Lang.t("Symmetrie-Achse", "Symmetry axis"), "mirror", () -> c.symOn, () -> {
            c.symOn = !c.symOn;
            c.save();
        }, () -> new BuildScreen(4)));
    }
}
