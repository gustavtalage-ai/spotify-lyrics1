package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/** Werbung im Hauptmenü: Saison-Kiste, neue Skins, Coins – Klick öffnet die Kisten. */
public final class TitleAd {
    private TitleAd() {
    }

    private static final int W = 196, H = 60;
    private static boolean scanned;

    static boolean isMenu(Screen s) {
        if (s instanceof TitleScreen) return true;
        String n = s.getClass().getSimpleName().toLowerCase();
        return n.contains("title") || n.contains("mainmenu");
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!isMenu(screen)) return;
            if (!scanned) {
                scanned = true;
                try {
                    WeaponSkins.scan(client.getResourcePackDir().resolve(Designer.PACK_DIR));
                } catch (Throwable ignored) {
                }
            }
            int x = 8, y = h - H - 18;
            Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(""), b -> {
                int ad = current();
                if (ad == 0) {
                    int k = Skins.currentSeasonCase();
                    if (k >= 0) PackScreen.select(k);
                }
                client.setScreen(new PackScreen());
            }).dimensions(x, y, W, H).build());
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> draw(ctx, x, y, mx, my));
        });
    }

    /** Welche Werbung gerade läuft: 0 Saison, 1 Skins, 2 Coins. */
    private static int current() {
        List<Integer> ads = new ArrayList<>();
        if (Skins.currentSeasonCase() >= 0) ads.add(0);
        ads.add(1);
        if (Skins.pending() > 0) ads.add(2);
        return ads.get((int) ((System.currentTimeMillis() / 5000) % ads.size()));
    }

    private static void draw(DrawContext ctx, int x, int y, int mx, int my) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            TextRenderer tr = mc.textRenderer;
            long now = System.currentTimeMillis();
            int ad = current();
            int season = Skins.currentSeasonCase();
            int col = ad == 0 && season >= 0 ? Skins.CASE_COL[season] : ad == 2 ? 0xFF4ADE80 : 0xFFFFC23D;
            boolean hov = Ui.in(mx, my, x, y, x + W, y + H);
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 300.0);
            ctx.fill(x - 2, y - 2, x + W + 2, y + H + 2, (int) ((0.25f + 0.35f * pulse) * 255) << 24 | (col & 0xFFFFFF));
            Ui.card(ctx, x, y, x + W, y + H, col, hov ? 0xF0181C26 : 0xE80E1016);
            ctx.fillGradient(x + 1, y + 1, x + W - 1, y + H - 1, 0x05000000, (0x40 << 24) | (col & 0xFFFFFF));
            // Skin, der sich alle 1,2 s ändert
            int b, t = (int) ((now / 1200) % Skins.TYPES.length);
            if (ad == 0 && season >= 0) {
                List<Integer> pool = new ArrayList<>();
                for (int i = 0; i < Skins.bundles(); i++) if (Skins.inCase(season, i)) pool.add(i);
                b = pool.isEmpty() ? 0 : pool.get((int) ((now / 1200) % pool.size()));
            } else b = Skins.ORDER[(int) ((now / 1200) % Skins.bundles())];
            float sc = 2.6f + 0.15f * (float) Math.sin(now / 250.0);
            CollectionScreen.drawBig(ctx, CollectionScreen.stackFor(t, b), x + 8, y + (H - (int) (16 * sc)) / 2, sc);
            String title, sub;
            if (ad == 0 && season >= 0) {
                title = Skins.CASES[season].toUpperCase() + "!";
                sub = Lang.t("Nur jetzt: ", "Only now: ") + WeaponSkins.BUNDLES[b];
            } else if (ad == 2) {
                title = "+" + Skins.pending() + " Coins";
                sub = Lang.t("aus Spielzeit – jetzt umwandeln", "from playtime – convert now");
            } else {
                title = Skins.bundles() + Lang.t(" SKIN-KOLLEKTIONEN", " SKIN COLLECTIONS");
                sub = WeaponSkins.BUNDLES[b] + " · " + Skins.tierName(b);
            }
            int tx = x + 54;
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(tx, y + 9);
            float ts = Math.min(1.25f, (W - 60) / (float) Math.max(1, tr.getWidth(title)));
            m.scale(ts, ts);
            ctx.drawTextWithShadow(tr, title, 0, 0, col);
            m.popMatrix();
            Ui.txt(ctx, tr, tr.trimToWidth(sub, W - 60), tx, y + 26, 0xFFD6D9E0);
            Ui.txt(ctx, tr, hov ? Lang.t("▶ Kisten öffnen", "▶ Open cases") : Lang.t("Klick für Kisten", "Click for cases"), tx, y + 42, hov ? 0xFFFFFFFF : 0xFF8B92A5);
        } catch (Throwable ignored) {
        }
    }
}
