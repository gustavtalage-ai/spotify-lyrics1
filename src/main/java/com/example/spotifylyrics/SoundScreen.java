package com.example.spotifylyrics;

import net.minecraft.client.gui.screen.Screen;

import java.util.List;

/** Soundboard-Bereich in großen Kästen: Effekte, Musik, Voice, Spotify und Lyrics. */
public class SoundScreen extends CardGridScreen {
    public SoundScreen() {
        super("Soundboard");
    }

    @Override
    protected String heading() {
        return "Soundboard";
    }

    @Override
    protected String subheading() {
        return Lang.t("Kasten anklicken = öffnen, Zahnrad = einstellen", "Click a box = open, gear = settings");
    }

    @Override
    protected Screen fresh() {
        return new SoundScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected boolean big() {
        return true;
    }

    @Override
    protected boolean bulkButtons() {
        return false;
    }

    @Override
    protected void cards(List<Card> out, String q) {
        Config c = Config.INSTANCE;
        boolean playing = Soundboard.nowPlaying != null;
        out.add(new Card(Lang.t("Effekte", "Sounds"), playing ? Lang.t("Spielt gerade", "Playing") : Lang.t("Kurze Sounds · unter 30 Sek.", "Short sounds · under 30 s"),
            "bang", () -> false, null, () -> SoundboardScreen.open(0)));
        out.add(new Card(Lang.t("Musik", "Music"), Lang.t("Lieder · ab 30 Sek.", "Songs · 30 s and longer"),
            "note", () -> false, null, () -> SoundboardScreen.open(1)));
        int preset = VoiceChanger.getPreset();
        out.add(new Card("Voice", preset == 0 ? Lang.t("Stimme verändern", "Change your voice") : Lang.t("Aktiv: ", "Active: ") + VoiceChanger.PRESETS[preset].name(),
            "mic", () -> false, null, () -> SoundboardScreen.open(2)));
        out.add(new Card("Spotify", Lang.t("Songs suchen und abspielen", "Search and play songs"),
            "play", () -> false, null, () -> SoundboardScreen.open(3)));
        out.add(new Card("Lyrics", Lang.t("Songtext im Spiel anzeigen", "Show song lyrics in game"), "chart",
            () -> c.enabled, () -> {
                c.enabled = !c.enabled;
                c.save();
            }, LyricsScreen::new));
    }
}
