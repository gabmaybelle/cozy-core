package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HomeComforts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Animals and villagers at home aren't hurt by creeper and ghast blasts, fire or lightning
 * ({@link HomeComforts#sparesAnimal}); players and monsters are hurt as usual.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void cozyCore$spareAnimalsAtHome(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (HomeComforts.sparesAnimal(level, (LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}
