package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HomeLife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The garden at home: after grass's own random tick (spreading), now and then a flower grows on it
 * ({@link HomeLife#tickGrass}). Grass's tick is inherited from this class, shared with mycelium.
 */
@Mixin(SpreadingSnowyDirtBlock.class)
public abstract class SpreadingSnowyDirtBlockMixin {
	@Inject(method = "randomTick", at = @At("TAIL"))
	private void cozyCore$garden(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if ((Object) this instanceof GrassBlock) {
			HomeLife.tickGrass(level, pos, random, HomeLife.GARDEN_CHANCE);
		}
	}
}
