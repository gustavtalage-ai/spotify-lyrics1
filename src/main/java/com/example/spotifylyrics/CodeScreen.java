package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Code eingeben – der Admin-Code schaltet alle Skins frei und gibt unendlich Kisten. */
public class CodeScreen extends Screen {
    private static final int HEAD = 28;
    private int px0, px1, py0, py1;
    private TextFieldWidget field;
    private static String msg = "";
    private static int msgColor = 0xFF8B92A5;

    public CodeScreen() {
        super(Text.literal("Code"));
    }

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    private void redeem() {
        if (field == null) return;
        if (Skins.redeem(field.getText())) {
            msg = Lang.t("Admin an: unendlich Coins (an/aus in der Sammlung)", "Admin on: unlimited coins (toggle in collection)");
            msgColor = 0xFF4ADE80;
            try {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(SoundEvent.of(Identifier.of("spotifylyrics", "pack.reveal")), 1f, 1f));
            } catch (Throwable ignored) {
            }
        } else {
            msg = Lang.t("Falscher Code", "Wrong code");
            msgColor = 0xFFFF5A5A;
        }
        field.setText("");
    }

    @Override
    protected void init() {
        int pw = Math.min(320, this.width - 16), ph = 150;
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 86, py0 + 5, 86, 18, () -> {
            msg = "";
            mc.setScreen(new CollectionScreen());
        });
        field = new TextFieldWidget(mc.textRenderer, px0 + 16, py0 + HEAD + 30, pw - 32, 20, Text.literal(""));
        field.setMaxLength(40);
        field.setPlaceholder(Text.literal(Lang.t("Code hier eingeben …", "Enter code …")));
        addDrawableChild(field);
        setInitialFocus(field);
        btn((px0 + px1) / 2 - 60, py0 + HEAD + 60, 120, 22, this::redeem);
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, Lang.t("Sammlung / Code", "Collection / Code"));
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Ui.txt(ctx, tr, Lang.t("Code einlösen", "Redeem code"), px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, Skins.admin() ? "§6ADMIN" : Lang.t("Skins & Kisten", "Skins & cases"), px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 96, py0 + 5, px1 - 10, py0 + 23, Lang.t("‹ Sammlung", "‹ Collection"), Ui.in(mouseX, mouseY, px1 - 96, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        Ui.txt(ctx, tr, Lang.t("Code:", "Code:"), px0 + 16, py0 + HEAD + 18, 0xFF8B92A5);
        int bx = (px0 + px1) / 2 - 60, by = py0 + HEAD + 60;
        Ui.btn(ctx, tr, bx, by, bx + 120, by + 22, Lang.t("Einlösen", "Redeem"), Ui.in(mouseX, mouseY, bx, by, bx + 120, by + 22), 0xFF8A5A00, 0xFFB07A10, 0xFFFFFFFF);
        if (!msg.isEmpty()) Ui.txt(ctx, tr, msg, (px0 + px1 - tr.getWidth(msg)) / 2, by + 30, msgColor);
    }
}
