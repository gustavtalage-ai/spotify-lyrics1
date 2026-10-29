package com.example.spotifylyrics;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.maxhenkel.voicechat.api.mp3.Mp3Decoder;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Anbindung an Simple Voice Chat. Diese Klasse wird nur geladen, wenn die Voice-Chat-Mod da ist
 * (Eintrag "voicechat" in fabric.mod.json).
 */
public class SoundboardPlugin implements VoicechatPlugin {
    private VoicechatApi api;

    @Override
    public String getPluginId() {
        return "spotifylyrics_soundboard";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        Soundboard.loader = this::decode;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Wird fuer jedes 20-ms-Stueck gerufen, auch wenn du still bist: eigenes Audio ins Mikrofon mischen.
        registration.registerEvent(MergeClientSoundEvent.class, event -> {
            short[] frame = Soundboard.nextFrame();
            if (frame != null) event.mergeAudio(frame);
        });
        // Lernt die echte Stueckgroesse des Voice Chats und wendet den Voice Changer auf dein Mikrofon an.
        registration.registerEvent(ClientSoundEvent.class, event -> {
            short[] raw = event.getRawAudio();
            if (raw != null && raw.length > 0) {
                Soundboard.noteFrameSize(raw.length);
                short[] changed = VoiceChanger.process(raw);
                if (changed != null) event.setRawAudio(changed);
            }
        });
    }

    private Soundboard.Pcm decode(Path file) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Mp3Decoder decoder = api.createMp3Decoder(in);
            if (decoder == null) throw new IOException("MP3-Decoder nicht verfuegbar");
            short[] data = decoder.decode();
            AudioFormat format = decoder.getAudioFormat();
            return new Soundboard.Pcm(data, format.getSampleRate(), format.getChannels());
        }
    }
}
