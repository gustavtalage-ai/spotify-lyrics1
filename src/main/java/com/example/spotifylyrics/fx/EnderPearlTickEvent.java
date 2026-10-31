// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class EnderPearlTickEvent {
   public static void tick(EnderPearlEntity entity) {
      if (!MasConfig.on()) return;
      if (MasConfig.INSTANCE.PearlTrailParticles) {
         Vec3d motion = entity.getMovement().normalize().multiply(0.05F);

         for (int i = 0; i < 3; i++) {
            entity.getEntityWorld()
               .addParticleClient(
                  ModParticles.ENDER_PEARL_TRAIL,
                  entity.getX(),
                  entity.getY(),
                  entity.getZ(),
                  motion.getX(),
                  motion.getY(),
                  motion.getZ()
               );
         }
      }
   }
}
