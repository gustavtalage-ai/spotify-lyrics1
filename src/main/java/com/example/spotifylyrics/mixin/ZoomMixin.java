package com.example.spotifylyrics.mixin;

import com.example.spotifylyrics.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: Sichtfeld verkleinern, solange die Zoom-Taste gedrückt ist. */
@Mixin(GameRenderer.class)
public abstract class ZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void alphamod$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        float z = Zoom.factor();
        if (z > 1.001f) cir.setReturnValue(cir.getReturnValue() / z);
    }
}
