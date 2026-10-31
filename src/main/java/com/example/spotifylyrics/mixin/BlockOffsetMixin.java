package com.example.spotifylyrics.mixin;

import com.example.spotifylyrics.Config;
import net.minecraft.block.AbstractBlock;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Anti-Koordinaten: Blumen, Gras usw. sitzen überall gleich versetzt (Versatz verrät sonst die Position). */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockOffsetMixin {
    @ModifyVariable(method = "getModelOffset", at = @At("HEAD"), argsOnly = true, require = 0)
    private BlockPos alphamod$samePos(BlockPos pos) {
        if (Config.INSTANCE != null && Config.INSTANCE.antiRotation && Config.INSTANCE.antiOffset) return BlockPos.ORIGIN;
        return pos;
    }
}
