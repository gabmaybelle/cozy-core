package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fire spares blocks at home (inside a Hearth Lantern's reach), and everywhere with
 * {@code cozy_core:fire_burns_blocks} off. Fire placed by a player, lightning or lava still appears
 * and still hurts.
 * <ul>
 * <li>A fire that is itself at home, or anywhere while the rule is off, sees every block's odds of
 * igniting and burning out as zero for its tick, so nothing around it can burn and it goes out the
 * way fire on stone does. The odds are looked up by block state alone, so this is read once at
 * the start of the tick and put back at the end.</li>
 * <li>A fire outside, next to home, can't burn a block that is at home or spread into it: its
 * burn-out and spread steps are checked spot by spot.</li>
 * </ul>
 * Campfires and the Nether's everlasting fire are untouched.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
	@Unique
	private boolean cozyCore$burnsBlocks = true;

	@Inject(method = "tick", at = @At("HEAD"))
	private void cozyCore$readRule(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		cozyCore$burnsBlocks = Rules.is(level, Rules.FIRE_BURNS_BLOCKS) && !HearthLanterns.guards(level, pos);
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void cozyCore$forgetRule(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		cozyCore$burnsBlocks = true;
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

	/**
	 * A block at home doesn't burn away, even next to a fire outside. NeoForge adds the side the
	 * fire is on to this method, so there's one hook for each shape and each is optional; the
	 * fire-on-the-edge game test on both loaders fails if neither applies.
	 */
	@Inject(method = "checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cozyCore$spareHome(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
		if (level instanceof ServerLevel server && HearthLanterns.guards(server, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "checkBurnOut(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void cozyCore$spareHomeNeoForge(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face, CallbackInfo ci) {
		cozyCore$spareHome(level, pos, chance, random, age, ci);
	}

	/** Fire doesn't spread into home, even from a fire outside. */
	@Inject(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
	private void cozyCore$noSpreadingHome(LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (level instanceof ServerLevel server && HearthLanterns.guards(server, pos)) {
			cir.setReturnValue(0);
		}
	}
}
