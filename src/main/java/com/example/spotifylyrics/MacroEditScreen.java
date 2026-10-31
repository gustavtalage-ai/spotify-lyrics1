package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Ein Makro bearbeiten: Name, Starttaste, Ablauf (Wiederholung) und die Schritte. */
public class MacroEditScreen extends ItemScreen {
    private static int tab = 0;
    private static boolean capturing = false;
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
        super("Makro bearbeiten", "Bausteine verbinden, Start drücken");
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
        areaY = -1;
        if (m == null) {
            text("Dieses Makro gibt es nicht mehr.", 0xFFFF9FB0);
            return;
        }
        row(tabCell("Ablauf", 0), tabCell("Bausteine (" + m.steps.size() + ")", 1));
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

    // ---------- Bausteine ----------
    private static final int COLS = 3, VIS = 3, BH = 26, ROWH = 34, GAPX = 12;
    private static final int C_START = 0xFFA3E635, C_TAP = 0xFFFB923C, C_HOLD = 0xFF38BDF8, C_WAIT = 0xFFA78BFA,
        C_CHAT = 0xFFF472B6, C_END = 0xFF2DD4BF, C_WIRE = 0xFFC084FC;
    private static int sel = -1, selMacro = -1, scroll = 0;
    private int areaY = -1;

    private static int colorOf(Config.MacroStep st) {
        switch (st.type) {
            case Macros.T_TAP: return C_TAP;
            case Macros.T_HOLD: return C_HOLD;
            case Macros.T_WAIT: return C_WAIT;
            default: return C_CHAT;
        }
    }

    private static String typeOf(Config.MacroStep st) {
        switch (st.type) {
            case Macros.T_TAP: return "TASTE";
            case Macros.T_HOLD: return "HALTEN";
            case Macros.T_WAIT: return "WARTEN";
            default: return "CHAT";
        }
    }

    private static String valueOf(Config.MacroStep st) {
        switch (st.type) {
            case Macros.T_TAP: return Macros.ACTIONS[Math.floorMod(st.arg, Macros.ACTIONS.length)];
            case Macros.T_HOLD: return Macros.ACTIONS[Math.floorMod(st.arg, Macros.ACTIONS.length)] + " · " + Macros.durText(st.arg2);
            case Macros.T_WAIT: return Macros.durText(st.arg);
            default: return st.text;
        }
    }

    private static String endText(Config.Macro m) {
        return m.mode == 0 ? "Einmal" : m.mode == 1 ? m.count + "×" : "Endlos";
    }

    private int totalRows(Config.Macro m) {
        return (m.steps.size() + 2 + COLS - 1) / COLS;
    }

    /** Kasten eines Bausteins (0 = Start, 1..n = Schritte, n+1 = Wiederholen) auf dem Bildschirm, null = gerade nicht sichtbar. */
    private int[] block(int i) {
        int r = i / COLS - scroll, c = i % COLS;
        if (r < 0 || r >= VIS || areaY < 0) return null;
        int bw = (cw - (COLS - 1) * GAPX) / COLS;
        return new int[] {cx + c * (bw + GAPX), py0 + areaY - scrollY() + 2 + r * ROWH, bw, BH};
    }

    private static int nextDur(int ms) {
        for (int d : Macros.DURATIONS) if (d > ms) return d;
        return Macros.DURATIONS[0];
    }

    private void insert(Config.Macro m, Config.MacroStep st) {
        int at = sel >= 0 && sel < m.steps.size() ? sel + 1 : m.steps.size();
        m.steps.add(at, st);
        sel = at;
        int row = (at + 1) / COLS;
        if (row < scroll) scroll = row;
        if (row >= scroll + VIS) scroll = row - VIS + 1;
        Config.INSTANCE.save();
        again();
    }

    @Override
    protected boolean onScroll(int dir) {
        Config.Macro m = macro();
        if (m == null || tab != 1) return false;
        int max = Math.max(0, totalRows(m) - VIS);
        int ns = Math.max(0, Math.min(max, scroll - dir));
        if (ns != scroll) {
            scroll = ns;
            again();
        }
        return true;
    }

    private void buildSteps(Config.Macro m) {
        Config c = Config.INSTANCE;
        if (selMacro != idx) {
            selMacro = idx;
            sel = -1;
            scroll = 0;
        }
        if (sel >= m.steps.size()) sel = -1;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, totalRows(m) - VIS)));
        sec("Bausteine – anklicken zum Bearbeiten" + (totalRows(m) > VIS ? " · Mausrad ▼" : ""));
        areaY = area(VIS * ROWH);
        if (sel >= 0) {
            Config.MacroStep st = m.steps.get(sel);
            final int s0 = sel;
            Cell earlier = G("‹", () -> {
                if (s0 > 0) {
                    m.steps.add(s0 - 1, m.steps.remove(s0));
                    sel = s0 - 1;
                    c.save();
                }
                again();
            });
            Cell later = G("›", () -> {
                if (s0 < m.steps.size() - 1) {
                    m.steps.add(s0 + 1, m.steps.remove(s0));
                    sel = s0 + 1;
                    c.save();
                }
                again();
            });
            Cell del = R("Löschen", () -> {
                m.steps.remove(s0);
                sel = Math.min(s0, m.steps.size() - 1);
                c.save();
                again();
            });
            Cell keyC = G("Taste ›", () -> {
                st.arg = (st.arg + 1) % Macros.ACTIONS.length;
                if (st.type == Macros.T_HOLD && st.arg == Macros.ESC) st.arg = 0;
                c.save();
                again();
            });
            Cell durC = G("Dauer ›", () -> {
                if (st.type == Macros.T_WAIT) st.arg = nextDur(st.arg);
                else st.arg2 = nextDur(st.arg2);
                c.save();
                again();
            });
            Cell textC = G("Text übernehmen", () -> {
                if (!pendingText.isBlank()) {
                    st.text = pendingText.trim();
                    pendingText = "";
                    c.save();
                }
                again();
            });
            if (st.type == Macros.T_TAP) row(earlier.w(1), later.w(1), keyC.w(3), del.w(3));
            else if (st.type == Macros.T_HOLD) row(earlier.w(1), later.w(1), keyC.w(3), durC.w(3), del.w(3));
            else if (st.type == Macros.T_WAIT) row(earlier.w(1), later.w(1), durC.w(3), del.w(3));
            else row(earlier.w(1), later.w(1), textC.w(4), del.w(3));
        } else {
            row(X("Baustein anklicken = verschieben, ändern, löschen"));
        }
        row(C("+ Taste", 0xFF3A2414, 0xFF52321B, 0xFFFFD2AE, () -> {
            Config.MacroStep st = new Config.MacroStep();
            st.type = Macros.T_TAP;
            st.arg = 0;
            insert(m, st);
        }), C("+ Halten", 0xFF10304A, 0xFF164466, 0xFFC7EBFF, () -> {
            Config.MacroStep st = new Config.MacroStep();
            st.type = Macros.T_HOLD;
            st.arg = 5;
            st.arg2 = 1000;
            insert(m, st);
        }), C("+ Warten", 0xFF2A2145, 0xFF3A2E60, 0xFFE2D8FF, () -> {
            Config.MacroStep st = new Config.MacroStep();
            st.type = Macros.T_WAIT;
            st.arg = 500;
            insert(m, st);
        }));
        row(X("").w(5), C("+ Chat", 0xFF3D1630, 0xFF561E44, 0xFFFFD0EA, () -> {
            if (pendingText.isBlank()) return;
            Config.MacroStep st = new Config.MacroStep();
            st.type = Macros.T_CHAT;
            st.text = pendingText.trim();
            pendingText = "";
            insert(m, st);
        }).w(2));
        textRow = lastRow();
    }

    /** Klickflächen für die Bausteine (nur der sichtbare Teil). */
    private void initBlocks(Config.Macro m) {
        int n = m.steps.size();
        for (int i = 0; i < n + 2; i++) {
            int[] r = block(i);
            if (r == null) continue;
            final int item = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                if (item == 0) {
                    tab = 0;
                    capturing = true;
                } else if (item == n + 1) {
                    m.mode = (m.mode + 1) % Macros.MODES.length;
                    Config.INSTANCE.save();
                } else {
                    sel = sel == item - 1 ? -1 : item - 1;
                }
                again();
            }).dimensions(r[0], r[1], r[2], r[3]).build());
        }
    }

    private void drawBlocks(DrawContext ctx, int mouseX, int mouseY) {
        Config.Macro m = macro();
        if (m == null || areaY < 0) return;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int n = m.steps.size();
        int ax0 = cx, ay0 = py0 + areaY - scrollY(), ax1 = cx + cw, ay1 = ay0 + VIS * ROWH;
        // Untergrund mit Punktraster
        ctx.fill(ax0 - 2, ay0 - 2, ax1 + 2, ay1, 0xFF0B0D12);
        for (int x = ax0 + 4; x < ax1; x += 10) for (int y = ay0 + 3; y < ay1 - 1; y += 10) ctx.fill(x, y, x + 1, y + 1, 0x22FFFFFF);
        boolean run = Macros.isRunning(idx);
        int cur = run ? Macros.currentStep() + 1 : -1;
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 140.0));
        // Kabel zwischen den Bausteinen
        for (int i = 0; i < n + 1; i++) {
            int[] a = block(i), b = block(i + 1);
            int wire = (run && i + 1 == cur) ? Ui.mix(C_WIRE, 0xFFFFFFFF, pulse) : (C_WIRE & 0x00FFFFFF) | 0x99000000;
            if (a != null && b != null && b[1] == a[1]) {
                int y = a[1] + BH / 2;
                ctx.fill(a[0] + a[2], y - 1, b[0], y + 1, wire);
                ctx.fill(a[0] + a[2] - 1, y - 2, a[0] + a[2] + 2, y + 2, 0xFFFFFFFF);
                ctx.fill(b[0] - 2, y - 2, b[0] + 1, y + 2, 0xFFFFFFFF);
            } else if (a != null) {
                int x0 = a[0] + a[2] / 2, yb = a[1] + BH, ym = yb + (ROWH - BH) / 2;
                ctx.fill(x0 - 1, yb, x0 + 1, Math.min(ym + 1, ay1), wire);
                if (b != null) {
                    int x1 = b[0] + b[2] / 2;
                    ctx.fill(Math.min(x0, x1) - 1, ym - 1, Math.max(x0, x1) + 1, ym + 1, wire);
                    ctx.fill(x1 - 1, ym, x1 + 1, b[1], wire);
                }
            } else if (b != null) {
                int x1 = b[0] + b[2] / 2;
                ctx.fill(x1 - 1, ay0, x1 + 1, b[1], wire);
            }
        }
        // Bausteine
        for (int i = 0; i < n + 2; i++) {
            int[] r = block(i);
            if (r == null) continue;
            int x0 = r[0], y0 = r[1], x1 = r[0] + r[2], y1 = r[1] + r[3];
            int col;
            String type, val;
            if (i == 0) {
                col = C_START;
                type = "START";
                val = "Taste " + Macros.keyName(m.key);
            } else if (i == n + 1) {
                col = C_END;
                type = "WIEDERHOLEN";
                val = endText(m);
            } else {
                Config.MacroStep st = m.steps.get(i - 1);
                col = colorOf(st);
                type = typeOf(st);
                val = valueOf(st);
            }
            boolean hov = Ui.in(mouseX, mouseY, x0, y0, x1, y1);
            boolean on = i == cur;
            boolean picked = i - 1 == sel && i > 0 && i <= n;
            if (on) ctx.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, (col & 0x00FFFFFF) | ((int) (0x50 + 0x60 * pulse) << 24));
            int border = picked || on ? col : hov ? Ui.mix(col, 0xFF2B3140, 0.35f) : Ui.mix(col, 0xFF2B3140, 0.6f);
            Ui.card(ctx, x0, y0, x1, y1, border, picked ? 0xFF1C2230 : 0xFF141821);
            ctx.fill(x0 + 1, y0 + 1, x0 + 4, y1 - 1, col);
            Ui.txt(ctx, tr, type, x0 + 8, y0 + 4, Ui.mix(col, 0xFF8B92A5, 0.35f));
            Ui.txt(ctx, tr, tr.trimToWidth(val, r[2] - 12), x0 + 8, y0 + 15, 0xFFFFFFFF);
            if (i > 0 && i <= n) {
                String num = String.valueOf(i);
                Ui.txt(ctx, tr, num, x1 - 4 - tr.getWidth(num), y0 + 4, 0xFF5A6275);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        Config.Macro m = macro();
        nameField = null;
        textField = null;
        if (m == null) return;
        if (tab == 1) initBlocks(m);
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
        if (tab == 1) drawBlocks(ctx, mouseX, mouseY);
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
