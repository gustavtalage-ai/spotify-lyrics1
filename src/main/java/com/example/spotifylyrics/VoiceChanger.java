package com.example.spotifylyrics;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Voice Changer: verändert dein Mikrofon (48 kHz, Mono) Stück für Stück.
 * Enthält absichtlich KEINE Klassen von Simple Voice Chat; SoundboardPlugin ruft process() auf.
 */
public final class VoiceChanger {
    public record Preset(String name, String desc) {}

    public static final Preset[] PRESETS = {
        new Preset("Aus", "Deine normale Stimme"),
        new Preset("Tief", "Dunkle Monster-Stimme"),
        new Preset("Riese", "Noch tiefer und grummelig"),
        new Preset("Hoch", "Chipmunk-Stimme"),
        new Preset("Roboter", "Metallisch wie ein Droide"),
        new Preset("Alien", "Hoch und schwirrend"),
        new Preset("Echo", "Deine Stimme hallt nach"),
        new Preset("Höhle", "Großer Raum mit Hall"),
        new Preset("Megafon", "Laut und scharf"),
        new Preset("Telefon", "Dünn wie am Handy"),
    };

    private static final int RATE = 48000;
    private static final Object LOCK = new Object();
    private static volatile int lastPreset = 0;

    // Zustände der Effekte
    private static final Pitch PITCH = new Pitch();
    private static double ringPhase;
    private static final float[] ECHO = new float[RATE * 3 / 10];
    private static int echoPos;
    private static final float[] CAVE = new float[16384];
    private static int cavePos;
    private static float lp1, lp2;

    private VoiceChanger() {}

    public static int getPreset() {
        int p = Config.INSTANCE.voicePreset;
        return p < 0 || p >= PRESETS.length ? 0 : p;
    }

    public static void setPreset(int p) {
        Config.INSTANCE.voicePreset = Math.max(0, Math.min(PRESETS.length - 1, p));
        Config.INSTANCE.save();
    }

    public static boolean isSelfHear() {
        return Config.INSTANCE.voiceSelfHear;
    }

    public static void setSelfHear(boolean on) {
        Config.INSTANCE.voiceSelfHear = on;
        Config.INSTANCE.save();
    }

    /** Verändert ein Mikrofon-Stück. Gibt null zurück, wenn der Voice Changer aus ist. */
    public static short[] process(short[] in) {
        int preset = getPreset();
        if (preset == 0) return null;
        short[] out = new short[in.length];
        synchronized (LOCK) {
            if (preset != lastPreset) {
                reset();
                lastPreset = preset;
            }
            for (int i = 0; i < in.length; i++) {
                out[i] = clamp(effect(preset, in[i] / 32768f) * 32768f);
            }
        }
        if (Config.INSTANCE.voiceSelfHear) monitor(out);
        return out;
    }

    private static void reset() {
        PITCH.clear();
        java.util.Arrays.fill(ECHO, 0f);
        java.util.Arrays.fill(CAVE, 0f);
        echoPos = 0;
        cavePos = 0;
        lp1 = 0;
        lp2 = 0;
    }

    private static float effect(int preset, float x) {
        switch (preset) {
            case 1: return PITCH.process(x, 0.76) * 1.1f;
            case 2: return PITCH.process(x, 0.58) * 1.2f;
            case 3: return PITCH.process(x, 1.6);
            case 4: return ring(x, 55.0, 1.0f) * 1.4f;
            case 5: return ring(PITCH.process(x, 1.3), 38.0, 0.55f) * 1.2f;
            case 6: {
                float d = ECHO[echoPos];
                float y = x + 0.55f * d;
                ECHO[echoPos] = x + 0.45f * d;
                echoPos = (echoPos + 1) % ECHO.length;
                return y;
            }
            case 7: {
                int mask = CAVE.length - 1;
                float y = x + 0.38f * CAVE[(cavePos - 4800) & mask] + 0.3f * CAVE[(cavePos - 9100) & mask] + 0.2f * CAVE[(cavePos - 14000) & mask];
                CAVE[cavePos] = x + 0.25f * CAVE[(cavePos - 4800) & mask];
                cavePos = (cavePos + 1) & mask;
                return y * 0.85f;
            }
            case 8: {
                lp1 += 0.07f * (x - lp1);
                float h = x - lp1;
                float y = h * 3.2f;
                return Math.max(-0.45f, Math.min(0.45f, y)) * 1.6f;
            }
            case 9: {
                lp1 += 0.05f * (x - lp1);
                float h = x - lp1;
                lp2 += 0.36f * (h - lp2);
                return lp2 * 1.8f;
            }
            default: return x;
        }
    }

    private static float ring(float x, double hz, float mix) {
        ringPhase += 2 * Math.PI * hz / RATE;
        if (ringPhase > 2 * Math.PI) ringPhase -= 2 * Math.PI;
        float m = (float) Math.sin(ringPhase);
        return x * ((1f - mix) + mix * m);
    }

    private static short clamp(float v) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(v)));
    }

    /** Tonhöhe ändern, ohne dass es langsamer oder schneller wird (zwei überblendete Lesepunkte). */
    private static final class Pitch {
        private static final int SIZE = 8192, MASK = SIZE - 1, WIN = 2048;
        private final float[] buf = new float[SIZE];
        private int wp;
        private double delay;

        void clear() {
            java.util.Arrays.fill(buf, 0f);
            wp = 0;
            delay = 0;
        }

        float process(float x, double ratio) {
            buf[wp] = x;
            double d1 = delay;
            double d2 = delay + WIN / 2.0;
            if (d2 >= WIN) d2 -= WIN;
            float y = tap(d1) * tri(d1) + tap(d2) * tri(d2);
            delay += 1.0 - ratio;
            while (delay >= WIN) delay -= WIN;
            while (delay < 0) delay += WIN;
            wp = (wp + 1) & MASK;
            return y;
        }

        private float tap(double d) {
            double p = wp - d;
            int i0 = (int) Math.floor(p);
            double fr = p - i0;
            i0 &= MASK;
            int i1 = (i0 + 1) & MASK;
            return (float) (buf[i0] * (1.0 - fr) + buf[i1] * fr);
        }

        private float tri(double d) {
            return (float) (1.0 - Math.abs(2.0 * d / WIN - 1.0));
        }
    }

    // ---------- Selbst hören ----------

    private static final BlockingQueue<byte[]> QUEUE = new ArrayBlockingQueue<>(12);
    private static Thread monitorThread;

    private static void monitor(short[] frame) {
        byte[] b = new byte[frame.length * 2];
        for (int i = 0; i < frame.length; i++) {
            b[2 * i] = (byte) frame[i];
            b[2 * i + 1] = (byte) (frame[i] >> 8);
        }
        QUEUE.offer(b); // bei vollem Puffer einfach weglassen
        synchronized (QUEUE) {
            if (monitorThread == null || !monitorThread.isAlive()) {
                monitorThread = new Thread(VoiceChanger::monitorLoop, "voicechanger-monitor");
                monitorThread.setDaemon(true);
                monitorThread.start();
            }
        }
    }

    private static void monitorLoop() {
        SourceDataLine line = null;
        try {
            while (true) {
                byte[] b = QUEUE.poll(1500, TimeUnit.MILLISECONDS);
                if (b == null) { // länger still: Ausgabe freigeben
                    if (line != null) {
                        line.drain();
                        line.close();
                        line = null;
                    }
                    if (getPreset() == 0 || !Config.INSTANCE.voiceSelfHear) return;
                    continue;
                }
                if (line == null) {
                    AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
                    line = AudioSystem.getSourceDataLine(fmt);
                    line.open(fmt, 7680);
                    line.start();
                }
                if (line.available() >= b.length) line.write(b, 0, b.length);
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Selbst hören (Voice Changer) nicht möglich", e);
        } finally {
            if (line != null) line.close();
        }
    }
}
