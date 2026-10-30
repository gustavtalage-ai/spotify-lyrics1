package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pack-Sets: deine aktuelle Pack-Kombination in 4 Plätzen speichern und mit einem Klick laden. */
public class PackSetsScreen extends ItemScreen {
    private static final int SLOTS = 4;

    public PackSetsScreen() {
        super("Texture Packs", "Pack-Sets speichern und laden");
    }

    @Override
    protected ItemScreen fresh() {
        return new PackSetsScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ Menü";
    }

    private static String slot(int i) {
        List<String> l = Config.INSTANCE.packSets;
        while (l.size() < SLOTS) l.add("");
        String s = l.get(i);
        return s == null ? "" : s;
    }

    private static List<String> ids(String raw) {
        List<String> out = new ArrayList<>();
        if (raw.isEmpty()) return out;
        for (String id : raw.split("\\|")) if (!id.isEmpty()) out.add(id);
        return out;
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec("Meine Sets  ·  jetzt aktiv: " + Packs.activeCount());
        for (int i = 0; i < SLOTS; i++) {
            final int idx = i;
            List<String> saved = ids(slot(i));
            List<String> names = new ArrayList<>();
            for (String id : saved) {
                String n = Packs.nameOf(id);
                if (n != null) names.add(n);
            }
            String label = "Set " + (i + 1) + (names.isEmpty() ? "  ·  leer" : "  ·  " + names.size() + " Packs");
            row(X(label).w(5), B("Laden", names.isEmpty() ? null : () -> {
                Packs.apply(saved);
                again();
            }).w(2), G("Speichern", () -> {
                List<String> cur = Packs.enabledIds();
                c.packSets.set(idx, String.join("|", cur));
                c.save();
                again();
            }).w(3), R("x", names.isEmpty() ? null : () -> {
                c.packSets.set(idx, "");
                c.save();
                again();
            }).w(1));
            text(names.isEmpty() ? "Noch nichts gespeichert." : String.join(", ", names), 0xFF7E8494);
        }
        gap(2);
        sec("Schnell");
        row(B("PvP-Packs an", () -> {
            Packs.quickAll(true);
            again();
        }), G("Alle Packs aus", () -> {
            Packs.apply(new ArrayList<>());
            again();
        }));
        row(G("Alle Packs einzeln ›", () -> MinecraftClient.getInstance().setScreen(new TexturePacksScreen())));
        text("Speichern merkt, welche Packs an sind (mit Reihenfolge).", 0xFF7E8494);
    }
}
