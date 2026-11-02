package com.example.spotifylyrics.mixin;

import com.example.spotifylyrics.WeaponSkins;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Waffen-Skin nur für Waffen, die du selbst hältst – alle anderen Spieler behalten ihre normalen Waffen. */
@Mixin(ItemModelManager.class)
public abstract class SkinModelMixin {
    @Shadow
    public abstract void update(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, World world, HeldItemContext heldItemContext, int seed);

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void alphamod$skin(ItemRenderState renderState, ItemStack stack, ItemDisplayContext displayContext, World world, HeldItemContext held, int seed, CallbackInfo ci) {
        ItemStack s = WeaponSkins.skinned(stack, held);
        if (s != stack) {
            ci.cancel();
            this.update(renderState, s, displayContext, world, held, seed);
        }
    }
}
