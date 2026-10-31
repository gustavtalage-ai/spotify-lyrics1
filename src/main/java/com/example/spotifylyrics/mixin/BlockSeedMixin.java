package com.example.spotifylyrics.mixin;

import com.example.spotifylyrics.Config;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Anti-Koordinaten: alle Blöcke bekommen dieselbe Textur-Drehung, egal wo sie stehen. */
@Mixin(AbstractBlock.class)
public abstract class BlockSeedMixin {
    @Inject(method = "getRenderingSeed", at = @At("HEAD"), cancellable = true, require = 0)
    private void alphamod$sameSeed(BlockState state, BlockPos pos, CallbackInfoReturnable<Long> cir) {
        if (Config.INSTANCE != null && Config.INSTANCE.antiRotation) cir.setReturnValue(0L);
    }
}
