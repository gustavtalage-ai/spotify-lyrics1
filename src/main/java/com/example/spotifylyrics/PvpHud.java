package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
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
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PvP-Client: kleine Anzeigen und Helfer. Alle einzeln schaltbar (Bits in Config.pvpMods).
 * Alles läuft nur im eigenen Spiel auf deinem Rechner (nur Anzeige bzw. normale Tasten).
 */
final class PvpHud {
    private PvpHud() {}

    static final int CPS = 0, COMBO = 1, ARMOR = 2, EFFECTS = 3, KEYS = 4, INFO = 5, STOCK = 6, TARGET = 7,
        BAR = 8, REACH = 9, HITSOUND = 10, TOTEM = 11, LOWHP = 12, SPRINT = 13, ZOOM = 14, FRIENDS = 15;
    /** Anzeigenamen in derselben Reihenfolge wie die Bits. */
    static final String[] NAMES = {"CPS", "Combo", "Rüstung", "Effekte", "Tasten", "Info (FPS, Ping, XYZ)", "Vorrat",
        "Ziel-HUD", "Angriffs-Balken", "Reichweite", "Treffer-Sound", "Totem-Warnung", "Leben-Warnung", "Auto-Sprint",
        "Zoom (Taste Z)", "Freunde schützen"};
    static final int HUD_COUNT = 10;

    static boolean on(int bit) {
        return ((Config.INSTANCE.pvpMods >> bit) & 1) != 0;
    }

    static void flip(int bit) {
        Config.INSTANCE.pvpMods ^= (1 << bit);
        Config.INSTANCE.save();
    }

    static void setAll(int fromBit, int toBitExcl, boolean value) {
        for (int b = fromBit; b < toBitExcl; b++) {
            if (value) Config.INSTANCE.pvpMods |= (1 << b);
            else Config.INSTANCE.pvpMods &= ~(1 << b);
        }
        Config.INSTANCE.save();
    }

    // ---------- Zustand ----------
    private static long tickN = 0;
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean lDown, rDown;
    private static LivingEntity lastTarget;
    private static long lastAttackTick = -1000, lastHitTick = -1000;
    private static int prevTargetHurt, prevSelfHurt;
    private static int combo = 0;
    private static boolean sprintForced = false;
    private static boolean zoomed = false;
    private static int origFov = 70;

    static void register() {
        // Freunde nicht schlagen + Ziel merken (läuft nach dem Hit-Assistenten, der zu frühe Schläge schon abgefangen hat)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient()) return ActionResult.PASS;
            if (on(FRIENDS) && entity instanceof PlayerEntity pe && isFriend(pe)) return ActionResult.FAIL;
            if (entity instanceof LivingEntity le) {
                lastTarget = le;
                lastAttackTick = tickN;
                prevTargetHurt = le.hurtTime;
            }
            return ActionResult.PASS;
        });
        // Auch das Schwingen bei einem Freund verhindern
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!on(FRIENDS) || client.currentScreen != null) return false;
            return client.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof PlayerEntity pe && isFriend(pe);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(PvpHud::resetZoom);
    }

    static boolean isFriend(PlayerEntity p) {
        return Config.INSTANCE.friends.contains(p.getName().getString().toLowerCase(Locale.ROOT));
    }

    private static void resetZoom(MinecraftClient mc) {
        if (zoomed) {
            try {
                mc.options.getFov().setValue(origFov);
            } catch (Throwable ignored) {
            }
            zoomed = false;
        }
    }

    // ---------- Tick ----------
    static void tick(MinecraftClient mc) {
        tickN++;
        ClientPlayerEntity p = mc.player;
        long win = GLFW.glfwGetCurrentContext();
        if (p == null || mc.world == null || win == 0) {
            resetZoom(mc);
            combo = 0;
            lastTarget = null;
            if (sprintForced) {
                mc.options.sprintKey.setPressed(false);
                sprintForced = false;
            }
            return;
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

        // Treffer erkennen: das Ziel wurde kurz nach deinem Schlag "verletzt"
        if (lastTarget != null) {
            if (tickN - lastAttackTick > 100 || !lastTarget.isAlive()) {
                lastTarget = null;
            } else {
                int h = lastTarget.hurtTime;
                if (tickN - lastAttackTick <= 10 && h > prevTargetHurt && h > 0) {
                    combo++;
                    lastHitTick = tickN;
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
        if (p.hurtTime > prevSelfHurt && p.hurtTime > 0) combo = 0;
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

        // Zoom: Taste Z halten
        boolean zd = on(ZOOM) && free && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
        if (zd) {
            if (!zoomed) {
                origFov = mc.options.getFov().getValue();
                zoomed = true;
            }
            int target = Math.max(30, Math.min(110, Config.INSTANCE.zoomFov));
            if (mc.options.getFov().getValue() != target) mc.options.getFov().setValue(target);
        } else if (zoomed) {
            resetZoom(mc);
        }
    }

    private static void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.removeFirst();
    }

    // ---------- Zeichnen ----------
    static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        try {
            draw(ctx);
        } catch (Exception e) {
            SpotifyLyricsClient.LOGGER.debug("PvP-HUD-Fehler", e);
        }
    }

    private static int chip(DrawContext ctx, TextRenderer tr, int x, int y, String text, int color) {
        int w = tr.getWidth(text) + 8;
        Ui.round(ctx, x, y, x + w, y + 12, 0x90000000);
        ctx.drawTextWithShadow(tr, text, x + 4, y + 2, color);
        return y + 14;
    }

    private static void draw(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden) return;
        if (Config.INSTANCE.pvpMods == 0) return;
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int cxm = w / 2, cym = h / 2;

        // ----- links oben: Info, CPS, Combo, Effekte -----
        int y = 4;
        if (on(INFO)) {
            ClientPlayNetworkHandler nh = mc.getNetworkHandler();
            PlayerListEntry pe = nh == null ? null : nh.getPlayerListEntry(p.getUuid());
            String ping = pe != null && pe.getLatency() > 0 ? " · " + pe.getLatency() + " ms" : "";
            y = chip(ctx, tr, 4, y, "FPS " + mc.getCurrentFps() + ping, 0xFFFFFFFF);
            y = chip(ctx, tr, 4, y, p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + " · " + dirName(p.getHorizontalFacing()), 0xFFD6D9E0);
        }
        if (on(CPS)) y = chip(ctx, tr, 4, y, "CPS " + LEFT.size() + " | " + RIGHT.size(), 0xFFFFFFFF);
        if (on(COMBO)) y = chip(ctx, tr, 4, y, "Combo " + combo, combo > 0 ? 0xFFFFD166 : 0xFF9AA0AE);
        if (on(EFFECTS)) {
            List<StatusEffectInstance> effs = new ArrayList<>(p.getStatusEffects());
            for (StatusEffectInstance e : effs) {
                String nm = e.getEffectType().value().getName().getString();
                if (e.getAmplifier() > 0) nm += " " + (e.getAmplifier() + 1);
                nm += " · " + (e.isInfinite() ? "∞" : mmss(e.getDuration() / 20));
                y = chip(ctx, tr, 4, y, nm, e.getEffectType().value().isBeneficial() ? 0xFF7DF29E : 0xFFFF7A7A);
            }
        }

        // ----- links Mitte: Tasten -----
        if (on(KEYS)) drawKeys(ctx, tr, mc, 4, cym + 14);

        // ----- rechts: Rüstung + Vorrat -----
        int rx = w - 66;
        int ry = cym - 54;
        if (on(ARMOR)) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
            for (EquipmentSlot sl : slots) {
                ItemStack st = p.getEquippedStack(sl);
                if (st.isEmpty()) continue;
                Ui.round(ctx, rx - 2, ry - 1, w - 3, ry + 17, 0x70000000);
                ctx.drawItem(st, rx, ry);
                if (st.isDamageable()) {
                    int max = Math.max(1, st.getMaxDamage());
                    int pct = Math.max(0, (max - st.getDamage()) * 100 / max);
                    int col = pct > 50 ? 0xFF7DF29E : pct > 25 ? 0xFFFFD166 : 0xFFFF6A6A;
                    ctx.drawTextWithShadow(tr, pct + "%", rx + 20, ry + 4, col);
                } else if (st.getCount() > 1) {
                    ctx.drawTextWithShadow(tr, "x" + st.getCount(), rx + 20, ry + 4, 0xFFD6D9E0);
                }
                ry += 19;
            }
            ry += 4;
        }
        if (on(STOCK)) {
            Item[][] groups = {{Items.TOTEM_OF_UNDYING}, {Items.END_CRYSTAL}, {Items.OBSIDIAN},
                {Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE}, {Items.ENDER_PEARL}};
            for (int gi = 0; gi < groups.length; gi++) {
                int n = count(p, groups[gi]);
                if (n == 0 && gi != 0) continue;
                Ui.round(ctx, rx - 2, ry - 1, w - 3, ry + 17, 0x70000000);
                ctx.drawItem(new ItemStack(groups[gi][0]), rx, ry);
                ctx.drawTextWithShadow(tr, "x" + n, rx + 20, ry + 4, n == 0 ? 0xFFFF6A6A : 0xFFFFFFFF);
                ry += 19;
            }
        }

        // ----- Mitte: Balken, Reichweite, Ziel, Warnungen -----
        EntityHitResult eh = mc.crosshairTarget instanceof EntityHitResult e2 ? e2 : null;
        if (on(BAR)) {
            float prog = Math.max(0f, Math.min(1f, p.getAttackCooldownProgress(0.5f)));
            float thr = Config.INSTANCE.hitAssist ? Math.max(0.5f, Config.INSTANCE.hitAssistMin / 100f) : 1f;
            int col = prog >= thr - 0.001f ? 0xFF4ADE80 : prog >= 0.5f ? 0xFFFFD166 : 0xFFFF5A5A;
            int bx = cxm - 20, by = cym + 12;
            ctx.fill(bx - 1, by - 1, bx + 41, by + 4, 0x90000000);
            ctx.fill(bx, by, bx + Math.round(40 * prog), by + 3, col);
        }
        if (on(REACH) && eh != null) {
            double d = eh.getPos().distanceTo(p.getEyePos());
            String t = String.format(Locale.ROOT, "%.2f", d);
            ctx.drawTextWithShadow(tr, t, cxm - tr.getWidth(t) / 2, cym + 19, d <= 3.0 ? 0xFF7DF29E : 0xFFFFD166);
        }
        if (on(TARGET)) {
            LivingEntity t = null;
            if (eh != null && eh.getEntity() instanceof LivingEntity le) t = le;
            else if (lastTarget != null && tickN - lastAttackTick < 100 && lastTarget.isAlive()) t = lastTarget;
            if (t != null) drawTarget(ctx, tr, p, t, cxm, cym + 34);
        }
        if (on(TOTEM) && !p.isCreative() && !p.isSpectator()
            && !p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) && !p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            boolean has = count(p, new Item[] {Items.TOTEM_OF_UNDYING}) > 0;
            if (!has || (tickN / 6) % 2 == 0) {
                String msg = has ? "TOTEM IN DIE HAND!" : "KEINE TOTEMS!";
                int mw = tr.getWidth(msg) + 10;
                Ui.round(ctx, cxm - mw / 2, cym - 62, cxm + mw / 2, cym - 48, 0xA0000000);
                ctx.drawTextWithShadow(tr, msg, cxm - tr.getWidth(msg) / 2, cym - 59, has ? 0xFFFF5A5A : 0xFFFFAA33);
            }
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

    private static void drawTarget(DrawContext ctx, TextRenderer tr, ClientPlayerEntity p, LivingEntity t, int cx, int top) {
        boolean anyGear = false;
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        for (EquipmentSlot sl : slots) if (!t.getEquippedStack(sl).isEmpty()) anyGear = true;
        int bw = 124, bh = anyGear ? 46 : 28;
        int x0 = cx - bw / 2;
        boolean hurt = t.hurtTime > 0;
        Ui.round2(ctx, x0 - 1, top - 1, x0 + bw + 1, top + bh + 1, hurt ? 0xFFFF5A5A : 0xFF303546);
        Ui.round2(ctx, x0, top, x0 + bw, top + bh, 0xD00F1117);
        String name = tr.trimToWidth(t.getName().getString(), bw - 12);
        ctx.drawTextWithShadow(tr, name, x0 + 6, top + 4, 0xFFFFFFFF);
        float hp = Math.max(0f, t.getHealth());
        float max = Math.max(1f, t.getMaxHealth());
        float frac = Math.max(0f, Math.min(1f, hp / max));
        int col = frac > 0.6f ? 0xFF4ADE80 : frac > 0.3f ? 0xFFFFD166 : 0xFFFF5A5A;
        int barX = x0 + 6, barY = top + 16, barW = bw - 12;
        ctx.fill(barX, barY, barX + barW, barY + 5, 0xFF2A2F3D);
        ctx.fill(barX, barY, barX + Math.round(barW * frac), barY + 5, col);
        float abs = t.getAbsorptionAmount();
        String hs = String.format(Locale.ROOT, "%.1f", hp) + (abs > 0 ? " +" + String.format(Locale.ROOT, "%.1f", abs) : "");
        ctx.drawTextWithShadow(tr, hs, x0 + bw - 6 - tr.getWidth(hs), top + 4, 0xFFD6D9E0);
        if (anyGear) {
            int ix = x0 + 6;
            for (EquipmentSlot sl : slots) {
                ItemStack st = t.getEquippedStack(sl);
                if (!st.isEmpty()) ctx.drawItem(st, ix, top + 26);
                ix += 18;
            }
        }
    }

    private static void drawKeys(DrawContext ctx, TextRenderer tr, MinecraftClient mc, int x, int y) {
        key(ctx, tr, x + 22, y, 20, 20, "W", mc.options.forwardKey.isPressed());
        key(ctx, tr, x, y + 22, 20, 20, "A", mc.options.leftKey.isPressed());
        key(ctx, tr, x + 22, y + 22, 20, 20, "S", mc.options.backKey.isPressed());
        key(ctx, tr, x + 44, y + 22, 20, 20, "D", mc.options.rightKey.isPressed());
        key(ctx, tr, x, y + 44, 31, 20, "L " + LEFT.size(), lDown || mc.options.attackKey.isPressed());
        key(ctx, tr, x + 33, y + 44, 31, 20, "R " + RIGHT.size(), rDown || mc.options.useKey.isPressed());
        key(ctx, tr, x, y + 66, 64, 10, "", mc.options.jumpKey.isPressed());
    }

    private static void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, boolean down) {
        Ui.round(ctx, x, y, x + w, y + h, down ? 0xC8FFFFFF : 0x90000000);
        if (!label.isEmpty()) {
            ctx.drawTextWithShadow(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, down ? 0xFF111111 : 0xFFFFFFFF);
        }
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
