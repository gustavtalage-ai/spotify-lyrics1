package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** PvP-Loadout: jedes Modul ein Kasten mit Symbol, Schalter und Zahnrad. Mit Kategorien und Suche. */
public class PvpScreen extends CardGridScreen {
    private static final String[] CAT_ICONS = {"sword", "chart", "cross", "packs"};
    private static int cat = 0;

    public PvpScreen() {
        super("PvP");
    }

    @Override
    protected String heading() {
        return "PvP";
    }

    @Override
    protected String subheading() {
        return Lang.t("Kasten anklicken = an/aus, Zahnrad = einstellen", "Click a box = on/off, gear = settings");
    }

    @Override
    protected Screen fresh() {
        return new PvpScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String[] categories() {
        return new String[] {Lang.t("Kampf", "Combat"), "HUD", Lang.t("Visuell", "Visual"), "Packs"};
    }

    @Override
    protected String[] categoryIcons() {
        return CAT_ICONS;
    }

    @Override
    protected int category() {
        return cat;
    }

    @Override
    protected void setCategory(int c) {
        cat = c;
    }

    @Override
    protected int categoryAccent(int c) {
        int[] col = {Ui.RED, Ui.ACCENT, 0xFFA78BFA, 0xFF34D399};
        return col[Math.floorMod(c, col.length)];
    }

    @Override
    protected boolean searchable() {
        return true;
    }

    @Override
    protected void footer(List<Card> out) {
        if (cat == 1 && query().isEmpty()) out.add(new Card(Lang.t("Anzeigen verschieben ›", "Move HUD ›"), "", "chart", () -> false, null, () -> new HudEditScreen(new PvpScreen())));
    }

    private static Card bit(String name, String desc, String icon, int b, Supplier<Screen> settings) {
        Supplier<Screen> s = settings != null ? settings : () -> new ModuleScreen(b, desc);
        return new Card(name, desc, icon, () -> PvpHud.on(b), () -> PvpHud.flip(b), s);
    }

    private static void kampf(List<Card> out) {
        Config c = Config.INSTANCE;
        out.add(new Card("Hit-Assistent", Lang.t("Nur volle Schläge", "Only full hits"), "sword", () -> c.hitAssist, () -> {
            c.hitAssist = !c.hitAssist;
            c.save();
        }, HitAssistScreen::new));
        out.add(bit(Lang.t("Treffer-Sound", "Hit sound"), Lang.t("Ton bei jedem Treffer", "Sound on every hit"), "note", PvpHud.HITSOUND, null));
        out.add(bit("Auto-Sprint", Lang.t("Immer sprinten", "Always sprint"), "run", PvpHud.SPRINT, null));
        out.add(bit(Lang.t("Rüstung-Warnung", "Armor warning"), Lang.t("Wenn Rüstung bricht", "When armor breaks"), "armor", PvpHud.ARMORWARN, null));
        out.add(bit(Lang.t("Totem-Hinweis", "Totem alert"), Lang.t("Wenn ein Totem poppt", "When a totem pops"), "totem", PvpHud.TOTEM, null));
        out.add(bit(Lang.t("Kampf-Timer", "Combat timer"), Lang.t("Zeit seit dem Kampf", "Time since combat"), "clock", PvpHud.COMBAT, CombatScreen::new));
        out.add(bit(Lang.t("Statistik", "Stats"), Lang.t("Treffer und Kills", "Hits and kills"), "chart", PvpHud.STATS, CombatScreen::new));
    }

    private static void hud(List<Card> out) {
        Config c = Config.INSTANCE;
        out.add(new Card(Lang.t("Zweiter Chat", "Second chat"), Lang.t("Regeln → eigenes Fenster", "Rules → own window"), "people", () -> c.chat2On, () -> {
            c.chat2On = !c.chat2On;
            c.save();
        }, Chat2Screen::new));
        out.add(bit("Info", Lang.t("FPS, XYZ, Richtung", "FPS, XYZ, direction"), "info", PvpHud.INFO, null));
        out.add(bit("CPS", Lang.t("Klicks pro Sekunde", "Clicks per second"), "mouse", PvpHud.CPS, null));
        out.add(bit("Combo", Lang.t("Treffer in Folge", "Hits in a row"), "fire", PvpHud.COMBO, null));
        out.add(bit(Lang.t("Effekte", "Effects"), Lang.t("Tränke mit Zeit", "Potions with time"), "potion", PvpHud.EFFECTS, null));
        out.add(bit(Lang.t("Rüstung", "Armor"), Lang.t("Haltbarkeit", "Durability"), "armor", PvpHud.ARMOR, null));
        out.add(bit(Lang.t("Vorrat", "Supplies"), Lang.t("Äpfel, Perlen, Totems", "Apples, pearls, totems"), "apple", PvpHud.STOCK, null));
        out.add(bit(Lang.t("Ziel-HUD", "Target HUD"), Lang.t("Leben des Gegners", "Enemy health"), "eye", PvpHud.TARGET, null));
        out.add(bit(Lang.t("Spieler-Liste", "Player list"), Lang.t("Wer ist in der Nähe", "Who is nearby"), "people", PvpHud.NEAR, null));
        out.add(bit(Lang.t("Crit-Anzeige", "Crit display"), Lang.t("Kritische Treffer", "Critical hits"), "star", PvpHud.CRIT, null));
        out.add(bit("Cooldowns", Lang.t("Perle, Schild, Apfel", "Pearl, shield, apple"), "hourglass", PvpHud.COOLDOWN, null));
        out.add(bit(Lang.t("Todespunkt", "Death point"), Lang.t("Wo du gestorben bist", "Where you died"), "skull", PvpHud.DEATH, null));
    }

    private static void visuell(List<Card> out) {
        Config c = Config.INSTANCE;
        out.add(new Card("Pop-Chams", Lang.t("Umriss beim Totem-Pop", "Outline on totem pop"), "totem", () -> c.popChams, () -> {
            c.popChams = !c.popChams;
            c.save();
        }, PopChamsScreen::new));
        out.add(new Card(Lang.t("Reichweiten-Kreise", "Reach circles"), Lang.t("Kreis um jeden Gegner", "Circle around enemies"), "ring", () -> c.rcOn, () -> {
            c.rcOn = !c.rcOn;
            c.save();
        }, ReachCirclesScreen::new));
        out.add(new Card(Lang.t("Kampf-Effekte", "Combat effects"), Lang.t("Schockwelle, Spuren, Tod", "Shockwave, trails, death"), "bang", () -> c.fxOn, () -> {
            c.fxOn = !c.fxOn;
            c.save();
        }, CombatFxScreen::new));
        out.add(new Card(Lang.t("Anti-Koordinaten", "Anti coords"), Lang.t("Keine Textur-Drehung", "No texture rotation"), "grid", () -> c.antiRotation,
            () -> AntiCoordsScreen.set(!c.antiRotation), AntiCoordsScreen::new));
        out.add(bit(Lang.t("Fadenkreuz", "Crosshair"), Lang.t("Selbst gepixelt", "Pixel it yourself"), "cross", PvpHud.CROSS, CrosshairScreen::new));
        out.add(bit(Lang.t("Abstand-Zahl", "Distance"), Lang.t("Meter zum Gegner", "Meters to enemy"), "ruler", PvpHud.REACH, ReachScreen::new));
        out.add(bit(Lang.t("Angriffs-Balken", "Attack bar"), Lang.t("Lädt am Fadenkreuz", "Charges at crosshair"), "bar", PvpHud.BAR, BarScreen::new));
        out.add(bit(Lang.t("Schadenszahlen", "Damage numbers"), Lang.t("Zahlen beim Treffer", "Numbers on hit"), "bang", PvpHud.DMGNUM, null));
        out.add(bit(Lang.t("Leben-Warnung", "Low HP warning"), Lang.t("Roter Rand", "Red border"), "heart", PvpHud.LOWHP, null));
    }

    private static void packs(List<Card> out) {
        List<String> seen = new ArrayList<>();
        for (String[] q : Packs.QUICK) {
            ResourcePackProfile f = Packs.find(Arrays.copyOfRange(q, 1, q.length));
            if (f == null || seen.contains(q[0])) continue;
            seen.add(q[0]);
            out.add(new Card(q[0], Lang.t("Schnell-Pack", "Quick pack"), "packs", () -> Packs.isOn(f), () -> Packs.set(f, !Packs.isOn(f)), PackSetsScreen::new));
        }
        out.add(new Card(Lang.t("Alle Packs", "All packs"), Lang.t("Pack-Sets und mehr ›", "Pack sets and more ›"), "packs", () -> false, null, PackSetsScreen::new));
    }

    @Override
    protected void cards(List<Card> out, String query) {
        if (query.isEmpty()) {
            if (cat == 0) kampf(out);
            else if (cat == 1) hud(out);
            else if (cat == 2) visuell(out);
            else packs(out);
            return;
        }
        List<Card> all = new ArrayList<>();
        kampf(all);
        hud(all);
        visuell(all);
        packs(all);
        List<String> names = new ArrayList<>();
        for (Card c : all) {
            if (c.matches(query) && !names.contains(c.name)) {
                names.add(c.name);
                out.add(c);
            }
        }
    }
}
