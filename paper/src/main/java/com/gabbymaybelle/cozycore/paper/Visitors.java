package com.gabbymaybelle.cozycore.paper;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Cushion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jspecify.annotations.Nullable;

/**
 * A visitor at your door, the mod's 0.8.0 visitors and 0.9.0 cushion naps (its {@code HomeLife}):
 * now and then in the evening, while someone is home, a stray cat, or a rabbit or fox where they
 * live, walks up to the Hearth Lantern and rests by it, on a free cushion if there is one near. It
 * leaves in the morning unless it's tamed, on a lead or named. A visitor carries the mod's own entity
 * tag, {@code cozy_core_visitor}, so a world keeps its visitors when it moves between the mod and the
 * plugin.
 * <p>
 * The plugin's lantern is a vanilla lantern with no random tick of its own, so a timer stands in for
 * it: every {@link #EVERY} ticks in the evening, the lantern nearest each player at home has one
 * chance in {@link #CHANCE} of a visitor, about one evening in three, like the mod. The walk is a
 * Paper mob goal, added to each visitor as it enters the world (goals aren't saved).
 */
final class Visitors implements Listener, Runnable {
	/** The mark on a visitor (saved with it), so a home has one at a time and it leaves in the morning. */
	static final String TAG = "cozy_core_visitor";
	/** Evening, when a visitor may come: from sunset until a little after dark. It rests until dawn. */
	static final int VISIT_FROM = 12000, VISIT_UNTIL = 14000, MORNING_FROM = 23000;
	/**
	 * How often (ticks) the evening is rolled for, and the chance each time: ten rolls an evening at
	 * one in 25 each bring a visitor about one evening in three (1 - (24/25)^10 = 0.34).
	 */
	static final long EVERY = 200;
	static final int CHANCE = 25;
	/** How far from a Hearth Lantern (blocks, each way) a cushion is by the hearth. */
	static final int CUSHION_REACH = 5;
	/**
	 * The vanilla 26.3 biomes whose natural creature spawns have rabbits, and foxes (read from the
	 * game's biome data, 2026-11-02). The Paper API has no biome spawn lists; the mod reads them.
	 */
	static final Set<String> RABBIT_BIOMES = Set.of("cherry_grove", "dappled_forest", "desert", "flower_forest", "grove", "ice_spikes",
			"meadow", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_plains", "snowy_slopes", "snowy_taiga", "taiga");
	static final Set<String> FOX_BIOMES = Set.of("dappled_forest", "grove", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga", "taiga");

	private final CozyCorePlugin plugin;
	private final Hearths hearths;
	private final GoalKey<Mob> key;
	private final Random random = new Random();

	Visitors(CozyCorePlugin plugin, Hearths hearths) {
		this.plugin = plugin;
		this.hearths = hearths;
		this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "visit"));
	}

	/** The time of day in this world (0 at sunrise, 12000 sunset), or -1 where time stands still. */
	static int timeOfDay(World world) {
		return world.isFixedTime() ? -1 : (int) Math.floorMod(world.getTime(), 24000L);
	}

	private static boolean between(int time, int from, int until) {
		return from <= until ? time >= from && time < until : time >= from || time < until;
	}

	// ------------------------------------------------------------------ who comes, and when

	/** The evening roll: the lantern nearest each player at home may bring a visitor. */
	@Override
	public void run() {
		if (!plugin.hearthVisitors() || plugin.radius() <= 0) {
			return;
		}
		for (World world : Bukkit.getWorlds()) {
			int time = timeOfDay(world);
			if (time < 0 || !between(time, VISIT_FROM, VISIT_UNTIL)) {
				continue;
			}
			// The lantern nearest someone at home, once, so a home with several lanterns or players isn't visited more often.
			Set<Hearths.Pos> homes = new HashSet<>();
			for (Player player : world.getPlayers()) {
				Hearths.Hearth home = player.getGameMode() == GameMode.SPECTATOR ? null : hearths.homeAt(world, player.getX(), player.getZ());
				if (home != null && homes.add(home.pos()) && random.nextInt(CHANCE) == 0 && visitorsNear(world, home.pos()).isEmpty()) {
					arrive(world, home.pos(), null);
				}
			}
		}
	}

	/**
	 * A visitor arrives out in the yard of this lantern, 16 to 24 blocks away, so it has a little way
	 * to walk in: of the kind given, or the biome's pick when null. Returns it, or null when no spot fit.
	 */
	@Nullable Mob arrive(World world, Hearths.Pos lantern, @Nullable Class<? extends Mob> kind) {
		int radius = plugin.radius();
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = Math.min(radius - 2, 16 + random.nextInt(9));
			int x = lantern.x() + (int) Math.round(Math.cos(angle) * distance), z = lantern.z() + (int) Math.round(Math.sin(angle) * distance);
			Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
			Block stand = ground.getRelative(BlockFace.UP);
			// The yard, not a roof or a cave: close to the lantern's height, on solid dry ground, no one right there.
			if (Math.abs(stand.getY() - lantern.y()) > 12 || !ground.getType().isSolid() || ground.isLiquid() || stand.isLiquid()
					|| !stand.isPassable() || !stand.getRelative(BlockFace.UP).isPassable() || !hearths.protects(stand)
					|| !world.getNearbyPlayers(stand.getLocation().add(0.5, 0, 0.5), 12).isEmpty()) {
				continue;
			}
			Location at = stand.getLocation().add(0.5, 0, 0.5);
			at.setYaw(random.nextFloat() * 360);
			Class<? extends Mob> type = kind != null ? kind : visitorFor(stand);
			// Dressed for the biome (a cat's coat, a fox's colour), marked as a visitor before it enters the world.
			return world.spawn(at, type, CreatureSpawnEvent.SpawnReason.DEFAULT, true, mob -> {
				mob.addScoreboardTag(TAG);
				// It leaves in the morning by itself; until then it shouldn't vanish like a far-off stray.
				mob.setRemoveWhenFarAway(false);
			});
		}
		return null;
	}

	/** Who comes: a stray cat two times in three, otherwise a rabbit or a fox if this biome is one where they live. */
	Class<? extends Mob> visitorFor(Block at) {
		if (random.nextInt(3) != 0) {
			return Cat.class;
		}
		String biome = at.getBiome().getKey().getKey();
		List<Class<? extends Mob>> wild = new ArrayList<>();
		if (RABBIT_BIOMES.contains(biome)) {
			wild.add(Rabbit.class);
		}
		if (FOX_BIOMES.contains(biome)) {
			wild.add(Fox.class);
		}
		return wild.isEmpty() ? Cat.class : wild.get(random.nextInt(wild.size()));
	}

	/** The visitors within this lantern's reach (one that has become yours no longer counts). */
	List<Mob> visitorsNear(World world, Hearths.Pos lantern) {
		int r = plugin.radius();
		List<Mob> found = new ArrayList<>();
		for (Entity e : world.getNearbyEntities(new Location(world, lantern.x() + 0.5, lantern.y() + 0.5, lantern.z() + 0.5), r, r, r)) {
			if (e instanceof Mob mob && isVisitor(mob)) {
				found.add(mob);
			}
		}
		return found;
	}

	/**
	 * Whether this animal is still a visitor. One that is tamed, on a lead or named is yours: its mark
	 * comes off here, so it stays, and the home can have a new visitor.
	 */
	static boolean isVisitor(Mob mob) {
		if (!mob.getScoreboardTags().contains(TAG)) {
			return false;
		}
		if ((mob instanceof Tameable pet && pet.isTamed()) || mob.isLeashed() || mob.customName() != null) {
			mob.removeScoreboardTag(TAG);
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ the visit

	/**
	 * Each visitor gets the visit goal as it enters the world, spawned or loaded with its chunk.
	 * Rabbits and foxes at 3, above running from players (4), so a visitor still walks up with you
	 * nearby; cats at 5, below being tempted with fish (4), so you can still win a visiting cat over.
	 */
	@EventHandler
	void onAdd(EntityAddToWorldEvent event) {
		if (event.getEntity() instanceof Mob mob && (mob instanceof Cat || mob instanceof Rabbit || mob instanceof Fox)
				&& mob.getScoreboardTags().contains(TAG) && !Bukkit.getMobGoals().hasGoal(mob, key)) {
			Bukkit.getMobGoals().addGoal(mob, mob instanceof Cat ? 5 : 3, new VisitGoal(mob));
		}
	}

	/**
	 * A visitor's evening and morning: in the evening and at night it walks to the ground under the
	 * nearest Hearth Lantern and rests there (a cat lies down, a fox sits), or on a free cushion by it.
	 * In the morning it walks off and is gone once no one is near.
	 */
	final class VisitGoal implements Goal<Mob> {
		private final Mob mob;
		private @Nullable Block spot;
		private Hearths.@Nullable Pos lantern;
		private boolean leaving;
		private int recheck;

		VisitGoal(Mob mob) {
			this.mob = mob;
		}

		@Override
		public boolean shouldActivate() {
			if (!isVisitor(mob)) {
				return false;
			}
			int time = timeOfDay(mob.getWorld());
			leaving = time < 0 || !between(time, VISIT_FROM, MORNING_FROM);
			if (leaving) {
				if (mob.getWorld().getNearbyPlayers(mob.getLocation(), 24).isEmpty()) {
					mob.remove();
					return false;
				}
				return true;
			}
			spot = restingSpot();
			return spot != null;
		}

		@Override
		public boolean shouldStayActive() {
			if (--recheck > 0) {
				return true;
			}
			recheck = 40;
			return shouldActivate();
		}

		@Override
		public void start() {
			recheck = 40;
		}

		@Override
		public void stop() {
			spot = null;
			mob.getPathfinder().stopPathfinding();
			rest(false);
		}

		@Override
		public void tick() {
			if (leaving) {
				rest(false);
				if (!mob.getPathfinder().hasPath()) {
					// Away from home: straight out from the nearest lantern, or any way if none is near.
					Hearths.Hearth from = hearths.homeAt(mob.getWorld(), mob.getX(), mob.getZ());
					double dx = from == null ? 1 : mob.getX() - (from.pos().x() + 0.5), dz = from == null ? 0 : mob.getZ() - (from.pos().z() + 0.5);
					double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
					mob.getPathfinder().moveTo(mob.getLocation().add(dx / length * 16, 0, dz / length * 16), 1.0);
				}
				return;
			}
			if (spot == null) {
				return;
			}
			if (lantern != null && napOn(cushionFor(lantern))) {
				return;
			}
			Location to = spot.getLocation().add(0.5, 0, 0.5);
			if (mob.getLocation().distanceSquared(to) > 2.5 * 2.5) {
				rest(false);
				if (!mob.getPathfinder().hasPath()) {
					mob.getPathfinder().moveTo(to, 0.9);
				}
			} else {
				mob.getPathfinder().stopPathfinding();
				rest(true);
			}
		}

		/** The ground by the nearest lantern whose reach takes in this visitor, or null away from home. */
		private @Nullable Block restingSpot() {
			Hearths.Hearth home = hearths.homeAt(mob.getWorld(), mob.getX(), mob.getZ());
			lantern = home == null ? null : home.pos();
			return lantern == null ? null : groundUnder(mob.getWorld().getBlockAt(lantern.x(), lantern.y(), lantern.z()));
		}

		/** The cushion it's on, or else the free cushion by the lantern nearest it; null when there's none. */
		private @Nullable Cushion cushionFor(Hearths.Pos lantern) {
			if (mob.getVehicle() instanceof Cushion on) {
				return on;
			}
			Location centre = new Location(mob.getWorld(), lantern.x() + 0.5, lantern.y() + 0.5, lantern.z() + 0.5);
			Cushion best = null;
			double bestDistance = 0;
			for (Cushion c : centre.getNearbyEntitiesByType(Cushion.class, CUSHION_REACH + 0.5, 3.5, CUSHION_REACH + 0.5, c -> c.isValid() && c.isEmpty())) {
				double d = c.getLocation().distanceSquared(mob.getLocation());
				if (best == null || d < bestDistance) {
					best = c;
					bestDistance = d;
				}
			}
			return best;
		}

		/**
		 * One step of a nap on a cushion: walk to it, then get on and rest. False when there's no
		 * cushion, or it was taken just now, so it rests by the lantern instead.
		 */
		private boolean napOn(@Nullable Cushion cushion) {
			if (cushion == null) {
				return false;
			}
			if (mob.getVehicle() == cushion) {
				mob.getPathfinder().stopPathfinding();
				restingPose(true);
				return true;
			}
			// Within a step and a half: a rabbit's hops end about 1.4 blocks short.
			if (mob.getLocation().distanceSquared(cushion.getLocation()) > 1.5 * 1.5) {
				restingPose(false);
				if (!mob.getPathfinder().hasPath()) {
					mob.getPathfinder().moveTo(cushion.getLocation(), 0.9);
				}
				return true;
			}
			mob.getPathfinder().stopPathfinding();
			if (cushion.addPassenger(mob)) {
				restingPose(true);
				return true;
			}
			return false;
		}

		private void rest(boolean on) {
			if (!on && mob.getVehicle() instanceof Cushion) {
				mob.leaveVehicle();
			}
			restingPose(on);
		}

		/** A cat lies down, a fox sits; a rabbit has no resting pose and just keeps still. */
		private void restingPose(boolean on) {
			if (mob instanceof Cat cat) {
				cat.setLyingDown(on);
			} else if (mob instanceof Fox fox) {
				fox.setSitting(on);
			}
		}

		@Override
		public GoalKey<Mob> getKey() {
			return key;
		}

		@Override
		public EnumSet<GoalType> getTypes() {
			return EnumSet.of(GoalType.MOVE, GoalType.JUMP);
		}
	}

	/** The open ground under (or beside) a lantern: it may hang from a post or sit on a table. */
	static @Nullable Block groundUnder(Block lantern) {
		Block p = lantern.getRelative(BlockFace.DOWN);
		for (int i = 0; i < 8 && p.getType().isAir(); i++) {
			p = p.getRelative(BlockFace.DOWN);
		}
		// p is the first block that isn't air: stand on top of it.
		Block stand = p.getRelative(BlockFace.UP);
		if (stand.equals(lantern) || !stand.getType().isAir()) {
			// A lantern standing on something: rest beside it instead.
			for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
				Block g = lantern.getRelative(face);
				for (int i = 0; i < 4 && g.getRelative(BlockFace.DOWN).getType().isAir(); i++) {
					g = g.getRelative(BlockFace.DOWN);
				}
				if (g.getType().isAir() && !g.getRelative(BlockFace.DOWN).getType().isAir()) {
					return g;
				}
			}
			return null;
		}
		return stand;
	}
}
