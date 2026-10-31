// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.example.spotifylyrics.fx.MasConfig;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.TotemParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({TotemParticle.class})
public abstract class TotemParticleMixin extends AnimatedParticle {
   protected TotemParticleMixin(ClientWorld world, double x, double y, double z, SpriteProvider spriteProvider, float upwardsAcceleration) {
      super(world, x, y, z, spriteProvider, upwardsAcceleration);
   }

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void injectAlpha(
      ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteProvider spriteProvider, CallbackInfo ci
   ) {
      if (MasConfig.on() && MasConfig.INSTANCE.CustomTotemEffect) {
         this.alpha = 0.0F;
      }
   }
}
