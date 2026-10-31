// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;

@Environment(EnvType.CLIENT)
public class TotemEvent {
   public static void run(Entity popper) {
      if (!MasConfig.on()) return;
      if (MasConfig.INSTANCE.CustomTotemEffect) {
         for (int i = 1; i < 18; i++) {
            double scaler = 2.5;
            popper.getEntityWorld()
               .addParticleClient(
                  ModParticles.REVIVE,
                  popper.getX(),
                  popper.getY() + popper.getDimensions(popper.getPose()).height() / 2.0F,
                  popper.getZ(),
                  scaler,
                  popper.getId(),
                  0.0
               );
         }

         for (int i = 1; i < 100; i++) {
            popper.getEntityWorld()
               .addParticleClient(
                  ModParticles.REVIVE_SPARK,
                  popper.getX(),
                  popper.getY() + popper.getDimensions(popper.getPose()).height() / 2.0F,
                  popper.getZ(),
                  popper.getId(),
                  0.0,
                  0.0
               );
         }
      }
   }
}
