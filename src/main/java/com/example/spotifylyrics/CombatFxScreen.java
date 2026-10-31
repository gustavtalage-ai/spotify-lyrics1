package com.example.spotifylyrics;

import com.example.spotifylyrics.fx.MasConfig;
import net.minecraft.client.gui.screen.Screen;

import java.util.Locale;

/** Einstellungen der Kampf-Effekte – dieselben Optionen wie in MasEffects. */
public class CombatFxScreen extends ItemScreen {
    public CombatFxScreen() {
        super(Lang.t("Kampf-Effekte", "Combat effects"), "MasEffects");
    }

    @Override
    protected ItemScreen fresh() {
        return new CombatFxScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    private Cell t(String label, boolean on, Runnable flip) {
        return T(label, on, () -> {
            flip.run();
            Config.INSTANCE.save();
            again();
        });
    }

    private static String pct(float f) {
        return String.format(Locale.ROOT, "%d%%", Math.round(f * 100));
    }

    private static float step(float v, float d, float min, float max) {
        return Math.round(Math.max(min, Math.min(max, v + d)) * 10f) / 10f;
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        MasConfig m = MasConfig.INSTANCE;
        row(t(Lang.t("Kampf-Effekte an", "Combat effects on"), c.fxOn, () -> c.fxOn = !c.fxOn));
        gap(4);

        sec(Lang.t("Streitkolben", "Mace"));
        row(t(Lang.t("Schockwelle", "Shockwave"), m.MaceShockwave, () -> m.MaceShockwave = !m.MaceShockwave));
        stepper(Lang.t("Größe der Schockwelle: ", "Shockwave size: ") + pct(m.MaceShockwaveSize), () -> {
            m.MaceShockwaveSize = step(m.MaceShockwaveSize, -0.1f, 0.1f, 3f);
            c.save();
            again();
        }, () -> {
            m.MaceShockwaveSize = step(m.MaceShockwaveSize, 0.1f, 0.1f, 3f);
            c.save();
            again();
        });
        stepper(Lang.t("Deckkraft der Schockwelle: ", "Shockwave opacity: ") + pct(m.MaceShockwaveOpacity), () -> {
            m.MaceShockwaveOpacity = step(m.MaceShockwaveOpacity, -0.1f, 0f, 1f);
            c.save();
            again();
        }, () -> {
            m.MaceShockwaveOpacity = step(m.MaceShockwaveOpacity, 0.1f, 0f, 1f);
            c.save();
            again();
        });
        row(t(Lang.t("Funken", "Sparks"), m.MaceSpark, () -> m.MaceSpark = !m.MaceSpark),
            t(Lang.t("Blitz", "Flash"), m.MaceFlash, () -> m.MaceFlash = !m.MaceFlash));
        row(t(Lang.t("Schild-Aufprall", "Shield impact"), m.ShieldEffect, () -> m.ShieldEffect = !m.ShieldEffect),
            t(Lang.t("Rüstungs-Splitter (Durchbruch)", "Armor scraps (Breach)"), m.ArmorParticles, () -> m.ArmorParticles = !m.ArmorParticles));
        gap(4);

        sec(Lang.t("Allgemein", "General"));
        row(t(Lang.t("Windkugel-Welle", "Wind charge wave"), m.WindParticles, () -> m.WindParticles = !m.WindParticles),
            t(Lang.t("Elytra-Symbol", "Elytra icon"), m.GlideIcon, () -> m.GlideIcon = !m.GlideIcon));
        row(t(Lang.t("Eigener Totem-Effekt", "Custom totem effect"), m.CustomTotemEffect, () -> m.CustomTotemEffect = !m.CustomTotemEffect),
            t(Lang.t("Todes-Effekt", "Death effect"), m.PlayerDeathEffect, () -> m.PlayerDeathEffect = !m.PlayerDeathEffect));
        stepper(Lang.t("Deckkraft Totem-Effekt: ", "Totem effect opacity: ") + pct(m.TotemEffectOpacity), () -> {
            m.TotemEffectOpacity = step(m.TotemEffectOpacity, -0.1f, 0f, 1f);
            c.save();
            again();
        }, () -> {
            m.TotemEffectOpacity = step(m.TotemEffectOpacity, 0.1f, 0f, 1f);
            c.save();
            again();
        });
        row(t(Lang.t("Enderperlen-Spur", "Ender pearl trail"), m.PearlTrailParticles, () -> m.PearlTrailParticles = !m.PearlTrailParticles));
        stepper(Lang.t("Deckkraft Perlen-Spur: ", "Pearl trail opacity: ") + pct(m.PearlTrailOpacity), () -> {
            m.PearlTrailOpacity = step(m.PearlTrailOpacity, -0.1f, 0f, 1f);
            c.save();
            again();
        }, () -> {
            m.PearlTrailOpacity = step(m.PearlTrailOpacity, 0.1f, 0f, 1f);
            c.save();
            again();
        });
    }
}
