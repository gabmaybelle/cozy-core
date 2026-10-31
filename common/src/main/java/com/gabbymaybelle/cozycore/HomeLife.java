package com.gabbymaybelle.cozycore;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * 0.7.0's "home comes alive" (Gabby, 2026-10-21: Cozy Core should feel slow-paced, peaceful and
 * cute): gentle things home does on its own, inside a Hearth Lantern's reach. None runs on a
 * timer: the candles, the grass and the lantern use the game's own random ticks, and the pets and
 * visitors a goal that only runs while the animal itself ticks.
 * <ul>
 * <li>Lights at dusk: candles light themselves after sunset and go out in the early morning.</li>
 * <li>A garden: now and then a flower that suits the biome grows on open grass.</li>
 * <li>Pets come home: at night, with their owner at home, tamed cats and dogs settle by the lantern.</li>
 * <li>A visitor at your door (0.8.0, Gabby, 2026-10-22): now and then in the evening a stray cat,
 * or a rabbit or fox where they live, walks up to the lantern; it leaves in the morning unless
 * it's tamed, on a lead or named.</li>
 * <li>Cushion naps (0.9.0, Gabby, 2026-10-30): an animal settling by the lantern, a pet cat or dog
 * or a visiting cat, rabbit or fox, rests on a free cushion near it instead of the ground.</li>
 * </ul>
 */
public final class HomeLife {
	/** Evening, when candles at home light: from sunset (12000) until just before dawn. */
	static final int EVENING_FROM = 12000, EVENING_UNTIL = 23000;
	/** Early morning, when candles at home go out: dawn (23000) to two hours after sunrise (2000). */
	static final int MORNING_FROM = 23000, MORNING_UNTIL = 2000;
	/** Night, when pets settle by the hearth. */
	static final int NIGHT_FROM = 13000, NIGHT_UNTIL = 23000;
	/**
	 * One random tick of grass at home in this many grows a flower. Grass gets a random tick about
	 * once a minute, so a home with about 10,000 grass blocks (a 64-block reach is about 12,900
	 * blocks of ground) grows a flower about every five minutes, a few a day, before the cap.
	 */
	public static final int GARDEN_CHANCE = 40_000;
	/** At most this many flowers in the 5x5 patch around a spot, the new one included. */
	public static final int GARDEN_CAP = 2;

	private HomeLife() {
	}

	/** The time of day in this world (0 at sunrise, 6000 noon, 12000 sunset), or -1 where time stands still. */
	public static int timeOfDay(ServerLevel level) {
		if (level.dimensionType().hasFixedTime() || level.dimensionType().defaultClock().isEmpty()) {
			return -1;
		}
		return (int) Math.floorMod(level.getDefaultClockTime(), 24000L);
	}

	private static boolean between(int time, int from, int until) {
		return from <= until ? time >= from && time < until : time >= from || time < until;
	}

	// ------------------------------------------------------------------ lights at dusk

	/**
	 * A candle's random tick (candles are made to tick, see {@code BlockBehaviourMixin}): at home,
	 * an unlit candle lights after sunset and a lit one goes out in the early morning. A candle lit
	 * by hand during the day stays lit until the next morning. Returns whether it changed.
	 */
	public static boolean tickCandle(BlockState state, ServerLevel level, BlockPos pos) {
		if (!(state.getBlock() instanceof AbstractCandleBlock) || !state.hasProperty(AbstractCandleBlock.LIT)) {
			return false;
		}
		if (!Rules.is(level, Rules.HEARTH_LIGHTS) || !HearthLanterns.protects(level, pos)) {
			return false;
		}
		int time = timeOfDay(level);
		if (time < 0) {
			return false;
		}
		boolean lit = state.getValue(AbstractCandleBlock.LIT);
		if (!lit && between(time, EVENING_FROM, EVENING_UNTIL) && canLight(state)) {
			level.setBlock(pos, state.setValue(AbstractCandleBlock.LIT, true), 11);
			return true;
		}
		if (lit && between(time, MORNING_FROM, MORNING_UNTIL)) {
			AbstractCandleBlock.extinguish(null, state, level, pos);
			return true;
		}
		return false;
	}

	/** Vanilla's rule for lighting by hand: not under water. */
	private static boolean canLight(BlockState state) {
		return !state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
				|| !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);
	}

	// ------------------------------------------------------------------ the garden

	/**
	 * A grass block's random tick at home: one time in {@code chance}, a flower that suits the biome
	 * (bone meal's own choice) grows on top, if the grass is open to the sky with air above and the
	 * 5x5 patch around holds fewer than {@link #GARDEN_CAP} flowers. Returns whether one grew.
	 */
	public static boolean tickGrass(ServerLevel level, BlockPos pos, RandomSource random, int chance) {
		if (!level.getBlockState(pos).is(Blocks.GRASS_BLOCK) || random.nextInt(chance) != 0) {
			return false;
		}
		if (!Rules.is(level, Rules.HEARTH_GARDEN) || !HearthLanterns.protects(level, pos)) {
			return false;
		}
		BlockPos above = pos.above();
		if (!level.getBlockState(above).isAir() || !level.canSeeSky(above) || flowersNear(level, above) >= GARDEN_CAP) {
			return false;
		}
		List<Feature> flowers = level.getBiome(above).value().getGenerationSettings().getBoneMealFeatures();
		if (flowers.isEmpty()) {
			return false;
		}
		Util.getRandom(flowers, random).place(level, level.getChunkSource().getGenerator(), random, above);
		return level.getBlockState(above).is(BlockTags.FLOWERS);
	}

	/** Flowers in the 5x5 patch at this height (and one up, for tall flowers' tops). */
	public static int flowersNear(ServerLevel level, BlockPos at) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 0, 2))) {
			if (level.getBlockState(p).is(BlockTags.FLOWERS)) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ pets come home

	/**
	 * At night, while its owner is at home, a tamed cat or dog walks to the ground under the nearest
	 * Hearth Lantern and settles there: a cat lies down, a dog sits. In the morning, or when the
	 * owner leaves home, it follows again. Pets told to sit, on a lead or riding something are left
	 * alone. Added to cats and wolves just above their follow-owner goal, so it takes its place.
	 */
	public static final class SettleByHearthGoal extends Goal {
		private final TamableAnimal pet;
		private @Nullable BlockPos spot, lantern;
		private int recheck;

		public SettleByHearthGoal(TamableAnimal pet) {
			this.pet = pet;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			spot = restingSpot();
			if (spot == null && pet.getVehicle() instanceof Cushion) {
				// Still on a cushion from last night (a world loaded in the morning): time to get up.
				rest(false);
			}
			return spot != null;
		}

		@Override
		public boolean canContinueToUse() {
			if (--recheck > 0) {
				return spot != null;
			}
			recheck = 40;
			BlockPos now = restingSpot();
			if (now == null) {
				return false;
			}
			if (spot == null || HearthLanterns.distanceSqr(now, spot) > 9) {
				spot = now;
			}
			return true;
		}

		@Override
		public void start() {
			recheck = 40;
		}

		@Override
		public void stop() {
			spot = null;
			pet.getNavigation().stop();
			rest(false);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return false;
		}

		@Override
		public void tick() {
			if (spot == null) {
				return;
			}
			if (pet.level() instanceof ServerLevel level && lantern != null && napOn(cushionFor(level, lantern, pet), pet, 1.0)) {
				return;
			}
			double d = pet.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			if (d > 2.5 * 2.5) {
				rest(false);
				if (pet.getNavigation().isDone()) {
					pet.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
				}
			} else {
				pet.getNavigation().stop();
				rest(true);
			}
		}

		private void rest(boolean on) {
			if (!on) {
				getOffCushion(pet);
			}
			restingPose(pet, on);
		}

		/** Where this pet should rest now, or null when it shouldn't (day, owner away, told to sit...). */
		private @Nullable BlockPos restingSpot() {
			if (!(pet.level() instanceof ServerLevel level) || !pet.isTame() || pet.isOrderedToSit() || pet.isLeashed()
					|| (pet.isPassenger() && !(pet.getVehicle() instanceof Cushion))) {
				return null;
			}
			if (!Rules.is(level, Rules.PETS_COME_HOME)) {
				return null;
			}
			int time = timeOfDay(level);
			if (time < 0 || !between(time, NIGHT_FROM, NIGHT_UNTIL)) {
				return null;
			}
			LivingEntity owner = pet.getOwner();
			if (owner == null || owner.level() != level || !owner.isAlive()) {
				return null;
			}
			lantern = HearthLanterns.nearest(level, owner.blockPosition());
			if (lantern == null || HearthLanterns.distanceSqr(lantern, pet.blockPosition()) > 48L * 48L) {
				return null;
			}
			return groundUnder(level, lantern);
		}

		/** The open ground under (or beside) a lantern: it may hang from a post or sit on a table. */
		private static @Nullable BlockPos groundUnder(ServerLevel level, BlockPos lantern) {
			BlockPos p = lantern.below();
			for (int i = 0; i < 8 && level.getBlockState(p).isAir(); i++) {
				p = p.below();
			}
			// p is the first block that isn't air: stand on top of it.
			BlockPos stand = p.above();
			if (stand.equals(lantern) || !level.getBlockState(stand).isAir()) {
				// A lantern standing on something: rest beside it instead.
				for (BlockPos side : List.of(lantern.north(), lantern.south(), lantern.east(), lantern.west())) {
					BlockPos g = side;
					for (int i = 0; i < 4 && level.getBlockState(g.below()).isAir(); i++) {
						g = g.below();
					}
					if (level.getBlockState(g).isAir() && !level.getBlockState(g.below()).isAir()) {
						return g;
					}
				}
				return null;
			}
			return stand;
		}
	}

	// ------------------------------------------------------------------ cushion naps

	/** How far from a Hearth Lantern (blocks, each way) a cushion is by the hearth. */
	public static final int CUSHION_REACH = 5;

	/**
	 * How an animal rests by the hearth: a cat lies down, a dog sits (unless it was told to sit, which
	 * is its owner's to undo), a fox sits; a rabbit has no resting pose and just keeps still.
	 */
	static void restingPose(Mob mob, boolean on) {
		if (mob instanceof Cat cat) {
			cat.setLying(on);
		} else if (mob instanceof Fox fox) {
			fox.setSitting(on);
		} else if (mob instanceof TamableAnimal pet && !pet.isOrderedToSit()) {
			pet.setInSittingPose(on);
		}
	}

	/**
	 * The cushion an animal settling by this lantern should nap on: the one it's on, or else the free
	 * cushion by the lantern nearest it (one with no one on it). Null when there's none.
	 */
	public static @Nullable Cushion cushionFor(ServerLevel level, BlockPos lantern, Mob mob) {
		if (mob.getVehicle() instanceof Cushion on) {
			return on;
		}
		Cushion best = null;
		double bestDistance = 0;
		for (Cushion c : level.getEntitiesOfClass(Cushion.class, new AABB(lantern).inflate(CUSHION_REACH, 3, CUSHION_REACH), c -> c.isAlive() && !c.isVehicle())) {
			double d = c.distanceToSqr(mob);
			if (best == null || d < bestDistance) {
				best = c;
				bestDistance = d;
			}
		}
		return best;
	}

	/**
	 * One step of a nap on a cushion: walk to it, then get on and rest (a cat curls up, the way it lies
	 * on a bed). Vanilla cushions take any rider, and an animal rests on top of one, as on a bed. False
	 * when there's no cushion, or it was taken just now, so the animal rests by the lantern instead.
	 */
	public static boolean napOn(@Nullable Cushion cushion, Mob mob, double speed) {
		if (cushion == null) {
			return false;
		}
		if (mob.getVehicle() == cushion) {
			mob.getNavigation().stop();
			restingPose(mob, true);
			return true;
		}
		// Within a step and a half: a rabbit's hops end about 1.4 blocks short (seen 2026-10-30).
		if (mob.distanceToSqr(cushion) > 1.5 * 1.5) {
			restingPose(mob, false);
			if (mob.getNavigation().isDone()) {
				mob.getNavigation().moveTo(cushion.getX(), cushion.getY(), cushion.getZ(), speed);
			}
			return true;
		}
		mob.getNavigation().stop();
		if (mob.startRiding(cushion)) {
			restingPose(mob, true);
			return true;
		}
		return false;
	}

	/** Gets an animal off the cushion it's napping on, if it is. */
	static void getOffCushion(Mob mob) {
		if (mob.getVehicle() instanceof Cushion) {
			mob.stopRiding();
		}
	}

	// ------------------------------------------------------------------ a visitor at your door

	/** Evening, when a visitor may come: from sunset until a little after dark. */
	static final int VISIT_FROM = 12000, VISIT_UNTIL = 14000;
	/**
	 * One random tick of a Hearth Lantern in the evening in this many brings a visitor. A block gets a
	 * random tick about once every 1,365 game ticks, so a lantern gets one or two in the evening and a
	 * visitor comes about one evening in three (Gabby, 2026-10-22: about every three days).
	 */
	public static final int VISIT_CHANCE = 4;
	/** The mark on a visitor (saved with it), so a home has one at a time and it leaves in the morning. */
	public static final String VISITOR_TAG = "cozy_core_visitor";

	/**
	 * A Hearth Lantern's random tick (it ticks, see {@code HearthLanternBlock}): in the evening, one
	 * time in {@code chance}, while someone is home and this is the lantern nearest them, a visitor
	 * comes, unless this home already has one. Returns the visitor, or null when none came.
	 */
	public static @Nullable Mob tickLantern(ServerLevel level, BlockPos lantern, RandomSource random, int chance) {
		if (random.nextInt(chance) != 0 || !Rules.is(level, Rules.HEARTH_VISITORS)) {
			return null;
		}
		int time = timeOfDay(level);
		int radius = Rules.get(level, Rules.HEARTH_LANTERN_RADIUS);
		if (time < 0 || !between(time, VISIT_FROM, VISIT_UNTIL) || radius <= 0) {
			return null;
		}
		// The lantern nearest someone at home, so a home with several lanterns isn't visited more often.
		boolean home = level.players().stream().anyMatch(p -> !p.isSpectator() && lantern.equals(HearthLanterns.nearest(level, p.blockPosition())));
		if (!home || !visitorsNear(level, lantern, radius).isEmpty()) {
			return null;
		}
		for (int i = 0; i < 12; i++) {
			// Out in the yard, 16 to 24 blocks from the lantern, so it has a little way to walk in.
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = Math.min(radius - 2, 16 + random.nextInt(9));
			int x = lantern.getX() + (int) Math.round(Math.cos(angle) * distance), z = lantern.getZ() + (int) Math.round(Math.sin(angle) * distance);
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			// The yard, not a roof or a cave: close to the lantern's height, on solid dry ground, no one right there.
			if (Math.abs(spot.getY() - lantern.getY()) > 12 || !level.getFluidState(spot).isEmpty() || !level.getFluidState(spot.below()).isEmpty()
					|| !level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), Direction.UP)
					|| !HearthLanterns.protects(level, spot) || level.getNearestPlayer(x + 0.5, spot.getY(), z + 0.5, 12, false) != null) {
				continue;
			}
			Mob visitor = visit(level, spot, visitorFor(level, spot, random));
			if (visitor != null) {
				return visitor;
			}
		}
		return null;
	}

	/** The visitors within this lantern's reach (one that has become yours no longer counts). */
	public static List<Mob> visitorsNear(ServerLevel level, BlockPos lantern, int radius) {
		return level.getEntitiesOfClass(Mob.class, new AABB(lantern).inflate(radius), HomeLife::isVisitor);
	}

	/**
	 * Whether this animal is still a visitor. One that is tamed, on a lead or named is yours: its
	 * mark comes off here, so it stays, and the home can have a new visitor.
	 */
	public static boolean isVisitor(Mob mob) {
		if (!mob.entityTags().contains(VISITOR_TAG)) {
			return false;
		}
		if ((mob instanceof TamableAnimal pet && pet.isTame()) || mob.isLeashed() || mob.hasCustomName()) {
			mob.removeTag(VISITOR_TAG);
			return false;
		}
		return true;
	}

	/**
	 * Who comes: a stray cat two times in three. Otherwise a rabbit or a fox, if this biome is one
	 * where they live (its natural creature spawns name them); a cat if it's neither.
	 */
	public static EntityType<? extends Mob> visitorFor(ServerLevel level, BlockPos at, RandomSource random) {
		if (random.nextInt(3) != 0) {
			return EntityTypes.CAT;
		}
		List<EntityType<? extends Mob>> wild = new ArrayList<>();
		for (Weighted<MobSpawnSettings.SpawnerData> spawn : level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, at).getMobsToSpawn(MobCategory.CREATURE).unwrap()) {
			if (spawn.value().type() == EntityTypes.RABBIT && !wild.contains(EntityTypes.RABBIT)) {
				wild.add(EntityTypes.RABBIT);
			} else if (spawn.value().type() == EntityTypes.FOX && !wild.contains(EntityTypes.FOX)) {
				wild.add(EntityTypes.FOX);
			}
		}
		return wild.isEmpty() ? EntityTypes.CAT : Util.getRandom(wild, random);
	}

	/** A visitor of this kind arrives at this spot, dressed for the biome (a cat's coat, a fox's colour), marked as a visitor. */
	public static @Nullable Mob visit(ServerLevel level, BlockPos spot, EntityType<? extends Mob> type) {
		Mob visitor = type.create(level, EntitySpawnReason.EVENT);
		if (visitor == null) {
			return null;
		}
		visitor.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
		if (!level.noCollision(visitor)) {
			visitor.discard();
			return null;
		}
		visitor.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		visitor.addTag(VISITOR_TAG);
		// It leaves in the morning by itself; until then it shouldn't vanish like a far-off stray.
		visitor.setPersistenceRequired();
		level.addFreshEntity(visitor);
		return visitor;
	}

	/**
	 * A visitor's evening and morning: in the evening and at night it walks to the ground under the
	 * nearest Hearth Lantern and rests there (a cat lies down, a fox sits). In the morning it walks
	 * off and is gone once no one is near. A visitor that is tamed, on a lead or named is yours: it
	 * stops being a visitor and stays. Added to cats, rabbits and foxes.
	 */
	public static final class VisitGoal extends Goal {
		private final Mob mob;
		private @Nullable BlockPos spot, lantern;
		private boolean leaving;
		private int recheck;

		public VisitGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (!(mob.level() instanceof ServerLevel level) || !isVisitor(mob)) {
				return false;
			}
			int time = timeOfDay(level);
			leaving = time < 0 || !between(time, VISIT_FROM, MORNING_FROM);
			if (leaving) {
				if (level.getNearestPlayer(mob, 24) == null) {
					mob.discard();
					return false;
				}
				return true;
			}
			spot = restingSpot(level);
			return spot != null;
		}

		@Override
		public boolean canContinueToUse() {
			if (--recheck > 0) {
				return true;
			}
			recheck = 40;
			return canUse();
		}

		@Override
		public void start() {
			recheck = 40;
		}

		@Override
		public void stop() {
			spot = null;
			mob.getNavigation().stop();
			rest(false);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return false;
		}

		@Override
		public void tick() {
			if (leaving) {
				rest(false);
				if (mob.getNavigation().isDone()) {
					// Away from home: straight out from the nearest lantern, or any way if none is near.
					BlockPos from = mob.level() instanceof ServerLevel level ? HearthLanterns.nearest(level, mob.blockPosition()) : null;
					double dx = from == null ? 1 : mob.getX() - (from.getX() + 0.5), dz = from == null ? 0 : mob.getZ() - (from.getZ() + 0.5);
					double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
					mob.getNavigation().moveTo(mob.getX() + dx / length * 16, mob.getY(), mob.getZ() + dz / length * 16, 1.0);
				}
				return;
			}
			if (spot == null) {
				return;
			}
			if (mob.level() instanceof ServerLevel level && lantern != null && napOn(cushionFor(level, lantern, mob), mob, 0.9)) {
				return;
			}
			double d = mob.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			if (d > 2.5 * 2.5) {
				rest(false);
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.9);
				}
			} else {
				mob.getNavigation().stop();
				rest(true);
			}
		}

		private void rest(boolean on) {
			if (!on) {
				getOffCushion(mob);
			}
			restingPose(mob, on);
		}

		/** The ground by the nearest lantern whose reach takes in this visitor, or null away from home. */
		private @Nullable BlockPos restingSpot(ServerLevel level) {
			lantern = HearthLanterns.nearest(level, mob.blockPosition());
			return lantern == null ? null : SettleByHearthGoal.groundUnder(level, lantern);
		}
	}
}
