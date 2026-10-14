package com.gabbymaybelle.cozycore;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Where the Hearth Lanterns are, per world, as far as loaded chunks go. Natural spawning only
 * happens in loaded chunks, and a lantern reaches at most {@code hearth_lantern_radius} blocks,
 * so lanterns in unloaded chunks can't matter to it.
 */
public final class HearthLanterns {
	private static final Map<ServerLevel, Set<BlockPos>> BY_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());

	private HearthLanterns() {
	}

	static void add(ServerLevel level, BlockPos pos) {
		BY_LEVEL.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(pos.immutable());
	}

	static void remove(ServerLevel level, BlockPos pos) {
		Set<BlockPos> lanterns = BY_LEVEL.get(level);
		if (lanterns != null) {
			lanterns.remove(pos);
		}
	}

	/** The lanterns in this world's loaded chunks. */
	public static Set<BlockPos> in(ServerLevel level) {
		Set<BlockPos> lanterns = BY_LEVEL.get(level);
		return lanterns == null ? Set.of() : Collections.unmodifiableSet(lanterns);
	}

	/** Whether a Hearth Lantern keeps monsters from spawning at this spot: within the radius sideways, at any height. */
	public static boolean protects(ServerLevel level, BlockPos pos) {
		Set<BlockPos> lanterns = BY_LEVEL.get(level);
		if (lanterns == null || lanterns.isEmpty()) {
			return false;
		}
		int radius = Rules.get(level, Rules.HEARTH_LANTERN_RADIUS);
		if (radius <= 0) {
			return false;
		}
		long reach = (long) radius * radius;
		for (BlockPos lantern : lanterns) {
			long dx = lantern.getX() - pos.getX();
			long dz = lantern.getZ() - pos.getZ();
			if (dx * dx + dz * dz <= reach) {
				return true;
			}
		}
		return false;
	}
}
