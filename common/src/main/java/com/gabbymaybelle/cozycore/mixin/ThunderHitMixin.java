package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.HomeComforts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lightning leaves animals and villagers at home alone: no burn, no damage, and no turning a
 * villager into a witch, a pig into a zombified piglin or a mooshroom into the other colour.
 * Each of those mobs has its own {@code thunderHit}, so this hooks every one (and the base one).
 */
@Mixin({ Entity.class, Villager.class, Pig.class, MushroomCow.class, Turtle.class })
public abstract class ThunderHitMixin {
	@Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
	private void cozyCore$lightningSparesHome(ServerLevel level, LightningBolt bolt, CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		if (HomeComforts.isHomeAnimal(entity) && HearthLanterns.guards(level, entity.blockPosition())) {
			ci.cancel();
		}
	}
}
