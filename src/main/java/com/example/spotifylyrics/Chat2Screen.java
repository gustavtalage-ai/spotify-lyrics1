package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Regeln für den zweiten Chat – wie in SecondChat: Typ + Text, passende Nachrichten gehen in den zweiten Chat. */
public class Chat2Screen extends ItemScreen {
    private static int newType = 4;   // Enthält
    private static String draft = "";
    private int fieldRow = -1;
    private TextFieldWidget field;

    public Chat2Screen() {
        super(Lang.t("Zweiter Chat", "Second chat"), "SecondChat");
    }

    @Override
    protected ItemScreen fresh() {
        if (field != null) draft = field.getText();
        return new Chat2Screen();
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
    protected int panelWidth() {
        return 360;
    }

    private static String typeName(String t) {
        for (int i = 0; i < Chat2.TYPES.length; i++) if (Chat2.TYPES[i].equals(t)) return Lang.t(Chat2.TYPE_DE[i], Chat2.TYPE_EN[i]);
        return t;
    }

    private void add() {
        String v = field != null ? field.getText() : draft;
        if (v.isEmpty()) return;
        Config.INSTANCE.chat2Rules.add(Chat2.TYPES[newType] + "|" + v);
        Config.INSTANCE.save();
        draft = "";
        field = null;
        again();
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        row(T(Lang.t("Zweiter Chat an", "Second chat on"), c.chat2On, () -> {
            c.chat2On = !c.chat2On;
            c.save();
            again();
        }));
        gap(4);

        sec(Lang.t("Regeln", "Rules"));
        List<String> rules = c.chat2Rules;
        if (rules.isEmpty()) text(Lang.t("Noch keine Regeln.", "No rules yet."), 0xFF8B92A5);
        for (int i = 0; i < rules.size(); i++) {
            final int k = i;
            String r = rules.get(i);
            row(G(typeName(Chat2.type(r)), () -> {
                int t = 0;
                for (int j = 0; j < Chat2.TYPES.length; j++) if (Chat2.TYPES[j].equals(Chat2.type(rules.get(k)))) t = j;
                rules.set(k, Chat2.TYPES[(t + 1) % Chat2.TYPES.length] + "|" + Chat2.value(rules.get(k)));
                c.save();
                again();
            }).w(2), X(Chat2.value(r)).w(3), R("✕", () -> {
                rules.remove(k);
                c.save();
                again();
            }));
        }
        gap(4);

        sec(Lang.t("Neue Regel", "New rule"));
        row(G(Lang.t(Chat2.TYPE_DE[newType], Chat2.TYPE_EN[newType]), () -> {
            newType = (newType + 1) % Chat2.TYPES.length;
            again();
        }).w(2), X("").w(3), B("+", this::add));
        fieldRow = lastRow();
        gap(4);
        row(G(Lang.t("Zweiten Chat leeren", "Clear second chat"), Chat2::clear));
    }

    @Override
    protected void init() {
        super.init();
        if (fieldRow < 0) return;
        int[] r = rect(fieldRow, 1);
        if (r[1] < py0 + HEAD || r[1] + r[3] > py1) return;
        field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Text.literal(""));
        field.setMaxLength(256);
        field.setText(draft);
        addDrawableChild(field);
    }

    public void render(net.minecraft.client.gui.DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (field != null) field.render(ctx, mouseX, mouseY, delta);
    }
}
