# Spotify Lyrics (Fabric, Minecraft 1.21.10)

Zeigt oben im Bild die Lyrics des Spotify-Liedes, das gerade läuft. Der Kasten ist durchsichtig.
Kommt in der aktuellen Zeile das Wort "Musik" vor, wird der Kasten lila umrandet.

## Was du brauchst
- Minecraft 1.21.10 mit Fabric Loader und Fabric API
- Java 21
- Ein Spotify-Premium-Konto (Spotify verlangt das seit 2026 für selbst angelegte Entwickler-Apps)
- Spotify muss auf deinem PC oder Handy laufen, mit demselben Konto

## 1. Spotify-App anlegen (einmalig, kostenlos)
1. https://developer.spotify.com/dashboard öffnen, einloggen, "Create app".
2. Bei "Redirect URI" genau das eintragen: `http://127.0.0.1:8888/callback`
   (nicht localhost, nicht https, kein Schrägstrich am Ende)
3. Bei "Which API/SDKs" den Haken bei "Web API" setzen, speichern.
4. In den Einstellungen der App die Client ID kopieren (das Client Secret brauchst du nicht).

## 2. Mod bauen
1. https://fabricmc.net/develop/template öffnen und eintragen:
   - Minecraft-Version: 1.21.10
   - Mod ID: `spotifylyrics`
   - Package name: `com.example.spotifylyrics`
   - Fabric API: an
   - "Split client and common sources": falls vorhanden, aus
2. Das Projekt herunterladen und entpacken.
3. Den Ordner `src` aus dieser ZIP über den `src`-Ordner des Projekts kopieren (Dateien überschreiben).
4. Im Projekt die Beispieldateien löschen: `ExampleMod.java` und den Ordner `mixin` (und `ExampleModClient.java`, falls vorhanden) sowie die Dateien `spotifylyrics.mixins.json` und `spotifylyrics.client.mixins.json` in `src/main/resources`.
5. Im Projektordner ausführen: `gradlew build` (Windows: `gradlew.bat build`)
6. Die Datei `build/libs/spotifylyrics-1.0.0.jar` (die ohne `-sources`) in deinen `mods`-Ordner legen.

## 3. Im Spiel einrichten
```
/lyrics clientid DEINE_CLIENT_ID
/lyrics login
```
Auf den blauen Link im Chat klicken, bei Spotify "Zustimmen" drücken. Danach läuft alles automatisch.

## Weitere Befehle
- `/lyrics toggle` Leiste an/aus
- `/lyrics offset 300` Lyrics kommen 300 ms früher (negativ = später), falls sie nicht genau passen
- `/lyrics keyword Wort` anderes Wort für den lila Rahmen (Standard: musik)
- `/lyrics logout` Spotify-Verbindung trennen

## Hinweise
- Die Lyrics kommen vom freien Dienst lrclib.net. Gibt es für ein Lied keine synchronisierten Lyrics, steht "Keine Lyrics gefunden" da.
- Die Leiste liegt oben in der Mitte und kann sich mit Boss-Leisten überlagern.
- Die Einstellungen stehen in `config/spotifylyrics.json`.
