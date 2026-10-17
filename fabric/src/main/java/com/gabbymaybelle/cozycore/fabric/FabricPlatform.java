package com.gabbymaybelle.cozycore.fabric;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.platform.Platform;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;

public class FabricPlatform implements Platform {
	/** The "Cozy Core" heading in the Game Rules screen, styled like vanilla's headings. */
	public static final CustomGameRuleCategory CATEGORY = new CustomGameRuleCategory(CozyCore.id("cozy_core"),
			Component.translatable("gamerule.category.cozy_core.cozy_core").withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));

	@Override
	public String name() {
		return "Fabric";
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory) {
		Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
		T value = Registry.register(target, id, factory.get());
		return () -> value;
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.accept(item.get()));
	}

	@Override
	public GameRules.Key<GameRules.BooleanValue> booleanRule(String name, GameRules.Category category, boolean fallback) {
		return GameRuleRegistry.register(name, CATEGORY, GameRuleFactory.createBooleanRule(fallback));
	}

	@Override
	public GameRules.Key<GameRules.IntegerValue> integerRule(String name, GameRules.Category category, int fallback, int min, int max) {
		return GameRuleRegistry.register(name, CATEGORY, GameRuleFactory.createIntRule(fallback, min, max));
	}

	@Override
	public void addVillagerTrades(VillagerProfession profession, int level, List<VillagerTrades.ItemListing> offers) {
		TradeOfferHelper.registerVillagerOffers(profession, level, factories -> factories.addAll(offers));
	}

	@Override
	public void addWanderingTraderTrades(List<VillagerTrades.ItemListing> offers) {
		TradeOfferHelper.registerWanderingTraderOffers(1, factories -> factories.addAll(offers));
	}
}
