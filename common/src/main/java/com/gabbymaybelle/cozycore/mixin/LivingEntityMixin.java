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
	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void cozyCore$spareAnimalsAtHome(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.level() instanceof ServerLevel level && HomeComforts.sparesAnimal(level, entity, source)) {
			cir.setReturnValue(false);
		}
	}
}
