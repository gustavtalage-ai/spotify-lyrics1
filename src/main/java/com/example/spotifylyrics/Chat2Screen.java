package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Einstellungen für den zweiten Chat: Filter, Fenster-Position, Wörter hinzufügen. */
public class Chat2Screen extends ItemScreen {
    private static int page = 0;
    private int fieldRow = -1;
    private TextFieldWidget field;

    public Chat2Screen() {
        super(Lang.t("Zweiter Chat", "Second chat"), Lang.t("Bestimmte Nachrichten in eigenes Fenster", "Certain messages in their own window"));
    }

    @Override
    protected ItemScreen fresh() {
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
        return 340;
    }

    private void addWord() {
        if (field == null) return;
        String w = field.getText().trim();
        Config c = Config.INSTANCE;
        if (!w.isEmpty() && !c.chat2Words.contains(w)) {
            c.chat2Words.add(w);
            c.save();
        }
        again();
    }

    @Override
    protected void build() {
        Config c = Config.INSTANCE;
        sec(Lang.t("Zweiter Chat", "Second chat"));
        row(T(Lang.t("Zweiter Chat an", "Second chat on"), c.chat2On, () -> {
            c.chat2On = !c.chat2On;
            c.save();
            again();
        }));
        row(T(Lang.t("Aus normalem Chat entfernen", "Remove from main chat"), c.chat2Hide, () -> {
            c.chat2Hide = !c.chat2Hide;
            c.save();
            again();
        }));
        row(G(Lang.t("Ort: ", "Place: ") + Chat2.CORNERS[Math.floorMod(c.chat2Corner, Chat2.CORNERS.length)] + " ›", () -> {
            c.chat2Corner = (c.chat2Corner + 1) % Chat2.CORNERS.length;
            c.save();
            again();
        }));
        stepper(Lang.t("Zeilen: ", "Lines: ") + c.chat2Lines, () -> {
            c.chat2Lines = Math.max(3, c.chat2Lines - 1);
            c.save();
            again();
        }, () -> {
            c.chat2Lines = Math.min(20, c.chat2Lines + 1);
            c.save();
            again();
        });
        gap(2);
        sec(Lang.t("Was kommt rein", "What goes in"));
        row(T(Lang.t("Privatnachrichten (/msg)", "Private messages (/msg)"), c.chat2Pm, () -> {
            c.chat2Pm = !c.chat2Pm;
            c.save();
            again();
        }));
        text(Lang.t("Eigene Wörter (z. B. Name, Clan, \"Kill\"):", "Own words (e.g. name, clan, \"kill\"):"), 0xFF8B92A5);
        row(X("").w(4), B(Lang.t("Hinzufügen", "Add"), this::addWord));
        fieldRow = lastRow();
        int per = 4;
        int pages = Math.max(1, (c.chat2Words.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * per; i < Math.min(c.chat2Words.size(), page * per + per); i++) {
            final String w = c.chat2Words.get(i);
            row(X(w).w(4), R(Lang.t("Entfernen", "Remove"), () -> {
                c.chat2Words.remove(w);
                c.save();
                again();
            }));
        }
        if (pages > 1) {
            row(G("‹", () -> {
                page = Math.max(0, page - 1);
                again();
            }), X((page + 1) + " / " + pages).w(4), G("›", () -> {
                page = Math.min(pages - 1, page + 1);
                again();
            }));
        }
        if (c.chat2Words.isEmpty() && !c.chat2Pm) text(Lang.t("Noch kein Filter – dann landet nichts im zweiten Chat.", "No filter yet – nothing will go to the second chat."), 0xFFFFD166);
        gap(2);
        row(G(Lang.t("Fenster leeren", "Clear window"), () -> {
            Chat2.clear();
            again();
        }));
        text(Lang.t("Bisher aufgefangen: ", "Caught so far: ") + Chat2.caught, 0xFF8B92A5);
    }

    @Override
    protected void init() {
        super.init();
        if (fieldRow < 0) return;
        int[] r = rect(fieldRow, 0);
        if (r[1] < py0 + HEAD || r[1] + r[3] > py1) return;   // gerade weggescrollt
        field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Text.literal(""));
        field.setMaxLength(40);
        field.setPlaceholder(Text.literal(Lang.t("Wort eingeben …", "Type a word …")));
        addDrawableChild(field);
        setInitialFocus(field);
    }

    public void render(net.minecraft.client.gui.DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (field != null) field.render(ctx, mouseX, mouseY, delta);   // über den Kasten zeichnen
    }
}
