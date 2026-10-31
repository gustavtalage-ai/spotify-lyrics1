// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class MathUtility {
   public static Quaternionf euler(float x, float y, float z) {
      Quaternionf qx = new Quaternionf().fromAxisAngleDeg(new Vector3f(1.0F, 0.0F, 0.0F), x);
      Quaternionf qy = new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 1.0F, 0.0F), y);
      Quaternionf qz = new Quaternionf().fromAxisAngleDeg(new Vector3f(0.0F, 0.0F, 1.0F), z);
      return qx.mul(qy).mul(qz);
   }

   public static boolean isShielding(PlayerEntity player, PlayerEntity victim) {
      if (!victim.isBlocking()) {
         return false;
      } else {
         double shield_angle = victim.getYaw();
         Vec3d attack_dir = player.getEntityPos().subtract(victim.getEntityPos()).normalize();
         double attack_angle = Math.atan2(attack_dir.x, attack_dir.z);
         attack_angle = Math.toDegrees(attack_angle);
         return angleDif(shield_angle, attack_angle) < 90.0 || angleDif(shield_angle, attack_angle) > 270.0;
      }
   }

   public static double angleDif(double angle1, double angle2) {
      if (angle1 < 0.0) {
         angle1 += 360.0;
      }

      if (angle2 < 0.0) {
         angle2 += 360.0;
      }

      double result = angle2 + angle1;
      if (result < 0.0) {
         result += 360.0;
      }

      if (result > 360.0) {
         result -= 360.0;
      }

      return result;
   }
}
