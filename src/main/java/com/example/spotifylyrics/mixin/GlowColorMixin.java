package com.example.spotifylyrics.mixin;

import com.example.spotifylyrics.ReachCircles;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Farbe des Umrisses: grün für Gegner, die du treffen kannst. */
@Mixin(Entity.class)
public abstract class GlowColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void alphamod$glowColor(CallbackInfoReturnable<Integer> cir) {
        if (ReachCircles.glowing(((Entity) (Object) this).getId())) cir.setReturnValue(0x3DFF5A);
    }
}
