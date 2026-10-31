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
    private long shownSecond = -1;
    private boolean builtRunning = false;

    public AuctionScreen() {
        super(Lang.t("Auktionen", "Auctions"), Lang.t("Bieten mit /pay · höchste Summe gewinnt", "Bid with /pay · highest total wins"));
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
        builtRunning = Auction.running;
        if (Auction.running) buildRunning();
        else if (Auction.finished) buildResult();
        else buildSetup();
    }

    // ---------- 1. Neue Auktion ----------
    private void buildSetup() {
        sec(Lang.t("Item", "Item"));
        row(X("").w(3), G(Lang.t("Aus der Hand", "From hand"), () -> {
            draft = handItem();
            field = null;
            again();
        }));
        fieldRow = lastRow();
        gap(4);

        sec(Lang.t("Regeln", "Rules"));
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
        gap(4);

        row(B(Lang.t("Auktion starten", "Start auction"), () -> {
            String it = field != null ? field.getText() : draft;
            if (it.isBlank()) it = handItem();
            Auction.start(it, Auction.MINUTES[minIdx], Auction.MIN_BIDS[bidIdx], announce);
            draft = "";
            field = null;
            again();
        }));
    }

    // ---------- 2. Läuft ----------
    private void buildRunning() {
        String lead = Auction.leader();
        sec(Auction.item);
        row(X(Lang.t("Noch ", "Time left ") + Auction.timeLeft()), X(lead.isEmpty()
            ? (Auction.minBid > 0 ? Lang.t("ab ", "from ") + Auction.fmt(Auction.minBid) : Lang.t("Kein Gebot", "No bid"))
            : lead + "  " + Auction.fmt(Auction.BIDS.getOrDefault(lead, 0.0))).w(2));
        gap(4);

        sec(Lang.t("Gebote", "Bids"));
        List<Map.Entry<String, Double>> l = Auction.sorted();
        if (l.isEmpty()) text(Lang.t("Noch niemand hat bezahlt.", "Nobody has paid yet."), 0xFF8B92A5);
        for (int i = 0; i < Math.min(8, l.size()); i++) {
            Map.Entry<String, Double> e = l.get(i);
            row(X((i + 1) + ".").w(1), X(e.getKey()).w(4), X(Auction.fmt(e.getValue())).w(3));
        }
        gap(4);

        row(G("+1 Min", () -> {
            Auction.extend(1);
            again();
        }), G("+5 Min", () -> {
            Auction.extend(5);
            again();
        }), B(Lang.t("Beenden", "End"), () -> {
            Auction.finish();
            again();
        }));
        row(R(Lang.t("Abbrechen", "Cancel"), () -> {
            Auction.cancel();
            again();
        }));
    }

    // ---------- 3. Ergebnis ----------
    private void buildResult() {
        sec(Lang.t("Ergebnis", "Result"));
        if (!Auction.winner.isEmpty()) {
            row(X(Auction.winner).w(3), X(Auction.fmt(Auction.winAmount)).w(2));
            row(B("/tpa " + Auction.winner + Lang.t(" kopieren", " copy"), () -> Auction.copy("/tpa " + Auction.winner)),
                G(Lang.t("Im Chat verkünden", "Announce"), () -> Auction.say("[Auktion] " + Auction.winner + " hat " + Auction.item
                    + " für " + Auction.fmt(Auction.winAmount) + " gewonnen!")));
        } else {
            text(Lang.t("Kein gültiges Gebot.", "No valid bid."), 0xFFF87171);
        }
        boolean refunds = false;
        for (Map.Entry<String, Double> e : Auction.sorted()) {
            if (e.getKey().equals(Auction.winner)) continue;
            if (!refunds) {
                gap(4);
                sec(Lang.t("Zurückzahlen", "Refunds"));
                refunds = true;
            }
            String cmd = "/pay " + e.getKey() + " " + Auction.plain(e.getValue());
            row(X(e.getKey()).w(3), X(Auction.fmt(e.getValue())).w(2), G(Lang.t("Kopieren", "Copy"), () -> Auction.copy(cmd)).w(2));
        }
        gap(4);
        row(B(Lang.t("Neue Auktion", "New auction"), () -> {
            Auction.finished = false;
            again();
        }));
    }

    @Override
    protected void init() {
        super.init();
        if (fieldRow < 0 || Auction.running || Auction.finished) return;
        int[] r = rect(fieldRow, 0);
        if (r[1] < py0 + HEAD || r[1] + r[3] > py1) return;
        field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, Text.literal(""));
        field.setMaxLength(60);
        field.setText(draft.isEmpty() ? handItem() : draft);
        field.setPlaceholder(Text.literal(Lang.t("Name des Items …", "Item name …")));
        addDrawableChild(field);
    }

    /** Während der Auktion jede Sekunde neu aufbauen (Restzeit und Gebote), am Ende einmal fürs Ergebnis. */
    @Override
    public void tick() {
        super.tick();
        if (!builtRunning) return;
        long sec = System.currentTimeMillis() / 1000;
        if (shownSecond < 0) shownSecond = sec;
        if (sec != shownSecond || !Auction.running) again();
    }

    public void render(net.minecraft.client.gui.DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (field != null) field.render(ctx, mouseX, mouseY, delta);
    }
}
