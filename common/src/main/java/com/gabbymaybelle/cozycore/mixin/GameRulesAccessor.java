package com.gabbymaybelle.cozycore.mixin;

import java.util.Map;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets {@code Rules} change what a vanilla rule starts at in new worlds (phantoms off). */
@Mixin(GameRules.class)
public interface GameRulesAccessor {
	/** Every rule and its type; a rule's type is what makes its value, default included, in each new {@code GameRules}. */
	@Accessor("GAME_RULE_TYPES")
	static Map<GameRules.Key<?>, GameRules.Type<?>> cozyCore$types() {
		throw new AssertionError();
	}
}
