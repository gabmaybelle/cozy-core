package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.HomeLife;
import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The checks for 0.7.0, "home comes alive": the 64-block reach, candles that light at dusk, the
 * garden, and pets that settle by the hearth at night. Like the other checks, each places its own
 * Hearth Lantern, runs in a batch of its own, compares home with the wild (or the rule on with it
 * off), and removes everything it made, even when it fails.
 */
final class LifeChecks {
	private LifeChecks() {
	}

	private static void command(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/** A spot under the open sky in the test's area, at the ground the test world stands on. */
	private static BlockPos open(GameTestHelper helper, ServerLevel level) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		return new BlockPos(at.getX(), Math.max(at.getY(), level.getSeaLevel()) + 1, at.getZ());
	}

	/** In a new world a Hearth Lantern reaches 64 blocks: a spot 60 out is home, one 70 out isn't. */
	static void reach(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 1));
		Runnable tidy = () -> {
			level.setBlockAndUpdate(hearth, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(hearth.below(), Blocks.AIR.defaultBlockState());
		};
		RuleChecks.orTidy(tidy, () -> {
			level.setBlockAndUpdate(hearth.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
		});
		helper.runAfterDelay(1, () -> RuleChecks.orTidy(tidy, () -> {
			helper.assertValueEqual(Rules.get(level, Rules.HEARTH_LANTERN_RADIUS), 64, "the reach in a new world");
			helper.assertTrue(HearthLanterns.protects(level, hearth.offset(60, 0, 0)), "a spot 60 blocks from a lantern should be home");
			helper.assertFalse(HearthLanterns.protects(level, hearth.offset(70, 0, 0)), "a spot 70 blocks from a lantern shouldn't be home");
			tidy.run();
			helper.succeed();
		}));
	}

	/**
	 * Candles tick; at home after sunset an unlit candle lights and in the early morning a lit one
	 * goes out, while one lit by hand at noon stays lit; a candle in the wild never changes; and with
	 * {@code hearth_lights} off the candle at home is left alone too.
	 */
	static void lights(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = open(helper, level);
		BlockPos home = hearth.offset(10, 0, 0), wild = hearth.offset(45, 0, 0);
		BlockState unlit = Blocks.CANDLE.defaultBlockState(), lit = unlit.setValue(AbstractCandleBlock.LIT, true);
		Runnable tidy = () -> {
			for (BlockPos p : new BlockPos[] { hearth, home, wild }) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(p.below(), Blocks.AIR.defaultBlockState());
			}
			level.getGameRules().getRule(Rules.HEARTH_LIGHTS).set(true, level.getServer());
			command(level, "time set noon");
		};
		RuleChecks.orTidy(tidy, () -> {
			helper.assertTrue(unlit.isRandomlyTicking() && lit.isRandomlyTicking(), "candles should get random ticks");
			for (BlockPos p : new BlockPos[] { hearth, home, wild }) {
				level.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState());
			}
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
			level.setBlockAndUpdate(home, unlit);
			level.setBlockAndUpdate(wild, unlit);
			command(level, "time set 14000");
		});
		helper.runAfterDelay(1, () -> RuleChecks.orTidy(tidy, () -> {
			int time = HomeLife.timeOfDay(level);
			helper.assertTrue(time >= 14000 && time < 14020, "the time of day after time set 14000 (" + time + ")");
			level.getBlockState(home).randomTick(level, home, RandomSource.create());
			level.getBlockState(wild).randomTick(level, wild, RandomSource.create());
			boolean homeLit = level.getBlockState(home).getValue(AbstractCandleBlock.LIT), wildLit = level.getBlockState(wild).getValue(AbstractCandleBlock.LIT);
			// Rule off: a fresh unlit candle at home stays unlit.
			level.getGameRules().getRule(Rules.HEARTH_LIGHTS).set(false, level.getServer());
			BlockPos off = home.offset(0, 0, 2);
			level.setBlockAndUpdate(off.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(off, unlit);
			level.getBlockState(off).randomTick(level, off, RandomSource.create());
			boolean offLit = level.getBlockState(off).getValue(AbstractCandleBlock.LIT);
			level.setBlockAndUpdate(off, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(off.below(), Blocks.AIR.defaultBlockState());
			level.getGameRules().getRule(Rules.HEARTH_LIGHTS).set(true, level.getServer());
			// Early morning: the candle at home goes out.
			command(level, "time set 1000");
			level.getBlockState(home).randomTick(level, home, RandomSource.create());
			boolean homeLitMorning = level.getBlockState(home).getValue(AbstractCandleBlock.LIT);
			// Noon: a candle lit by hand stays lit.
			command(level, "time set noon");
			level.setBlockAndUpdate(home, lit);
			level.getBlockState(home).randomTick(level, home, RandomSource.create());
			boolean handLitNoon = level.getBlockState(home).getValue(AbstractCandleBlock.LIT);
			System.out.println("[cozy_core_gametest] lights: evening at home lit " + homeLit + ", in the wild lit " + wildLit
					+ ", rule off lit " + offLit + "; morning at home lit " + homeLitMorning + "; lit by hand at noon stays lit " + handLitNoon);
			tidy.run();
			helper.assertTrue(homeLit, "a candle at home should light after sunset");
			helper.assertFalse(wildLit, "a candle in the wild shouldn't light itself");
			helper.assertFalse(offLit, "with hearth_lights off, a candle at home shouldn't light itself");
			helper.assertFalse(homeLitMorning, "a candle at home should go out in the early morning");
			helper.assertTrue(handLitNoon, "a candle lit by hand at noon should stay lit");
			helper.succeed();
		}));
	}

	/**
	 * Open grass at home grows a flower that suits the biome; grass in the wild doesn't; grass with
	 * two flowers already in its 5x5 patch doesn't (the cap); and with {@code hearth_garden} off,
	 * grass at home doesn't either. The chance is 1 here (in play it's one tick in 40,000).
	 */
	static void garden(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = open(helper, level);
		BlockPos home = hearth.offset(10, -1, 0), crowded = hearth.offset(10, -1, 8), wild = hearth.offset(45, -1, 0);
		RandomSource random = RandomSource.create(7);
		Runnable tidy = () -> {
			level.setBlockAndUpdate(hearth, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(hearth.below(), Blocks.AIR.defaultBlockState());
			for (BlockPos g : new BlockPos[] { home, crowded, wild }) {
				for (BlockPos p : BlockPos.betweenClosed(g.offset(-2, 0, -2), g.offset(2, 1, 2))) {
					level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				}
			}
			level.getGameRules().getRule(Rules.HEARTH_GARDEN).set(true, level.getServer());
		};
		RuleChecks.orTidy(tidy, () -> {
			level.setBlockAndUpdate(hearth.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
			for (BlockPos g : new BlockPos[] { home, crowded, wild }) {
				for (BlockPos p : BlockPos.betweenClosed(g.offset(-2, 0, -2), g.offset(2, 0, 2))) {
					level.setBlockAndUpdate(p, Blocks.GRASS_BLOCK.defaultBlockState());
					level.setBlockAndUpdate(p.above(), Blocks.AIR.defaultBlockState());
				}
			}
			level.setBlockAndUpdate(crowded.offset(1, 1, 0), Blocks.POPPY.defaultBlockState());
			level.setBlockAndUpdate(crowded.offset(-1, 1, 1), Blocks.DANDELION.defaultBlockState());
		});
		helper.runAfterDelay(1, () -> RuleChecks.orTidy(tidy, () -> {
			boolean grew = false;
			for (int i = 0; i < 20 && !grew; i++) {
				grew = HomeLife.tickGrass(level, home, random, 1);
			}
			boolean wildGrew = false, crowdedGrew = false;
			for (int i = 0; i < 20; i++) {
				wildGrew |= HomeLife.tickGrass(level, wild, random, 1);
				crowdedGrew |= HomeLife.tickGrass(level, crowded, random, 1);
			}
			// The real thing: grass's own random tick, at play's odds (one in 40,000), reaches the garden.
			BlockPos real = home.offset(-2, 0, 2);
			int ticks = 0;
			while (ticks < 400_000 && !level.getBlockState(real.above()).is(net.minecraft.tags.BlockTags.FLOWERS)) {
				level.getBlockState(real).randomTick(level, real, random);
				ticks++;
			}
			boolean realGrew = level.getBlockState(real.above()).is(net.minecraft.tags.BlockTags.FLOWERS);
			System.out.println("[cozy_core_gametest] garden: grass's own random tick grew a flower " + realGrew + " after " + ticks + " ticks");
			helper.assertTrue(realGrew, "grass's own random tick at home should grow a flower within 400,000 ticks");
			level.getGameRules().getRule(Rules.HEARTH_GARDEN).set(false, level.getServer());
			BlockPos other = home.offset(2, 0, 2);
			boolean offGrew = false;
			for (int i = 0; i < 20; i++) {
				offGrew |= HomeLife.tickGrass(level, other, random, 1);
			}
			String flower = level.getBlockState(home.above()).getBlock().toString();
			System.out.println("[cozy_core_gametest] garden: home grew " + grew + " (" + flower + "), wild " + wildGrew
					+ ", crowded patch " + crowdedGrew + " (" + HomeLife.flowersNear(level, crowded.above()) + " flowers), rule off " + offGrew);
			tidy.run();
			helper.assertTrue(grew, "open grass at home should grow a flower (got " + flower + ")");
			helper.assertFalse(wildGrew, "grass in the wild shouldn't grow flowers");
			helper.assertFalse(crowdedGrew, "grass with two flowers in its patch shouldn't grow another");
			helper.assertFalse(offGrew, "with hearth_garden off, grass at home shouldn't grow flowers");
			helper.succeed();
		}));
	}

	/**
	 * A tamed cat, at night, with its owner home but 14 blocks from the lantern: the cat leaves its
	 * owner and lies down by the lantern. At noon it gets up again.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: a pet needs a real owner in the world
	static void pets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = open(helper, level);
		BlockPos ownerAt = hearth.offset(14, 0, 0), catAt = hearth.offset(10, 0, 0);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Cat[] cat = new Cat[1];
		Runnable tidy = () -> {
			level.getServer().getPlayerList().remove(owner);
			if (cat[0] != null) {
				cat[0].discard();
			}
			for (BlockPos p : BlockPos.betweenClosed(hearth.offset(-3, -1, -3), hearth.offset(17, 2, 3))) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			command(level, "time set noon");
		};
		RuleChecks.orTidy(tidy, () -> {
			for (BlockPos p : BlockPos.betweenClosed(hearth.offset(-3, -1, -3), hearth.offset(17, -1, 3))) {
				level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
			owner.setGameMode(GameType.SURVIVAL);
			owner.teleportTo(level, ownerAt.getX() + 0.5, ownerAt.getY(), ownerAt.getZ() + 0.5, 0, 0);
			cat[0] = EntityType.CAT.create(level);
			cat[0].moveTo(catAt.getX() + 0.5, catAt.getY(), catAt.getZ() + 0.5, 0, 0);
			cat[0].tame(owner);
			cat[0].setOrderedToSit(false);
			cat[0].setPersistenceRequired();
			level.addFreshEntity(cat[0]);
			command(level, "time set 14000");
		});
		helper.runAfterDelay(20, () -> {
			// What the cat's mind is doing, for when this check fails.
			try {
				java.lang.reflect.Field f = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
				f.setAccessible(true);
				var selector = (net.minecraft.world.entity.ai.goal.GoalSelector) f.get(cat[0]);
				System.out.println("[cozy_core_gametest] pets: goals " + selector.getAvailableGoals().stream()
						.map(g -> g.getPriority() + ":" + g.getGoal().getClass().getSimpleName() + (g.isRunning() ? "*" : "")).toList()
						+ "; owner " + cat[0].getOwner() + "; tame " + cat[0].isTame() + "; sitting " + cat[0].isOrderedToSit()
						+ "; time " + HomeLife.timeOfDay(level) + "; owner home " + (cat[0].getOwner() != null && HearthLanterns.protects(level, cat[0].getOwner().blockPosition())));
			} catch (ReflectiveOperationException e) {
				System.out.println("[cozy_core_gametest] pets: couldn't read the goals: " + e);
			}
		});
		helper.runAfterDelay(200, () -> RuleChecks.orTidy(tidy, () -> {
			double fromHearth = Math.sqrt(cat[0].distanceToSqr(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 0.5));
			boolean lying = cat[0].isLying();
			command(level, "time set noon");
			helper.runAfterDelay(120, () -> RuleChecks.orTidy(tidy, () -> {
				boolean lyingAtNoon = cat[0].isLying();
				System.out.println("[cozy_core_gametest] pets: at night the cat is " + String.format("%.1f", fromHearth)
						+ " blocks from the lantern, lying " + lying + "; at noon lying " + lyingAtNoon);
				tidy.run();
				helper.assertTrue(fromHearth < 3.5, "at night with its owner home, the cat should settle by the lantern (" + fromHearth + " blocks away)");
				helper.assertTrue(lying, "the cat should lie down by the lantern");
				helper.assertFalse(lyingAtNoon, "at noon the cat should get up again");
				helper.succeed();
			}));
		}));
	}
}
