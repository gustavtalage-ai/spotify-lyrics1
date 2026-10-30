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
        row(tab("Kampf", 0), tab("Anzeigen", 1), tab("Extras", 2));
        gap(4);
        if (page == 0) buildFight();
        else if (page == 1) buildHud();
        else buildExtras();
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
        text("Zu früh geklickt: Schlag kommt bei voller Leiste.", 0xFF7E8494);
        sec("Gilt für diese Waffen");
        for (int i = 0; i < 5; i += 2) {
            final int a = i, b = i + 1;
            Cell ca = T(HitAssist.NAMES[a], ((c.hitWeapons >> a) & 1) != 0, () -> {
                c.hitWeapons ^= 1 << a;
                c.save();
                again();
            });
            if (b < 5) {
                row(ca, T(HitAssist.NAMES[b], ((c.hitWeapons >> b) & 1) != 0, () -> {
                    c.hitWeapons ^= 1 << b;
                    c.save();
                    again();
                }));
            } else {
                row(ca);
            }
        }
        stepper("Aufladung ab: " + c.hitAssistMin + " %", () -> {
            c.hitAssistMin = Math.max(50, c.hitAssistMin - 5);
            c.save();
            again();
        }, () -> {
            c.hitAssistMin = Math.min(100, c.hitAssistMin + 5);
            c.save();
            again();
        });
        gap(2);
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
        sec("Auf dem Bildschirm");
        int[][] pairs = {{PvpHud.INFO, PvpHud.CPS}, {PvpHud.COMBO, PvpHud.EFFECTS}, {PvpHud.ARMOR, PvpHud.STOCK},
            {PvpHud.TARGET, PvpHud.NEAR}, {PvpHud.STATS, PvpHud.CRIT}};
        for (int[] pr : pairs) row(mod(pr[0]), mod(pr[1]));
        row(mod(PvpHud.TOTEM));
        row(mod(PvpHud.BAR).w(4), G("Einstellungen \u203a", () -> MinecraftClient.getInstance().setScreen(new BarScreen())).w(3));
        row(mod(PvpHud.REACH).w(4), G("Einstellungen \u203a", () -> MinecraftClient.getInstance().setScreen(new ReachScreen())).w(3));
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

    private void buildExtras() {
        sec("Kampf-Hilfen");
        row(mod(PvpHud.HITSOUND), mod(PvpHud.LOWHP));
        row(mod(PvpHud.SPRINT), mod(PvpHud.ARMORWARN));
        gap(2);
        sec("Totem");
        row(mod(PvpHud.TOTEM));
        row(mod(PvpHud.AUTOTOTEM));
        text("Hinweis kommt, wenn ein Totem gepoppt ist.", 0xFF7E8494);
        text("Auto nachlegen kann auf Servern als Cheat gelten.", 0xFFFFAA33);
        gap(2);
        sec("Kreis und Statistik");
        row(mod(PvpHud.CIRCLE), G("Kreis-Einstellungen \u203a", () -> MinecraftClient.getInstance().setScreen(new ReachScreen())));
        row(G("Statistik zur\u00fccksetzen", () -> {
            PvpHud.resetStats();
            again();
        }));
    }
}
