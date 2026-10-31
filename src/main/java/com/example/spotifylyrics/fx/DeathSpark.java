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
public class DeathSpark extends BillboardParticle {
   private final SpriteProvider spriteProv;

   public DeathSpark(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.gravityStrength = -0.1F;
      this.collidesWithWorld = false;
      this.velocityMultiplier = 1.0F;
      this.velocityY = 0.0;
      this.scale = this.random.nextBetween(5, 10) * 0.06F;
      this.velocityX = this.random.nextBetween(-10, 10) * 0.02F;
      this.velocityZ = this.random.nextBetween(-10, 10) * 0.02F;
      this.maxAge = 20;
      this.alpha = 1.0F;
      this.spriteProv = spriteProvider;
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));
   }

   public void tick() {
      super.tick();
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));
      this.alpha = (1.0F - (float)this.age / this.maxAge) * 0.45F;
      this.velocityX *= 0.9F;
      this.velocityZ *= 0.9F;
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
         return new DeathSpark(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
