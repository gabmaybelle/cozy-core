package com.gabbymaybelle.cozycore.forge;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.platform.Services;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.event.village.WandererTradesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;

/** The Forge entrypoint: everything happens in {@code common}. */
@Mod(CozyCore.MOD_ID)
public class CozyCoreForge {
	public CozyCoreForge() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		ForgePlatform platform = (ForgePlatform) Services.PLATFORM;
		// init() queues the registrations and trades; the events hand them to Forge.
		CozyCore.init();
		modBus.addListener((RegisterEvent event) -> platform.onRegister(event));
		modBus.addListener((BuildCreativeModeTabContentsEvent event) -> platform.onCreativeTab(event));
		MinecraftForge.EVENT_BUS.addListener((VillagerTradesEvent event) -> platform.onVillagerTrades(event));
		MinecraftForge.EVENT_BUS.addListener((WandererTradesEvent event) -> platform.onWandererTrades(event));
	}
}
