// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class ReviveParticle extends BillboardParticle {
   private final SpriteProvider spriteProv;
   private final double scaler;
   private final double rotX;
   private final double rotZ;
   private double rotY;
   private LivingEntity target = null;
   private Quaternionf QUATERNION = new Quaternionf(0.0F, -0.7F, 0.7F, 0.0F);

   public ReviveParticle(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.maxAge = 20;
      this.alpha = 0.0F;
      this.scale = 0.2F;
      this.scaler = xSpeed;
      this.velocityMultiplier = 0.0F;
      this.rotX = this.random.nextBetween(-180, 180);
      this.rotY = this.random.nextBetween(-180, 180);
      this.rotZ = this.random.nextBetween(-180, 180);
      Entity ent = clientWorld.getEntityById((int)ySpeed);
      if (ent != null && ent instanceof LivingEntity) {
         this.target = (LivingEntity)ent;
      }

      if (this.random.nextBoolean()) {
         this.setColor(1.0F, 1.0F, 0.0F);
      } else {
         this.setColor(0.0F, 1.0F, 0.0F);
      }

      this.spriteProv = spriteProvider;
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));
   }

   public void renderVertex(BillboardParticleSubmittable submittable, Quaternionf rotation, float x, float y, float z, float tickProgress) {
      submittable.render(
         this.getRenderType(),
         x,
         y,
         z,
         this.QUATERNION.x,
         this.QUATERNION.y,
         this.QUATERNION.z,
         this.QUATERNION.w,
         this.getSize(tickProgress),
         this.getMinU(),
         this.getMaxU(),
         this.getMinV(),
         this.getMaxV(),
         ColorHelper.fromFloats(this.alpha, this.red, this.green, this.blue),
         this.getBrightness(tickProgress)
      );
      Quaternionf INVERT = this.QUATERNION.invert();
      submittable.render(
         this.getRenderType(),
         x,
         y,
         z,
         INVERT.x,
         INVERT.y,
         INVERT.z,
         INVERT.w,
         this.getSize(tickProgress),
         this.getMinU(),
         this.getMaxU(),
         this.getMinV(),
         this.getMaxV(),
         ColorHelper.fromFloats(this.alpha, this.red, this.green, this.blue),
         this.getBrightness(tickProgress)
      );
   }

   protected RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
   }

   public void tick() {
      super.tick();
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));
      if (this.target != null) {
         this.setPos(
            this.target.getX(),
            this.target.getY() + this.target.getDimensions(this.target.getPose()).height() / 2.0F,
            this.target.getZ()
         );
      }

      if (!(MasConfig.INSTANCE.TotemEffectOpacity <= 0.0F)) {
         this.rotY += 20.0;
         this.QUATERNION = MathUtility.euler(0.0F, 0.0F, (float)this.rotY);
         this.QUATERNION = MathUtility.euler((float)this.rotZ, (float)this.rotX, (float)(-this.rotZ)).mul(this.QUATERNION);
         this.scale = this.alpha / MasConfig.INSTANCE.TotemEffectOpacity * (float)this.scaler;
         this.alpha = Math.clamp((float)Math.sqrt(Math.sin((double)this.age / this.maxAge * Math.PI)) / 1.2F, 0.0F, 1.0F)
            * MasConfig.INSTANCE.TotemEffectOpacity;
      }
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
         return new ReviveParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
