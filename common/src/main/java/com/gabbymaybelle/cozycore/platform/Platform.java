package com.gabbymaybelle.cozycore.platform;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;

/**
 * The few things that differ between loaders. {@code fabric} and {@code neoforge} each provide
 * one implementation through {@code META-INF/services}; common code reaches it as
 * {@link Services#PLATFORM}.
 */
public interface Platform {
	/** "Fabric" or "NeoForge", for the log. */
	String name();

	/**
	 * Registers something (a block, an item...). Fabric does it at once; NeoForge waits for its
	 * registry event, so the result is a supplier that is only valid once the game has finished loading.
	 */
	<T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory);

	/** Puts an item in one of the vanilla creative tabs. */
	void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item);

	/**
	 * A true/false game rule. Fabric shows it under the "Cozy Core" heading; NeoForge, which can't
	 * add a heading, under {@code category}.
	 */
	GameRules.Key<GameRules.BooleanValue> booleanRule(String name, GameRules.Category category, boolean fallback);

	/** A number game rule, {@code min} to {@code max} (Fabric's command refuses others; NeoForge's has no bounds). */
	GameRules.Key<GameRules.IntegerValue> integerRule(String name, GameRules.Category category, int fallback, int min, int max);

	/** Offers that join a villager profession's pool at a level (1 novice to 5 master). */
	void addVillagerTrades(VillagerProfession profession, int level, List<VillagerTrades.ItemListing> offers);

	/** Offers that join the wandering trader's common pool. */
	void addWanderingTraderTrades(List<VillagerTrades.ItemListing> offers);
}
