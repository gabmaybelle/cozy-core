package com.gabbymaybelle.cozycore.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import net.kyori.adventure.text.Component;

/**
 * Runs before the server loads its worlds: hands Paper the mod's data pack, which the build puts in
 * the jar as {@code datapack/}, so the stonecutter wood, re-dyeing, the other recipes, the
 * builder trades and the advancements tab are on from the first start, with nothing copied into the
 * world folder. Taking the plugin out takes the pack with it.
 */
public final class CozyCoreBootstrap implements PluginBootstrap {
	static final String PACK = "datapack";

	@Override
	public void bootstrap(BootstrapContext context) {
		context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
			URL pack = getClass().getResource("/" + PACK);
			if (pack == null) {
				context.getLogger().error("Cozy Core: the data pack is missing from the jar, so its recipes and trades are off");
				return;
			}
			try {
				event.registrar().discoverPack(pack.toURI(), "recipes", configurer -> configurer
						.title(Component.text("Cozy Core recipes and trades"))
						.autoEnableOnServerStart(true));
			} catch (IOException | URISyntaxException e) {
				context.getLogger().error("Cozy Core: couldn't load the data pack, so its recipes and trades are off", e);
			}
		});
	}
}
