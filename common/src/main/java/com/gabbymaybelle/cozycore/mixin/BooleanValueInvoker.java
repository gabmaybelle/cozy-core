package com.gabbymaybelle.cozycore.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Makes a true/false rule type (vanilla keeps the maker to itself), for the new default of {@code doInsomnia}. */
@Mixin(GameRules.BooleanValue.class)
public interface BooleanValueInvoker {
	@Invoker("create")
	static GameRules.Type<GameRules.BooleanValue> cozyCore$create(boolean fallback) {
		throw new AssertionError();
	}
}
