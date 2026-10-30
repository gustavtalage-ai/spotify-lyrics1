package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Dein "LowOnFire"-Pack (Name oder Dateiname enthält lowonfire / lowfire). */
    static ResourcePackProfile lowFire() {
        for (ResourcePackProfile p : list()) {
            String a = norm(p.getId());
            String b = norm(p.getDisplayName().getString());
            if (a.contains("lowonfire") || b.contains("lowonfire") || a.contains("lowfire") || b.contains("lowfire")) return p;
        }
        return null;
    }

    static boolean lowFireOn() {
        ResourcePackProfile p = lowFire();
        return p != null && isOn(p);
    }
}
