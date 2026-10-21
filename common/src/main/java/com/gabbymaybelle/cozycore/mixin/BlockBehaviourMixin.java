package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.HomeComforts;
import com.gabbymaybelle.cozycore.HomeLife;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glass, ice and bookshelves ({@code cozy_core:keeps_at_home}) broken at home drop themselves, as
 * with Silk Touch: glass comes back instead of shattering, a bookshelf instead of three books.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
	private void cozyCore$keepAtHome(BlockState state, LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
		if (!state.is(HomeComforts.KEEPS_AT_HOME)) {
			return;
		}
		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin != null && HearthLanterns.protects(params.getLevel(), BlockPos.containing(origin))) {
			cir.setReturnValue(List.of(new ItemStack(state.getBlock())));
		}
	}

	/**
	 * Candles (and candle cakes) get random ticks, so that at home they can light at dusk and go
	 * out in the morning ({@link HomeLife#tickCandle}). The game asks each block state this once, at
	 * start-up; out in the wild a candle's tick does nothing.
	 */
	@Inject(method = "isRandomlyTicking", at = @At("HEAD"), cancellable = true)
	private void cozyCore$candlesTick(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof AbstractCandleBlock) {
			cir.setReturnValue(true);
		}
	}

	/** Candles don't have a random tick of their own, so theirs lands here, in the empty default. */
	@Inject(method = "randomTick", at = @At("HEAD"))
	private void cozyCore$candleTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if ((Object) this instanceof AbstractCandleBlock) {
			HomeLife.tickCandle(state, level, pos);
		}
	}
}
