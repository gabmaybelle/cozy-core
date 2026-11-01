package com.gabbymaybelle.cozycore.paper;

import com.destroystokyo.paper.event.entity.PhantomPreSpawnEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Home is safe: the mod's rules as event handlers, each one checking the same "is this spot home"
 * as the mod's mixin did.
 * <ul>
 * <li>No monster spawns naturally at home (spawners, raids and patrols are vanilla).</li>
 * <li>Phantoms don't come for a player who is at home.</li>
 * <li>A creeper's or ghast's blast breaks no blocks at home (it still hurts); an edge blast leaves
 * a crater that stops where home begins. TNT, end crystals, beds and the wither are vanilla.</li>
 * <li>Fire doesn't burn a block away at home nor spread into home. Fire lit by hand, lightning or
 * lava still appears; a fire at home goes out the first time it tries to burn something.</li>
 * <li>Endermen don't pick blocks up at home.</li>
 * <li>A creeper's or ghast's blast centred at home awards "Safe and sound" to the players near it.</li>
 * </ul>
 * The world-wide switches in config.yml make the last three apply everywhere when off.
 */
final class HomeGuard implements Listener {
	private final CozyCorePlugin plugin;
	private final Hearths hearths;
	private final HomeComforts comforts;

	HomeGuard(CozyCorePlugin plugin, Hearths hearths, HomeComforts comforts) {
		this.plugin = plugin;
		this.hearths = hearths;
		this.comforts = comforts;
	}

	@EventHandler(ignoreCancelled = true)
	public void onSpawn(CreatureSpawnEvent event) {
		if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL
				&& event.getEntity().getSpawnCategory() == SpawnCategory.MONSTER
				&& hearths.protects(event.getLocation())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPhantom(PhantomPreSpawnEvent event) {
		Entity player = event.getSpawningEntity();
		if (hearths.protects(player.getLocation())) {
			event.setCancelled(true);
		}
	}

	/** Whether this is a creeper, a ghast, or a ghast's fireball: the blasts (and burns) home is spared. */
	static boolean sparingMob(Entity entity) {
		return entity instanceof Creeper || entity instanceof Ghast
				|| entity instanceof Fireball fireball && fireball.getShooter() instanceof Ghast;
	}

	@EventHandler(ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent event) {
		if (!sparingMob(event.getEntity())) {
			return;
		}
		if (hearths.guards(event.getLocation().getBlock())) {
			comforts.safeAndSound(event.getLocation());
		}
		if (!plugin.mobExplosionsBreakBlocks()) {
			event.blockList().clear();
		} else if (plugin.protectsBuilds()) {
			event.blockList().removeIf(hearths::protects);
		}
	}

	/** A block burning away: not at home, nor anywhere with fireBurnsBlocks off; the fire that tried goes out. */
	@EventHandler(ignoreCancelled = true)
	public void onBurn(BlockBurnEvent event) {
		if (!plugin.fireBurnsBlocks() || hearths.guards(event.getBlock())) {
			event.setCancelled(true);
			Block fire = event.getIgnitingBlock();
			if (fire != null && (fire.getType() == Material.FIRE || fire.getType() == Material.SOUL_FIRE)) {
				fire.setType(Material.AIR, false);
			}
		}
	}

	/** Fire spreading: not into home, not from a fire at home, nor anywhere with fireBurnsBlocks off. */
	@EventHandler(ignoreCancelled = true)
	public void onIgnite(BlockIgniteEvent event) {
		if (event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) {
			return;
		}
		Block from = event.getIgnitingBlock();
		if (!plugin.fireBurnsBlocks() || hearths.guards(event.getBlock()) || from != null && hearths.guards(from)) {
			event.setCancelled(true);
		}
	}

	/** An enderman taking a block: not at home, nor anywhere with endermenTakeBlocks off. Putting one down is fine. */
	@EventHandler(ignoreCancelled = true)
	public void onEndermanTakesBlock(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof Enderman && event.getTo().isAir()
				&& (!plugin.endermenTakeBlocks() || hearths.guards(event.getBlock()))) {
			event.setCancelled(true);
		}
	}
}
