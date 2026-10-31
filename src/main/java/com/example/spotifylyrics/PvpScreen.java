package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** PvP-Loadout: jedes Modul ist ein eigener Kasten mit Schalter und (wo es geht) eigenen Einstellungen. */
public class PvpScreen extends Screen {
    private static final int HEAD = 34, SIDE_W = 52, CARD_H = 46, GAP = 6, FOOT = 26;
    private static final String[] CATS = {"Kampf", "HUD", "Visuell", "Packs"};
    private static final int[] CAT_COL = {Ui.RED, Ui.ACCENT, 0xFFA78BFA, 0xFF34D399};
    private static int cat = 0;
    private static int scroll = 0;

    /** Ein Modul-Kasten. */
    private static final class Mod {
        final String name, desc;
        final ItemStack icon;
        final BooleanSupplier on;
        final Runnable flip;
        final Supplier<Screen> settings;

        Mod(String name, String desc, Item icon, BooleanSupplier on, Runnable flip, Supplier<Screen> settings) {
            this.name = name;
            this.desc = desc;
            this.icon = new ItemStack(icon);
            this.on = on;
            this.flip = flip;
            this.settings = settings;
        }
    }

    private final List<Mod> mods = new ArrayList<>();
    private int px0, px1, py0, py1, gx0, gy0, cardW, vis;

    public PvpScreen() {
        super(Text.literal("PvP"));
    }

    private static Mod bit(String name, String desc, Item icon, int b, Supplier<Screen> settings) {
        return new Mod(name, desc, icon, () -> PvpHud.on(b), () -> PvpHud.flip(b), settings);
    }

    private void buildMods() {
        mods.clear();
        Config c = Config.INSTANCE;
        Supplier<Screen> move = () -> new HudEditScreen(new PvpScreen());
        if (cat == 0) {
            mods.add(new Mod("Hit-Assistent", "Nur volle Schläge", Items.DIAMOND_SWORD, () -> c.hitAssist, () -> {
                c.hitAssist = !c.hitAssist;
                c.save();
            }, HitAssistScreen::new));
            mods.add(bit("Treffer-Sound", "Ton bei jedem Treffer", Items.NOTE_BLOCK, PvpHud.HITSOUND, null));
            mods.add(bit("Auto-Sprint", "Immer sprinten", Items.FEATHER, PvpHud.SPRINT, null));
            mods.add(bit("Rüstung-Warnung", "Wenn Rüstung bricht", Items.IRON_CHESTPLATE, PvpHud.ARMORWARN, null));
            mods.add(bit("Totem-Hinweis", "Wenn ein Totem poppt", Items.TOTEM_OF_UNDYING, PvpHud.TOTEM, move));
            mods.add(bit("Kampf-Timer", "Zeit seit dem Kampf", Items.CLOCK, PvpHud.COMBAT, CombatScreen::new));
            mods.add(bit("Statistik", "Treffer und Kills", Items.WRITABLE_BOOK, PvpHud.STATS, CombatScreen::new));
        } else if (cat == 1) {
            mods.add(bit("Info", "FPS, XYZ, Richtung", Items.COMPASS, PvpHud.INFO, move));
            mods.add(bit("CPS", "Klicks pro Sekunde", Items.STONE_BUTTON, PvpHud.CPS, move));
            mods.add(bit("Combo", "Treffer in Folge", Items.BLAZE_POWDER, PvpHud.COMBO, move));
            mods.add(bit("Effekte", "Tränke mit Zeit", Items.POTION, PvpHud.EFFECTS, move));
            mods.add(bit("Rüstung", "Haltbarkeit", Items.DIAMOND_CHESTPLATE, PvpHud.ARMOR, move));
            mods.add(bit("Vorrat", "Äpfel, Perlen, Totems", Items.GOLDEN_APPLE, PvpHud.STOCK, move));
            mods.add(bit("Ziel-HUD", "Leben des Gegners", Items.ZOMBIE_HEAD, PvpHud.TARGET, move));
            mods.add(bit("Spieler-Liste", "Wer ist in der Nähe", Items.NAME_TAG, PvpHud.NEAR, move));
            mods.add(bit("Crit-Anzeige", "Zeigt kritische Treffer", Items.GLOWSTONE_DUST, PvpHud.CRIT, move));
            mods.add(bit("Cooldowns", "Perle, Schild, Apfel", Items.ENDER_PEARL, PvpHud.COOLDOWN, move));
            mods.add(bit("Todespunkt", "Wo du gestorben bist", Items.SKELETON_SKULL, PvpHud.DEATH, move));
        } else if (cat == 2) {
            mods.add(bit("Fadenkreuz", "Selbst gepixelt", Items.SPYGLASS, PvpHud.CROSS, CrosshairScreen::new));
            mods.add(bit("Reichweite", "Kreis am Boden", Items.TARGET, PvpHud.CIRCLE, ReachScreen::new));
            mods.add(bit("Abstand-Zahl", "Meter zum Gegner", Items.LEAD, PvpHud.REACH, ReachScreen::new));
            mods.add(bit("Angriffs-Balken", "Lädt am Fadenkreuz", Items.EXPERIENCE_BOTTLE, PvpHud.BAR, BarScreen::new));
            mods.add(bit("Schadenszahlen", "Zahlen beim Treffer", Items.REDSTONE, PvpHud.DMGNUM, null));
            mods.add(bit("Leben-Warnung", "Roter Rand", Items.REDSTONE_BLOCK, PvpHud.LOWHP, null));
        } else {
            for (String[] q : Packs.QUICK) {
                ResourcePackProfile f = Packs.find(Arrays.copyOfRange(q, 1, q.length));
                if (f == null) continue;
                boolean dup = false;
                for (Mod m : mods) if (m.name.equals(q[0])) dup = true;
                if (dup) continue;
                mods.add(new Mod(q[0], "Schnell-Pack", Items.GRASS_BLOCK, () -> Packs.isOn(f), () -> Packs.set(f, !Packs.isOn(f)), null));
            }
            mods.add(new Mod("Alle Packs", "Pack-Sets und mehr ›", Items.BOOKSHELF, () -> false, null, PackSetsScreen::new));
        }
    }

    private static void again() {
        MinecraftClient.getInstance().setScreen(new PvpScreen());
    }

    private int rows() {
        return (mods.size() + 2) / 3;
    }

    private int[] card(int i) {
        int r = i / 3 - scroll, col = i % 3;
        if (r < 0 || r >= vis) return null;
        return new int[] {gx0 + col * (cardW + GAP), gy0 + r * (CARD_H + GAP), cardW, CARD_H};
    }

    private int[] sideBtn(int i) {
        return new int[] {px0 + 8, gy0 + i * 40, SIDE_W - 6, 36};
    }

    @Override
    protected void init() {
        buildMods();
        int panelW = Math.min(460, this.width - 16);
        vis = Math.max(2, Math.min(4, (this.height - 12 - HEAD - 10 - FOOT) / (CARD_H + GAP)));
        int panelH = HEAD + 8 + vis * (CARD_H + GAP) + FOOT;
        px0 = (this.width - panelW) / 2;
        px1 = px0 + panelW;
        py0 = Math.max(4, (this.height - panelH) / 2);
        py1 = py0 + panelH;
        gx0 = px0 + 8 + SIDE_W + 4;
        gy0 = py0 + HEAD + 8;
        cardW = (px1 - 8 - gx0 - 2 * GAP) / 3;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows() - vis)));

        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(new HubScreen()))
            .dimensions(px1 - 8 - 64, py0 + 8, 64, 18).build());
        for (int i = 0; i < CATS.length; i++) {
            final int k = i;
            int[] r = sideBtn(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                cat = k;
                scroll = 0;
                again();
            }).dimensions(r[0], r[1], r[2], r[3]).build());
        }
        for (int i = 0; i < mods.size(); i++) {
            int[] r = card(i);
            if (r == null) continue;
            Mod m = mods.get(i);
            // zuerst "Einstellen", damit der Klick dort nicht den Schalter trifft
            if (m.settings != null && m.flip != null) {
                addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(m.settings.get()))
                    .dimensions(r[0] + 4, r[1] + r[3] - 15, 62, 12).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                if (m.flip != null) {
                    m.flip.run();
                    again();
                } else if (m.settings != null) {
                    MinecraftClient.getInstance().setScreen(m.settings.get());
                }
            }).dimensions(r[0], r[1], r[2], r[3]).build());
        }
        // Fußzeile: Alle an / aus (nicht bei Packs), bei HUD zusätzlich Verschieben
        int fy = py1 - FOOT + 4;
        if (cat != 3) {
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                setAll(true);
                again();
            }).dimensions(px1 - 8 - 130, fy, 62, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                setAll(false);
                again();
            }).dimensions(px1 - 8 - 64, fy, 64, 18).build());
        }
        if (cat == 1) {
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> MinecraftClient.getInstance().setScreen(new HudEditScreen(new PvpScreen())))
                .dimensions(gx0, fy, 110, 18).build());
        }
    }

    private void setAll(boolean value) {
        for (Mod m : mods) if (m.flip != null && m.on.getAsBoolean() != value) m.flip.run();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, rows() - vis);
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        if (ns != scroll) {
            scroll = ns;
            again();
        }
        return true;
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD);
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int acc = CAT_COL[cat];

        // Kopf
        int active = 0, total = 0;
        for (Mod m : mods) {
            if (m.flip == null) continue;
            total++;
            if (m.on.getAsBoolean()) active++;
        }
        Ui.txt(ctx, tr, "PvP", px0 + 10, py0 + 8, Ui.TEXT);
        Ui.txt(ctx, tr, "Loadout · " + CATS[cat], px0 + 10 + tr.getWidth("PvP") + 6, py0 + 8, Ui.MUTED);
        Ui.txt(ctx, tr, "Module an – fertig. Wird sofort gespeichert.", px0 + 10, py0 + 20, 0xFF6B7385);
        String cnt = active + "/" + total + " an";
        int cw = tr.getWidth(cnt) + 20, cx0 = px1 - 8 - 64 - 8 - cw;
        Ui.card(ctx, cx0, py0 + 9, cx0 + cw, py0 + 25, Ui.BORDER, Ui.SURFACE);
        Ui.round(ctx, cx0 + 6, py0 + 14, cx0 + 11, py0 + 19, active > 0 ? Ui.GREEN : 0xFF4A5164);
        Ui.txt(ctx, tr, cnt, cx0 + 14, py0 + 13, Ui.TEXT);
        int bx0 = px1 - 8 - 64;
        Ui.btn(ctx, tr, bx0, py0 + 8, bx0 + 64, py0 + 26, "‹ Menü", Ui.in(mouseX, mouseY, bx0, py0 + 8, bx0 + 64, py0 + 26), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        // Seitenleiste
        ItemStack[] catIcons = {new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.ITEM_FRAME), new ItemStack(Items.SPYGLASS), new ItemStack(Items.GRASS_BLOCK)};
        for (int i = 0; i < CATS.length; i++) {
            int[] r = sideBtn(i);
            boolean sel = i == cat, hov = Ui.in(mouseX, mouseY, r[0], r[1], r[0] + r[2], r[1] + r[3]);
            ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], Ui.BG);
            Ui.card(ctx, r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? CAT_COL[i] : hov ? 0xFF3A4152 : Ui.BORDER,
                sel ? Ui.mix(0xFF141821, CAT_COL[i], 0.18f) : 0xFF141821);
            ctx.drawItem(catIcons[i], r[0] + (r[2] - 16) / 2, r[1] + 4);
            String l = CATS[i];
            Ui.txt(ctx, tr, l, r[0] + (r[2] - tr.getWidth(l)) / 2, r[1] + 24, sel ? Ui.TEXT : Ui.MUTED);
        }

        // Kästen
        for (int i = 0; i < mods.size(); i++) {
            int[] r = card(i);
            if (r == null) continue;
            Mod m = mods.get(i);
            boolean on = m.flip != null && m.on.getAsBoolean();
            int x0 = r[0], y0 = r[1], x1 = r[0] + r[2], y1 = r[1] + r[3];
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            ctx.fill(x0, y0, x1, y1, Ui.BG);
            if (on) Ui.round2(ctx, x0 - 1, y0 - 1, x1 + 1, y1 + 1, (acc & 0x00FFFFFF) | 0x40000000);
            Ui.card(ctx, x0, y0, x1, y1, on ? acc : hov ? 0xFF3A4152 : Ui.BORDER,
                on ? Ui.mix(0xFF151921, acc, 0.14f) : hov ? 0xFF1A1F29 : 0xFF151921);
            Ui.round2(ctx, x0 + 5, y0 + 5, x0 + 25, y0 + 25, on ? (acc & 0x00FFFFFF) | 0x50000000 : 0x18FFFFFF);
            ctx.drawItem(m.icon, x0 + 7, y0 + 7);
            Ui.txt(ctx, tr, tr.trimToWidth(m.name, r[2] - 32), x0 + 29, y0 + 6, on ? 0xFFFFFFFF : Ui.TEXT);
            Ui.txt(ctx, tr, tr.trimToWidth(m.desc, r[2] - 32), x0 + 29, y0 + 17, Ui.MUTED);
            if (m.flip != null) {
                // Schalter unten rechts
                int sx1 = x1 - 6, sx0 = sx1 - 20, sy0 = y1 - 13;
                Ui.round2(ctx, sx0, sy0, sx1, sy0 + 10, on ? acc : 0xFF333A4A);
                int kx = on ? sx1 - 9 : sx0 + 1;
                Ui.round(ctx, kx, sy0 + 1, kx + 8, sy0 + 9, on ? 0xFF0B0D12 : 0xFFE8EAF0);
                if (m.settings != null) {
                    boolean sh = Ui.in(mouseX, mouseY, x0 + 4, y1 - 15, x0 + 66, y1 - 3);
                    Ui.txt(ctx, tr, "Einstellen ›", x0 + 6, y1 - 13, sh ? acc : 0xFF7D8598);
                }
            } else {
                Ui.txt(ctx, tr, "Öffnen ›", x0 + 6, y1 - 13, hov ? acc : 0xFF7D8598);
            }
        }

        // Fuß
        int fy = py1 - FOOT + 4;
        if (rows() > vis) {
            String h = "Mausrad: mehr (" + (scroll + 1) + "/" + (rows() - vis + 1) + ")";
            Ui.txt(ctx, tr, h, cat == 1 ? gx0 + 118 : gx0, fy + 5, 0xFF6B7385);
        }
        if (cat != 3) {
            int ax = px1 - 8 - 130;
            Ui.btn(ctx, tr, ax, fy, ax + 62, fy + 18, "Alle an", Ui.in(mouseX, mouseY, ax, fy, ax + 62, fy + 18), Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
            int bx = px1 - 8 - 64;
            Ui.btn(ctx, tr, bx, fy, bx + 64, fy + 18, "Alle aus", Ui.in(mouseX, mouseY, bx, fy, bx + 64, fy + 18), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        }
        if (cat == 1) {
            Ui.btn(ctx, tr, gx0, fy, gx0 + 110, fy + 18, "Anzeigen verschieben ›", Ui.in(mouseX, mouseY, gx0, fy, gx0 + 110, fy + 18),
                Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFD6F0FF);
        }
    }
}
