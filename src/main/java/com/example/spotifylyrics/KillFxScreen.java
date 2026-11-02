package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für Finisher, Kill-Banner und Sounds (die Skins selbst sind in der Sammlung). */
public class KillFxScreen extends ItemScreen {
    public KillFxScreen() {
        super(Lang.t("Finisher-Einstellungen", "Finisher settings"), Lang.t("Banner, Sound, Größe", "Banner, sound, size"));
    }

    @Override
    protected ItemScreen fresh() {
        return new KillFxScreen();
    }

    @Override
    protected Screen back() {
        return new CollectionScreen();
    }

    @Override
    protected String backLabel() {
        return Lang.t("‹ Sammlung", "‹ Collection");
    }

    private Cell t(String label, boolean on, Runnable flip) {
        return T(label, on, () -> {
            flip.run();
            Config.INSTANCE.save();
            again();
        });
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        text(Lang.t("Der Finisher kommt von dem Skin der Waffe, mit der du killst.", "The finisher comes from the skin of the weapon you kill with."), 0xFF8A93A6);
        gap(4);
        row(t(Lang.t("Finisher an", "Finisher on"), c.kfOn, () -> c.kfOn = !c.kfOn),
            t(Lang.t("Waffen-Skins an", "Weapon skins on"), c.wsOn, () -> c.wsOn = !c.wsOn));
        row(t(Lang.t("Kill-Banner (Serie 1–5)", "Kill banner (streak 1–5)"), c.kfBanner, () -> c.kfBanner = !c.kfBanner),
            t("Sound", c.kfSound, () -> c.kfSound = !c.kfSound));
        row(t(Lang.t("Partikel", "Particles"), c.kfParticles, () -> c.kfParticles = !c.kfParticles),
            t(Lang.t("Bildschirm-Blitz", "Screen flash"), c.kfFlash, () -> c.kfFlash = !c.kfFlash));
        row(t(Lang.t("Auch bei Mobs", "Also for mobs"), c.kfMobs, () -> c.kfMobs = !c.kfMobs));
        stepper(Lang.t("Größe: ", "Size: ") + c.kfSize + "%", () -> {
            c.kfSize = Math.max(50, c.kfSize - 25);
            c.save();
            again();
        }, () -> {
            c.kfSize = Math.min(300, c.kfSize + 25);
            c.save();
            again();
        });
    }
}
