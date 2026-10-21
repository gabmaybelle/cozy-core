package com.gabbymaybelle.cozycore;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Where the Hearth Lanterns are, per world, as far as loaded chunks go: on the server for the
 * rules, and on a player's computer for the "Home" sign and the edge glow. Spawning, blasts,
 * fire and endermen only act in loaded chunks, and a lantern reaches at most
 * {@code hearth_lantern_radius} blocks, so lanterns in unloaded chunks can't matter to them.
 * Every check is the same sideways distance, at any height: no timers, just a short list.
 */
public final class HearthLanterns {
	private static final Map<Level, Map<BlockPos, HearthLanternBlockEntity>> BY_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());

	private HearthLanterns() {
	}

	static void add(Level level, HearthLanternBlockEntity lantern) {
		BY_LEVEL.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).put(lantern.getBlockPos().immutable(), lantern);
	}

	static void remove(Level level, BlockPos pos) {
		Map<BlockPos, HearthLanternBlockEntity> lanterns = BY_LEVEL.get(level);
		if (lanterns != null) {
			lanterns.remove(pos);
		}
	}

	/** The lanterns in this world's loaded chunks. */
	public static Set<BlockPos> in(Level level) {
		Map<BlockPos, HearthLanternBlockEntity> lanterns = BY_LEVEL.get(level);
		return lanterns == null ? Set.of() : Collections.unmodifiableSet(lanterns.keySet());
	}

	/** The lanterns in this world's loaded chunks, with their names and (on a player's computer) reach. */
	public static Collection<HearthLanternBlockEntity> lanterns(Level level) {
		Map<BlockPos, HearthLanternBlockEntity> lanterns = BY_LEVEL.get(level);
		return lanterns == null ? Set.of() : Collections.unmodifiableCollection(lanterns.values());
	}

	/** Whether a Hearth Lantern keeps monsters from spawning at this spot: within the radius sideways, at any height. */
	public static boolean protects(ServerLevel level, BlockPos pos) {
		return within(level, pos, 0);
	}

	/**
	 * Whether the blocks at this spot are home, where creeper and ghast blasts, fire and endermen
	 * leave them alone: inside a lantern's reach while {@code cozy_core:hearths_protect_builds} is on.
	 */
	public static boolean guards(ServerLevel level, BlockPos pos) {
		return guards(level, pos, 0);
	}

	/** {@link #guards(ServerLevel, BlockPos)} with the reach made {@code extra} blocks wider, for things that act a few blocks from where they stand. */
	public static boolean guards(ServerLevel level, BlockPos pos, int extra) {
		return Rules.is(level, Rules.HEARTHS_PROTECT_BUILDS) && within(level, pos, extra);
	}

	/** Whether any lantern is loaded in this world, so callers can skip work in a world with none. */
	public static boolean any(Level level) {
		Map<BlockPos, HearthLanternBlockEntity> lanterns = BY_LEVEL.get(level);
		return lanterns != null && !lanterns.isEmpty();
	}

	/** On the server: the nearest lantern whose reach takes in this spot, or null when the spot isn't home. */
	public static @Nullable BlockPos nearest(ServerLevel level, BlockPos pos) {
		Map<BlockPos, HearthLanternBlockEntity> lanterns = BY_LEVEL.get(level);
		int radius = Rules.get(level, Rules.HEARTH_LANTERN_RADIUS);
		if (lanterns == null || radius <= 0) {
			return null;
		}
		BlockPos nearest = null;
		long best = (long) radius * radius;
		for (BlockPos lantern : lanterns.keySet()) {
			long d = distanceSqr(lantern, pos);
			if (d <= best) {
				nearest = lantern;
				best = d;
			}
		}
		return nearest;
	}

	private static boolean within(ServerLevel level, BlockPos pos, int extra) {
		Map<BlockPos, HearthLanternBlockEntity> lanterns = BY_LEVEL.get(level);
		if (lanterns == null || lanterns.isEmpty()) {
			return false;
		}
		int radius = Rules.get(level, Rules.HEARTH_LANTERN_RADIUS);
		if (radius <= 0) {
			return false;
		}
		long reach = (long) (radius + extra) * (radius + extra);
		for (BlockPos lantern : lanterns.keySet()) {
			if (distanceSqr(lantern, pos) <= reach) {
				return true;
			}
		}
		return false;
	}

	/**
	 * On a player's computer: the nearest lantern whose reach (as the server sent it) takes in this
	 * spot, or null when the spot isn't home.
	 */
	public static @Nullable HearthLanternBlockEntity homeAt(Level level, BlockPos pos) {
		HearthLanternBlockEntity nearest = null;
		long best = Long.MAX_VALUE;
		for (HearthLanternBlockEntity lantern : lanterns(level)) {
			int radius = lantern.reach();
			long d = distanceSqr(lantern.getBlockPos(), pos);
			if (radius > 0 && d <= (long) radius * radius && d < best) {
				nearest = lantern;
				best = d;
			}
		}
		return nearest;
	}

	/** Sideways distance squared; height doesn't count. */
	public static long distanceSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}
}
