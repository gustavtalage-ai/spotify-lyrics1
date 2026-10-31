// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EnderPearlTrail extends BillboardParticle {
   private final SpriteProvider spriteProv;
   private float alpha_ctrl = 1.0F;

   public EnderPearlTrail(ClientWorld clientWorld, double x, double y, double z, SpriteProvider spriteProvider, double xSpeed, double ySpeed, double zSpeed) {
      super(clientWorld, x, y, z, xSpeed, ySpeed, zSpeed, spriteProvider.getFirst());
      this.gravityStrength = 0.0F;
      this.velocityX = xSpeed + this.random.nextBetween(-10, 10) * 0.005F;
      this.velocityY = ySpeed + this.random.nextBetween(-10, 10) * 0.005F;
      this.velocityZ = zSpeed + this.random.nextBetween(-10, 10) * 0.005F;
      this.maxAge = 10;
      this.spriteProv = spriteProvider;
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));

      assert MinecraftClient.getInstance().player != null;

      if (MinecraftClient.getInstance().player.getEntityPos().distanceTo(new Vec3d(x, y, z)) < 6.0) {
         this.alpha_ctrl -= 0.8F;
      }

      this.alpha = this.alpha_ctrl * MasConfig.INSTANCE.PearlTrailOpacity;
   }

   public void tick() {
      super.tick();
      this.setSprite(this.spriteProv.getSprite(this.age, this.maxAge));
      this.scale = (1.0F - (float)this.age / this.maxAge) * 0.15F;
      this.alpha_ctrl -= 0.1F;

      assert MinecraftClient.getInstance().player != null;

      if (MinecraftClient.getInstance().player.getEntityPos().distanceTo(new Vec3d(this.x, this.y, this.z)) < 6.0) {
         this.alpha_ctrl -= 0.8F;
      }

      this.alpha = this.alpha_ctrl * MasConfig.INSTANCE.PearlTrailOpacity;
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
         return new EnderPearlTrail(world, x, y, z, this.spriteProvider, velocityX, velocityY, velocityZ);
      }
   }
}
