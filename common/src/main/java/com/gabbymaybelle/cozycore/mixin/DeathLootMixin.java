package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.KeepOnDeath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code keep_hotbar_on_death} rule, first half: set the hotbar, armour and off-hand aside at
 * the very start of a player's death drops, before vanilla or any other mod (graves mods take
 * over the equipment drop later in this method) sees the inventory.
 */
@Mixin(LivingEntity.class)
public abstract class DeathLootMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
	private void cozyCore$keepHotbar(DamageSource source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			KeepOnDeath.stash(player);
		}
	}
}
