package com.example.spotifylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Einstellungen, gespeichert in .minecraft/config/spotifylyrics.json */
public class Config {
    public String clientId = "e99a8703feb94217839c96299a537b19";
    public String refreshToken = "";
    public boolean enabled = true;
    /** Kommt dieses Wort in der Zeile vor, wird sie lila umrandet. */
    public String keyword = "musik";
    /** Feinabstimmung: positiv = Lyrics kommen früher. */
    public int offsetMs = 0;
    /** Soundboard: Lautstärke pro Sound (Dateiname -> Faktor, 1.0 = 100 %). */
    public java.util.Map<String, Float> soundVolumes = new java.util.HashMap<>();
    /** Soundboard: Sound auch selbst hören. */
    public boolean listenSelf = true;

    /** Lyrics per Spracherkennung heraushören, wenn lrclib nichts findet. */
    public boolean transcribe = true;
    /** Voice Changer: gewählter Effekt (0 = aus) und ob du dich selbst hörst. */
    public int voicePreset = 0;
    public boolean voiceSelfHear = true;
    /** Spotify (PC-Ton) in den Voice Chat mischen, Aufnahmegerät ("" = automatisch) und Lautstärke. */
    public boolean spotifyShare = false;
    public String spotifyDevice = "";
    public float spotifyVolume = 1.0f;
    /** Spotify-Lautstärke in Prozent (zuletzt im Menü gestellt). */
    public int playerVolume = 50;
    /** Lichtshow am Bildschirmrand und Farbmodus. */
    public boolean lightshow = true;
    public int lightMode = 0;
    /** Noten-Symbol: Abstand von links und von unten (in GUI-Pixeln). */
    public int noteX = 20;
    public int noteY = 3;
    /** Platzhilfe: an/aus, Abstand (jeder n-te Block), Richtung (-1 = Blickrichtung beim Setzen). */
    public boolean placeHelper = false;
    public int placeSpacing = 3;
    public int placeDir = -1;
    /** Inventar-Filter: an/aus und Regeln (Item-ID -> Slot-Maske, Bit 0-8 Hotbar, 9-35 Rucksack). */
    public boolean filterOn = true;
    /** Streng: Alles ohne Regel wird sofort weggeworfen (nur Filter-Items bleiben im Inventar). */
    public boolean filterStrict = false;
    public java.util.Map<String, Long> filterRules = new java.util.LinkedHashMap<>();
    /** Hit-Assistent: nur schlagen, wenn die Leiste voll ist. Waffen als Bits: 0 Schwert, 1 Axt, 2 Mace, 3 Speer, 4 Dreizack. */
    public boolean hitAssist = false;
    public int hitAssistMin = 100;
    public int hitWeapons = 1;
    /** Zu früh geklickt: Schlag merken und bei voller Leiste ausführen. */
    public boolean hitQueue = true;

    /** PvP-Client: Module als Bits (siehe PvpHud.NAMES). */
    public int pvpMods = 0;
    /** Zoom (Taste Z): Sichtfeld beim Zoomen (30 bis 110). */
    public int zoomFov = 30;
    /** Freunde, die nicht geschlagen werden (kleingeschrieben). */
    public java.util.List<String> friends = new java.util.ArrayList<>();

    /** Version der PvP-Einstellungen (für Aufräumen alter Bits). */
    public int pvpVer = 0;
    /** Positionen der Anzeigen: je Anzeige x und y in Promille der Bildschirmgröße (-1 = Standard). */
    public int[] hudPos = posInit();
    /** Angriffs-Balken: Breite, Dicke, nur beim Laden zeigen. */
    public int barW = 24;
    public int barH = 1;
    public boolean barOnly = true;
    /** Reichweiten-Kreis: Radius in Zehntel-Blöcken, Farbe, nur mit Waffe, Punkte. */
    public int circleR = 33;
    public int circleColor = 0;
    public boolean circleWeapon = false;
    public int circleDots = 72;
    /** Kreis-Linie: Dicke in GUI-Pixeln. */
    public int circleThick = 1;
    public boolean circleMark = true;

    private static int[] posInit() {
        int[] a = new int[48];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    /** Auto-Klick: 0 = im Abstand, 1 = Taste gedrückt halten. */
    public int autoMode = 0;
    /** Auto-Klick: 0 = Angriff (links), 1 = Benutzen (rechts). */
    public int autoButton = 0;
    /** Auto-Klick: Abstand in Millisekunden (Modus "im Abstand"). */
    public int autoIntervalMs = 3000;
    /** Auto-Klick: Taste zum Starten/Stoppen (GLFW-Code, 74 = J). */
    public int autoKey = 74;

    /** Pack-Sets: bis zu 4 Plätze, je die Pack-IDs getrennt mit "|" (leer = frei). */
    public java.util.List<String> packSets = new java.util.ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("spotifylyrics.json");
    public static final Config INSTANCE = load();

    private static Config load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) {
                    if (c.clientId == null || c.clientId.isBlank()) c.clientId = "e99a8703feb94217839c96299a537b19";
                    if (c.hudPos == null || c.hudPos.length < 48) c.hudPos = posInit();
                    if (c.pvpVer < 2) {
                        c.pvpMods &= ~((1 << 14) | (1 << 15));
                        c.pvpVer = 2;
                    }
                    return c;
                }
            }
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Config konnte nicht gelesen werden", e);
        }
        return new Config();
    }

    public synchronized void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.warn("Config konnte nicht gespeichert werden", e);
        }
    }
}
