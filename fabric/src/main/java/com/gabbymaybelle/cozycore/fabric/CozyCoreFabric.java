package com.gabbymaybelle.cozycore.fabric;

import com.gabbymaybelle.cozycore.CozyCore;
import net.fabricmc.api.ModInitializer;

/** The Fabric entrypoint: everything happens in {@code common}. */
public class CozyCoreFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		CozyCore.init();
	}
}
