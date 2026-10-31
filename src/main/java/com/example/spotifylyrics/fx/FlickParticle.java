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
public class FlickParticle extends BillboardParticle {
   public float time;
   public float scaler;

   public FlickParticle(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.alpha = 1.0F;
      this.scaler = (float)xSpeed;
      this.maxAge = 20;
      this.time = 0.0F;
      this.velocityMultiplier = 0.0F;
      this.setSprite(spriteProvider.getFirst());
   }

   public void tick() {
      super.tick();
      this.alpha = Math.clamp((1.0F - this.time) * 1.5F, 0.2F, 1.0F) - 0.2F;
      this.time = this.time + 1.0F / this.maxAge;
      this.scale = this.alpha * this.scaler;
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
         return new FlickParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
