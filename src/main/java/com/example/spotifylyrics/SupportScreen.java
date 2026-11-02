package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Support-Fenster: Idee, Frage oder Fehler schreiben. Im Admin-Modus zusätzlich das Postfach. */
public class SupportScreen extends Screen {
    private static final int HEAD = 28;
    private static int cat = 0;
    private static String msg = "";
    private static int msgCol = 0xFF8B92A5;
    private static boolean inbox = false;
    private static int scroll = 0;
    private int px0, px1, py0, py1;
    private TextFieldWidget field;

    public SupportScreen() {
        super(Text.literal("Support"));
    }

    private void btn(int x, int y, int w, int h, Runnable r) {
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> r.run()).dimensions(x, y, w, h).build());
    }

    @Override
    protected void init() {
        int pw = Math.min(420, this.width - 16), ph = Math.min(260, this.height - 8);
        px0 = (this.width - pw) / 2;
        px1 = px0 + pw;
        py0 = Math.max(4, (this.height - ph) / 2);
        py1 = py0 + ph;
        MinecraftClient mc = MinecraftClient.getInstance();
        btn(px1 - 10 - 72, py0 + 5, 72, 18, () -> mc.setScreen(new HubScreen()));
        if (Skins.admin()) btn(px1 - 10 - 72 - 6 - 96, py0 + 5, 96, 18, () -> {
            inbox = !inbox;
            scroll = 0;
            mc.setScreen(new SupportScreen());
        });
        if (inbox && Skins.admin()) {
            btn(px1 - 10 - 40, py1 - 28, 40, 18, () -> scroll++);
            btn(px1 - 10 - 86, py1 - 28, 40, 18, () -> scroll = Math.max(0, scroll - 1));
            return;
        }
        int cw = (pw - 20 - 12) / 3;
        for (int i = 0; i < 3; i++) {
            final int k = i;
            btn(px0 + 10 + i * (cw + 6), py0 + HEAD + 24, cw, 20, () -> cat = k);
        }
        field = new TextFieldWidget(mc.textRenderer, px0 + 12, py0 + HEAD + 70, pw - 24, 20, Text.literal(""));
        field.setMaxLength(300);
        field.setPlaceholder(Text.literal(Lang.t("Deine Nachricht …", "Your message …")));
        addDrawableChild(field);
        setInitialFocus(field);
        btn((px0 + px1) / 2 - 70, py0 + HEAD + 100, 140, 22, () -> {
            if (field == null) return;
            msg = Support.send(cat, field.getText());
            msgCol = msg.startsWith(Lang.t("Gesendet", "Sent")) ? 0xFF4ADE80 : 0xFFFFC23D;
            if (msgCol == 0xFF4ADE80 || msg.startsWith(Lang.t("Gespeichert", "Saved"))) field.setText("");
        });
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        Ui.panel(ctx, this.width, this.height, px0, py0, px1, py1, HEAD, "Support");
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        boolean showInbox = inbox && Skins.admin();
        Ui.txt(ctx, tr, showInbox ? Lang.t("Support-Postfach", "Support inbox") : "Support", px0 + 10, py0 + 6, Ui.TEXT);
        Ui.txt(ctx, tr, showInbox ? Lang.t("nur für Admin", "admin only") : Lang.t("Ideen, Fragen, Fehler", "Ideas, questions, bugs"), px0 + 10, py0 + 17, 0xFF8B92A5);
        Ui.btn(ctx, tr, px1 - 82, py0 + 5, px1 - 10, py0 + 23, "‹ Menü", Ui.in(mouseX, mouseY, px1 - 82, py0 + 5, px1 - 10, py0 + 23), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
        if (Skins.admin()) {
            int x1 = px1 - 88, x0 = x1 - 96;
            Ui.btn(ctx, tr, x0, py0 + 5, x1, py0 + 23, showInbox ? Lang.t("✎ Schreiben", "✎ Write") : Lang.t("Postfach", "Inbox"), Ui.in(mouseX, mouseY, x0, py0 + 5, x1, py0 + 23),
                0xFF5A3A00, 0xFF7A5200, 0xFFFFD700);
        }
        if (showInbox) {
            List<String> l = Config.INSTANCE.supportMsgs;
            int y = py0 + HEAD + 8, maxLines = (py1 - 36 - y) / 11;
            if (!Support.ready()) {
                Ui.txt(ctx, tr, Lang.t("Postfach noch nicht verbunden (support.txt fehlt).", "Inbox not connected yet (support.txt missing)."), px0 + 12, y, 0xFFFFC23D);
                y += 12;
            } else {
                Ui.txt(ctx, tr, Lang.t("Alle Nachrichten kommen in deinem Discord-Kanal an.", "All messages arrive in your Discord channel."), px0 + 12, y, 0xFF4ADE80);
                y += 12;
            }
            Ui.txt(ctx, tr, Lang.t("Von diesem PC gesendet:", "Sent from this PC:"), px0 + 12, y, 0xFF8B92A5);
            y += 12;
            int shown = 0;
            for (int i = l.size() - 1 - scroll; i >= 0 && shown < maxLines - 3; i--) {
                String[] p = l.get(i).split("\\|", 4);
                if (p.length < 4) continue;
                int col = p[1].equals("Idee") ? 0xFF4ADE80 : p[1].equals("Frage") ? 0xFF60A5FA : 0xFFFF6B6B;
                String head = p[0] + " · " + p[1] + " · " + p[2] + ": ";
                Ui.txt(ctx, tr, head, px0 + 12, y, col);
                Ui.txt(ctx, tr, tr.trimToWidth(p[3], px1 - px0 - 24 - tr.getWidth(head)), px0 + 12 + tr.getWidth(head), y, Ui.TEXT);
                y += 11;
                shown++;
            }
            if (l.isEmpty()) Ui.txt(ctx, tr, Lang.t("Noch keine Nachrichten.", "No messages yet."), px0 + 12, y, 0xFF6F7380);
            Ui.btn(ctx, tr, px1 - 96, py1 - 28, px1 - 56, py1 - 10, "▲", Ui.in(mouseX, mouseY, px1 - 96, py1 - 28, px1 - 56, py1 - 10), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
            Ui.btn(ctx, tr, px1 - 50, py1 - 28, px1 - 10, py1 - 10, "▼", Ui.in(mouseX, mouseY, px1 - 50, py1 - 28, px1 - 10, py1 - 10), 0xFF1A1E27, 0xFF252B38, 0xFFD6D9E0);
            return;
        }
        Ui.txt(ctx, tr, Lang.t("Worum geht's?", "What is it about?"), px0 + 12, py0 + HEAD + 12, 0xFF8B92A5);
        int cw = (px1 - px0 - 20 - 12) / 3;
        String[] names = {Lang.t("Idee", "Idea"), Lang.t("Frage", "Question"), Lang.t("Fehler", "Bug")};
        int[] cols = {0xFF4ADE80, 0xFF60A5FA, 0xFFFF6B6B};
        for (int i = 0; i < 3; i++) {
            int x = px0 + 10 + i * (cw + 6), y = py0 + HEAD + 24;
            boolean s = cat == i;
            Ui.btn(ctx, tr, x, y, x + cw, y + 20, names[i], Ui.in(mouseX, mouseY, x, y, x + cw, y + 20), s ? Ui.mix(0xFF0B0D12, cols[i], 0.35f) : 0xFF1A1E27,
                s ? Ui.mix(0xFF0B0D12, cols[i], 0.45f) : 0xFF252B38, s ? cols[i] : 0xFFD6D9E0);
        }
        Ui.txt(ctx, tr, Lang.t("Nachricht:", "Message:"), px0 + 12, py0 + HEAD + 58, 0xFF8B92A5);
        int bx = (px0 + px1) / 2 - 70, by = py0 + HEAD + 100;
        Ui.btn(ctx, tr, bx, by, bx + 140, by + 22, Lang.t("Senden", "Send"), Ui.in(mouseX, mouseY, bx, by, bx + 140, by + 22), Ui.ACCENT_DIM, Ui.ACCENT_DIM_HOV, 0xFFFFFFFF);
        if (!msg.isEmpty()) Ui.txt(ctx, tr, msg, (px0 + px1 - tr.getWidth(msg)) / 2, by + 30, msgCol);
        String note = Lang.t("Wird mit deinem Minecraft-Namen an den Entwickler geschickt.", "Sent to the developer with your Minecraft name.");
        Ui.txt(ctx, tr, tr.trimToWidth(note, px1 - px0 - 24), px0 + 12, py1 - 16, 0xFF6F7380);
    }
}
