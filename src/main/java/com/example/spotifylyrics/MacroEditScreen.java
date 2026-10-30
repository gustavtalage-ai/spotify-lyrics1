package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Ein Makro bearbeiten: Name, Starttaste, Ablauf (Wiederholung) und die Schritte. */
public class MacroEditScreen extends ItemScreen {
    private static int tab = 0;
    private static boolean capturing = false;
    private static int selAction = 2;
    private static int durIdx = 4;
    private static int countIdx = 3;
    private static String pendingText = "";

    private final int idx;
    private int nameRow = -1, textRow = -1;
    private TextFieldWidget nameField, textField;

    static void openTab(int t) {
        tab = t;
        capturing = false;
        pendingText = "";
    }

    public MacroEditScreen(int idx) {
        super("Makro bearbeiten", "Ablauf aus Tasten, Warten und Befehlen");
        this.idx = idx;
    }

    private Config.Macro macro() {
        List<Config.Macro> ms = Config.INSTANCE.macros;
        return idx >= 0 && idx < ms.size() ? ms.get(idx) : null;
    }

    @Override
    protected ItemScreen fresh() {
        return new MacroEditScreen(idx);
    }

    @Override
    protected Screen back() {
        return new MacroScreen();
    }

    @Override
    protected String backLabel() {
        return "‹ Makros";
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    private Cell tabCell(String label, int t) {
        return t == tab ? S(label) : G(label, () -> {
            tab = t;
            capturing = false;
            again();
        });
    }

    @Override
    protected void build() {
        Config.Macro m = macro();
        nameRow = -1;
        textRow = -1;
        if (m == null) {
            text("Dieses Makro gibt es nicht mehr.", 0xFFFF9FB0);
            return;
        }
        row(tabCell("Ablauf", 0), tabCell("Schritte (" + m.steps.size() + ")", 1));
        gap(4);
        if (tab == 0) buildFlow(m);
        else buildSteps(m);
    }

    private void buildFlow(Config.Macro m) {
        Config c = Config.INSTANCE;
        sec("Name");
        row(X("").w(1));
        nameRow = lastRow();
        gap(2);
        sec("Starttaste");
        row(capturing ? B("Drücke jetzt eine Taste …  (Rück = löschen)", () -> {
            capturing = false;
            again();
        }) : G("Taste: " + Macros.keyName(m.key) + "  ›  ändern", () -> {
            capturing = true;
            again();
        }));
        text("Eine Taste, die im Spiel sonst nichts macht (z. B. J, Num 1, F8).", 0xFF8B92A5);
        gap(2);
        sec("Wie oft?");
        row(G("Ablauf: " + Macros.MODES[Math.floorMod(m.mode, Macros.MODES.length)] + " ›", () -> {
            m.mode = (m.mode + 1) % Macros.MODES.length;
            c.save();
            again();
        }));
        if (m.mode == 1) {
            stepper("Wiederholen: " + m.count + "×", () -> {
                countIdx = nearest(Macros.COUNTS, m.count);
                m.count = Macros.COUNTS[Math.max(0, countIdx - 1)];
                c.save();
                again();
            }, () -> {
                countIdx = nearest(Macros.COUNTS, m.count);
                m.count = Macros.COUNTS[Math.min(Macros.COUNTS.length - 1, countIdx + 1)];
                c.save();
                again();
            });
        } else if (m.mode == 2) {
            text("Läuft immer wieder, bis du die Starttaste erneut drückst.", 0xFF8B92A5);
        }
        gap(4);
        row(B("Starten und Menü schließen", () -> {
            Macros.start(idx);
            MinecraftClient.getInstance().setScreen(null);
        }));
    }

    private static int nearest(int[] arr, int v) {
        int best = 0;
        for (int i = 0; i < arr.length; i++) if (Math.abs(arr[i] - v) < Math.abs(arr[best] - v)) best = i;
        return best;
    }

    private void buildSteps(Config.Macro m) {
        Config c = Config.INSTANCE;
        sec("Schritte (laufen von oben nach unten)");
        if (m.steps.isEmpty()) {
            text("Noch keine Schritte. Unten welche hinzufügen.", 0xFF8B92A5);
        } else {
            int from = Math.max(0, m.steps.size() - 6);
            if (from > 0) text("… " + from + " weitere oben", 0xFF8B92A5);
            for (int i = from; i < m.steps.size(); i++) {
                text((i + 1) + "  " + Macros.describe(m.steps.get(i)), 0xFFE8EAF0);
            }
        }
        row(G("Letzten löschen", () -> {
            if (!m.steps.isEmpty()) m.steps.remove(m.steps.size() - 1);
            c.save();
            again();
        }), R("Alle löschen", () -> {
            m.steps.clear();
            c.save();
            again();
        }));
        gap(2);
        sec("Schritt hinzufügen");
        row(G("Taste: " + Macros.ACTIONS[selAction] + " ›", () -> {
            selAction = (selAction + 1) % Macros.ACTIONS.length;
            again();
        }).w(5), B("+ Tippen", () -> {
            Config.MacroStep s = new Config.MacroStep();
            s.type = Macros.T_TAP;
            s.arg = selAction;
            m.steps.add(s);
            c.save();
            again();
        }).w(3));
        stepper("Dauer: " + Macros.durText(Macros.DURATIONS[durIdx]), () -> {
            durIdx = Math.max(0, durIdx - 1);
            again();
        }, () -> {
            durIdx = Math.min(Macros.DURATIONS.length - 1, durIdx + 1);
            again();
        });
        row(B("+ Halten", () -> {
            Config.MacroStep s = new Config.MacroStep();
            s.type = Macros.T_HOLD;
            s.arg = selAction == Macros.ESC ? 0 : selAction;
            s.arg2 = Macros.DURATIONS[durIdx];
            m.steps.add(s);
            c.save();
            again();
        }), B("+ Warten", () -> {
            Config.MacroStep s = new Config.MacroStep();
            s.type = Macros.T_WAIT;
            s.arg = Macros.DURATIONS[durIdx];
            m.steps.add(s);
            c.save();
            again();
        }));
        row(X("").w(5), B("+ Chat", () -> {
            if (pendingText.isBlank()) return;
            Config.MacroStep s = new Config.MacroStep();
            s.type = Macros.T_CHAT;
            s.text = pendingText.trim();
            m.steps.add(s);
            pendingText = "";
            c.save();
            again();
        }).w(2));
        textRow = lastRow();
        text("Chat: Befehl oder Text, z. B. /sell all (wird mit Enter abgeschickt).", 0xFF8B92A5);
    }

    @Override
    protected void init() {
        super.init();
        Config.Macro m = macro();
        nameField = null;
        textField = null;
        if (m == null) return;
        if (nameRow >= 0) {
            int[] r = rect(nameRow, 0);
            TextFieldWidget f = new TextFieldWidget(this.textRenderer, r[0] + 7, r[1] + 6, r[2] - 14, 10, Text.literal("Name"));
            f.setDrawsBackground(false);
            f.setMaxLength(20);
            f.setText(m.name);
            f.setChangedListener(t -> {
                m.name = t.isBlank() ? "Makro" : t;
                Config.INSTANCE.save();
            });
            addDrawableChild(f);
            nameField = f;
        }
        if (textRow >= 0) {
            int[] r = rect(textRow, 0);
            TextFieldWidget f = new TextFieldWidget(this.textRenderer, r[0] + 7, r[1] + 6, r[2] - 14, 10, Text.literal("Chat"));
            f.setDrawsBackground(false);
            f.setMaxLength(100);
            f.setSuggestion("Befehl oder Text …");
            f.setText(pendingText);
            f.setChangedListener(t -> {
                pendingText = t;
                f.setSuggestion(t.isEmpty() ? "Befehl oder Text …" : "");
            });
            addDrawableChild(f);
            textField = f;
        }
    }

    // Absichtlich ohne @Override, wie in den anderen Menüs.
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        // Die Kästen werden über den Standard-Elementen gezeichnet, darum die Textfelder nochmal obendrauf
        if (nameField != null) nameField.render(ctx, mouseX, mouseY, delta);
        if (textField != null) textField.render(ctx, mouseX, mouseY, delta);
        if (!capturing) return;
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0) return;
        for (int k = 32; k <= 348; k++) {
            if (GLFW.glfwGetKey(win, k) != GLFW.GLFW_PRESS) continue;
            Config.Macro m = macro();
            if (k == GLFW.GLFW_KEY_ESCAPE) {
                capturing = false;
                break;
            }
            if (m != null) {
                m.key = k == GLFW.GLFW_KEY_BACKSPACE ? -1 : k;
                Config.INSTANCE.save();
            }
            capturing = false;
            again();
            break;
        }
    }
}
