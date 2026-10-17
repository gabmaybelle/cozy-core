package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With {@code cozy_core:endermen_take_blocks} off, the goal that picks a block up never starts. */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
	@Shadow
	@Final
	private EnderMan enderman;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void cozyCore$leaveBlocksAlone(CallbackInfoReturnable<Boolean> cir) {
		if (enderman.level() instanceof ServerLevel level && !Rules.is(level, Rules.ENDERMEN_TAKE_BLOCKS)) {
			cir.setReturnValue(false);
		}
	}
}
