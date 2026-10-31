// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ShieldWave extends BillboardParticle {
   private float scaler = 1.0F;

   public ShieldWave(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.velocityMultiplier = 0.0F;
      this.maxAge = 30;
      this.scale = 0.3F;
      this.setSprite(spriteProvider.getFirst());
   }

   public void tick() {
      super.tick();
      this.scale = this.scale + this.scaler * 0.2F;
      this.scaler = this.scaler - 1.0F / this.maxAge;
      this.alpha = this.alpha - 1.0F / this.maxAge;
      this.scaler = Math.clamp(this.scaler, 0.0F, 1.0F);
      this.alpha = Math.clamp(this.alpha, 0.0F, 1.0F);
   }

   protected RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
   }

   @Environment(EnvType.CLIENT)
   public static class Factory implements ParticleFactory<SimpleParticleType> {
      private final SpriteProvider spriteProvider;

      public Factory(SpriteProvider spriteProvider) {
         this.spriteProvider = spriteProvider;
      }

      @Nullable
      public Particle createParticle(
         SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return new ShieldWave(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
