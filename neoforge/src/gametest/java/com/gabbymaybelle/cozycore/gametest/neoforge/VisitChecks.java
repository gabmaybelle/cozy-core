package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.HomeLife;
import com.gabbymaybelle.cozycore.Rules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The checks for 0.8.0, "a visitor at your door": a visitor comes in the evening while someone is
 * home, one at a time, and not by day, with no one home or with the rule off; it walks to the
 * lantern and rests there, and leaves in the morning unless it's been tamed; and it suits the
 * biome. Each check places its own Hearth Lantern, runs in a batch of its own and removes what it
 * made, even when it fails.
 */
final class VisitChecks {
	private VisitChecks() {
	}

	private static void command(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	private static BlockPos open(GameTestHelper helper, ServerLevel level) {
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		return new BlockPos(at.getX(), Math.max(at.getY(), level.getSeaLevel()) + 1, at.getZ());
	}

	private static void discardVisitors(ServerLevel level, BlockPos hearth) {
		HomeLife.visitorsNear(level, hearth, 64).forEach(Mob::discard);
	}

	/**
	 * The lantern ticks; in the evening with someone home, a tick at chance 1 brings a visitor, out in
	 * the yard and at home; a second tick brings none while it's there; and none comes by day, with
	 * {@code hearth_visitors} off, or with no one home.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: "someone home" needs a player in the world
	static void arrives(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = open(helper, level);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		RandomSource random = RandomSource.create(11);
		Runnable tidy = () -> {
			discardVisitors(level, hearth);
			level.getServer().getPlayerList().remove(player);
			level.setBlockAndUpdate(hearth, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(hearth.below(), Blocks.AIR.defaultBlockState());
			level.getGameRules().set(Rules.HEARTH_VISITORS, true, level.getServer());
			command(level, "time set noon");
		};
		RuleChecks.orTidy(tidy, () -> {
			level.setBlockAndUpdate(hearth.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(level, hearth.getX() + 3.5, hearth.getY(), hearth.getZ() + 0.5, Set.of(), 0, 0, false);
			command(level, "time set 12500");
		});
		helper.runAfterDelay(1, () -> RuleChecks.orTidy(tidy, () -> {
			boolean ticks = level.getBlockState(hearth).isRandomlyTicking();
			Mob first = HomeLife.tickLantern(level, hearth, random, 1);
			String firstWhere = first == null ? "none" : first.getType().toShortString() + " "
					+ String.format("%.1f", Math.sqrt(first.distanceToSqr(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 0.5))) + " blocks out";
			boolean firstMarked = first != null && first.entityTags().contains(HomeLife.VISITOR_TAG);
			boolean firstAtHome = first != null && HearthLanterns.protects(level, first.blockPosition());
			double firstOut = first == null ? 0 : Math.sqrt(first.distanceToSqr(hearth.getX() + 0.5, first.getY(), hearth.getZ() + 0.5));
			boolean second = HomeLife.tickLantern(level, hearth, random, 1) != null;
			discardVisitors(level, hearth);
			command(level, "time set noon");
			boolean byDay = HomeLife.tickLantern(level, hearth, random, 1) != null;
			command(level, "time set 12500");
			level.getGameRules().set(Rules.HEARTH_VISITORS, false, level.getServer());
			boolean ruleOff = HomeLife.tickLantern(level, hearth, random, 1) != null;
			level.getGameRules().set(Rules.HEARTH_VISITORS, true, level.getServer());
			player.teleportTo(level, hearth.getX() + 100.5, hearth.getY(), hearth.getZ() + 0.5, Set.of(), 0, 0, false);
			boolean noOneHome = HomeLife.tickLantern(level, hearth, random, 1) != null;
			System.out.println("[cozy_core_gametest] visitor: lantern ticks " + ticks + "; first " + firstWhere + ", marked " + firstMarked
					+ ", at home " + firstAtHome + "; a second while it's there " + second + "; by day " + byDay + "; rule off " + ruleOff
					+ "; no one home " + noOneHome);
			tidy.run();
			helper.assertTrue(ticks, "a Hearth Lantern should get random ticks");
			helper.assertTrue(first != null, "in the evening with someone home, a visitor should come");
			helper.assertTrue(firstMarked, "the visitor should be marked as one");
			helper.assertTrue(firstAtHome && firstOut >= 14 && firstOut <= 26, "the visitor should arrive in the yard, 16 to 24 blocks out (" + firstWhere + ")");
			helper.assertFalse(second, "a home should have one visitor at a time");
			helper.assertFalse(byDay, "no visitor should come by day");
			helper.assertFalse(ruleOff, "with hearth_visitors off, no visitor should come");
			helper.assertFalse(noOneHome, "no visitor should come while no one is home");
			helper.succeed();
		}));
	}

	/**
	 * At night a visiting cat 12 blocks out walks to the lantern and lies down; a visiting cat that's
	 * tamed stops being a visitor. In the morning, with no one near, the untamed one is gone and the
	 * tamed one stays. Rabbits and foxes have the visit goal too.
	 */
	@SuppressWarnings("removal")
	static void staysTheNight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = open(helper, level);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Mob[] cats = new Mob[2];
		Runnable tidy = () -> {
			for (Mob cat : cats) {
				if (cat != null) {
					cat.discard();
				}
			}
			discardVisitors(level, hearth);
			level.getServer().getPlayerList().remove(player);
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
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(level, hearth.getX() + 16.5, hearth.getY(), hearth.getZ() + 2.5, Set.of(), 0, 0, false);
			command(level, "time set 14000");
			cats[0] = HomeLife.visit(level, hearth.offset(12, 0, 0), EntityTypes.CAT);
			cats[1] = HomeLife.visit(level, hearth.offset(12, 0, 2), EntityTypes.CAT);
			((Cat) cats[1]).tame(player);
		});
		helper.runAfterDelay(200, () -> RuleChecks.orTidy(tidy, () -> {
			double fromHearth = Math.sqrt(cats[0].distanceToSqr(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 0.5));
			boolean lying = ((Cat) cats[0]).isLying();
			// The home's visitor count is what lets a new one come: a tamed cat mustn't be in it.
			boolean tamedStillVisitor = HomeLife.visitorsNear(level, hearth, 32).contains(cats[1]) || cats[1].entityTags().contains(HomeLife.VISITOR_TAG);
			List<String> goals = new ArrayList<>();
			for (EntityType<? extends Mob> type : List.of(EntityTypes.RABBIT, EntityTypes.FOX)) {
				Mob mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				goals.add(type.toShortString() + " " + hasVisitGoal(mob));
				mob.discard();
			}
			command(level, "time set 1000");
			player.teleportTo(level, hearth.getX() + 200.5, hearth.getY(), hearth.getZ() + 0.5, Set.of(), 0, 0, false);
			helper.runAfterDelay(100, () -> RuleChecks.orTidy(tidy, () -> {
				boolean untamedGone = cats[0].isRemoved(), tamedStays = !cats[1].isRemoved();
				System.out.println("[cozy_core_gametest] visitor: at night the visiting cat is " + String.format("%.1f", fromHearth)
						+ " blocks from the lantern, lying " + lying + "; tamed one still marked " + tamedStillVisitor + "; goals " + goals
						+ "; in the morning untamed gone " + untamedGone + ", tamed stays " + tamedStays);
				tidy.run();
				helper.assertTrue(fromHearth < 3.5, "at night the visiting cat should rest by the lantern (" + fromHearth + " blocks away)");
				helper.assertTrue(lying, "the visiting cat should lie down by the lantern");
				helper.assertFalse(tamedStillVisitor, "a visitor that's tamed should stop being a visitor");
				helper.assertTrue(goals.stream().allMatch(g -> g.endsWith("true")), "rabbits and foxes should have the visit goal (" + goals + ")");
				helper.assertTrue(untamedGone, "in the morning, with no one near, an untamed visitor should have gone");
				helper.assertTrue(tamedStays, "a visitor that's been tamed should stay");
				helper.succeed();
			}));
		}));
	}

	private static boolean hasVisitGoal(Mob mob) {
		try {
			java.lang.reflect.Field f = Mob.class.getDeclaredField("goalSelector");
			f.setAccessible(true);
			return ((GoalSelector) f.get(mob)).getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof HomeLife.VisitGoal);
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	/** Over 300 picks, about two in three are cats, and every other visitor is one this biome spawns. */
	static void biome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = open(helper, level);
		RandomSource random = RandomSource.create(5);
		Map<String, Integer> counts = new HashMap<>();
		List<String> notHere = new ArrayList<>();
		var spawns = level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, at).getMobsToSpawn(MobCategory.CREATURE).unwrap();
		for (int i = 0; i < 300; i++) {
			EntityType<? extends Mob> type = HomeLife.visitorFor(level, at, random);
			counts.merge(type.toShortString(), 1, Integer::sum);
			if (type != EntityTypes.CAT && spawns.stream().noneMatch(s -> s.value().type() == type)) {
				notHere.add(type.toShortString());
			}
		}
		int cats = counts.getOrDefault("cat", 0);
		System.out.println("[cozy_core_gametest] visitor: in " + level.getBiome(at).getRegisteredName() + " 300 picks gave " + counts);
		helper.assertTrue(cats >= 170 && cats < 300 || cats == 300 && counts.size() == 1, "about two visitors in three should be cats (" + counts + ")");
		helper.assertTrue(notHere.isEmpty(), "every other visitor should be one this biome spawns (" + notHere + ")");
		helper.succeed();
	}
}
