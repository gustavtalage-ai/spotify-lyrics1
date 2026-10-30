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
        for (int i = 0; i < PvpHud.HUD_COUNT; i += 2) row(mod(i), mod(i + 1));
        gap(2);
        row(B("Alle an", () -> {
            PvpHud.setAll(0, PvpHud.HUD_COUNT, true);
            again();
        }), G("Alle aus", () -> {
            PvpHud.setAll(0, PvpHud.HUD_COUNT, false);
            again();
        }));
        gap(2);
        text("Links oben: Info, CPS, Combo, Effekte. Links Mitte: Tasten.", 0xFF7E8494);
        text("Rechts: Rüstung und Vorrat. Mitte: Balken, Reichweite, Ziel.", 0xFF7E8494);
    }

    private void buildExtras() {
        Config c = Config.INSTANCE;
        sec("Kampf-Hilfen");
        row(mod(PvpHud.HITSOUND), mod(PvpHud.TOTEM));
        row(mod(PvpHud.LOWHP), mod(PvpHud.SPRINT));
        gap(2);
        sec("Zoom");
        row(mod(PvpHud.ZOOM));
        stepper("Zoom-Stärke: Sichtfeld " + c.zoomFov, () -> {
            c.zoomFov = Math.max(30, c.zoomFov - 5);
            c.save();
            again();
        }, () -> {
            c.zoomFov = Math.min(110, c.zoomFov + 5);
            c.save();
            again();
        });
        text("Im Spiel Taste Z halten. Kleiner Wert = stärker.", 0xFF7E8494);
        gap(2);
        sec("Freunde");
        row(mod(PvpHud.FRIENDS), B("Freunde wählen ›", () -> MinecraftClient.getInstance().setScreen(new FriendsScreen())));
        text("Freunde nicht schlagen: Klicks auf sie werden ignoriert.", 0xFF7E8494);
    }
}
