package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Einstellungen für das eigene Fadenkreuz, mit Vorschau. */
public class CrosshairScreen extends ItemScreen {
    public CrosshairScreen() {
        super("Fadenkreuz", "Eigenes Fadenkreuz mit Vorschau");
    }

    @Override
    protected ItemScreen fresh() {
        return new CrosshairScreen();
    }

    @Override
    protected Screen back() {
        return new PvpScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ PvP";
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec("Fadenkreuz");
        row(T("Eigenes Fadenkreuz", PvpHud.on(PvpHud.CROSS), () -> {
            PvpHud.flip(PvpHud.CROSS);
            again();
        }));
        row(G("Stil: " + PvpHud.CROSS_STYLES[Math.floorMod(c.crossStyle, PvpHud.CROSS_STYLES.length)] + " ›", () -> {
            c.crossStyle = (c.crossStyle + 1) % PvpHud.CROSS_STYLES.length;
            c.save();
            again();
        }));
        row(G("Farbe: " + PvpHud.CROSS_COLORS[Math.floorMod(c.crossColor, PvpHud.CROSS_COLORS.length)] + " ›", () -> {
            c.crossColor = (c.crossColor + 1) % PvpHud.CROSS_COLORS.length;
            c.save();
            again();
        }));
        stepper("Länge: " + c.crossSize, () -> {
            c.crossSize = Math.max(1, c.crossSize - 1);
            c.save();
            again();
        }, () -> {
            c.crossSize = Math.min(14, c.crossSize + 1);
            c.save();
            again();
        });
        stepper("Lücke: " + c.crossGap, () -> {
            c.crossGap = Math.max(0, c.crossGap - 1);
            c.save();
            again();
        }, () -> {
            c.crossGap = Math.min(10, c.crossGap + 1);
            c.save();
            again();
        });
        stepper("Dicke: " + c.crossThick, () -> {
            c.crossThick = Math.max(1, c.crossThick - 1);
            c.save();
            again();
        }, () -> {
            c.crossThick = Math.min(4, c.crossThick + 1);
            c.save();
            again();
        });
        gap(2);
        text("Dynamisch: rot, wenn ein Gegner im Visier ist,", 0xFF8B92A5);
        text("kurzes Aufleuchten bei jedem Treffer.", 0xFF8B92A5);
        gap(52);
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int bx0 = px0 + 10, bx1 = px1 - 10, by1 = py1 - 8, by0 = by1 - 44;
        Ui.card(ctx, bx0, by0, bx1, by1, Ui.BORDER, 0xFF12151B);
        PvpHud.drawCrosshair(ctx, MinecraftClient.getInstance(), (bx0 + bx1) / 2, (by0 + by1) / 2, true);
    }
}
