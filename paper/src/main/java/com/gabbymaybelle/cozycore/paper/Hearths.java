package com.gabbymaybelle.cozycore.paper;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Where the Hearth Lanterns are, per world, as far as loaded chunks go: the same rule as the mod's
 * {@code HearthLanterns}. The chunk's own persistent data is the record ({@code cozycore:hearths},
 * a list of x, y, z, style and the home's name), so it travels with the world like the mod's block
 * entities do; this class keeps an in-memory list of the loaded ones for the checks. Spawning,
 * blasts, fire and endermen only act in loaded chunks, and a lantern reaches at most the radius
 * setting, so lanterns in unloaded chunks can't matter to them. Every check is the same sideways
 * distance, at any height.
 */
final class Hearths implements Listener {
	record Pos(int x, int y, int z) {
		static Pos of(Block block) {
			return new Pos(block.getX(), block.getY(), block.getZ());
		}

		long distanceSqr(double x, double z) {
			double dx = this.x + 0.5 - x, dz = this.z + 0.5 - z;
			return (long) (dx * dx + dz * dz);
		}
	}

	/** One lantern: where it is, its style, and the name it was given on an anvil (null for plain "Home"). */
	record Hearth(Pos pos, HearthStyle style, @Nullable Component name) {
	}

	private final CozyCorePlugin plugin;
	private final HearthItems items;
	/** The chunk tag: a list of containers, one per lantern in the chunk. */
	private final NamespacedKey chunkKey, xKey, yKey, zKey, styleKey, nameKey;
	private final Map<UUID, Map<Pos, Hearth>> byWorld = new ConcurrentHashMap<>();

	Hearths(CozyCorePlugin plugin, HearthItems items) {
		this.plugin = plugin;
		this.items = items;
		chunkKey = new NamespacedKey(plugin, "hearths");
		xKey = new NamespacedKey(plugin, "x");
		yKey = new NamespacedKey(plugin, "y");
		zKey = new NamespacedKey(plugin, "z");
		styleKey = new NamespacedKey(plugin, "style");
		nameKey = new NamespacedKey(plugin, "name");
	}

	// ------------------------------------------------------------------ the checks

	/** The lanterns in this world's loaded chunks. */
	Collection<Hearth> in(World world) {
		Map<Pos, Hearth> map = byWorld.get(world.getUID());
		return map == null ? List.of() : map.values();
	}

	/** The nearest lantern whose reach takes in this spot, or null when the spot isn't home. */
	@Nullable Hearth homeAt(World world, double x, double z) {
		int radius = plugin.radius();
		if (radius <= 0) {
			return null;
		}
		Hearth nearest = null;
		long best = (long) radius * radius;
		for (Hearth hearth : in(world)) {
			long d = hearth.pos().distanceSqr(x, z);
			if (d <= best) {
				nearest = hearth;
				best = d;
			}
		}
		return nearest;
	}

	/** Whether a Hearth Lantern makes this spot home, where monsters don't spawn: within the radius sideways, at any height. */
	boolean protects(World world, double x, double z) {
		return homeAt(world, x, z) != null;
	}

	boolean protects(Location at) {
		return at.getWorld() != null && protects(at.getWorld(), at.getX(), at.getZ());
	}

	boolean protects(Block block) {
		return protects(block.getWorld(), block.getX() + 0.5, block.getZ() + 0.5);
	}

	/** Whether the blocks at this spot are home, where blasts, fire and endermen leave them alone: inside a lantern's reach while hearthsProtectBuilds is on. */
	boolean guards(Block block) {
		return plugin.protectsBuilds() && protects(block);
	}

	@Nullable Hearth at(Block block) {
		Map<Pos, Hearth> map = byWorld.get(block.getWorld().getUID());
		return map == null ? null : map.get(Pos.of(block));
	}

	// ------------------------------------------------------------------ placing, breaking, remembering

	/** A lantern placed by hand with a Hearth Lantern item, or by /cozycore place: remembered in its chunk. */
	void add(Block block, HearthStyle style, @Nullable Component name) {
		Hearth hearth = new Hearth(Pos.of(block), style, name);
		byWorld.computeIfAbsent(block.getWorld().getUID(), w -> new ConcurrentHashMap<>()).put(hearth.pos(), hearth);
		save(block.getChunk());
	}

	/** A tracked lantern is gone from the world: forgotten, and its Hearth item dropped unless told not to. */
	void remove(Block block, boolean drop) {
		Hearth hearth = at(block);
		if (hearth == null) {
			return;
		}
		byWorld.get(block.getWorld().getUID()).remove(hearth.pos());
		save(block.getChunk());
		if (drop) {
			block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), items.make(hearth.style(), hearth.name()));
		}
	}

	/** Rewrites the chunk's list from what is remembered for it. */
	private void save(Chunk chunk) {
		PersistentDataContainer data = chunk.getPersistentDataContainer();
		List<PersistentDataContainer> list = new ArrayList<>();
		for (Hearth hearth : in(chunk.getWorld())) {
			if (hearth.pos().x() >> 4 != chunk.getX() || hearth.pos().z() >> 4 != chunk.getZ()) {
				continue;
			}
			PersistentDataContainer entry = data.getAdapterContext().newPersistentDataContainer();
			entry.set(xKey, PersistentDataType.INTEGER, hearth.pos().x());
			entry.set(yKey, PersistentDataType.INTEGER, hearth.pos().y());
			entry.set(zKey, PersistentDataType.INTEGER, hearth.pos().z());
			entry.set(styleKey, PersistentDataType.STRING, hearth.style().id);
			if (hearth.name() != null) {
				entry.set(nameKey, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(hearth.name()));
			}
			list.add(entry);
		}
		if (list.isEmpty()) {
			data.remove(chunkKey);
		} else {
			data.set(chunkKey, PersistentDataType.LIST.dataContainers(), list);
		}
	}

	/** Reads a loaded chunk's list; an entry whose block is no longer a lantern (an editor took it) is dropped. */
	void load(Chunk chunk) {
		List<PersistentDataContainer> list = chunk.getPersistentDataContainer().get(chunkKey, PersistentDataType.LIST.dataContainers());
		if (list == null || list.isEmpty()) {
			return;
		}
		Map<Pos, Hearth> map = byWorld.computeIfAbsent(chunk.getWorld().getUID(), w -> new ConcurrentHashMap<>());
		boolean changed = false;
		for (PersistentDataContainer entry : list) {
			Integer x = entry.get(xKey, PersistentDataType.INTEGER), y = entry.get(yKey, PersistentDataType.INTEGER), z = entry.get(zKey, PersistentDataType.INTEGER);
			HearthStyle style = HearthStyle.byId(entry.getOrDefault(styleKey, PersistentDataType.STRING, ""));
			if (x == null || y == null || z == null || style == null) {
				changed = true;
				continue;
			}
			if (chunk.getWorld().getBlockAt(x, y, z).getType() != style.block) {
				changed = true;
				continue;
			}
			String json = entry.get(nameKey, PersistentDataType.STRING);
			Component name = json == null ? null : GsonComponentSerializer.gson().deserialize(json);
			map.put(new Pos(x, y, z), new Hearth(new Pos(x, y, z), style, name));
		}
		if (changed) {
			save(chunk);
		}
	}

	void unload(Chunk chunk) {
		Map<Pos, Hearth> map = byWorld.get(chunk.getWorld().getUID());
		if (map == null) {
			return;
		}
		map.keySet().removeIf(pos -> pos.x() >> 4 == chunk.getX() && pos.z() >> 4 == chunk.getZ());
	}

	/** The chunks already loaded when the plugin starts (or is reloaded). */
	void loadAll() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				load(chunk);
			}
		}
	}

	void forgetAll() {
		byWorld.clear();
	}

	// ------------------------------------------------------------------ events

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		load(event.getChunk());
	}

	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent event) {
		unload(event.getChunk());
	}

	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		for (Chunk chunk : event.getWorld().getLoadedChunks()) {
			load(chunk);
		}
	}

	/** A Hearth Lantern item placed: the block is now a hearth, with the name the item carried. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		HearthStyle style = items.styleOf(event.getItemInHand());
		if (style == null || !event.canBuild()) {
			return;
		}
		// The item is the style's own block (the waxed one for copper, so it never weathers), so the block placed is the right one.
		add(event.getBlockPlaced(), style, event.getItemInHand().getData(DataComponentTypes.CUSTOM_NAME));
	}

	/** Breaking a hearth drops the Hearth Lantern item (with its name) instead of a plain lantern. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (at(event.getBlock()) == null) {
			return;
		}
		event.setDropItems(false);
		remove(event.getBlock(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
	}

	/** A hearth that pops off (its chain or the block under it gone) drops the Hearth Lantern item too. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDestroy(BlockDestroyEvent event) {
		if (at(event.getBlock()) == null) {
			return;
		}
		boolean drop = event.willDrop();
		event.setWillDrop(false);
		remove(event.getBlock(), drop);
	}

	/** An explosion that takes a hearth (TNT; creeper blasts spare home) drops the Hearth Lantern item. */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onExplode(EntityExplodeEvent event) {
		blownUp(event.blockList());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		blownUp(event.blockList());
	}

	private void blownUp(List<Block> blocks) {
		for (Iterator<Block> it = blocks.iterator(); it.hasNext();) {
			Block block = it.next();
			if (at(block) != null) {
				it.remove();
				block.setType(Material.AIR, false);
				remove(block, true);
			}
		}
	}
}
