// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.example.spotifylyrics.fx.PlayerAttackManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ClientWorld.class})
public abstract class MaceMixin {
   @Inject(
      method = {"syncWorldEvent"},
      at = {@At("HEAD")}
   )
   private void MaceAttack(Entity source, int eventId, BlockPos pos, int data, CallbackInfo ci) {
      if (eventId == 2013) {
         Vec3d vec = pos.toCenterPos().add(0.0, 0.5, 0.0);
         PlayerAttackManager.SlamEffect(vec.getX(), vec.getY(), vec.getZ(), 5.0);
      }
   }
}
