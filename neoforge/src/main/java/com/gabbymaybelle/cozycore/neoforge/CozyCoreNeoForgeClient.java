package com.gabbymaybelle.cozycore.neoforge;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.client.HearthClient;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The NeoForge client entrypoint (loaded only on players' computers): the "Home" sign and the edge glow, in {@code common}'s {@link HearthClient}. */
@Mod(value = CozyCore.MOD_ID, dist = Dist.CLIENT)
public class CozyCoreNeoForgeClient {
	public CozyCoreNeoForgeClient() {
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> HearthClient.tick(Minecraft.getInstance()));
	}
}
