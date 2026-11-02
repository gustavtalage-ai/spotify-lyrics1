package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/** Tools: alles Praktische an einem Ort, in großen Kästen. */
public class ToolsScreen extends CardGridScreen {
    public ToolsScreen() {
        super("Tools");
    }

    @Override
    protected String heading() {
        return "Tools";
    }

    @Override
    protected String subheading() {
        return Lang.t("Kasten anklicken = öffnen oder an/aus, Zahnrad = einstellen", "Click a box = open or on/off, gear = settings");
    }

    @Override
    protected Screen fresh() {
        return new ToolsScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected int accent() {
        return Ui.AMBER;
    }

    @Override
    protected boolean big() {
        return true;
    }

    @Override
    protected boolean bulkButtons() {
        return false;
    }

    @Override
    protected void cards(List<Card> out, String q) {
        Config c = Config.INSTANCE;
        int macros = c.macros.size();
        out.add(new Card(Lang.t("Makros", "Macros"),
            Macros.running >= 0 ? Lang.t("Makro läuft gerade", "Macro running") : macros + Lang.t(" Makros · Bausteine", " macros · blocks"),
            "play", () -> false, null, MacroScreen::new));
        int build = BuildHelper.activeCount();
        out.add(new Card(Lang.t("Bauen", "Build"),
            build > 0 ? build + Lang.t(" Helfer an", " helpers on") : Lang.t("Kreis · Raster · Lineal", "Circle · Grid · Ruler"),
            "cube", () -> false, null, BuildMenuScreen::new));
        out.add(new Card(Lang.t("Auktionen", "Auctions"),
            Auction.running ? Lang.t("Läuft noch ", "Running ") + Auction.timeLeft() : Lang.t("Bieten per /pay", "Bid with /pay"),
            "gavel", () -> false, null, AuctionScreen::new));
        out.add(new Card(Lang.t("Item-Filter", "Item filter"), c.filterRules.size() + Lang.t(" Regeln", " rules"), "eye",
            () -> c.filterOn, () -> {
                c.filterOn = !c.filterOn;
                c.save();
            }, FilterScreen::new));
        out.add(new Card(Lang.t("Lichtshow", "Light show"), Lang.t("Farbe: ", "Color: ") + Lightshow.MODES[Math.floorMod(c.lightMode, Lightshow.MODES.length)], "star",
            () -> c.lightshow, () -> {
                c.lightshow = !c.lightshow;
                c.save();
            }, LightScreen::new));
        out.add(new Card(Lang.t("Auto-Klick", "Auto click"), AutoClick.describe() + Lang.t(" · Taste ", " · key ") + AutoClick.keyName(), "mouse",
            () -> AutoClick.active, () -> AutoClick.setActive(!AutoClick.active), AutoClickScreen::new));
        out.add(new Card(Lang.t("Item-Designer", "Item designer"), c.designs.size() + Lang.t(" Items · Bild & Sound", " items · picture & sound"), "packs",
            () -> false, null, DesignerScreen::new));
    }
}
