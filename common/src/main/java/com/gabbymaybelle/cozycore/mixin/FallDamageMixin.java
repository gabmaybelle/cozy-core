package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.HomeComforts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Players and their tamed pets take no fall damage at home, so a slip off a roof or scaffolding
 * doesn't cost a trip back. Horses and llamas work out their own fall damage, so they're hooked too.
 */
@Mixin({ LivingEntity.class, AbstractHorse.class, Llama.class })
public abstract class FallDamageMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
	private void cozyCore$safeFootingAtHome(float fallDistance, float damageModifier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.level() instanceof ServerLevel level && HomeComforts.isPlayerOrPet(entity) && HearthLanterns.protects(level, entity.blockPosition())) {
			cir.setReturnValue(false);
		}
	}
}
