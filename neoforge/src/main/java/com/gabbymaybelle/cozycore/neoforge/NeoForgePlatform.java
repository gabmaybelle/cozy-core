package com.gabbymaybelle.cozycore.neoforge;

import com.gabbymaybelle.cozycore.platform.Platform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge only lets mods register during its registry events, so everything common code
 * registers is queued here and handed over when {@link #onRegister} fires for that registry.
 * Trades are handed over the same way, when NeoForge asks for them as a server starts.
 */
public class NeoForgePlatform implements Platform {
	private record Pending<T>(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory) {
		void offer(RegisterEvent event) {
			if (event.getRegistryKey().equals(registry)) {
				event.register(registry, id, factory);
			}
		}
	}

	private record TabEntry(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
	}

	private record VillagerOffers(VillagerProfession profession, int level, List<VillagerTrades.ItemListing> offers) {
	}

	private final List<Pending<?>> pending = new ArrayList<>();
	private final List<TabEntry> tabEntries = new ArrayList<>();
	private final List<VillagerOffers> villagerOffers = new ArrayList<>();
	private final List<VillagerTrades.ItemListing> wanderingOffers = new ArrayList<>();

	@Override
	public String name() {
		return "NeoForge";
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> Supplier<T> register(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, Supplier<T> factory) {
		pending.add(new Pending<>(registry, id, factory));
		return new Supplier<>() {
			private T value;

			@Override
			public T get() {
				if (value == null) {
					Registry<T> target = (Registry<T>) BuiltInRegistries.REGISTRY.get(registry.location());
					value = target.get(id);
					if (value == null) {
						throw new IllegalStateException(id + " isn't registered yet");
					}
				}
				return value;
			}
		};
	}

	@Override
	public void addToCreativeTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item) {
		tabEntries.add(new TabEntry(tab, item));
	}

	@Override
	public GameRules.Key<GameRules.BooleanValue> booleanRule(String name, GameRules.Category category, boolean fallback) {
		return GameRules.register(name, category, GameRules.BooleanValue.create(fallback));
	}

	@Override
	public GameRules.Key<GameRules.IntegerValue> integerRule(String name, GameRules.Category category, int fallback, int min, int max) {
		return GameRules.register(name, category, GameRules.IntegerValue.create(fallback));
	}

	@Override
	public void addVillagerTrades(VillagerProfession profession, int level, List<VillagerTrades.ItemListing> offers) {
		villagerOffers.add(new VillagerOffers(profession, level, offers));
	}

	@Override
	public void addWanderingTraderTrades(List<VillagerTrades.ItemListing> offers) {
		wanderingOffers.addAll(offers);
	}

	void onRegister(RegisterEvent event) {
		for (Pending<?> entry : pending) {
			entry.offer(event);
		}
	}

	void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
		for (TabEntry entry : tabEntries) {
			if (event.getTabKey().equals(entry.tab())) {
				event.accept(entry.item().get());
			}
		}
	}

	void onVillagerTrades(VillagerTradesEvent event) {
		for (VillagerOffers entry : villagerOffers) {
			if (event.getType() == entry.profession()) {
				event.getTrades().computeIfAbsent(entry.level(), level -> new ArrayList<>()).addAll(entry.offers());
			}
		}
	}

	void onWandererTrades(WandererTradesEvent event) {
		event.getGenericTrades().addAll(wanderingOffers);
	}
}
