// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.example.spotifylyrics.fx.TotemEvent;
import net.minecraft.entity.Entity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.listener.TickablePacketListener;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientConnectionState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ClientPlayNetworkHandler.class})
public abstract class TotemMixin extends ClientCommonNetworkHandler implements ClientPlayPacketListener, TickablePacketListener {
   @Shadow
   private ClientWorld world;

   protected TotemMixin(MinecraftClient client, ClientConnection connection, ClientConnectionState connectionState) {
      super(client, connection, connectionState);
   }

   @Inject(
      method = {"onEntityStatus"},
      at = {@At("HEAD")}
   )
   private void onEntityTotem(EntityStatusS2CPacket packet, CallbackInfo ci) {
      NetworkThreadUtils.forceMainThread(packet, this, this.client.getPacketApplyBatcher());
      Entity entity = packet.getEntity(this.world);
      if (entity != null && packet.getStatus() == 35) {
         TotemEvent.run(entity);
      }
   }
}
