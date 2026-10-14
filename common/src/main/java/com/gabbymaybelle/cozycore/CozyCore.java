package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cozy Core: building helpers for Minecraft 26.3. The recipes and trades are data (generated into
 * pack/ and bundled into this jar); the code here is the few builder-kind rules that need it (each
 * one a {@code cozy_core:} game rule, see {@link Rules}) and the {@link HearthLantern}. The loader
 * entrypoints ({@code fabric} and {@code neoforge}) call {@link #init()} once; everything that
 * doesn't care which loader it runs on lives here in {@code common}.
 */
public final class CozyCore {
	public static final String MOD_ID = "cozy_core";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private CozyCore() {
	}

	public static void init() {
		Rules.register();
		HearthLantern.register();
		LOGGER.info("Cozy Core: ready on {}", Services.PLATFORM.name());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
