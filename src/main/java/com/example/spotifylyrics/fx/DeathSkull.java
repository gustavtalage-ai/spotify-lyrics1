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

@Environment(EnvType.CLIENT)
public class DeathSkull extends BillboardParticle {
   private final SpriteProvider spriteProv;

   public DeathSkull(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.gravityStrength = 0.0F;
      this.collidesWithWorld = false;
      this.velocityY = 0.2;
      this.scale = 1.0F;
      this.velocityX = 0.0;
      this.velocityZ = 0.0;
      this.maxAge = 60;
      this.alpha = 1.0F;
      this.spriteProv = spriteProvider;
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));
   }

   public void tick() {
      super.tick();
      this.setSprite(this.spriteProv.getSprite(Math.clamp((long)this.age, 0, 30), 30));
      if (this.age > 30) {
         this.alpha -= 0.033333335F;
      }

      this.velocityY *= 0.95F;
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

      public Particle createParticle(
         SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return new DeathSkull(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
