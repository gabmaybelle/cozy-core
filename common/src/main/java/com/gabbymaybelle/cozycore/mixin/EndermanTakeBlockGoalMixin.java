package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The goal that picks a block up never starts for an enderman at home, nor anywhere with
 * {@code cozy_core:endermen_take_blocks} off. An enderman takes a block up to two blocks from where
 * it stands, so "at home" here reaches {@link #COZY_CORE$ARM} blocks further than the lantern's
 * reach: one standing just outside can't take a block from inside.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
	@Unique
	private static final int COZY_CORE$ARM = 3;

	@Shadow
	@Final
	private EnderMan enderman;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void cozyCore$leaveBlocksAlone(CallbackInfoReturnable<Boolean> cir) {
		if (enderman.level() instanceof ServerLevel level
				&& (!Rules.is(level, Rules.ENDERMEN_TAKE_BLOCKS) || HearthLanterns.guards(level, enderman.blockPosition(), COZY_CORE$ARM))) {
			cir.setReturnValue(false);
		}
	}
}
