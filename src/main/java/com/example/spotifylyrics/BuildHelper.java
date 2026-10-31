package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bauen: Kreis-/Kugel-Vorlage, Raster am Boden, Lineal und Symmetrie-Linie (alles als leuchtende Linien direkt auf dem Bildschirm). */
public class BuildHelper {
    public static final String[] SHAPES = {"Kreis liegend", "Kreis stehend", "Kugel", "Kuppel"};
    public static final String[] AXES = {"Nord-Süd", "Ost-West", "Beide"};
    static final int P_NONE = 0, P_SHAPE = 1, P_A = 2, P_B = 3, P_SYM = 4;
    /** Höchstens so viele fehlende Blöcke der Vorlage werden gezeigt (die nächsten). */
    static final int CAP = 320;
    private static final double RANGE = 48;
    private static final double PICK = 64;
    private static final int[][] EDGES = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7},
        {0, 2}, {1, 3}, {4, 6}, {5, 7},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    /** Wird im Menü gesetzt; der nächste Tick (Menü zu) nimmt den angeschauten Block. */
    public static volatile int pending = P_NONE;
    static BlockPos shapeCenter = null, rulerA = null, rulerB = null, symPoint = null;
    /** Raster bleibt auf seiner Höhe, auch wenn du woanders stehst. */
    static boolean gridLock = false;
    private static boolean shapeVertX = true;
    private static int gridY = Integer.MIN_VALUE;
    private static String error = "";

    // Vorlage: Versatz-Liste (nur neu berechnet, wenn Form/Größe sich ändert) und die gerade gezeigten Blöcke
    private static int cType = -1, cR = -1;
    private static boolean cVert = true;
    private static int[][] offs = new int[0][];
    private static final List<BlockPos> show = new ArrayList<>();
    private static int shapeTotal = 0, shapeMissing = 0, tickN = 0;

    private BuildHelper() {}

    static int clampR(int r) {
        return Math.max(1, Math.min(32, r));
    }

    /** Menü schließen; danach wird der angeschaute Block übernommen. */
    static void pick(int what) {
        pending = what;
        MinecraftClient.getInstance().setScreen(null);
    }

    static void moveCenter(int dy) {
        if (shapeCenter != null) {
            shapeCenter = shapeCenter.up(dy);
            tickN = 0;
        }
    }

    static void clearShape() {
        shapeCenter = null;
        show.clear();
    }

    static void clearRuler() {
        rulerA = null;
        rulerB = null;
    }

    static void clearSym() {
        symPoint = null;
    }

    static int activeCount() {
        Config c = Config.INSTANCE;
        int n = 0;
        if (c.placeHelper) n++;
        if (c.shapeOn) n++;
        if (c.gridOn) n++;
        if (c.rulerOn) n++;
        if (c.symOn) n++;
        return n;
    }

    // ---------- Texte fürs Menü ----------

    static String shapeInfo() {
        if (!error.isEmpty()) return "Fehler: " + error;
        Config c = Config.INSTANCE;
        int r = clampR(c.shapeR);
        String size = (2 * r + 1) + " Blöcke breit";
        if (shapeCenter == null) return "Noch keine Mitte · " + size;
        if (!c.shapeOn) return "Mitte " + pos(shapeCenter) + " · ausgeblendet";
        return size + " · " + shapeTotal + " Blöcke, " + shapeMissing + " fehlen";
    }

    static String measure() {
        if (rulerA == null && rulerB == null) return "Noch keine Punkte gesetzt";
        if (rulerA == null || rulerB == null) return (rulerA != null ? "A" : "B") + " gesetzt – jetzt den anderen Punkt";
        int dx = Math.abs(rulerB.getX() - rulerA.getX()) + 1;
        int dy = Math.abs(rulerB.getY() - rulerA.getY()) + 1;
        int dz = Math.abs(rulerB.getZ() - rulerA.getZ()) + 1;
        return "Breite " + dx + " · Höhe " + dy + " · Tiefe " + dz;
    }

    static String airLine() {
        if (rulerA == null || rulerB == null) return "";
        double dx = rulerB.getX() - rulerA.getX(), dy = rulerB.getY() - rulerA.getY(), dz = rulerB.getZ() - rulerA.getZ();
        return "Luftlinie " + String.format(Locale.ROOT, "%.1f", Math.sqrt(dx * dx + dy * dy + dz * dz)) + " Blöcke";
    }

    static String symInfo() {
        if (symPoint == null) return "Noch keine Achse gesetzt";
        return "Achse bei " + pos(symPoint);
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void fail(Throwable ex) {
        if (error.isEmpty()) {
            error = String.valueOf(ex);
            SpotifyLyricsClient.LOGGER.warn("Bauen-Fehler", ex);
        }
    }

    // ---------- Tick ----------

    public static void tick(MinecraftClient mc) {
        try {
            if (mc.world == null || mc.player == null) return;
            Config cfg = Config.INSTANCE;
            if (gridY == Integer.MIN_VALUE || (!gridLock && mc.player.isOnGround())) gridY = mc.player.getBlockPos().getY();
            if (pending != P_NONE && mc.currentScreen == null) {
                int what = pending;
                pending = P_NONE;
                BlockHitResult hit = look(mc);
                BlockPos at = hit != null ? hit.getBlockPos() : mc.player.getBlockPos().down();
                if (what == P_SHAPE) {
                    // Mitte = da, wo ein Block hinkäme (vor der angeschauten Seite)
                    shapeCenter = hit != null ? at.offset(hit.getSide()) : at.up();
                    shapeVertX = mc.player.getHorizontalFacing().getAxis() == Direction.Axis.Z;
                    tickN = 0;
                } else if (what == P_A) {
                    rulerA = at;
                } else if (what == P_B) {
                    rulerB = at;
                } else if (what == P_SYM) {
                    symPoint = at;
                }
            }
            if (cfg.shapeOn && shapeCenter != null) {
                if (tickN % 4 == 0) refreshShape(mc, cfg);
                tickN++;
            }
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    /** Angeschauter Block bis 64 Blöcke weit (weiter als die normale Reichweite). */
    private static BlockHitResult look(MinecraftClient mc) {
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        Vec3d end = new Vec3d(eye.x - Math.sin(yaw) * Math.cos(pitch) * PICK, eye.y - Math.sin(pitch) * PICK,
            eye.z + Math.cos(yaw) * Math.cos(pitch) * PICK);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit != null && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static final class Miss {
        final BlockPos p;
        final double d;

        Miss(BlockPos p, double d) {
            this.p = p;
            this.d = d;
        }
    }

    private static void refreshShape(MinecraftClient mc, Config cfg) {
        int type = Math.floorMod(cfg.shapeType, SHAPES.length);
        int r = clampR(cfg.shapeR);
        if (type != cType || r != cR || shapeVertX != cVert) {
            offs = buildOffsets(type, r, shapeVertX);
            cType = type;
            cR = r;
            cVert = shapeVertX;
        }
        double px = mc.player.getX(), py = mc.player.getY() + 1.0, pz = mc.player.getZ();
        BlockPos c = shapeCenter;
        List<Miss> list = new ArrayList<>();
        int missing = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int[] o : offs) {
            m.set(c.getX() + o[0], c.getY() + o[1], c.getZ() + o[2]);
            if (!mc.world.getBlockState(m).isReplaceable()) continue;   // hier steht schon ein Block
            missing++;
            double dx = m.getX() + 0.5 - px, dy = m.getY() + 0.5 - py, dz = m.getZ() + 0.5 - pz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > RANGE * RANGE) continue;
            list.add(new Miss(m.toImmutable(), d2));
        }
        list.sort((a, b) -> Double.compare(a.d, b.d));
        show.clear();
        for (int i = 0; i < list.size() && i < CAP; i++) show.add(list.get(i).p);
        shapeTotal = offs.length;
        shapeMissing = missing;
    }

    /** Blöcke der Form relativ zur Mitte: nur die Hülle (Rand), ohne Lücken. */
    private static int[][] buildOffsets(int type, int r, boolean vertX) {
        List<int[]> out = new ArrayList<>();
        double lim = (r + 0.5) * (r + 0.5);
        if (type <= 1) {
            for (int a = -r - 1; a <= r + 1; a++) {
                for (int b = -r - 1; b <= r + 1; b++) {
                    if (!in2(a, b, lim)) continue;
                    if (in2(a + 1, b, lim) && in2(a - 1, b, lim) && in2(a, b + 1, lim) && in2(a, b - 1, lim)) continue;
                    if (type == 0) out.add(new int[] {a, 0, b});
                    else if (vertX) out.add(new int[] {a, b, 0});
                    else out.add(new int[] {0, b, a});
                }
            }
        } else {
            int y0 = type == 3 ? 0 : -r - 1;
            for (int x = -r - 1; x <= r + 1; x++) {
                for (int y = y0; y <= r + 1; y++) {
                    for (int z = -r - 1; z <= r + 1; z++) {
                        if (!in3(x, y, z, lim)) continue;
                        if (in3(x + 1, y, z, lim) && in3(x - 1, y, z, lim) && in3(x, y + 1, z, lim)
                            && in3(x, y - 1, z, lim) && in3(x, y, z + 1, lim) && in3(x, y, z - 1, lim)) continue;
                        out.add(new int[] {x, y, z});
                    }
                }
            }
        }
        return out.toArray(new int[0][]);
    }

    private static boolean in2(int a, int b, double lim) {
        return a * a + b * b < lim;
    }

    private static boolean in3(int x, int y, int z, double lim) {
        return x * x + y * y + z * z < lim;
    }

    // ---------- Zeichnen ----------

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Config cfg = Config.INSTANCE;
            if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
            boolean shape = cfg.shapeOn && shapeCenter != null;
            boolean ruler = cfg.rulerOn && (rulerA != null || rulerB != null);
            boolean sym = cfg.symOn && symPoint != null;
            if (!cfg.gridOn && !shape && !ruler && !sym) return;
            float td = tc.getTickProgress(false);
            if (!Overlay3D.begin(mc, td)) return;
            Vec3d pp = mc.player.getLerpedPos(td);
            if (cfg.gridOn) renderGrid(ctx, cfg, pp);
            if (sym) renderSym(ctx, cfg);
            if (shape) renderShape(ctx, pp);
            if (ruler) renderRuler(ctx, mc);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    private static void renderGrid(DrawContext ctx, Config cfg, Vec3d pp) {
        if (gridY == Integer.MIN_VALUE) return;
        int s = Math.max(1, Math.min(16, cfg.gridStep));
        int rr = Math.max(4, Math.min(32, cfg.gridRange));
        double y = gridY + 0.03;
        int bx = (int) Math.floor(pp.x), bz = (int) Math.floor(pp.z);
        double r2 = (double) rr * rr;
        // Linien entlang Nord-Süd (feste x)
        for (int x = bx - rr; x <= bx + rr + 1; x++) {
            boolean chunk = cfg.gridChunks && Math.floorMod(x, 16) == 0;
            if (!chunk && Math.floorMod(x, s) != 0) continue;
            for (int z = bz - rr; z <= bz + rr; z++) {
                double mx = x - pp.x, mz = z + 0.5 - pp.z;
                double f = 1 - (mx * mx + mz * mz) / r2;
                if (f <= 0) continue;
                double[] a = Overlay3D.view(x, y, z), b = Overlay3D.view(x, y, z + 1);
                if (chunk) Overlay3D.line(ctx, a, b, 0xFFB347, (int) (0xE0 * f), 2);
                else Overlay3D.line(ctx, a, b, 0xCDE8FF, (int) (0x8C * f), 1);
            }
        }
        // Linien entlang Ost-West (feste z)
        for (int z = bz - rr; z <= bz + rr + 1; z++) {
            boolean chunk = cfg.gridChunks && Math.floorMod(z, 16) == 0;
            if (!chunk && Math.floorMod(z, s) != 0) continue;
            for (int x = bx - rr; x <= bx + rr; x++) {
                double mx = x + 0.5 - pp.x, mz = z - pp.z;
                double f = 1 - (mx * mx + mz * mz) / r2;
                if (f <= 0) continue;
                double[] a = Overlay3D.view(x, y, z), b = Overlay3D.view(x + 1, y, z);
                if (chunk) Overlay3D.line(ctx, a, b, 0xFFB347, (int) (0xE0 * f), 2);
                else Overlay3D.line(ctx, a, b, 0xCDE8FF, (int) (0x8C * f), 1);
            }
        }
    }

    private static void renderShape(DrawContext ctx, Vec3d pp) {
        float pulse = (float) (0.75 + 0.25 * Math.sin(System.currentTimeMillis() / 200.0));
        double ey = pp.y + 1.0;
        for (int i = 0; i < show.size(); i++) {
            BlockPos p = show.get(i);
            double[] c = Overlay3D.view(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (c[2] < -1.0) continue;   // hinter der Kamera
            double dx = p.getX() + 0.5 - pp.x, dy = p.getY() + 0.5 - ey, dz = p.getZ() + 0.5 - pp.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 14) {
                box(ctx, p, 0xFFE7C2, 0xFF7A1A, i < 16 ? pulse : 0.7f, true);
            } else {
                float f = (float) Math.max(0.25, 1.0 - (d - 14) / (RANGE - 14));
                box(ctx, p, 0xFFB061, 0, 0.75f * f, false);
            }
        }
        // Mitte: kleiner Würfel
        BlockPos m = shapeCenter;
        cube(ctx, m.getX() + 0.35, m.getY() + 0.35, m.getZ() + 0.35, m.getX() + 0.65, m.getY() + 0.65, m.getZ() + 0.65,
            0xFFF1D6, 0xFF9A3D, 0.8f, true);
    }

    private static void renderRuler(DrawContext ctx, MinecraftClient mc) {
        BlockPos a = rulerA, b = rulerB;
        if (a != null && b != null) {
            // ganzer Bereich als gelber Kasten
            cube(ctx, Math.min(a.getX(), b.getX()) - 0.01, Math.min(a.getY(), b.getY()) - 0.01, Math.min(a.getZ(), b.getZ()) - 0.01,
                Math.max(a.getX(), b.getX()) + 1.01, Math.max(a.getY(), b.getY()) + 1.01, Math.max(a.getZ(), b.getZ()) + 1.01,
                0xFFE58A, 0, 0.55f, false);
        }
        if (a != null) box(ctx, a, 0xDDFFE8, 0x22DD66, 0.9f, true);
        if (b != null) box(ctx, b, 0xFFE0F0, 0xFF3D9A, 0.9f, true);
        if (a == null || b == null) return;
        double[] va = Overlay3D.view(a.getX() + 0.5, a.getY() + 0.5, a.getZ() + 0.5);
        double[] vb = Overlay3D.view(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5);
        Overlay3D.beam(ctx, va, vb, 0xFFFFFF, 0xFFC93D, 1, 0.85f);
        double[] mid = Overlay3D.screen(Overlay3D.view((a.getX() + b.getX()) / 2.0 + 0.5, Math.max(a.getY(), b.getY()) + 1.4,
            (a.getZ() + b.getZ()) / 2.0 + 0.5));
        if (mid == null) return;
        TextRenderer tr = mc.textRenderer;
        String l1 = measure(), l2 = airLine();
        int w = Math.max(tr.getWidth(l1), tr.getWidth(l2));
        int x = (int) mid[0] - w / 2, y = (int) mid[1] - 12;
        if (x < -w || y < -30 || x > Overlay3D.screenW() || y > Overlay3D.screenH()) return;
        ctx.fill(x - 5, y - 4, x + w + 5, y + 21, 0xC00E1015);
        ctx.fill(x - 5, y - 4, x + w + 5, y - 3, 0xFFFFC93D);
        ctx.drawTextWithShadow(tr, l1, x + (w - tr.getWidth(l1)) / 2, y, 0xFFFFE08A);
        ctx.drawTextWithShadow(tr, l2, x + (w - tr.getWidth(l2)) / 2, y + 10, 0xFFD6D9E0);
    }

    private static void renderSym(DrawContext ctx, Config cfg) {
        BlockPos s = symPoint;
        int axis = Math.floorMod(cfg.symAxis, AXES.length);
        double off = cfg.symEdge ? 1.0 : 0.5;
        double y = s.getY() + 1.03;
        if (axis == 0 || axis == 2) symLine(ctx, true, s.getX() + off, s.getZ() + 0.5, y);
        if (axis == 1 || axis == 2) symLine(ctx, false, s.getZ() + off, s.getX() + 0.5, y);
        box(ctx, s, 0xF3DDFF, 0xB04DFF, 0.8f, true);
    }

    /** Spiegel-Linie: alongZ = Linie läuft Nord-Süd (feste x = k), sonst Ost-West (feste z = k). */
    private static void symLine(DrawContext ctx, boolean alongZ, double k, double c, double y) {
        int len = 24;
        for (int i = -len; i < len; i++) {
            double t0 = c + i, t1 = c + i + 1;
            float f = (float) (1.0 - Math.abs(i + 0.5) / len);
            double[] a = alongZ ? Overlay3D.view(k, y, t0) : Overlay3D.view(t0, y, k);
            double[] b = alongZ ? Overlay3D.view(k, y, t1) : Overlay3D.view(t1, y, k);
            Overlay3D.beam(ctx, a, b, 0xF6E8FF, 0xB04DFF, 1, 0.9f * f);
            double[] a2 = alongZ ? Overlay3D.view(k, y + 5, t0) : Overlay3D.view(t0, y + 5, k);
            double[] b2 = alongZ ? Overlay3D.view(k, y + 5, t1) : Overlay3D.view(t1, y + 5, k);
            Overlay3D.line(ctx, a2, b2, 0xC98BFF, (int) (0x60 * f), 1);
            if (Math.floorMod(i, 3) == 0) Overlay3D.line(ctx, a, a2, 0xC98BFF, (int) (0x50 * f), 1);
        }
    }

    private static void box(DrawContext ctx, BlockPos p, int core, int glow, float power, boolean shine) {
        cube(ctx, p.getX() - 0.02, p.getY() - 0.02, p.getZ() - 0.02, p.getX() + 1.02, p.getY() + 1.02, p.getZ() + 1.02, core, glow, power, shine);
    }

    private static void cube(DrawContext ctx, double x0, double y0, double z0, double x1, double y1, double z1,
                             int core, int glow, float power, boolean shine) {
        double[][] v = new double[8][];
        for (int i = 0; i < 8; i++) {
            v[i] = Overlay3D.view((i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1);
        }
        int alpha = (int) (0xD0 * Math.max(0.05f, Math.min(1f, power)));
        for (int[] e : EDGES) {
            if (shine) Overlay3D.beam(ctx, v[e[0]], v[e[1]], core, glow, 1, power);
            else Overlay3D.line(ctx, v[e[0]], v[e[1]], core, alpha, 1);
        }
    }
}
