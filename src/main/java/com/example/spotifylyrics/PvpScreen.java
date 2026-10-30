package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** PvP-Client: Kampf (Hit-Assistent, Schnell-Packs), Anzeigen (HUD-Module) und Extras. */
public class PvpScreen extends ItemScreen {
    private static int page = 0;

    public PvpScreen() {
        super("PvP", "Dein Client im Client");
    }

    @Override
    protected ItemScreen fresh() {
        return new PvpScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ Menü";
    }

    private Cell tab(String label, int idx) {
        return idx == page ? S(label) : G(label, () -> {
            page = idx;
            again();
        });
    }

    private Cell mod(int bit) {
        return T(PvpHud.NAMES[bit], PvpHud.on(bit), () -> {
            PvpHud.flip(bit);
            again();
        });
    }

    @Override
    protected void build() {
        row(tab("Kampf", 0), tab("HUD", 1), tab("Visuell", 2), tab("Extras", 3), tab("Packs", 4));
        gap(4);
        if (page == 0) buildFight();
        else if (page == 1) buildHud();
        else if (page == 2) buildVisual();
        else if (page == 3) buildExtras();
        else buildPacks();
    }

    private void buildFight() {
        Config c = Config.INSTANCE;
        sec("Hit-Assistent");
        row(T("Hit-Assistent", c.hitAssist, () -> {
            c.hitAssist = !c.hitAssist;
            c.save();
            again();
        }));
        row(T("Klick merken (perfekter Schlag)", c.hitQueue, () -> {
            c.hitQueue = !c.hitQueue;
            c.save();
            again();
        }));
        text("Zu früh geklickt: Schlag kommt bei voller Leiste.", 0xFF8B92A5);
        gap(2);
        sec("Gilt für diese Waffen (blau = an)");
        row(weapon(0), weapon(1), weapon(2));
        row(weapon(3), weapon(4));
        stepper("Aufladung ab: " + c.hitAssistMin + " %", () -> {
            c.hitAssistMin = Math.max(50, c.hitAssistMin - 5);
            c.save();
            again();
        }, () -> {
            c.hitAssistMin = Math.min(100, c.hitAssistMin + 5);
            c.save();
            again();
        });
    }

    /** Waffen-Knopf: blau = an, grau = aus. */
    private Cell weapon(int i) {
        Config c = Config.INSTANCE;
        boolean on = ((c.hitWeapons >> i) & 1) != 0;
        Runnable r = () -> {
            c.hitWeapons ^= 1 << i;
            c.save();
            again();
        };
        return on ? B(HitAssist.NAMES[i], r) : G(HitAssist.NAMES[i], r);
    }

    private void buildPacks() {
        sec("Schnell-Packs");
        List<String> labels = new ArrayList<>();
        List<ResourcePackProfile> profs = new ArrayList<>();
        for (String[] q : Packs.QUICK) {
            ResourcePackProfile f = Packs.find(Arrays.copyOfRange(q, 1, q.length));
            if (f != null && !profs.contains(f)) {
                labels.add(q[0]);
                profs.add(f);
            }
        }
        for (int i = 0; i < profs.size(); i += 2) {
            final ResourcePackProfile pa = profs.get(i);
            Cell ca = T(labels.get(i), Packs.isOn(pa), () -> {
                Packs.set(pa, !Packs.isOn(pa));
                again();
            });
            if (i + 1 < profs.size()) {
                final ResourcePackProfile pb = profs.get(i + 1);
                row(ca, T(labels.get(i + 1), Packs.isOn(pb), () -> {
                    Packs.set(pb, !Packs.isOn(pb));
                    again();
                }));
            } else {
                row(ca);
            }
        }
        if (profs.isEmpty()) text("Keine passenden Packs gefunden.", 0xFFFF6A6A);
        row(G("Pack-Sets und alle Packs ›", () -> MinecraftClient.getInstance().setScreen(new PackSetsScreen())));
    }

    private void buildHud() {
        sec("Anzeigen auf dem Bildschirm");
        int[][] pairs = {{PvpHud.INFO, PvpHud.CPS}, {PvpHud.COMBO, PvpHud.EFFECTS}, {PvpHud.ARMOR, PvpHud.STOCK},
            {PvpHud.TARGET, PvpHud.NEAR}, {PvpHud.STATS, PvpHud.CRIT}, {PvpHud.COOLDOWN, PvpHud.COMBAT}};
        for (int[] pr : pairs) row(mod(pr[0]), mod(pr[1]));
        row(mod(PvpHud.DEATH), mod(PvpHud.TOTEM));
        gap(2);
        row(B("Anzeigen verschieben \u203a", () -> MinecraftClient.getInstance().setScreen(new HudEditScreen())));
        row(G("Alle an", () -> {
            PvpHud.setAll(PvpHud.HUD_IDS, true);
            again();
        }), G("Alle aus", () -> {
            PvpHud.setAll(PvpHud.HUD_IDS, false);
            again();
        }));
    }

    private void buildVisual() {
        sec("Fadenkreuz");
        row(mod(PvpHud.CROSS).w(4), G("Einstellungen \u203a", () -> MinecraftClient.getInstance().setScreen(new CrosshairScreen())).w(3));
        gap(2);
        sec("Reichweite");
        row(T("Reichweite (Kreis)", PvpHud.on(PvpHud.CIRCLE), () -> {
            PvpHud.flip(PvpHud.CIRCLE);
            again();
        }).w(4), G("Einstellungen \u203a", () -> MinecraftClient.getInstance().setScreen(new ReachScreen())).w(3));
        gap(2);
        sec("Im Spiel");
        row(mod(PvpHud.BAR).w(4), G("Einstellungen \u203a", () -> MinecraftClient.getInstance().setScreen(new BarScreen())).w(3));
        row(mod(PvpHud.DMGNUM), mod(PvpHud.LOWHP));
    }

    private void buildExtras() {
        Config c = Config.INSTANCE;
        sec("Kampf-Hilfen");
        row(mod(PvpHud.HITSOUND), mod(PvpHud.SPRINT));
        row(mod(PvpHud.ARMORWARN));
        gap(2);
        sec("Totem");
        row(mod(PvpHud.TOTEM));
        text("Hinweis kommt, wenn ein Totem gepoppt ist.", 0xFF8B92A5);
        gap(2);
        sec("Kampf-Timer und Statistik");
        stepper("Kampf-Timer: " + c.combatSecs + " s", () -> {
            c.combatSecs = Math.max(5, c.combatSecs - 5);
            c.save();
            again();
        }, () -> {
            c.combatSecs = Math.min(60, c.combatSecs + 5);
            c.save();
            again();
        });
        row(G("Statistik zur\u00fccksetzen", () -> {
            PvpHud.resetStats();
            again();
        }));
    }
}
