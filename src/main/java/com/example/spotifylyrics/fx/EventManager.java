// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

@Environment(EnvType.CLIENT)
public class EventManager {
   public static void registerEvents() {
      AttackEntityCallback.EVENT.register((AttackEntityCallback)(playerEntity, world, hand, entity, entityHitResult) -> {
         PlayerAttackManager.clientAttack(playerEntity, entity, world);
         return ActionResult.PASS;
      });
      HudElementRegistry.attachElementBefore(
         VanillaHudElements.CROSSHAIR,
         Identifier.of("spotifylyrics", "elytra_hud"),
         (drawContext, renderTickCounter) -> {
            if (MinecraftClient.getInstance().player != null && MasConfig.on() && MasConfig.INSTANCE.GlideIcon && MinecraftClient.getInstance().player.isGliding()) {
               drawContext.drawTexture(
                  RenderPipelines.GUI_TEXTURED,
                  Identifier.of("maseffects", "textures/hud/elytra_icon.png"),
                  drawContext.getScaledWindowWidth() / 2 - 5,
                  drawContext.getScaledWindowHeight() / 2 + 7,
                  0.0F,
                  0.0F,
                  9,
                  9,
                  9,
                  9
               );
            }
         }
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)minecraftClient -> {
         if (minecraftClient.world != null) {
            if (minecraftClient.world.getPlayers() != null) {
               for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
                  if (player.deathTime == 1) {
                     DeathEvent.runDeath(player);
                  }
               }

               for (Entity entity : minecraftClient.world.getEntities()) {
                  if (entity instanceof EnderPearlEntity end) {
                     EnderPearlTickEvent.tick(end);
                  }
               }
            }
         }
      });
   }
}
