package com.gabbymaybelle.cozycore.neoforge;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The NeoForge entrypoint: everything happens in {@code common}. */
@Mod(CozyCore.MOD_ID)
public class CozyCoreNeoForge {
	public CozyCoreNeoForge(IEventBus modBus) {
		NeoForgePlatform platform = (NeoForgePlatform) Services.PLATFORM;
		// init() queues the registrations and trades; the events hand them to NeoForge.
		CozyCore.init();
		modBus.addListener(RegisterEvent.class, platform::onRegister);
		modBus.addListener(BuildCreativeModeTabContentsEvent.class, platform::onCreativeTab);
		NeoForge.EVENT_BUS.addListener(VillagerTradesEvent.class, platform::onVillagerTrades);
		NeoForge.EVENT_BUS.addListener(WandererTradesEvent.class, platform::onWandererTrades);
	}
}
