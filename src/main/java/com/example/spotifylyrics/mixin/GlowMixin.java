package com.example.spotifylyrics.mixin;

import com.example.spotifylyrics.ReachCircles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gegner in deiner Reichweite bekommen einen leuchtenden Umriss. */
@Mixin(MinecraftClient.class)
public abstract class GlowMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void alphamod$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity != null && ReachCircles.glowing(entity.getId())) cir.setReturnValue(true);
    }
}
