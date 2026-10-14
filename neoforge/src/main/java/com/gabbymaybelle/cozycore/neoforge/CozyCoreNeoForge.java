package com.gabbymaybelle.cozycore.neoforge;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.platform.Services;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** The NeoForge entrypoint: everything happens in {@code common}. */
@Mod(CozyCore.MOD_ID)
public class CozyCoreNeoForge {
	public CozyCoreNeoForge(IEventBus modBus) {
		NeoForgePlatform platform = (NeoForgePlatform) Services.PLATFORM;
		// init() queues the registrations; the registry events hand them to NeoForge.
		CozyCore.init();
		modBus.addListener(RegisterEvent.class, platform::onRegister);
		modBus.addListener(BuildCreativeModeTabContentsEvent.class, platform::onCreativeTab);
	}
}
