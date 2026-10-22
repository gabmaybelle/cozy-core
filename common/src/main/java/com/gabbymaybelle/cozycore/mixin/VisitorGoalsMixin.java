package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HomeLife;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A visitor at your door: cats, rabbits and foxes get {@link HomeLife.VisitGoal}, which only acts
 * on one marked as a visitor. Rabbits and foxes get it at 3, above running from players (4), so a
 * visitor still walks up with you nearby; cats at 5, below being tempted with fish (4), so you can
 * still win a visiting cat over.
 */
@Mixin({ Cat.class, Rabbit.class, Fox.class })
public abstract class VisitorGoalsMixin extends Animal {
	protected VisitorGoalsMixin(EntityType<? extends Animal> type, Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void cozyCore$visit(CallbackInfo ci) {
		this.goalSelector.addGoal((Object) this instanceof Cat ? 5 : 3, new HomeLife.VisitGoal(this));
	}
}
