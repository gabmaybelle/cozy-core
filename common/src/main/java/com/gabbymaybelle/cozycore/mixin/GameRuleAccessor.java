package com.gabbymaybelle.cozycore.mixin;

import net.minecraft.world.level.gamerules.GameRule;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code Rules} change what a vanilla rule starts at in new worlds (phantoms off). */
@Mixin(GameRule.class)
public interface GameRuleAccessor {
	@Mutable
	@Accessor("defaultValue")
	void cozyCore$setDefaultValue(Object value);
}
