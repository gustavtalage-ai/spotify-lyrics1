// Portiert aus MasEffects (MIT-Lizenz, (c) MasUN0, https://github.com/MasUnoYT/maseffects) – siehe THIRD_PARTY_NOTICES.txt
package com.example.spotifylyrics.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.client.world.ClientWorld;

@Environment(EnvType.CLIENT)
public class PlayerAttackManager {
   private static final net.minecraft.registry.tag.TagKey<Item> HEAD_TAG = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, Identifier.ofVanilla("enchantable/head_armor"));
   private static final net.minecraft.registry.tag.TagKey<Item> CHEST_TAG = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, Identifier.ofVanilla("enchantable/chest_armor"));
   private static final net.minecraft.registry.tag.TagKey<Item> LEGS_TAG = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, Identifier.ofVanilla("enchantable/leg_armor"));
   private static final net.minecraft.registry.tag.TagKey<Item> FEET_TAG = net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.ITEM, Identifier.ofVanilla("enchantable/foot_armor"));
   public static List<Item> DIAMOND_ARMOR = new ArrayList<>();
   public static List<Item> NETHERITE_ARMOR = new ArrayList<>();

   public static void clientAttack(PlayerEntity player, Entity victim, World world) {
      if (!MasConfig.on()) return;
      if (world.isClient()) {
         if (!MasConfig.INSTANCE.ShieldEffect) {
            return;
         }

         if (!player.getMainHandStack().isOf(Items.MACE)) {
            return;
         }

         boolean is_shielding = false;
         if (victim instanceof PlayerEntity victim_player && MathUtility.isShielding(player, victim_player) && player.fallDistance >= 1.5) {
            is_shielding = true;
            ShieldShockwave(victim_player);
         }

         if (!is_shielding
            && victim instanceof LivingEntity livingEntity
            && hasEnchantment(player.getMainHandStack(), Enchantments.BREACH)
            && player.fallDistance > 1.5
            && MasConfig.INSTANCE.ArmorParticles) {
            ArmorParticles(livingEntity);
         }
      }
   }

   public static void ShieldShockwave(PlayerEntity shielder) {
      Vec3d direction = shielder.getRotationVector();
      Vec3d height = new Vec3d(0.0, shielder.getDimensions(shielder.getPose()).height() * 0.6F, 0.0);
      Vec3d pos = shielder.getEntityPos().add(height).add(direction.multiply(0.5));
      shielder.getEntityWorld().addParticleClient(ModParticles.SHIELD_WAVE, pos.getX(), pos.getY(), pos.getZ(), 0.0, 0.0, 0.0);
   }

   public static void SlamEffect(double x, double y, double z, double size) {
      if (!MasConfig.on()) return;
      ClientWorld world = MinecraftClient.getInstance().world;
      if (world != null) {
         size = Math.clamp(size, 1.0, 10.0);
         Random rand = new Random();
         Vec3d r = new Vec3d(rand.nextFloat(-0.6F, 0.6F), rand.nextFloat(-1.2F, 1.2F), rand.nextFloat(-0.6F, 0.6F));
         if (MasConfig.INSTANCE.MaceShockwave) {
            world.addParticleClient(ModParticles.SHOCKWAVE, x, y, z, 0.8, 0.8, 1.5 * MasConfig.INSTANCE.MaceShockwaveSize * size);
            world.addParticleClient(ModParticles.SHOCKWAVE, x, y, z, 0.4, 1.0, 0.35 * MasConfig.INSTANCE.MaceShockwaveSize * size);
            world.addParticleClient(ModParticles.WINDWAVE, x, y, z, 0.8, 0.8, 1.75 * MasConfig.INSTANCE.MaceShockwaveSize * size);
         }

         if (MasConfig.INSTANCE.MaceSpark) {
            world.addParticleClient(ModParticles.FLICK, x + r.x, y + r.y + 0.9F, z + r.z, 0.5, 0.0, 0.0);
            world.addParticleClient(ModParticles.FLICK, x + r.x, y + r.y + 0.9F, z + r.z, 0.2F, 0.0, 0.0);
         }

         if (MasConfig.INSTANCE.MaceFlash) {
            world.addParticleClient(ModParticles.FLASH, x, y, z, 0.0, 0.0, 0.0);
         }
      }
   }

   public static void SpawnParticlesOnHitbox(World world, Vec3d pos, Vec3d size, ParticleEffect particle, int count) {
      for (int i = 0; i <= count; i++) {
         Vec3d r = new Vec3d(
            new Random().nextDouble(-size.x / 2.0, size.x / 2.0),
            new Random().nextDouble(0.0, size.y),
            new Random().nextDouble(-size.x / 2.0, size.x / 2.0)
         );
         world.addParticleClient(
            particle,
            pos.getX() + r.getX(),
            pos.getY() + r.getY(),
            pos.getZ() + r.getZ(),
            r.getX(),
            r.getY(),
            r.getZ()
         );
      }
   }

   public static boolean hasEnchantment(ItemStack stack, RegistryKey<Enchantment> enchantment) {
      return stack.getEnchantments().getEnchantments().toString().contains(enchantment.getValue().toString());
   }

   public static void ArmorParticles(LivingEntity le) {
      List<ItemStack> armor_items = new ArrayList<>();
      if (DIAMOND_ARMOR.isEmpty()) {
      DIAMOND_ARMOR.add(Items.DIAMOND_HELMET);
      DIAMOND_ARMOR.add(Items.DIAMOND_CHESTPLATE);
      DIAMOND_ARMOR.add(Items.DIAMOND_LEGGINGS);
      DIAMOND_ARMOR.add(Items.DIAMOND_BOOTS);
      NETHERITE_ARMOR.add(Items.NETHERITE_HELMET);
      NETHERITE_ARMOR.add(Items.NETHERITE_CHESTPLATE);
      NETHERITE_ARMOR.add(Items.NETHERITE_LEGGINGS);
      NETHERITE_ARMOR.add(Items.NETHERITE_BOOTS);
      }
      armor_items.add(le.getEquippedStack(EquipmentSlot.HEAD));
      armor_items.add(le.getEquippedStack(EquipmentSlot.CHEST));
      armor_items.add(le.getEquippedStack(EquipmentSlot.LEGS));
      armor_items.add(le.getEquippedStack(EquipmentSlot.FEET));

      for (ItemStack item : armor_items) {
         Vec3d size = new Vec3d(
            le.getDimensions(le.getPose()).width(),
            le.getDimensions(le.getPose()).height() / 4.0F,
            le.getDimensions(le.getPose()).width()
         );
         if (item.isIn(HEAD_TAG)) {
            int count = new Random().nextInt(0, 3);
            if (DIAMOND_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(
                  le.getEntityWorld(), le.getEntityPos().add(new Vec3d(0.0, size.getY() * 3.0, 0.0)), size, ModParticles.DIAMOND_SCRAP, count
               );
               continue;
            }

            if (NETHERITE_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(
                  le.getEntityWorld(),
                  le.getEntityPos().add(new Vec3d(0.0, size.getY() * 3.0, 0.0)),
                  size,
                  ModParticles.NETHERITE_SCRAP,
                  count
               );
               continue;
            }
         }

         if (item.isIn(CHEST_TAG)) {
            int countx = new Random().nextInt(0, 3);
            if (DIAMOND_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(
                  le.getEntityWorld(),
                  le.getEntityPos().add(new Vec3d(0.0, size.getY() * 2.0, 0.0)),
                  size,
                  ModParticles.DIAMOND_SCRAP,
                  countx
               );
               continue;
            }

            if (NETHERITE_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(
                  le.getEntityWorld(),
                  le.getEntityPos().add(new Vec3d(0.0, size.getY() * 2.0, 0.0)),
                  size,
                  ModParticles.NETHERITE_SCRAP,
                  countx
               );
               continue;
            }
         }

         if (item.isIn(LEGS_TAG)) {
            int countxx = new Random().nextInt(0, 3);
            if (DIAMOND_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(
                  le.getEntityWorld(),
                  le.getEntityPos().add(new Vec3d(0.0, size.getY() * 3.0, 0.0)),
                  size,
                  ModParticles.DIAMOND_SCRAP,
                  countxx
               );
               continue;
            }

            if (NETHERITE_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(
                  le.getEntityWorld(),
                  le.getEntityPos().add(new Vec3d(0.0, size.getY() * 3.0, 0.0)),
                  size,
                  ModParticles.NETHERITE_SCRAP,
                  countxx
               );
               continue;
            }
         }

         if (item.isIn(FEET_TAG)) {
            int countxxx = new Random().nextInt(0, 3);
            if (DIAMOND_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(le.getEntityWorld(), le.getEntityPos(), size, ModParticles.DIAMOND_SCRAP, countxxx);
            } else if (NETHERITE_ARMOR.contains(item.getItem())) {
               SpawnParticlesOnHitbox(le.getEntityWorld(), le.getEntityPos(), size, ModParticles.NETHERITE_SCRAP, countxxx);
            }
         }
      }
   }
}
