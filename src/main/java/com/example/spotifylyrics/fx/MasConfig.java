// Einstellungen wie in MasEffects (MIT-Lizenz, (c) MasUN0) – gespeichert in der Alpha-Mod-Config.
package com.example.spotifylyrics.fx;

import com.example.spotifylyrics.Config;

public class MasConfig {
    /** Wird beim Start auf Config.INSTANCE.mas gesetzt. */
    public static MasConfig INSTANCE = new MasConfig();

    public boolean MaceShockwave = true;
    public float MaceShockwaveSize = 1.0F;
    public float MaceShockwaveOpacity = 1.0F;
    public boolean MaceSpark = true;
    public boolean MaceFlash = true;
    public boolean ShieldEffect = true;
    public boolean ArmorParticles = true;
    public boolean WindParticles = true;
    public boolean CustomTotemEffect = true;
    public float TotemEffectOpacity = 1.0F;
    public boolean PlayerDeathEffect = true;
    public boolean PearlTrailParticles = false;
    public float PearlTrailOpacity = 0.5F;
    public boolean GlideIcon = true;

    /** Hauptschalter "Kampf-Effekte" im PvP-Menü. */
    public static boolean on() {
        return Config.INSTANCE != null && Config.INSTANCE.fxOn;
    }

    public static void init() {
        if (Config.INSTANCE.mas == null) Config.INSTANCE.mas = new MasConfig();
        INSTANCE = Config.INSTANCE.mas;
    }
}
