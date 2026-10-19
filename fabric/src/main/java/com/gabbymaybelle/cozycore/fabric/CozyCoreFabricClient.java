package com.gabbymaybelle.cozycore.fabric;

import com.gabbymaybelle.cozycore.client.HearthClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** The Fabric client entrypoint: the "Home" sign and the edge glow, in {@code common}'s {@link HearthClient}. */
public class CozyCoreFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(HearthClient::tick);
	}
}
