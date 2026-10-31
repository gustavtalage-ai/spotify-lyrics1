// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.BillboardParticleSubmittable;
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
public class WindParticle extends BillboardParticle {
   private static final Quaternionf QUATERNION = new Quaternionf(0.0, 0.7, 0.7F, 0.0);
   private static final Quaternionf ROTATED_QUATERNION = new Quaternionf(-0.2705981, 0.6532815, 0.6532815, 0.2705981);
   private final SpriteProvider provider;
   private float scaler = 1.0F;
   private float sizer = 1.0F;
   private float alpha_ctrl = 1.0F;

   public WindParticle(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.scaler = (float)xSpeed;
      this.provider = spriteProvider;
      this.alpha_ctrl = (float)ySpeed;
      this.alpha = Math.clamp(this.alpha_ctrl * MasConfig.INSTANCE.MaceShockwaveOpacity, 0.0F, 1.0F);
      this.scale = 0.5F;
      this.maxAge = 40;
      this.velocityMultiplier = 0.0F;
      this.sizer = (float)zSpeed * 0.1F;
      this.setSprite(spriteProvider.getFirst());
   }

   public void renderVertex(BillboardParticleSubmittable submittable, Quaternionf rotation, float x, float y, float z, float tickProgress) {
      submittable.render(
         this.getRenderType(),
         x,
         y,
         z,
         QUATERNION.x,
         QUATERNION.y,
         QUATERNION.z,
         QUATERNION.w,
         this.getSize(tickProgress),
         this.getMinU(),
         this.getMaxU(),
         this.getMinV(),
         this.getMaxV(),
         ColorHelper.fromFloats(this.alpha, this.red, this.green, this.blue),
         this.getBrightness(tickProgress)
      );
      submittable.render(
         this.getRenderType(),
         x,
         y,
         z,
         ROTATED_QUATERNION.x,
         ROTATED_QUATERNION.y,
         ROTATED_QUATERNION.z,
         ROTATED_QUATERNION.w,
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
      this.scale = this.scale + this.scaler * this.sizer;
      this.scaler = this.scaler - 1.0F / this.maxAge;
      this.alpha_ctrl = this.alpha_ctrl - 1.0F / this.maxAge;
      this.scaler = Math.clamp(this.scaler, 0.0F, 1.0F);
      this.alpha = Math.clamp(this.alpha_ctrl * MasConfig.INSTANCE.MaceShockwaveOpacity, 0.0F, 1.0F);
      this.setSprite(this.provider.getSprite(Math.min(this.age, this.maxAge / 2), this.maxAge / 2));
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
         return new WindParticle(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
