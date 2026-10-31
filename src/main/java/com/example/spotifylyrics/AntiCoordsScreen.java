package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Anti-Koordinaten: zufällige Textur-Drehungen aus, damit niemand aus Screenshots deine Position ausrechnen kann. */
public class AntiCoordsScreen extends ItemScreen {
    public AntiCoordsScreen() {
        super(Lang.t("Anti-Koordinaten", "Anti coords"), Lang.t("Keine Textur-Drehung, kein Versatz", "No texture rotation, no offset"));
    }

    @Override
    protected ItemScreen fresh() {
        return new AntiCoordsScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    /** Ein/aus und die Welt neu zeichnen, damit es sofort wirkt. */
    static void set(boolean on) {
        Config c = Config.INSTANCE;
        c.antiRotation = on;
        c.save();
        reload();
    }

    static void reload() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec(Lang.t("Anti-Koordinaten", "Anti coords"));
        row(T(Lang.t("Textur-Drehung aus", "Texture rotation off"), c.antiRotation, () -> {
            set(!c.antiRotation);
            again();
        }));
        row(T(Lang.t("Auch Blumen/Gras-Versatz aus", "Also flower/grass offset off"), c.antiOffset, () -> {
            c.antiOffset = !c.antiOffset;
            c.save();
            if (c.antiRotation) reload();
            again();
        }));
        gap(2);
        text(Lang.t("Gras, Sand, Stein usw. sind normal je nach Ort", "Grass, sand, stone etc. are normally rotated"), 0xFF8B92A5);
        text(Lang.t("anders gedreht. Daraus kann man deine", "by position. From that, people can work out"), 0xFF8B92A5);
        text(Lang.t("Koordinaten ausrechnen. Hiermit nicht mehr.", "your coordinates. Not anymore."), 0xFF8B92A5);
    }
}
