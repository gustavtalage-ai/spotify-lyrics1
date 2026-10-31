package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

/** Auktionen für HugoSMP: Item reinstellen, Gebote per /pay, Gewinner bekommt /tpa zum Kopieren. */
public class AuctionScreen extends ItemScreen {
    private static int minIdx = 3, bidIdx = 0;
    private static boolean announce = true;
    private static String draft = "";
    private int fieldRow = -1;
    private TextFieldWidget field;

    public AuctionScreen() {
        super(Lang.t("Auktionen", "Auctions"), Lang.t("Für HugoSMP: bieten per /pay", "For HugoSMP: bid with /pay"));
    }

    @Override
    protected ItemScreen fresh() {
        if (field != null) draft = field.getText();
        return new AuctionScreen();
    }

    @Override
    protected Screen back() {
        return new HubScreen();
    }

    @Override
    protected String backLabel() {
        return Lang.t("‹ Menü", "‹ Menu");
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    private static String handItem() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return "";
        ItemStack s = mc.player.getMainHandStack();
        if (s.isEmpty()) return "";
        return (s.getCount() > 1 ? s.getCount() + "x " : "") + s.getName().getString();
    }

    @Override
    protected void build() {
        if (Auction.running) buildRunning();
        else buildSetup();
    }

    private void buildSetup() {
        if (Auction.finished) {
            sec(Lang.t("Letzte Auktion", "Last auction"));
            if (!Auction.winner.isEmpty()) {
                text(Lang.t("Gewinner: ", "Winner: ") + Auction.winner + " – " + Auction.fmt(Auction.winAmount), Ui.GREEN);
                row(B("/tpa " + Auction.winner + Lang.t(" kopieren", " copy"), () -> Auction.copy("/tpa " + Auction.winner)),
                    G(Lang.t("Im Chat verkünden", "Announce in chat"), () -> Auction.say("[Auktion] " + Auction.winner + " hat " + Auction.item
                        + " für " + Auction.fmt(Auction.winAmount) + " gewonnen! Alle anderen bekommen ihr Geld zurück.")));
            } else {
                text(Lang.t("Kein gültiges Gebot.", "No valid bid."), 0xFFF87171);
            }
            for (Map.Entry<String, Double> e : Auction.sorted()) {
                if (e.getKey().equals(Auction.winner)) continue;
                String cmd = "/pay " + e.getKey() + " " + Auction.plain(e.getValue());
                row(X(Lang.t("Zurück: ", "Refund: ") + e.getKey() + " " + Auction.fmt(e.getValue())).w(3), G(Lang.t("Kopieren", "Copy"), () -> Auction.copy(cmd)));
            }
            gap(4);
        }
        sec(Lang.t("Neue Auktion", "New auction"));
        text(Lang.t("Was wird versteigert?", "What is being auctioned?"), 0xFF8B92A5);
        row(X("").w(4), G(Lang.t("Aus Hand", "From hand"), () -> {
            draft = handItem();
            field = null;
            again();
        }));
        fieldRow = lastRow();
        stepper(Lang.t("Dauer: ", "Duration: ") + Auction.MINUTES[minIdx] + " Min", () -> {
            minIdx = Math.max(0, minIdx - 1);
            again();
        }, () -> {
            minIdx = Math.min(Auction.MINUTES.length - 1, minIdx + 1);
            again();
        });
        stepper(Lang.t("Mindestgebot: ", "Minimum bid: ") + (Auction.MIN_BIDS[bidIdx] == 0 ? Lang.t("keins", "none") : Auction.fmt(Auction.MIN_BIDS[bidIdx])), () -> {
            bidIdx = Math.max(0, bidIdx - 1);
            again();
        }, () -> {
            bidIdx = Math.min(Auction.MIN_BIDS.length - 1, bidIdx + 1);
            again();
        });
        row(T(Lang.t("Im Chat ankündigen", "Announce in chat"), announce, () -> {
            announce = !announce;
            again();
        }));
        row(B(Lang.t("Auktion starten", "Start auction"), () -> {
            String it = field != null ? field.getText() : draft;
            if (it.isBlank()) it = handItem();
            Auction.start(it, Auction.MINUTES[minIdx], Auction.MIN_BIDS[bidIdx], announce);
            draft = "";
            again();
        }));
        gap(2);
        text(Lang.t("Alle zahlen mit /pay an dich. Wer am Ende", "Everyone pays you with /pay. Whoever paid"), 0xFF8B92A5);
        text(Lang.t("insgesamt am meisten gezahlt hat, gewinnt.", "the most in total at the end wins."), 0xFF8B92A5);
    }

    private void buildRunning() {
        sec(Lang.t("Läuft: ", "Running: ") + Auction.item);
        String lead = Auction.leader();
        text(Lang.t("Noch ", "Time left ") + Auction.timeLeft(), 0xFFFFD166);
        text(lead.isEmpty() ? Lang.t("Noch kein Gebot", "No bid yet")
            : Lang.t("Höchstgebot: ", "Highest bid: ") + lead + " – " + Auction.fmt(Auction.BIDS.getOrDefault(lead, 0.0)), Ui.TEXT);
        List<Map.Entry<String, Double>> l = Auction.sorted();
        for (int i = 0; i < Math.min(6, l.size()); i++) {
            Map.Entry<String, Double> e = l.get(i);
            row(X((i + 1) + ". " + e.getKey() + "   " + Auction.fmt(e.getValue())));
        }
        gap(2);
        row(G("+1 Min", () -> {
            Auction.extend(1);
            again();
        }), G("+5 Min", () -> {
            Auction.extend(5);
            again();
        }), B(Lang.t("Jetzt beenden", "End now"), () -> {
            Auction.finish();
            again();
        }));
        row(R(Lang.t("Abbrechen (alle zurückzahlen)", "Cancel (refund all)"), () -> {
            Auction.cancel();
            again();
        }), G(Lang.t("Aktualisieren", "Refresh"), this::again));
        gap(2);
        text(Lang.t("Erkannte Zahlungen: ", "Payments detected: ") + Auction.payCount, 0xFF8B92A5);
        if (!Auction.lastPayLine.isEmpty()) text(Auction.lastPayLine, 0xFF6B7385);
        text(Lang.t("Wird keine Zahlung erkannt? Schick mir die Chat-", "Payment not detected? Send me the chat"), 0xFF6B7385);
        text(Lang.t("Nachricht vom Server, dann passe ich es an.", "message from the server and I'll adjust it."), 0xFF6B7385);
    }

    @Override
    protected void init() {
        super.init();
        if (fieldRow < 0 || Auction.running) return;
        int[] r = rect(fieldRow, 0);
        if (r[1] < py0 + HEAD || r[1] + r[3] > py1) return;
        field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Text.literal(""));
        field.setMaxLength(60);
        field.setText(draft.isEmpty() ? handItem() : draft);
        field.setPlaceholder(Text.literal(Lang.t("Item-Name …", "Item name …")));
        addDrawableChild(field);
    }

    public void render(net.minecraft.client.gui.DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (field != null) field.render(ctx, mouseX, mouseY, delta);
    }
}
