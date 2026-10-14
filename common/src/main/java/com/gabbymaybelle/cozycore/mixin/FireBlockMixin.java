package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With {@code cozy_core:fire_burns_blocks} off, no block can catch fire or burn away: every
 * block's odds of igniting and of burning out read as zero while the fire ticks. Fire placed by a
 * player, lightning or lava still appears and still hurts, and because nothing around it can
 * burn it goes out the way fire on stone does. Campfires and the Nether's everlasting fire are
 * untouched. The odds are looked up by block state alone, so the rule is read once at the start
 * of each tick and remembered for that tick.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Unique
	private boolean cozyCore$burnsBlocks = true;

	@Inject(method = "tick", at = @At("HEAD"))
	private void cozyCore$readRule(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		cozyCore$burnsBlocks = Rules.is(level, Rules.FIRE_BURNS_BLOCKS);
	}

	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/block/state/BlockState;)I", at = @At("HEAD"), cancellable = true)
	private void cozyCore$noIgniting(BlockState state, CallbackInfoReturnable<Integer> cir) {
		if (!cozyCore$burnsBlocks) {
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "getBurnOdds", at = @At("HEAD"), cancellable = true)
	private void cozyCore$noBurning(BlockState state, CallbackInfoReturnable<Integer> cir) {
		if (!cozyCore$burnsBlocks) {
			cir.setReturnValue(0);
		}
	}
}
