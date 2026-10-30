package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für die Lyrics-Anzeige (Stil, Textart, Farbe, Hintergrund, Größe, Stelle), mit Vorschau. */
public class LyricsScreen extends ItemScreen {
    private static int page = 0;

    public LyricsScreen() {
        super("Lyrics", "Anzeige, Stil und Farben");
    }

    @Override
    protected ItemScreen fresh() {
        return new LyricsScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ Menü";
    }

    private Cell tab(String label, int idx) {
        return idx == page ? S(label) : G(label, () -> {
            page = idx;
            again();
        });
    }

    private static String pick(String[] a, int i) {
        return a[Math.floorMod(i, a.length)];
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(tab("Stil", 0), tab("Farben", 1), tab("Mehr", 2));
        gap(4);
        if (page == 0) {
            row(T("Lyrics anzeigen", c.enabled, () -> {
                c.enabled = !c.enabled;
                c.save();
                again();
            }));
            row(G("Stil: " + pick(LyricsHud.STYLES, c.lyStyle) + " ›", () -> {
                c.lyStyle = (c.lyStyle + 1) % LyricsHud.STYLES.length;
                c.save();
                again();
            }));
            row(G("Text: " + pick(LyricsHud.UNITS, c.lyUnit) + " ›", () -> {
                c.lyUnit = (c.lyUnit + 1) % LyricsHud.UNITS.length;
                c.save();
                again();
            }));
            stepper("Größe: " + c.lyScale + " %", () -> {
                c.lyScale = Math.max(60, c.lyScale - 10);
                c.save();
                again();
            }, () -> {
                c.lyScale = Math.min(200, c.lyScale + 10);
                c.save();
                again();
            });
            row(B("Anzeige verschieben ›", () -> MinecraftClient.getInstance().setScreen(new HudEditScreen(new LyricsScreen()))));
        } else if (page == 1) {
            row(G("Farbe: " + pick(LyricsHud.COLORS, c.lyColor) + " ›", () -> {
                c.lyColor = (c.lyColor + 1) % LyricsHud.COLORS.length;
                c.save();
                again();
            }));
            row(G("Hintergrund: " + pick(LyricsHud.BGS, c.lyBg) + " ›", () -> {
                c.lyBg = (c.lyBg + 1) % LyricsHud.BGS.length;
                c.save();
                again();
            }));
            row(T("Titel zeigen (nur Kasten)", c.lyTitle, () -> {
                c.lyTitle = !c.lyTitle;
                c.save();
                again();
            }));
            row(T("Fortschrittsbalken", c.lyProgress, () -> {
                c.lyProgress = !c.lyProgress;
                c.save();
                again();
            }));
        } else {
            row(T("Zeile davor zeigen", c.lyPrev, () -> {
                c.lyPrev = !c.lyPrev;
                c.save();
                again();
            }));
            stepper("Zeilen danach: " + c.lyNext, () -> {
                c.lyNext = Math.max(0, c.lyNext - 1);
                c.save();
                again();
            }, () -> {
                c.lyNext = Math.min(2, c.lyNext + 1);
                c.save();
                again();
            });
            stepper("Takt-Versatz: " + c.offsetMs + " ms", () -> {
                c.offsetMs = Math.max(-1500, c.offsetMs - 50);
                c.save();
                again();
            }, () -> {
                c.offsetMs = Math.min(1500, c.offsetMs + 50);
                c.save();
                again();
            });
            text("Plus = Lyrics kommen früher, minus = später.", 0xFF8B92A5);
            row(R("Alles zurücksetzen", () -> {
                c.lyStyle = 0;
                c.lyUnit = 0;
                c.lyColor = 0;
                c.lyBg = 0;
                c.lyScale = 100;
                c.lyTitle = true;
                c.lyProgress = true;
                c.lyPrev = true;
                c.lyNext = 2;
                c.offsetMs = 0;
                LyricsHud.resetPos();
                c.save();
                again();
            }));
        }
        gap(4);
        gap(76);
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int bx0 = px0 + 10, bx1 = px1 - 10, by1 = py1 - 8, by0 = by1 - 72;
        Ui.card(ctx, bx0, by0, bx1, by1, Ui.BORDER, 0xFF12151B);
        LyricsHud.drawPreview(ctx, (bx0 + bx1) / 2, (by0 + by1) / 2, bx1 - bx0 - 12, by1 - by0 - 10);
    }
}
