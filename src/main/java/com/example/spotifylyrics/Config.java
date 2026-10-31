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
    /** Einstellungen: Akzentfarbe, Hintergrund, Sprache (0 Deutsch, 1 English), Menü-Taste (GLFW, 75 = K). */
    public int uiAccent = 0;
    public int uiBg = 0;
    public int lang = 0;
    public int menuKey = 75;
    /** Skin-Menü: Modell schmal (Alex) statt klassisch. */
    public boolean skinSlim = false;
    /** Pop-Chams: Umriss wenn ein Totem poppt (Farbe aus PopChams.COLORS, Dauer in Sekunden). */
    public boolean popChams = false;
    public int popColor = 0;
    public int popTime = 3;
    public boolean popRise = true;
    public boolean popSelf = false;
    /** Anti-Koordinaten: keine zufällige Textur-Drehung / kein Versatz (verrät sonst die Position). */
    public boolean antiRotation = false;
    public boolean antiOffset = true;
    /** Reichweiten-Kreise um andere Spieler. */
    public boolean rcOn = false;
    public int rcRange = 24;
    public int rcStyle = 0;
    public boolean rcFriends = false;
    /** Kampf-Effekte (eigene Partikel). */
    public boolean fxOn = false;
    public boolean fxMace = true, fxDeath = true, fxPearl = true, fxTotem = true, fxWind = true;
    /** Zweiter Chat: Nachrichten mit diesen Wörtern landen in einem eigenen Fenster. */
    public boolean chat2On = false;
    public boolean chat2Hide = true;
    public boolean chat2Pm = true;
    public int chat2Corner = 0;
    public int chat2Lines = 8;
    public java.util.List<String> chat2Words = new java.util.ArrayList<>();
    /** Bauen: Kreis-/Kugel-Vorlage (Form siehe BuildHelper.SHAPES, Radius in Blöcken). */
    public boolean shapeOn = false;
    public int shapeType = 0;
    public int shapeR = 5;
    /** Bauen: Raster am Boden (Linien alle n Blöcke, Größe um dich, Chunk-Grenzen). */
    public boolean gridOn = false;
    public int gridStep = 1;
    public int gridRange = 12;
    public boolean gridChunks = false;
    /** Bauen: Lineal zwischen zwei Blöcken. */
    public boolean rulerOn = false;
    /** Bauen: Symmetrie-Linie (0 Nord-Süd, 1 Ost-West, 2 beide), Mitte auf Block oder Kante. */
    public boolean symOn = false;
    public int symAxis = 0;
    public boolean symEdge = false;
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
    /** Fadenkreuz: Stil, Größe, Lücke, Dicke, Farbe. */
    public int crossStyle = 0;
    public int crossSize = 5;
    public int crossGap = 2;
    public int crossThick = 1;
    public int crossColor = 4;
    /** Pixel-Fadenkreuz: an (sonst einfacher Stil), Feld (9 bis 15), Pixelgröße, Schatten, Farben je Pixel (15x15, 0 = leer). */
    public boolean crossPixel = true;
    public int crossN = 9;
    public int crossScale = 1;
    public boolean crossShadow = true;
    public int[] crossPix = crossDefault();
    /** Gegner-Erkennung: Fadenkreuz färbt sich um, wenn ein Gegner im Visier ist. */
    public boolean crossEnemy = true;
    public int crossEnemyColor = 0xFFFF3B4E;

    static int[] crossDefault() {
        int[] p = new int[225];
        int w = 0xFFFFFFFF;
        for (int k = 2; k <= 4; k++) {
            p[7 * 15 + 7 - k] = w;
            p[7 * 15 + 7 + k] = w;
            p[(7 - k) * 15 + 7] = w;
            p[(7 + k) * 15 + 7] = w;
        }
        p[7 * 15 + 7] = w;
        return p;
    }
    /** Lyrics-Anzeige: Stil (0 Kasten, 1 Leiste, 2 Karaoke), Textart (0 Sätze, 1 Wort für Wort, 2 ein Wort), Farbe, Hintergrund, Größe in %. */
    public int lyStyle = 0;
    public int lyUnit = 0;
    public int lyColor = 0;
    public int lyBg = 0;
    public int lyScale = 100;
    public boolean lyTitle = true;
    public boolean lyProgress = true;
    public boolean lyPrev = true;
    public int lyNext = 2;
    /** Lyrics-Stelle je Stil: x und y in Promille (-1 = Standard). */
    public int[] lyPos = lyPosInit();

    private static int[] lyPosInit() {
        int[] a = new int[6];
        java.util.Arrays.fill(a, -1);
        return a;
    }

    /** Kampf-Timer: Sekunden nach dem letzten Kampf. */
    public int combatSecs = 15;
    /** Letzter Todespunkt. */
    public boolean hasDeath = false;
    public double deathX, deathY, deathZ;
    public String deathDim = "";

    private static int[] posInit() {
        int[] a = new int[64];
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

    /** Makros: Schritt (type 0 = Taste tippen, 1 = Taste halten, 2 = warten, 3 = Chat/Befehl; arg = Aktion bzw. Millisekunden). */
    public static class MacroStep {
        public int type;
        public int arg;
        public int arg2 = 500;
        public String text = "";
    }

    /** Ein Makro: Name, Starttaste (GLFW-Code, -1 = keine), Ablauf (0 = einmal, 1 = Anzahl, 2 = endlos) und Schritte. */
    public static class Macro {
        public String name = "Makro";
        public int key = -1;
        public int mode = 0;
        public int count = 5;
        public java.util.List<MacroStep> steps = new java.util.ArrayList<>();
    }

    public java.util.List<Macro> macros = new java.util.ArrayList<>();

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
                    if (c.hudPos == null) {
                        c.hudPos = posInit();
                    } else if (c.hudPos.length < 64) {
                        int[] np = posInit();
                        System.arraycopy(c.hudPos, 0, np, 0, c.hudPos.length);
                        c.hudPos = np;
                    }
                    if (c.deathDim == null) c.deathDim = "";
                    if (c.lyPos == null || c.lyPos.length < 6) c.lyPos = lyPosInit();
                    if (c.macros == null) c.macros = new java.util.ArrayList<>();
                    if (c.crossPix == null || c.crossPix.length != 225) c.crossPix = crossDefault();
                    if (c.pvpVer < 2) {
                        c.pvpMods &= ~((1 << 14) | (1 << 15));
                        c.pvpVer = 2;
                    }
                    if (c.pvpVer < 4) {
                        c.circleColor = 0;           // alte Farben "immer grün/rot" gibt es nicht mehr
                    }
                    if (c.pvpVer < 3) {
                        c.pvpMods &= ~(1 << 18);
                        c.pvpVer = 3;
                    }
                    if (c.pvpVer < 4) c.pvpVer = 4;
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
