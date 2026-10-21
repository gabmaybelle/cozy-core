package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HomeLife;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pets come home: cats and dogs get {@link HomeLife.SettleByHearthGoal} at priority 5, just above
 * their follow-owner goal (6), so at night at home they settle by the lantern instead of following.
 */
@Mixin({ Cat.class, Wolf.class })
public abstract class PetGoalsMixin extends TamableAnimal {
	protected PetGoalsMixin(EntityType<? extends TamableAnimal> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void cozyCore$settleByHearth(CallbackInfo ci) {
		this.goalSelector.addGoal(5, new HomeLife.SettleByHearthGoal(this));
	}
}
