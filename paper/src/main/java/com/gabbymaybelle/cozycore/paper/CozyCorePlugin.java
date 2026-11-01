package com.gabbymaybelle.cozycore.paper;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cozy Core as a Paper plugin: "Light a hearth. Home is safe." The Hearth Lantern is a vanilla
 * lantern carrying the plugin's tag ({@link HearthItems}); where the placed ones are is kept in
 * each chunk's own data ({@link Hearths}); inside a lantern's reach no monster spawns and blasts,
 * fire and endermen leave blocks alone ({@link HomeGuard}); home is kind to drops, animals, falls
 * and glass, and a player who dies keeps their hotbar ({@link HomeComforts}); walking in shows
 * "Home" ({@link HomeSign}). The mod's recipes, trades and advancements tab are its data pack,
 * which {@link CozyCoreBootstrap} turns on. The mod's game rules are settings in
 * {@code plugins/CozyCore/config.yml}.
 */
public final class CozyCorePlugin extends JavaPlugin {
	static final int MAX_RADIUS = 128;

	private int radius = 64, hearthItemMinutes = 60;
	private boolean protectsBuilds = true, mobExplosionsBreakBlocks = true, endermenTakeBlocks = true, fireBurnsBlocks = true, keepHotbarOnDeath = true;
	private Hearths hearths;
	private HomeComforts comforts;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		radius = Math.max(0, Math.min(MAX_RADIUS, getConfig().getInt("hearthLanternRadius", 64)));
		protectsBuilds = getConfig().getBoolean("hearthsProtectBuilds", true);
		mobExplosionsBreakBlocks = getConfig().getBoolean("mobExplosionsBreakBlocks", true);
		endermenTakeBlocks = getConfig().getBoolean("endermenTakeBlocks", true);
		fireBurnsBlocks = getConfig().getBoolean("fireBurnsBlocks", true);
		hearthItemMinutes = Math.max(5, Math.min(1440, getConfig().getInt("hearthItemMinutes", 60)));
		keepHotbarOnDeath = getConfig().getBoolean("keepHotbarOnDeath", true);

		HearthItems items = new HearthItems(this);
		items.registerRecipes();
		hearths = new Hearths(this, items);
		hearths.loadAll();
		HomeSign sign = new HomeSign(hearths);
		comforts = new HomeComforts(this, hearths, items);
		Bukkit.getPluginManager().registerEvents(hearths, this);
		Bukkit.getPluginManager().registerEvents(new HomeGuard(this, hearths, comforts), this);
		Bukkit.getPluginManager().registerEvents(comforts, this);
		Bukkit.getPluginManager().registerEvents(sign, this);
		Bukkit.getScheduler().runTaskTimer(this, sign, HomeSign.HOME_CHECK, HomeSign.HOME_CHECK);
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("cozycore", "Hearth Lanterns for operators: give, place, selftest", new CozyCoreCommand(this, items, hearths)));
		getLogger().info("Cozy Core: ready, Hearth Lanterns reach " + radius + " blocks" + (radius == 0 ? " (off)" : "")
				+ (protectsBuilds ? ", builds safe at home" : ", builds not protected"));
	}

	@Override
	public void onDisable() {
		if (hearths != null) {
			hearths.forgetAll();
		}
	}

	/** How far a Hearth Lantern reaches, sideways at any height; 0 for off. */
	int radius() {
		return radius;
	}

	boolean protectsBuilds() {
		return protectsBuilds;
	}

	boolean mobExplosionsBreakBlocks() {
		return mobExplosionsBreakBlocks;
	}

	boolean endermenTakeBlocks() {
		return endermenTakeBlocks;
	}

	boolean fireBurnsBlocks() {
		return fireBurnsBlocks;
	}

	/** How many minutes a dropped item lasts at home; 5 is vanilla's five minutes. */
	int hearthItemMinutes() {
		return hearthItemMinutes;
	}

	boolean keepHotbarOnDeath() {
		return keepHotbarOnDeath;
	}

	HomeComforts comforts() {
		return comforts;
	}
}
