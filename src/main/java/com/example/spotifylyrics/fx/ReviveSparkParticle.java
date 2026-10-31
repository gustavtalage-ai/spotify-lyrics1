// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ReviveSparkParticle extends BillboardParticle {
   private LivingEntity target = null;
   private Vec3d targetDir;

   public ReviveSparkParticle(
      ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed, Random random
   ) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.maxAge = this.random.nextBetween(30, 48);
      this.alpha = MasConfig.INSTANCE.TotemEffectOpacity;
      this.scale = 0.1F;
      this.velocityX = this.random.nextBetween(-10, 10) / 25.0F;
      this.velocityY = this.random.nextBetween(-10, 10) / 25.0F;
      this.velocityZ = this.random.nextBetween(-10, 10) / 25.0F;
      this.velocityMultiplier = 1.0F;
      this.collidesWithWorld = true;
      this.setSprite(spriteProvider.getSprite(random));
      Entity ent = clientWorld.getEntityById((int)xSpeed);
      if (ent != null && ent instanceof LivingEntity) {
         this.target = (LivingEntity)ent;
      }

      if (this.random.nextBoolean()) {
         this.setColor(0.0F, 1.0F, 0.0F);
      } else {
         this.setColor(1.0F, 1.0F, 0.0F);
      }
   }

   public void tick() {
      super.tick();
      if (this.target != null) {
         this.targetDir = new Vec3d(
            this.target.getX() - this.x,
            this.target.getY() + this.target.getDimensions(this.target.getPose()).height() / 2.0F - this.y,
            this.target.getZ() - this.z
         );
         this.targetDir = this.targetDir.normalize();
      }

      if (this.age >= this.maxAge / 5.0F && this.age < this.maxAge / 4.0F) {
         this.velocityMultiplier = 0.0F;
      }

      if (this.target != null
         && this.age >= this.maxAge / 4.0F
         && this.target.getEntityPos().distanceTo(new Vec3d(this.x, this.y, this.z)) < 20.0) {
         this.alpha = Math.clamp(1.0F - (this.age - this.maxAge / 1.5F) / 20.0F, 0.0F, 1.0F) * MasConfig.INSTANCE.TotemEffectOpacity;
         this.velocityMultiplier = 1.0F;
         this.velocityX = this.targetDir.x * ((this.age - 15) * 0.05F);
         this.velocityY = this.targetDir.y * ((this.age - 15) * 0.05F);
         this.velocityZ = this.targetDir.z * ((this.age - 15) * 0.05F);
         if (this.target.getEntityPos().distanceTo(new Vec3d(this.x, this.y, this.z)) < 2.0) {
            this.alpha = 0.0F;
            this.dead = true;
         }
      }
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
         return new ReviveSparkParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ, random);
      }
   }
}
