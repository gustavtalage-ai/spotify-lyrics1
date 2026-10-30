package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * PvP-Client: kleine Anzeigen und Helfer. Alle einzeln schaltbar (Bits in Config.pvpMods),
 * alle Anzeigen frei verschiebbar (Config.hudPos). Läuft nur lokal bei dir (Anzeige bzw. normale Tasten).
 */
final class PvpHud {
    private PvpHud() {}

    static final int CPS = 0, COMBO = 1, ARMOR = 2, EFFECTS = 3, KEYS = 4, INFO = 5, STOCK = 6, TARGET = 7,
        BAR = 8, REACH = 9, HITSOUND = 10, TOTEM = 11, LOWHP = 12, SPRINT = 13, NEAR = 14, STATS = 15,
        CRIT = 16, CIRCLE = 17, AUTOTOTEM = 18, ARMORWARN = 19, CROSS = 20, COOLDOWN = 21, DMGNUM = 22,
        DEATH = 23, COMBAT = 24;
    /** Anzeigenamen in derselben Reihenfolge wie die Bits. */
    static final String[] NAMES = {"CPS", "Combo", "Rüstung", "Effekte", "Tasten", "Info (FPS, XYZ)", "Vorrat",
        "Ziel-HUD", "Angriffs-Balken", "Reichweite", "Treffer-Sound", "Totem-Hinweis", "Leben-Warnung", "Auto-Sprint",
        "Spieler-Liste", "Statistik", "Crit-Anzeige", "Reichweiten-Kreis", "Totem nachlegen (Auto)", "Rüstung-Warnung", "Fadenkreuz", "Cooldowns", "Schadenszahlen",
        "Todespunkt", "Kampf-Timer"};
    /** Verschiebbare Anzeigen (ihre Nummer ist zugleich das Bit, das sie einschaltet). */
    static final int[] HUD_IDS = {INFO, CPS, COMBO, EFFECTS, ARMOR, STOCK, TARGET, BAR, REACH, NEAR, STATS, CRIT, TOTEM, COOLDOWN, DEATH, COMBAT};
    static final String[] COLOR_NAMES = {"Rot, grün bei Gegner", "Immer grün", "Immer rot"};

    static boolean on(int bit) {
        return ((Config.INSTANCE.pvpMods >> bit) & 1) != 0;
    }

    static void flip(int bit) {
        Config.INSTANCE.pvpMods ^= (1 << bit);
        Config.INSTANCE.save();
    }

    static void setAll(int[] bits, boolean value) {
        for (int b : bits) {
            if (value) Config.INSTANCE.pvpMods |= (1 << b);
            else Config.INSTANCE.pvpMods &= ~(1 << b);
        }
        Config.INSTANCE.save();
    }

    static boolean enabled(int id) {
        return id == TOTEM ? (on(TOTEM) || on(ARMORWARN)) : on(id);
    }

    static String elemName(int id) {
        return id == TOTEM ? "Hinweise" : NAMES[id];
    }

    // ---------- Positionen ----------

    private static int[] defPos(int id, int sw, int sh) {
        int bw = Math.max(10, Config.INSTANCE.barW);
        return switch (id) {
            case INFO -> new int[] {4, 4};
            case CPS -> new int[] {4, 34};
            case COMBO -> new int[] {4, 50};
            case EFFECTS -> new int[] {4, 68};
            case KEYS -> new int[] {4, sh / 2 + 14};
            case ARMOR -> new int[] {sw - 66, sh / 2 - 54};
            case STOCK -> new int[] {sw - 66, sh / 2 + 46};
            case TARGET -> new int[] {sw / 2 - 62, sh / 2 + 34};
            case BAR -> new int[] {sw / 2 - bw / 2, sh / 2 + 9};
            case REACH -> new int[] {sw / 2 - 14, sh / 2 + 17};
            case NEAR -> new int[] {4, sh / 2 + 96};
            case STATS -> new int[] {sw / 2 - 70, 22};
            case CRIT -> new int[] {sw / 2 - 14, sh / 2 - 20};
            case COOLDOWN -> new int[] {sw / 2 + 26, sh / 2 - 30};
            case DEATH -> new int[] {sw / 2 - 70, 36};
            case COMBAT -> new int[] {sw / 2 - 28, 52};
            default -> new int[] {sw / 2 - 50, sh / 2 - 62};
        };
    }

    static int[] pos(int id, int sw, int sh) {
        int[] hp = Config.INSTANCE.hudPos;
        if (hp[2 * id] >= 0 && hp[2 * id + 1] >= 0) {
            return new int[] {hp[2 * id] * sw / 1000, hp[2 * id + 1] * sh / 1000};
        }
        return defPos(id, sw, sh);
    }

    static void setPos(int id, int x, int y, int sw, int sh) {
        x = Math.max(0, Math.min(sw - 6, x));
        y = Math.max(0, Math.min(sh - 6, y));
        Config.INSTANCE.hudPos[2 * id] = x * 1000 / Math.max(1, sw);
        Config.INSTANCE.hudPos[2 * id + 1] = y * 1000 / Math.max(1, sh);
    }

    static void resetPositions() {
        java.util.Arrays.fill(Config.INSTANCE.hudPos, -1);
        Config.INSTANCE.save();
    }

    // ---------- Zustand ----------
    private static long tickN = 0;
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean lDown, rDown;
    private static LivingEntity lastTarget;
    private static long lastAttackTick = -1000, lastHitTick = -1000, lastDmgTick = -1000;
    private static int prevTargetHurt, prevSelfHurt;
    private static float prevTargetHp, lastDmg;
    private static boolean killCounted = false;
    private static int combo = 0, swings = 0, hits = 0, kills = 0;
    private static boolean sprintForced = false;
    private static int prevTotal = -1;
    private static boolean prevOffTotem = false, popped = false;
    private static long popTick = -1000, lastSwapTick = -1000;
    private static boolean armorLow = false;
    private static long lastCombatTick = -100000;
    private static boolean deathRecorded = false;

    /** Schadenszahl, die über dem Gegner nach oben schwebt. */
    private static final class Pop {
        double x, y, z;
        String t;
        long born;
        int col;
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static final java.util.Random RND = new java.util.Random();

    private static void spawnPop(LivingEntity e, float dmg) {
        Pop f = new Pop();
        f.x = e.getX() + (RND.nextDouble() - 0.5) * 0.5;
        f.y = e.getY() + e.getHeight() + 0.15;
        f.z = e.getZ() + (RND.nextDouble() - 0.5) * 0.5;
        f.t = String.format(Locale.ROOT, "-%.1f", dmg);
        f.born = tickN;
        f.col = dmg >= 6f ? 0xFBBF24 : 0xFF6B7D;
        POPS.add(f);
        while (POPS.size() > 12) POPS.remove(0);
    }

    private static String dimId(MinecraftClient mc) {
        try {
            return mc.world.getRegistryKey().getValue().getPath();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String dimName(String id) {
        return switch (id) {
            case "overworld" -> "Oberwelt";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> id;
        };
    }

    /** Das normale Minecraft-Fadenkreuz wird ausgeblendet, wenn das eigene an ist. */
    static boolean hideVanillaCrosshair() {
        return on(CROSS);
    }

    static void resetStats() {
        swings = 0;
        hits = 0;
        kills = 0;
        combo = 0;
    }

    static void register() {
        // Ziel merken und Schläge zählen (läuft nach dem Hit-Assistenten, der zu frühe Schläge schon abgefangen hat)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient()) return ActionResult.PASS;
            if (entity instanceof LivingEntity le) {
                lastTarget = le;
                lastAttackTick = tickN;
                prevTargetHurt = le.hurtTime;
                prevTargetHp = le.getHealth();
                killCounted = false;
                swings++;
                if (le instanceof PlayerEntity) lastCombatTick = tickN;
            }
            return ActionResult.PASS;
        });
    }

    // ---------- Tick ----------
    static void tick(MinecraftClient mc) {
        tickN++;
        ClientPlayerEntity p = mc.player;
        long win = GLFW.glfwGetCurrentContext();
        if (p == null || mc.world == null || win == 0) {
            combo = 0;
            lastTarget = null;
            prevTotal = -1;
            popped = false;
            if (sprintForced) {
                mc.options.sprintKey.setPressed(false);
                sprintForced = false;
            }
            return;
        }
        if (p.getHealth() <= 0f) {
            if (!deathRecorded) {
                Config dc = Config.INSTANCE;
                dc.hasDeath = true;
                dc.deathX = p.getX();
                dc.deathY = p.getY();
                dc.deathZ = p.getZ();
                dc.deathDim = dimId(mc);
                dc.save();
                deathRecorded = true;
            }
        } else {
            deathRecorded = false;
        }
        long now = System.currentTimeMillis();
        boolean free = mc.currentScreen == null;

        // CPS: Maus-Tasten selbst abfragen (steigende Flanke = ein Klick)
        boolean ld = free && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean rd = free && GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (ld && !lDown) LEFT.addLast(now);
        if (rd && !rDown) RIGHT.addLast(now);
        lDown = ld;
        rDown = rd;
        prune(LEFT, now);
        prune(RIGHT, now);

        // Treffer, Schaden und Kills erkennen
        if (lastTarget != null) {
            if (!lastTarget.isAlive() && !killCounted && tickN - lastHitTick <= 40) {
                kills++;
                killCounted = true;
            }
            if (tickN - lastAttackTick > 100 || !lastTarget.isAlive()) {
                lastTarget = null;
            } else {
                int h = lastTarget.hurtTime;
                float hpNow = lastTarget.getHealth();
                if (hpNow < prevTargetHp - 0.01f && tickN - lastAttackTick <= 15) {
                    lastDmg = prevTargetHp - hpNow;
                    lastDmgTick = tickN;
                    if (on(DMGNUM)) spawnPop(lastTarget, lastDmg);
                }
                prevTargetHp = hpNow;
                if (tickN - lastAttackTick <= 10 && h > prevTargetHurt && h > 0) {
                    combo++;
                    hits++;
                    lastHitTick = tickN;
                    if (lastTarget instanceof PlayerEntity) lastCombatTick = tickN;
                    if (on(HITSOUND)) {
                        try {
                            mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.7f, 0.6f));
                        } catch (Throwable ignored) {
                        }
                    }
                }
                prevTargetHurt = h;
            }
        }
        // Combo endet, wenn du getroffen wirst oder 4 Sekunden nichts passiert
        if (p.hurtTime > prevSelfHurt && p.hurtTime > 0) {
            combo = 0;
            if (p.getAttacker() instanceof PlayerEntity) lastCombatTick = tickN;
        }
        prevSelfHurt = p.hurtTime;
        if (combo > 0 && tickN - lastHitTick > 80) combo = 0;

        // Auto-Sprint (wie "Sprint umschalten" aus den Minecraft-Optionen)
        if (on(SPRINT) && free && mc.options.forwardKey.isPressed() && !mc.options.sneakKey.isPressed()
            && p.getHungerManager().getFoodLevel() > 6) {
            mc.options.sprintKey.setPressed(true);
            sprintForced = true;
        } else if (sprintForced) {
            mc.options.sprintKey.setPressed(false);
            sprintForced = false;
        }

        // Rüstung fast kaputt?
        boolean low = false;
        EquipmentSlot[] aslots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot sl : aslots) {
            ItemStack as = p.getEquippedStack(sl);
            if (as.isEmpty() || !as.isDamageable()) continue;
            int amax = Math.max(1, as.getMaxDamage());
            if ((amax - as.getDamage()) * 100 / amax <= 15) low = true;
        }
        armorLow = low;

        // Totem gepoppt? (in der Hand war eins, jetzt eins weniger im ganzen Inventar)
        Item[] totem = {Items.TOTEM_OF_UNDYING};
        int total = count(p, totem);
        boolean offT = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (prevTotal >= 0 && prevOffTotem && !offT && total < prevTotal) {
            popped = true;
            popTick = tickN;
        }
        if (offT || total == 0) popped = false;
        prevTotal = total;
        prevOffTotem = offT;
    }

    private static void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.removeFirst();
    }

    // ---------- Zeichnen ----------
    static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx, tickCounter.getTickProgress(false));
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("PvP-HUD-Fehler", e);
        }
    }

    private static int chipW(TextRenderer tr, String text) {
        return tr.getWidth(text) + 9;
    }

    private static void chip(DrawContext ctx, TextRenderer tr, int x, int y, String text, int color) {
        Ui.hudBox(ctx, x, y, x + chipW(tr, text), y + 12, Ui.ACCENT);
        ctx.drawTextWithShadow(tr, text, x + 5, y + 2, color);
    }

    private static void draw(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden) return;
        if (Config.INSTANCE.pvpMods == 0) return;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

        if (on(CIRCLE)) drawCircle(ctx, mc, p, td, w, h);
        if (on(DEATH)) drawDeathBeam(ctx, mc, p, td);
        if (on(DMGNUM)) drawPops(ctx, mc, td);
        if (on(CROSS) && mc.currentScreen == null && mc.options.getPerspective().isFirstPerson()) drawCrosshair(ctx, mc, w / 2, h / 2, false);
        for (int id : HUD_IDS) {
            if (!enabled(id)) continue;
            int[] pp = pos(id, w, h);
            elem(id, ctx, pp[0], pp[1], false);
        }
        if (on(LOWHP) && p.getHealth() + p.getAbsorptionAmount() <= 6f && !p.isCreative() && !p.isSpectator()) {
            int pulse = (int) (24 * (0.5 + 0.5 * Math.sin(tickN / 4.0)));
            for (int i = 0; i < 4; i++) {
                int t = 3 * (i + 1);
                int col = ((0x30 + pulse - i * 9) << 24) | 0xFF0000;
                ctx.fill(0, 0, w, t, col);
                ctx.fill(0, h - t, w, h, col);
                ctx.fill(0, t, t, h - t, col);
                ctx.fill(w - t, t, w, h - t, col);
            }
        }
    }

    /** Zeichnet eine Anzeige an (x, y) und liefert ihre Größe {Breite, Höhe}; 0 = gerade nichts zu zeigen. demo = Beispieldaten für den Editor. */
    static int[] elem(int id, DrawContext ctx, int x, int y, boolean demo) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        TextRenderer tr = mc.textRenderer;
        if (p == null) return new int[] {0, 0};
        switch (id) {
            case INFO: {
                String a, b;
                if (demo) {
                    a = "FPS 144 · 23 ms";
                    b = "120 64 -30 · N";
                } else {
                    ClientPlayNetworkHandler nh = mc.getNetworkHandler();
                    PlayerListEntry pe = nh == null ? null : nh.getPlayerListEntry(p.getUuid());
                    String ping = pe != null && pe.getLatency() > 0 ? " · " + pe.getLatency() + " ms" : "";
                    a = "FPS " + mc.getCurrentFps() + ping;
                    b = p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + " · " + dirName(p.getHorizontalFacing());
                }
                chip(ctx, tr, x, y, a, 0xFFFFFFFF);
                chip(ctx, tr, x, y + 14, b, 0xFFD6D9E0);
                return new int[] {Math.max(chipW(tr, a), chipW(tr, b)), 26};
            }
            case CPS: {
                String s = demo ? "CPS 7 | 3" : "CPS " + LEFT.size() + " | " + RIGHT.size();
                chip(ctx, tr, x, y, s, 0xFFFFFFFF);
                return new int[] {chipW(tr, s), 12};
            }
            case COMBO: {
                int c = demo ? 5 : combo;
                String s = "Combo " + c;
                chip(ctx, tr, x, y, s, c > 0 ? 0xFFFFD166 : 0xFF9AA0AE);
                return new int[] {chipW(tr, s), 12};
            }
            case EFFECTS: {
                List<String> txt = new ArrayList<>();
                List<Integer> cols = new ArrayList<>();
                if (demo) {
                    txt.add("Speed II · 1:23");
                    cols.add(0xFF7DF29E);
                    txt.add("Weakness · 0:40");
                    cols.add(0xFFFF7A7A);
                } else {
                    for (StatusEffectInstance e : new ArrayList<>(p.getStatusEffects())) {
                        String nm = e.getEffectType().value().getName().getString();
                        if (e.getAmplifier() > 0) nm += " " + (e.getAmplifier() + 1);
                        nm += " · " + (e.isInfinite() ? "∞" : mmss(e.getDuration() / 20));
                        txt.add(nm);
                        cols.add(e.getEffectType().value().isBeneficial() ? 0xFF7DF29E : 0xFFFF7A7A);
                    }
                }
                int mw = 0, yy = y;
                for (int i = 0; i < txt.size(); i++) {
                    chip(ctx, tr, x, yy, txt.get(i), cols.get(i));
                    mw = Math.max(mw, chipW(tr, txt.get(i)));
                    yy += 14;
                }
                return new int[] {mw, yy - y};
            }
            case KEYS: {
                boolean wD = demo || mc.options.forwardKey.isPressed();
                key(ctx, tr, x + 22, y, 20, 20, "W", wD);
                key(ctx, tr, x, y + 22, 20, 20, "A", !demo && mc.options.leftKey.isPressed());
                key(ctx, tr, x + 22, y + 22, 20, 20, "S", !demo && mc.options.backKey.isPressed());
                key(ctx, tr, x + 44, y + 22, 20, 20, "D", !demo && mc.options.rightKey.isPressed());
                key(ctx, tr, x, y + 44, 31, 20, "L " + (demo ? 7 : LEFT.size()), !demo && (lDown || mc.options.attackKey.isPressed()));
                key(ctx, tr, x + 33, y + 44, 31, 20, "R " + (demo ? 3 : RIGHT.size()), !demo && (rDown || mc.options.useKey.isPressed()));
                key(ctx, tr, x, y + 66, 64, 10, "", !demo && mc.options.jumpKey.isPressed());
                return new int[] {64, 76};
            }
            case ARMOR: {
                List<ItemStack> st = new ArrayList<>();
                List<String> txt = new ArrayList<>();
                List<Integer> cols = new ArrayList<>();
                if (demo) {
                    Item[] it = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.NETHERITE_SWORD};
                    String[] pc = {"100%", "87%", "62%", "40%", "73%"};
                    int[] cc = {0xFF7DF29E, 0xFF7DF29E, 0xFF7DF29E, 0xFFFFD166, 0xFF7DF29E};
                    for (int i = 0; i < it.length; i++) {
                        st.add(new ItemStack(it[i]));
                        txt.add(pc[i]);
                        cols.add(cc[i]);
                    }
                } else {
                    EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
                    for (EquipmentSlot sl : slots) {
                        ItemStack s = p.getEquippedStack(sl);
                        if (s.isEmpty()) continue;
                        String t = "";
                        int col = 0xFFD6D9E0;
                        if (s.isDamageable()) {
                            int max = Math.max(1, s.getMaxDamage());
                            int pct = Math.max(0, (max - s.getDamage()) * 100 / max);
                            t = pct + "%";
                            col = pct > 50 ? 0xFF7DF29E : pct > 25 ? 0xFFFFD166 : 0xFFFF6A6A;
                        } else if (s.getCount() > 1) {
                            t = "x" + s.getCount();
                        }
                        st.add(s);
                        txt.add(t);
                        cols.add(col);
                    }
                }
                int yy = y;
                for (int i = 0; i < st.size(); i++) {
                    Ui.hudBox(ctx, x - 2, yy - 1, x + 60, yy + 17, Ui.ACCENT);
                    ctx.drawItem(st.get(i), x, yy);
                    ctx.drawTextWithShadow(tr, txt.get(i), x + 20, yy + 4, cols.get(i));
                    yy += 19;
                }
                return new int[] {62, yy - y};
            }
            case STOCK: {
                Item[][] groups = {{Items.TOTEM_OF_UNDYING}, {Items.END_CRYSTAL}, {Items.OBSIDIAN},
                    {Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE}, {Items.ENDER_PEARL}};
                int[] demoN = {3, 32, 16, 8, 6};
                int yy = y;
                for (int gi = 0; gi < groups.length; gi++) {
                    int n = demo ? demoN[gi] : count(p, groups[gi]);
                    if (n == 0) continue;
                    Ui.hudBox(ctx, x - 2, yy - 1, x + 60, yy + 17, Ui.ACCENT);
                    ctx.drawItem(new ItemStack(groups[gi][0]), x, yy);
                    ctx.drawTextWithShadow(tr, "x" + n, x + 20, yy + 4, 0xFFFFFFFF);
                    yy += 19;
                }
                return new int[] {62, yy - y};
            }
            case TARGET: {
                if (demo) {
                    ItemStack[] g = {new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE),
                        new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.NETHERITE_SWORD)};
                    return drawTarget(ctx, tr, x, y, "Gegner", 14.5f, 20f, 0f, g, false, "-6.5");
                }
                EntityHitResult eh = mc.crosshairTarget instanceof EntityHitResult e2 ? e2 : null;
                LivingEntity t = null;
                if (eh != null && eh.getEntity() instanceof LivingEntity le) t = le;
                else if (lastTarget != null && tickN - lastAttackTick < 100 && lastTarget.isAlive()) t = lastTarget;
                if (t == null) return new int[] {0, 0};
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
                ItemStack[] g = new ItemStack[5];
                for (int i = 0; i < 5; i++) g[i] = t.getEquippedStack(slots[i]);
                String dmg = (t == lastTarget && tickN - lastDmgTick < 40) ? String.format(Locale.ROOT, "-%.1f", lastDmg) : null;
                return drawTarget(ctx, tr, x, y, t.getName().getString(), t.getHealth(), t.getMaxHealth(), t.getAbsorptionAmount(), g, t.hurtTime > 0, dmg);
            }
            case BAR: {
                Config c = Config.INSTANCE;
                float prog = demo ? 0.6f : Math.max(0f, Math.min(1f, p.getAttackCooldownProgress(0.5f)));
                int bw = Math.max(10, c.barW), bh = Math.max(1, c.barH);
                if (!demo && c.barOnly && prog >= 0.999f) return new int[] {0, 0};
                float thr = c.hitAssist ? Math.max(0.5f, c.hitAssistMin / 100f) : 1f;
                int col = prog >= thr - 0.001f ? 0xE04ADE80 : 0xD0FFFFFF;
                ctx.fill(x, y, x + bw, y + bh, 0x50000000);
                ctx.fill(x, y, x + Math.round(bw * prog), y + bh, col);
                return new int[] {bw, bh};
            }
            case REACH: {
                double d;
                if (demo) {
                    d = 3.04;
                } else {
                    if (!(mc.crosshairTarget instanceof EntityHitResult eh2)) return new int[] {0, 0};
                    d = eh2.getPos().distanceTo(p.getEyePos());
                }
                String t = String.format(Locale.ROOT, "%.2f", d);
                ctx.drawTextWithShadow(tr, t, x, y, d <= 3.0 ? 0xFF7DF29E : 0xFFFFD166);
                return new int[] {tr.getWidth(t), 9};
            }
            case NEAR: {
                List<String> txt = new ArrayList<>();
                List<Integer> cols = new ArrayList<>();
                if (demo) {
                    txt.add("Steve · 4 m · 20 HP");
                    cols.add(0xFFFF7A7A);
                    txt.add("Alex · 18 m · 12 HP");
                    cols.add(0xFFFFFFFF);
                } else if (mc.world != null) {
                    List<PlayerEntity> ps = new ArrayList<>();
                    for (PlayerEntity o : mc.world.getPlayers()) {
                        if (o == p || o.isSpectator()) continue;
                        ps.add(o);
                    }
                    ps.sort(Comparator.comparingDouble(o -> o.squaredDistanceTo(p)));
                    for (int i = 0; i < Math.min(4, ps.size()); i++) {
                        PlayerEntity o = ps.get(i);
                        double d = Math.sqrt(o.squaredDistanceTo(p));
                        txt.add(o.getName().getString() + " · " + Math.round(d) + " m · " + Math.round(o.getHealth()) + " HP");
                        cols.add(d <= 6 ? 0xFFFF7A7A : 0xFFFFFFFF);
                    }
                }
                int mw = 0, yy = y;
                for (int i = 0; i < txt.size(); i++) {
                    chip(ctx, tr, x, yy, txt.get(i), cols.get(i));
                    mw = Math.max(mw, chipW(tr, txt.get(i)));
                    yy += 14;
                }
                return new int[] {mw, yy - y};
            }
            case STATS: {
                int s = demo ? 12 : swings, hh = demo ? 7 : hits, k = demo ? 2 : kills;
                int pct = s == 0 ? 0 : hh * 100 / s;
                String txt = "Treffer " + hh + "/" + s + " · " + pct + "% · Kills " + k;
                chip(ctx, tr, x, y, txt, 0xFFFFFFFF);
                return new int[] {chipW(tr, txt), 12};
            }
            case COOLDOWN: {
                Item[] its = {Items.ENDER_PEARL, Items.WIND_CHARGE, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                    Items.CHORUS_FRUIT, Items.SHIELD, Items.MACE};
                int yy = y;
                for (Item it : its) {
                    ItemStack st;
                    float pr;
                    if (demo) {
                        if (it != Items.ENDER_PEARL && it != Items.GOLDEN_APPLE) continue;
                        st = new ItemStack(it);
                        pr = it == Items.ENDER_PEARL ? 0.6f : 0.3f;
                    } else {
                        st = findStack(p, it);
                        if (st == null) continue;
                        pr = p.getItemCooldownManager().getCooldownProgress(st, 0f);
                    }
                    if (pr <= 0.001f) continue;
                    Ui.hudBox(ctx, x - 2, yy - 1, x + 62, yy + 17, Ui.ACCENT);
                    ctx.drawItem(st, x, yy);
                    ctx.fill(x + 20, yy + 6, x + 58, yy + 10, 0x50FFFFFF);
                    ctx.fill(x + 20, yy + 6, x + 20 + Math.round(38 * Math.min(1f, pr)), yy + 10, 0xFFFBBF24);
                    yy += 19;
                }
                return new int[] {64, yy - y};
            }
            case DEATH: {
                Config c = Config.INSTANCE;
                if (!demo && !c.hasDeath) return new int[] {0, 0};
                String s;
                int col = 0xFFFF7A90;
                if (demo) {
                    s = "Tod: 120 64 -30 · 85 m";
                } else {
                    String dim = dimId(mc);
                    if (!dim.equals(c.deathDim)) {
                        s = "Tod in " + dimName(c.deathDim);
                        col = 0xFFAEBBCB;
                    } else {
                        double dd = Math.sqrt(Math.pow(p.getX() - c.deathX, 2) + Math.pow(p.getY() - c.deathY, 2) + Math.pow(p.getZ() - c.deathZ, 2));
                        s = "Tod: " + (int) Math.floor(c.deathX) + " " + (int) Math.floor(c.deathY) + " " + (int) Math.floor(c.deathZ) + " · " + Math.round(dd) + " m";
                    }
                }
                chip(ctx, tr, x, y, s, col);
                return new int[] {chipW(tr, s), 12};
            }
            case COMBAT: {
                int total = Math.max(1, Config.INSTANCE.combatSecs) * 20;
                long since = demo ? 120 : tickN - lastCombatTick;
                if (since >= total) return new int[] {0, 0};
                int secs = (int) Math.ceil((total - since) / 20.0);
                String s = "Kampf " + secs + " s";
                int col = secs > 5 ? 0xFFFF7A90 : 0xFFFBBF24;
                chip(ctx, tr, x, y, s, col);
                int cw = chipW(tr, s);
                ctx.fill(x, y + 13, x + cw, y + 15, 0x50FFFFFF);
                ctx.fill(x, y + 13, x + Math.round(cw * (float) (total - since) / total), y + 15, col);
                return new int[] {cw, 15};
            }
            case CRIT: {
                boolean ok = demo || (p.fallDistance > 0.0 && !p.isOnGround() && !p.isClimbing() && !p.isTouchingWater()
                    && !p.hasVehicle() && !p.isSprinting() && p.getAttackCooldownProgress(0.5f) > 0.9f);
                if (!ok) return new int[] {0, 0};
                chip(ctx, tr, x, y, "CRIT", 0xFF7DF29E);
                return new int[] {chipW(tr, "CRIT"), 12};
            }
            default: {
                List<String> txt = new ArrayList<>();
                List<Integer> cols = new ArrayList<>();
                boolean blink = (tickN / 6) % 2 == 0;
                if (demo) {
                    txt.add("TOTEM NACHLEGEN!");
                    cols.add(0xFFFF5A5A);
                    txt.add("RÜSTUNG KAPUTT!");
                    cols.add(0xFFFFAA33);
                } else {
                    if (on(TOTEM) && popped && !p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && tickN - popTick < 200 && blink) {
                        txt.add("TOTEM NACHLEGEN!");
                        cols.add(0xFFFF5A5A);
                    }
                    if (on(ARMORWARN) && armorLow && blink) {
                        txt.add("RÜSTUNG KAPUTT!");
                        cols.add(0xFFFFAA33);
                    }
                }
                int mw = 0, yy = y;
                for (int i = 0; i < txt.size(); i++) {
                    chip(ctx, tr, x, yy, txt.get(i), cols.get(i));
                    mw = Math.max(mw, chipW(tr, txt.get(i)));
                    yy += 14;
                }
                return new int[] {mw, yy - y};
            }
        }
    }

    private static int[] drawTarget(DrawContext ctx, TextRenderer tr, int x0, int top, String name, float hp, float max, float abs,
                                    ItemStack[] gear, boolean hurt, String dmg) {
        boolean anyGear = false;
        for (ItemStack s : gear) if (!s.isEmpty()) anyGear = true;
        int bw = 124, bh = (anyGear || dmg != null) ? 46 : 28;
        Ui.round2(ctx, x0 - 1, top - 1, x0 + bw + 1, top + bh + 1, hurt ? 0xFFFF5A5A : 0xFF2B3140);
        Ui.round2(ctx, x0, top, x0 + bw, top + bh, 0xD00F1117);
        ctx.drawTextWithShadow(tr, tr.trimToWidth(name, bw - 50), x0 + 6, top + 4, 0xFFFFFFFF);
        float h = Math.max(0f, hp);
        float frac = Math.max(0f, Math.min(1f, h / Math.max(1f, max)));
        int col = frac > 0.6f ? 0xFF4ADE80 : frac > 0.3f ? 0xFFFFD166 : 0xFFFF5A5A;
        int barX = x0 + 6, barY = top + 16, barW = bw - 12;
        ctx.fill(barX, barY, barX + barW, barY + 5, 0xFF2A2F3D);
        ctx.fill(barX, barY, barX + Math.round(barW * frac), barY + 5, col);
        String hs = String.format(Locale.ROOT, "%.1f", h) + (abs > 0 ? " +" + String.format(Locale.ROOT, "%.1f", abs) : "");
        ctx.drawTextWithShadow(tr, hs, x0 + bw - 6 - tr.getWidth(hs), top + 4, 0xFFD6D9E0);
        if (anyGear) {
            int ix = x0 + 6;
            for (ItemStack s : gear) {
                if (!s.isEmpty()) ctx.drawItem(s, ix, top + 26);
                ix += 18;
            }
        }
        if (dmg != null) ctx.drawTextWithShadow(tr, dmg, x0 + bw - 6 - tr.getWidth(dmg), top + 31, 0xFFFF7A7A);
        return new int[] {bw, bh};
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        Ui.round(ctx, x, y, x + w, y + h, down ? 0xC8FFFFFF : 0x90000000);
        if (!label.isEmpty()) {
            ctx.drawTextWithShadow(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, down ? 0xFF111111 : 0xFFFFFFFF);
        }
    }

    /** Kreis um dich auf dem Boden, Radius = deine Reichweite. Schlichte durchgehende Linie. */
    private static void drawCircle(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, float td, int sw, int sh) {
        Config c = Config.INSTANCE;
        if (c.circleWeapon && HitAssist.kindOf(p.getMainHandStack()) < 0) return;
        if (!Overlay3D.begin(mc, td)) return;
        Vec3d feet = p.getLerpedPos(td);
        double r = 3.3;   // normale Minecraft-Reichweite (3.0 + halbe Gegner-Breite), fest

        // Spieler im Kreis sammeln
        java.util.List<PlayerEntity> in = new java.util.ArrayList<>();
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == p || o.isSpectator() || o.isInvisible()) continue;
            double dx = o.getX() - p.getX(), dz = o.getZ() - p.getZ();
            if (Math.sqrt(dx * dx + dz * dz) <= r && Math.abs(o.getY() - p.getY()) < 3.0) in.add(o);
        }
        // Farbe: 0 = rot, gruen sobald ein Spieler im Kreis ist; 1 = immer gruen; 2 = immer rot
        boolean green = c.circleColor == 1 || (c.circleColor == 0 && !in.isEmpty());
        int rgb = green ? 0x22B14C : 0xED1C24;   // schlichtes Paint-Gruen / Paint-Rot
        int th = Math.max(1, Math.min(3, c.circleThick));

        double gy = feet.y + 0.03;
        int n = 72;
        double[] prev = null;
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            double[] cur = Overlay3D.view(feet.x + Math.cos(a) * r, gy, feet.z + Math.sin(a) * r);
            if (prev != null) Overlay3D.line(ctx, prev, cur, rgb, 255, th);
            prev = cur;
        }

        // Markierung: Kasten um jeden Spieler im Kreis (immer gleiche Farbe wie der Kreis, bei "rot" gelb damit man es sieht)
        if (c.circleMark) {
            int mRgb = green ? 0x22B14C : 0xFFF200;
            for (PlayerEntity o : in) {
                Vec3d q = o.getLerpedPos(td);
                double hw = 0.35, h = o.getHeight() + 0.1;
                double[][] bot = new double[4][];
                double[][] top = new double[4][];
                double[] cx = {-hw, hw, hw, -hw}, cz = {-hw, -hw, hw, hw};
                for (int i = 0; i < 4; i++) {
                    bot[i] = Overlay3D.view(q.x + cx[i], q.y, q.z + cz[i]);
                    top[i] = Overlay3D.view(q.x + cx[i], q.y + h, q.z + cz[i]);
                }
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) % 4;
                    Overlay3D.line(ctx, bot[i], bot[j], mRgb, 255, 1);
                    Overlay3D.line(ctx, top[i], top[j], mRgb, 255, 1);
                    Overlay3D.line(ctx, bot[i], top[i], mRgb, 255, 1);
                }
            }
        }
    }

    private static ItemStack findStack(PlayerEntity p, Item item) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty() && st.isOf(item)) return st;
        }
        return null;
    }

    /** Schadenszahlen über dem Gegner. */
    private static void drawPops(DrawContext ctx, MinecraftClient mc, float td) {
        if (POPS.isEmpty()) return;
        if (!Overlay3D.begin(mc, td)) return;
        TextRenderer tr = mc.textRenderer;
        for (Iterator<Pop> it = POPS.iterator(); it.hasNext(); ) {
            Pop f = it.next();
            double age = (tickN - f.born + td) / 20.0;
            if (age > 1.2) {
                it.remove();
                continue;
            }
            double[] v = Overlay3D.view(f.x, f.y + age * 0.7, f.z);
            double[] sc = Overlay3D.screen(v);
            if (sc == null) continue;
            float scale = (float) Math.max(0.8, Math.min(2.0, 2.6 / v[2]));
            int alpha = (int) (255 * Math.min(1.0, (1.2 - age) / 0.4));
            if (alpha < 12) continue;
            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate((float) sc[0], (float) sc[1]);
            m.scale(scale, scale);
            ctx.drawText(tr, f.t, -tr.getWidth(f.t) / 2, -4, (alpha << 24) | f.col, true);
            m.popMatrix();
        }
    }

    /** Lichtstrahl am Ort, an dem du zuletzt gestorben bist. */
    private static void drawDeathBeam(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, float td) {
        Config c = Config.INSTANCE;
        if (!c.hasDeath || !dimId(mc).equals(c.deathDim)) return;
        double d = Math.sqrt(Math.pow(p.getX() - c.deathX, 2) + Math.pow(p.getZ() - c.deathZ, 2));
        if (d < 4.0) return;
        if (!Overlay3D.begin(mc, td)) return;
        double[] a = Overlay3D.view(c.deathX, c.deathY, c.deathZ);
        double[] b = Overlay3D.view(c.deathX, c.deathY + 6.0, c.deathZ);
        Overlay3D.line(ctx, a, b, 0xED1C24, 255, 1);
        double[] sc = Overlay3D.screen(b);
        if (sc != null) {
            TextRenderer tr = mc.textRenderer;
            String s = "Todespunkt · " + Math.round(d) + " m";
            ctx.drawText(tr, s, (int) sc[0] - tr.getWidth(s) / 2, (int) sc[1] - 12, 0xFFFFB4BE, true);
        }
    }

    private static int crossColor(MinecraftClient mc) {
        switch (Config.INSTANCE.crossColor) {
            case 1: return Ui.ACCENT;
            case 2: return Ui.GREEN;
            case 3: return 0xFFFF5A6E;
            case 4:
                if (mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity) return 0xFFFF4D5E;
                return 0xFFFFFFFF;
            default: return 0xFFFFFFFF;
        }
    }

    static final String[] CROSS_STYLES = {"Kreuz", "Punkt", "Kreuz + Punkt", "T-Form"};
    static final String[] CROSS_COLORS = {"Weiß", "Cyan", "Grün", "Rot", "Dynamisch"};

    /** Eigenes Fadenkreuz mit Mitte (cx, cy). preview = im Menü (ohne Treffer-Blitz, feste Farbe). */
    static void drawCrosshair(DrawContext ctx, MinecraftClient mc, int cx, int cy, boolean preview) {
        Config c = Config.INSTANCE;
        int col = preview ? (c.crossColor == 4 ? 0xFFFFFFFF : crossColor(mc)) : crossColor(mc);
        if (!preview && c.crossColor == 4 && tickN - lastHitTick < 6) col = Ui.GREEN;
        int s = Math.max(1, c.crossSize), g = Math.max(0, c.crossGap), t = Math.max(1, c.crossThick);
        int lo = t / 2;
        List<int[]> r = new ArrayList<>();
        int style = c.crossStyle;
        if (style == 0 || style == 2 || style == 3) {
            if (style != 3) r.add(new int[] {cx - lo, cy - lo - g - s, cx - lo + t, cy - lo - g});
            r.add(new int[] {cx - lo, cy - lo + t + g, cx - lo + t, cy - lo + t + g + s});
            r.add(new int[] {cx - lo - g - s, cy - lo, cx - lo - g, cy - lo + t});
            r.add(new int[] {cx - lo + t + g, cy - lo, cx - lo + t + g + s, cy - lo + t});
        }
        if (style == 1 || style == 2) {
            int d = t + 1;
            r.add(new int[] {cx - d / 2, cy - d / 2, cx - d / 2 + d, cy - d / 2 + d});
        }
        for (int[] q : r) ctx.fill(q[0] - 1, q[1] - 1, q[2] + 1, q[3] + 1, 0x90000000);
        for (int[] q : r) ctx.fill(q[0], q[1], q[2], q[3], col);
        if (!preview && tickN - lastHitTick < 6) {
            for (int k = 4; k <= 8; k++) {
                ctx.fill(cx - k, cy - k, cx - k + 1, cy - k + 1, 0xFFFF5A6E);
                ctx.fill(cx + k, cy - k, cx + k + 1, cy - k + 1, 0xFFFF5A6E);
                ctx.fill(cx - k, cy + k, cx - k + 1, cy + k + 1, 0xFFFF5A6E);
                ctx.fill(cx + k, cy + k, cx + k + 1, cy + k + 1, 0xFFFF5A6E);
            }
        }
    }

    static String colorName(int idx) {
        return COLOR_NAMES[Math.floorMod(idx, COLOR_NAMES.length)];
    }

    private static int count(PlayerEntity p, Item[] items) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            for (Item it : items) {
                if (st.isOf(it)) {
                    n += st.getCount();
                    break;
                }
            }
        }
        return n;
    }

    private static String mmss(int sec) {
        return (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
    }

    private static String dirName(Direction d) {
        return switch (d) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "O";
            case WEST -> "W";
            default -> "";
        };
    }
}
