package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Texture-Packs ein- und ausschalten (wie im normalen Packs-Menü). */
final class Packs {
    private Packs() {}

    private static ResourcePackManager mgr() {
        return MinecraftClient.getInstance().getResourcePackManager();
    }

    /** Alle Packs, die man schalten darf (ohne das eingebaute Standard-Pack). */
    static List<ResourcePackProfile> list() {
        List<ResourcePackProfile> out = new ArrayList<>();
        try {
            for (ResourcePackProfile p : mgr().getProfiles()) {
                if (p.isRequired() || p.isPinned()) continue;
                out.add(p);
            }
            out.sort((a, b) -> a.getDisplayName().getString().compareToIgnoreCase(b.getDisplayName().getString()));
        } catch (Throwable ignored) {
        }
        return out;
    }

    static boolean isOn(ResourcePackProfile p) {
        try {
            return mgr().getEnabledIds().contains(p.getId());
        } catch (Throwable e) {
            return false;
        }
    }

    static int activeCount() {
        int n = 0;
        for (ResourcePackProfile p : list()) if (isOn(p)) n++;
        return n;
    }

    /** Pack an/aus; lädt die Texturen danach automatisch neu. */
    static void set(ResourcePackProfile p, boolean on) {
        try {
            ResourcePackManager m = mgr();
            if (on) m.enable(p.getId()); else m.disable(p.getId());
            MinecraftClient.getInstance().options.refreshResourcePacks(m);
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.warn("Pack umschalten fehlgeschlagen", e);
        }
    }

    /** Schnellschalter: Anzeigename, danach Suchbegriffe (klein, ohne Leerzeichen) im Pack-Namen. */
    static final String[][] QUICK = {
        {"Low Fire", "lowonfire", "lowfire"},
        {"Low Shield", "lowshield"},
        {"Schild + Totem klein", "smallshieldtotem"},
        {"Totem-Pop klein", "smalltotempop"},
        {"Kurze Schwerter", "shortswords", "shorterswords"},
        {"Crystal PvP", "crystalvanilla"}
    };

    /** Aktive Packs in ihrer Reihenfolge (zuerst das unterste). */
    static List<String> enabledIds() {
        List<String> out = new ArrayList<>();
        try {
            Set<String> valid = new HashSet<>();
            for (ResourcePackProfile p : list()) valid.add(p.getId());
            for (String id : mgr().getEnabledIds()) if (valid.contains(id)) out.add(id);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Genau diese Packs einschalten (alle anderen aus), in dieser Reihenfolge; Texturen laden danach neu. */
    static void apply(List<String> ids) {
        try {
            ResourcePackManager m = mgr();
            for (ResourcePackProfile p : list()) if (isOn(p)) m.disable(p.getId());
            for (String id : ids) {
                ResourcePackProfile pr = m.getProfile(id);
                if (pr != null && !pr.isRequired() && !pr.isPinned()) m.enable(id);
            }
            MinecraftClient.getInstance().options.refreshResourcePacks(m);
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.warn("Pack-Set laden fehlgeschlagen", e);
        }
    }

    /** Alle gefundenen Schnell-Packs an oder aus. */
    static void quickAll(boolean on) {
        try {
            ResourcePackManager m = mgr();
            for (String[] q : QUICK) {
                ResourcePackProfile p = find(java.util.Arrays.copyOfRange(q, 1, q.length));
                if (p == null) continue;
                if (on) m.enable(p.getId()); else m.disable(p.getId());
            }
            MinecraftClient.getInstance().options.refreshResourcePacks(m);
        } catch (Throwable e) {
            SpotifyLyricsClient.LOGGER.warn("Schnell-Packs fehlgeschlagen", e);
        }
    }

    /** Anzeigename zu einer Pack-ID (oder null, wenn es das Pack nicht mehr gibt). */
    static String nameOf(String id) {
        try {
            ResourcePackProfile pr = mgr().getProfile(id);
            return pr == null ? null : pr.getDisplayName().getString();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Erstes Pack, dessen Name oder Dateiname einen der Begriffe enthält (Reihenfolge = Vorrang). */
    static ResourcePackProfile find(String... keys) {
        List<ResourcePackProfile> all = list();
        for (String k : keys) {
            for (ResourcePackProfile p : all) {
                if (norm(p.getId()).contains(k) || norm(p.getDisplayName().getString()).contains(k)) return p;
            }
        }
        return null;
    }

    /** Dein "LowOnFire"-Pack. */
    static ResourcePackProfile lowFire() {
        return find("lowonfire", "lowfire");
    }

    static boolean lowFireOn() {
        ResourcePackProfile p = lowFire();
        return p != null && isOn(p);
    }
}
