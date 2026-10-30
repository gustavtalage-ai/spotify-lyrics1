package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Spieler in deiner Nähe als Freunde markieren (werden dann nicht geschlagen). */
public class FriendsScreen extends ItemScreen {
    private static final int ROWS = 8;
    private static int scroll = 0;
    private List<String> names = new ArrayList<>();

    public FriendsScreen() {
        super("Freunde", "Werden nicht geschlagen");
    }

    @Override
    protected ItemScreen fresh() {
        return new FriendsScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    @Override
    protected boolean onScroll(int dir) {
        int max = Math.max(0, names.size() - ROWS);
        int next = Math.max(0, Math.min(max, scroll - dir));
        if (next == scroll) return max > 0;
        scroll = next;
        again();
        return true;
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (mc.world != null) {
            for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                all.add(p.getName().getString());
            }
        }
        for (String f : c.friends) all.add(f);
        names = new ArrayList<>(all);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - ROWS)));

        sec("Spieler in deiner Nähe");
        if (names.isEmpty()) {
            text("Niemand in der Nähe. Komm näher ran und öffne das Menü.", 0xFFFFAA00);
        }
        for (int i = scroll; i < Math.min(names.size(), scroll + ROWS); i++) {
            final String n = names.get(i);
            final String key = n.toLowerCase(Locale.ROOT);
            row(T(n, c.friends.contains(key), () -> {
                if (!c.friends.remove(key)) c.friends.add(key);
                c.save();
                again();
            }));
        }
        gap(2);
        text(names.size() > ROWS ? "Mausrad = weiter scrollen (" + names.size() + " Namen)" : "Gespeicherte Freunde bleiben in der Liste.", 0xFF8B92A5);
        text("Gilt nur, wenn \"Freunde schützen\" unter Extras an ist.", 0xFF8B92A5);
    }
}
