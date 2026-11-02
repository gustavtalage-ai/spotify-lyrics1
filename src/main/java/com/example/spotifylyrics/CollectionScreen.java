package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

/** Sammlung wie in Valorant: alle Waffen als Karten, oben die Packs. Klick auf eine Waffe = Skins und Finisher. */
public class CollectionScreen extends Screen {
    private static final int HEAD = 28;
    private int px0, px1, py0, py1, gx0, gy0, cw, ch;

    public CollectionScreen() {
        super(Text.literal("Sammlung"));
    }

    static void drawBig(DrawContext ctx, ItemStack s, int x, int y, float scale) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        ctx.drawItem(s, 0, 0);
        m.popMatrix();
    }

    static ItemStack stackFor(int type, int bundle) {
        return bundle < 0 ? new ItemStack(Skins.showItem(type)) : WeaponSkins.preview(Skins.showItem(type), bundle);
    }

    static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int pw = Math.min(460, this.width - 16), ph = Math.min(320, this.height - 8);
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 72, py0 + 5, 72, 18, () -> mc.setScreen(new PvpScreen()));
        btn(px1 - 10 - 72 - 6 - 56, py0 + 5, 56, 18, () -> mc.setScreen(new CodeScreen()));
        if (Skins.admin()) btn(px1 - 10 - 72 - 6 - 56 - 6 - 74, py0 + 5, 74, 18, () -> {
            Skins.toggleAdmin();
            mc.setScreen(new CollectionScreen());
        });
        btn(px1 - 10 - 110, py0 + HEAD + 8, 110, 26, () -> mc.setScreen(new PackScreen()));
        btn(px1 - 10 - 110 - 6 - 120, py0 + HEAD + 8, 120, 26, () -> {
            int n = Skins.convert();
            if (n > 0) try {
                mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("spotifylyrics", "coin")), 1f, 1f));
            } catch (Throwable ignored) {
            }
        });
        gx0 = px0 + 10;
        gy0 = py0 + HEAD + 44;
        cw = (pw - 20 - 2 * 8) / 3;
        ch = Math.min(100, (py1 - 34 - gy0 - 8) / 2);
        for (int i = 0; i < Skins.TYPES.length; i++) {
            final int t = i;
            int x = gx0 + (i % 3) * (cw + 8), y = gy0 + (i / 3) * (ch + 8);
            btn(x, y, cw, ch, () -> mc.setScreen(new WeaponSkinScreen(t)));
        }
        int fy = py1 - 28, fw = (pw - 20 - 8) / 3;
        btn(px0 + 10, fy, fw, 20, () -> {
            Config.INSTANCE.wsOn = !Config.INSTANCE.wsOn;
            Config.INSTANCE.save();
        });
        btn(px0 + 14 + fw, fy, fw, 20, () -> {
            Config.INSTANCE.kfOn = !Config.INSTANCE.kfOn;
            Config.INSTANCE.save();
        });
        btn(px0 + 18 + 2 * fw, fy, fw, 20, () -> mc.setScreen(new KillFxScreen()));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("PvP / Sammlung", "PvP / Collection"));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Config c = Config.INSTANCE;
        Ui.txt(ctx, tr, Lang.t("Sammlung", "Collection"), px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, Skins.totalOwned() + " / " + (Skins.bundles() * Skins.TYPES.length) + " Skins" + (Skins.adminOn() ? "  ·  §6ADMIN" : ""), px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 144, py0 + 5, px1 - 88, py0 + 23, Lang.t("Code", "Code"), Ui.in(mouseX, mouseY, px1 - 144, py0 + 5, px1 - 88, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFFFC23D);
        if (Skins.admin()) {
            int ax1 = px1 - 150, ax0 = ax1 - 74;
            Ui.btn(ctx, tr, ax0, py0 + 5, ax1, py0 + 23, Skins.adminOn() ? Lang.t("Admin: An", "Admin: On") : Lang.t("Admin: Aus", "Admin: Off"), Ui.in(mouseX, mouseY, ax0, py0 + 5, ax1, py0 + 23),
                Skins.adminOn() ? 0xFF5A3A00 : 0xFF1A1E27, Skins.adminOn() ? 0xFF7A5200 : 0xFF252B38, Skins.adminOn() ? 0xFFFFD700 : 0xFF8B92A5);
        }
        Ui.btn(ctx, tr, px1 - 10 - 72, py0 + 5, px1 - 10, py0 + 23, "‹ PvP", Ui.in(mouseX, mouseY, px1 - 82, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);

        // Coin-Leiste
        int by = py0 + HEAD + 8;
        Ui.card(ctx, px0 + 10, by, px1 - 10, by + 26, 0xFF3A2A10, 0xFF17140E);
        int pend = Skins.pending();
        Ui.txt(ctx, tr, Skins.coinsText() + " Coins", px0 + 18, by + 4, 0xFFFFC23D);
        Ui.txt(ctx, tr, Skins.adminOn() ? Lang.t("Admin: unendlich", "Admin: unlimited")
            : Lang.t("+1 Coin in ", "+1 coin in ") + time(Skins.msToNextCoin()), px0 + 18, by + 15, 0xFF8B92A5);
        int cx1 = px1 - 10 - 110 - 6, cx0 = cx1 - 120;
        Ui.btn(ctx, tr, cx0, by, cx1, by + 26, pend > 0 ? Lang.t("Umwandeln +", "Convert +") + pend : Lang.t("Nichts umzuwandeln", "Nothing to convert"),
            Ui.in(mouseX, mouseY, cx0, by, cx1, by + 26), pend > 0 ? 0xFF15301F : 0xFF1A1E27, pend > 0 ? 0xFF1F4A2C : 0xFF1A1E27, pend > 0 ? 0xFF4ADE80 : 0xFF6F7380);
        boolean hov = Ui.in(mouseX, mouseY, px1 - 120, by, px1 - 10, by + 26);
        Ui.btn(ctx, tr, px1 - 120, by, px1 - 10, by + 26, Lang.t("▶ Kisten", "▶ Cases"), hov, 0xFF8A5A00, 0xFFB07A10, 0xFFFFFFFF);

        // Waffen-Karten
        for (int i = 0; i < Skins.TYPES.length; i++) {
            int x = gx0 + (i % 3) * (cw + 8), y = gy0 + (i / 3) * (ch + 8);
            boolean h = Ui.in(mouseX, mouseY, x, y, x + cw, y + ch);
            int eq = Skins.equipped(i);
            int border = eq >= 0 ? 0xFF000000 | SkinGen.PAL[eq][2] : (h ? 0xFF3A4152 : Ui.BORDER);
            Ui.card(ctx, x, y, x + cw, y + ch, border, h ? Ui.CARD_HOV : Ui.CARD);
            if (eq >= 0) ctx.fillGradient(x + 1, y + 1, x + cw - 1, y + ch / 2, (0x30 << 24) | SkinGen.PAL[eq][1], 0);
            float sc = Math.min(3.5f, (ch - 34) / 16f);
            int iw = (int) (16 * sc);
            drawBig(ctx, stackFor(i, eq), x + (cw - iw) / 2, y + 6, sc);
            String name = Skins.TYPE_NAMES[i];
            Ui.txt(ctx, tr, name, x + 6, y + ch - 22, Ui.TEXT);
            String sk = eq >= 0 ? WeaponSkins.BUNDLES[eq] : Lang.t("Standard", "Default");
            Ui.txt(ctx, tr, tr.trimToWidth(sk, cw - 40), x + 6, y + ch - 11, eq >= 0 ? 0xFF000000 | SkinGen.PAL[eq][2] : 0xFF8B92A5);
            String cnt = Skins.ownedCount(i) + "/" + Skins.bundles();
            Ui.txt(ctx, tr, cnt, x + cw - 6 - tr.getWidth(cnt), y + ch - 11, 0xFF8B92A5);
        }

        int fy = py1 - 28, fw = (px1 - px0 - 20 - 8) / 3;
        Ui.toggle(ctx, tr, px0 + 10, fy, px0 + 10 + fw, fy + 20, Lang.t("Skins an", "Skins on"), c.wsOn, Ui.in(mouseX, mouseY, px0 + 10, fy, px0 + 10 + fw, fy + 20));
        Ui.toggle(ctx, tr, px0 + 14 + fw, fy, px0 + 14 + 2 * fw, fy + 20, Lang.t("Finisher an", "Finisher on"), c.kfOn, Ui.in(mouseX, mouseY, px0 + 14 + fw, fy, px0 + 14 + 2 * fw, fy + 20));
        Ui.btn(ctx, tr, px0 + 18 + 2 * fw, fy, px1 - 10, fy + 20, Lang.t("⚙ Einstellungen", "⚙ Settings"), Ui.in(mouseX, mouseY, px0 + 18 + 2 * fw, fy, px1 - 10, fy + 20), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
    }
}
