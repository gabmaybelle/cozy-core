package com.gabbymaybelle.cozycore;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomPatchConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * 0.7.0's "home comes alive" (Gabby, 2026-10-21: Cozy Core should feel slow-paced, peaceful and
 * cute): three gentle things home does on its own, inside a Hearth Lantern's reach. None runs on a
 * timer: the candles and the grass use the game's own random ticks, and the pets a goal that only
 * runs while the pet itself ticks.
 * <ul>
 * <li>Lights at dusk: candles light themselves after sunset and go out in the early morning.</li>
 * <li>A garden: now and then a flower that suits the biome grows on open grass.</li>
 * <li>Pets come home: at night, with their owner at home, tamed cats and dogs settle by the lantern.</li>
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
		if (level.dimensionType().hasFixedTime()) {
			return -1;
		}
		return (int) Math.floorMod(level.getDayTime(), 24000L);
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
		// Bone meal's own flower: the biome's first flower feature, placed once (GrassBlock.performBonemeal).
		List<ConfiguredFeature<?, ?>> flowers = level.getBiome(above).value().getGenerationSettings().getFlowerFeatures();
		if (flowers.isEmpty()) {
			return false;
		}
		((RandomPatchConfiguration) flowers.get(0).config()).feature().value().place(level, level.getChunkSource().getGenerator(), random, above);
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
		private @Nullable BlockPos spot;
		private int recheck;

		public SettleByHearthGoal(TamableAnimal pet) {
			this.pet = pet;
			setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			spot = restingSpot();
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
			if (pet instanceof Cat cat) {
				cat.setLying(on);
			} else if (!pet.isOrderedToSit()) {
				pet.setInSittingPose(on);
			}
		}

		/** Where this pet should rest now, or null when it shouldn't (day, owner away, told to sit...). */
		private @Nullable BlockPos restingSpot() {
			if (!(pet.level() instanceof ServerLevel level) || !pet.isTame() || pet.isOrderedToSit() || pet.isLeashed() || pet.isPassenger()) {
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
			BlockPos lantern = HearthLanterns.nearest(level, owner.blockPosition());
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
}
