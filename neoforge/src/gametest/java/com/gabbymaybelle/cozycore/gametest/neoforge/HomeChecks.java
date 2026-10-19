package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanternBlockEntity;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import com.gabbymaybelle.cozycore.SafeAndSound;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.phys.AABB;

/**
 * The checks for home: what a Hearth Lantern keeps safe inside its reach, and that the wild just
 * outside stays vanilla. Each runs in an environment of its own (the same rules as a new world),
 * so it's alone in its batch and its lanterns can't guard another test's blocks; and each removes
 * its lanterns before it ends. The reach is the default, 32 blocks.
 */
final class HomeChecks {
	private static final int REACH = 32;

	private HomeChecks() {
	}

	/** Places a Hearth Lantern (of the classic style) on a stone block at {@code pos}. */
	private static void lantern(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(pos, HearthLantern.BLOCK.get().defaultBlockState());
	}

	private static void removeLantern(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
	}

	/**
	 * Home (protecting on): a creeper's blast in the stone room, with a lantern 10 blocks away,
	 * breaks nothing and still hurts the zombie, and a player watching from 10 blocks earns "Safe
	 * and sound". Hearths switched off ({@code protecting} false): the same blast breaks blocks and
	 * nobody earns it.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: the only way to get a real ServerPlayer in a test
	static void explosions(GameTestHelper helper, boolean protecting) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(Rules.is(level, Rules.HEARTHS_PROTECT_BUILDS), protecting, "the hearths_protect_builds rule for this test");
		helper.assertTrue(Rules.is(level, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS), "the world-wide switch should be at vanilla for this test");
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos hearth = at.offset(10, -1, 0);
		RuleChecks.stoneRoom(level, at);
		lantern(level, hearth);
		AdvancementHolder safeAndSound = level.getServer().getAdvancements().get(CozyCore.id("cozy/" + SafeAndSound.CRITERION));
		RuleChecks.orTidy(() -> removeLantern(level, hearth), () -> {
			helper.assertTrue(safeAndSound != null, "the Safe and sound advancement should be in the game");
			helper.assertTrue(HearthLanterns.guards(level, at) == protecting, "whether the room counts as home should be " + protecting);
		});
		// Made after the checks above, so a failed check can't leave a player standing in the
		// world: vanilla spawns no monsters within 24 blocks of one, which would upset the next batch.
		ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
		watcher.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 10.5, Set.of(), 0, 0, false);
		RuleChecks.blast(helper, level, at, (gone, hurt) -> {
			boolean earned = watcher.getAdvancements().getOrStartProgress(safeAndSound).isDone();
			level.getServer().getPlayerList().remove(watcher);
			removeLantern(level, hearth);
			if (protecting) {
				helper.assertValueEqual(gone, 0, "stone blocks broken at home by a creeper's blast");
				helper.assertTrue(hurt, "the blast at home should still hurt the zombie");
				helper.assertTrue(earned, "the player watching should earn Safe and sound");
			} else {
				helper.assertTrue(gone > 0, "with hearths switched off, the blast should break blocks (" + gone + " gone)");
				helper.assertFalse(earned, "with hearths switched off, nobody should earn Safe and sound");
			}
			helper.succeed();
		});
	}

	/**
	 * A blast on the edge of home: the lantern is exactly the reach west of the room's centre, so
	 * the room's west half is home and its east half is wild. Every stone block at home stands;
	 * the wild half loses some.
	 */
	static void explosionEdge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos hearth = at.offset(-REACH, -1, 0);
		RuleChecks.stoneRoom(level, at);
		lantern(level, hearth);
		List<BlockPos> home = new ArrayList<>();
		List<BlockPos> wild = new ArrayList<>();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = -1; y <= 3; y++) {
					BlockPos pos = at.offset(x, y, z);
					if (level.getBlockState(pos).is(Blocks.STONE)) {
						(HearthLanterns.guards(level, pos) ? home : wild).add(pos);
					}
				}
			}
		}
		RuleChecks.orTidy(() -> removeLantern(level, hearth), () -> helper.assertTrue(!home.isEmpty() && !wild.isEmpty(),
				"the room should be split by the edge (" + home.size() + " at home, " + wild.size() + " wild)"));
		RuleChecks.blast(helper, level, at, (gone, hurt) -> {
			removeLantern(level, hearth);
			long homeGone = home.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.STONE)).count();
			long wildGone = wild.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.STONE)).count();
			System.out.println("[cozy_core_gametest] blast on the edge: " + homeGone + " of " + home.size() + " home blocks gone, "
					+ wildGone + " of " + wild.size() + " wild blocks gone");
			helper.assertValueEqual(homeGone, 0L, "stone blocks broken on the home side of the edge");
			helper.assertTrue(wildGone > 0, "the wild side of the edge should lose blocks (" + wildGone + " of " + wild.size() + ")");
			helper.succeed();
		});
	}

	/**
	 * The burning plank floor, 1500 ticks at 200 ticks a second. Home ({@code edge} false, a lantern
	 * 10 blocks away): no plank burns and the fires go out. On the edge (the lantern exactly the
	 * reach west of the floor's centre): every plank at home stands, even next to the fires burning
	 * in the wild half, which loses planks.
	 */
	static void fire(GameTestHelper helper, boolean edge) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		helper.assertTrue(Rules.is(level, Rules.FIRE_BURNS_BLOCKS), "the world-wide switch should be at vanilla for this test");
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos hearth = edge ? at.offset(-REACH, 0, 0) : at.offset(10, 0, 0);
		RuleChecks.burningFloor(level, at);
		lantern(level, hearth);
		List<BlockPos> home = new ArrayList<>();
		List<BlockPos> wild = new ArrayList<>();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				BlockPos plank = at.offset(x, 0, z);
				(HearthLanterns.guards(level, plank) ? home : wild).add(plank);
			}
		}
		RuleChecks.orTidy(() -> {
			removeLantern(level, hearth);
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					level.setBlockAndUpdate(at.offset(x, 1, z), Blocks.AIR.defaultBlockState());
				}
			}
		}, () -> helper.assertValueEqual(wild.isEmpty(), !edge, "whether the whole floor is home"));
		server.tickRateManager().setTickRate(200.0F);
		helper.runAfterDelay(1500, () -> {
			server.tickRateManager().setTickRate(20.0F);
			removeLantern(level, hearth);
			long homeGone = home.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.OAK_PLANKS)).count();
			long wildGone = wild.stream().filter(pos -> !level.getBlockState(pos).is(Blocks.OAK_PLANKS)).count();
			int fires = RuleChecks.count(level, at, -3, 1, -3, 3, 1, 3, Blocks.FIRE);
			System.out.println("[cozy_core_gametest] fire " + (edge ? "on the edge" : "at home") + ": " + homeGone + " of " + home.size()
					+ " home planks gone, " + wildGone + " of " + wild.size() + " wild planks gone, " + fires + " fires left");
			helper.assertValueEqual(homeGone, 0L, "planks burnt away at home");
			if (edge) {
				helper.assertTrue(wildGone > 0, "the wild half should still burn (" + wildGone + " of " + wild.size() + " gone)");
			} else {
				helper.assertTrue(fires <= 2, "the fires at home should still go out (" + fires + " of 16 left)");
			}
			// Put the fires out so nothing burns on into the next test.
			for (BlockPos plank : home) {
				level.setBlockAndUpdate(plank.above(), Blocks.AIR.defaultBlockState());
			}
			for (BlockPos plank : wild) {
				level.setBlockAndUpdate(plank.above(), Blocks.AIR.defaultBlockState());
			}
			helper.succeed();
		});
	}

	/**
	 * Endermen and home: one 10 blocks from a lantern never wants to take a block, nor one just
	 * outside the reach (it could reach in); one well outside soon does.
	 */
	static void endermen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Rules.is(level, Rules.ENDERMEN_TAKE_BLOCKS), "the world-wide switch should be at vanilla for this test");
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 1));
		lantern(level, hearth);
		int home = RuleChecks.wantsBlocks(level, hearth.offset(10, 0, 0), 300);
		int edge = RuleChecks.wantsBlocks(level, hearth.offset(REACH + 2, 0, 0), 300);
		int wild = RuleChecks.wantsBlocks(level, hearth.offset(REACH + 10, 0, 0), 2000);
		removeLantern(level, hearth);
		helper.assertValueEqual(home, 0, "times in 300 an enderman at home wanted to take a block");
		helper.assertValueEqual(edge, 0, "times in 300 an enderman two blocks outside home wanted to take a block");
		helper.assertTrue(wild > 0, "an enderman out in the wild should want to take a block (never in 2000 tries)");
		helper.succeed();
	}

	/**
	 * Vanilla's own phantom spawner, run 100 times at midnight over a player who hasn't slept for
	 * ages, standing in the open: with no lantern near, phantoms come; with a Hearth Lantern 10
	 * blocks away, none do.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: the spawner goes through the world's real players
	static void phantoms(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		helper.assertTrue(level.getGameRules().get(GameRules.SPAWN_PHANTOMS), "spawn_phantoms should be on for this test");
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set midnight");
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos stand = new BlockPos(at.getX(), Math.max(at.getY(), level.getSeaLevel()) + 1, at.getZ());
		BlockPos hearth = stand.offset(10, 0, 0);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Runnable tidy = () -> {
			server.getPlayerList().remove(player);
			removeLantern(level, hearth);
			level.getEntitiesOfClass(Phantom.class, new AABB(stand).inflate(64)).forEach(Phantom::discard);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set noon");
		};
		RuleChecks.orTidy(tidy, () -> {
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), 0, 0, false);
			player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 10_000_000);
		});
		// Let the sky darken for the new time.
		helper.runAfterDelay(5, () -> RuleChecks.orTidy(tidy, () -> {
			helper.assertTrue(level.getSkyDarken() >= 5, "it should be night for phantoms (sky darken " + level.getSkyDarken() + ")");
			helper.assertTrue(level.canSeeSky(player.blockPosition()), "the player should be under the open sky");
			int wild = phantomsOver(level, player, stand);
			lantern(level, hearth);
			int home = phantomsOver(level, player, stand);
			System.out.println("[cozy_core_gametest] phantoms: " + wild + " in the wild, " + home + " at home (100 tries each)");
			tidy.run();
			helper.assertTrue(wild > 0, "with no lantern near, phantoms should come (none in 100 tries)");
			helper.assertValueEqual(home, 0, "phantoms spawned over a player at home");
			helper.succeed();
		}));
	}

	/** Runs a fresh phantom spawner 100 times, each time as if its wait were over, and counts (and removes) the phantoms. */
	private static int phantomsOver(ServerLevel level, ServerPlayer player, BlockPos stand) {
		PhantomSpawner spawner = new PhantomSpawner();
		try {
			Field wait = PhantomSpawner.class.getDeclaredField("nextTick");
			wait.setAccessible(true);
			for (int i = 0; i < 100; i++) {
				wait.setInt(spawner, 0);
				spawner.tick(level, true);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("couldn't run the phantom spawner", e);
		}
		List<Phantom> phantoms = level.getEntitiesOfClass(Phantom.class, new AABB(stand).inflate(64));
		phantoms.forEach(Phantom::discard);
		return phantoms.size();
	}

	/**
	 * Every style: it's on the world's list of lanterns once placed, mines with a pickaxe, guards
	 * home, drops itself when broken, keeps its anvil name in its drop, and sends players its name
	 * and the reach.
	 */
	static void styles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Component name = Component.literal("Willow Cottage");
		int i = 0;
		for (HearthLantern.Style style : HearthLantern.STYLES) {
			BlockPos pos = helper.absolutePos(new BlockPos(1 + 2 * i++, 1, 1));
			level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(pos, style.block().get().defaultBlockState());
			RuleChecks.orTidy(() -> removeLantern(level, pos), () -> checkStyle(helper, level, style, pos, name));
			removeLantern(level, pos);
			helper.assertFalse(HearthLanterns.in(level).contains(pos), style.id() + " should leave the list when broken");
		}
		helper.succeed();
	}

	private static void checkStyle(GameTestHelper helper, ServerLevel level, HearthLantern.Style style, BlockPos pos, Component name) {
		Block block = style.block().get();
		BlockState state = level.getBlockState(pos);
		helper.assertTrue(state.is(block), style.id() + " should place");
		helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), style.id() + " should mine with a pickaxe");
		helper.assertTrue(HearthLanterns.in(level).contains(pos), style.id() + " should be on the world's list of lanterns");
		helper.assertTrue(HearthLanterns.guards(level, pos.offset(20, 0, 0)), style.id() + " should guard home 20 blocks away");
		if (!(level.getBlockEntity(pos) instanceof HearthLanternBlockEntity lantern)) {
			throw helper.assertionException(Component.literal(style.id() + " should have its block entity"));
		}
		helper.assertTrue(Block.getDrops(state, level, pos, lantern).stream().anyMatch(drop -> drop.is(style.item().get()) && drop.get(DataComponents.CUSTOM_NAME) == null),
				style.id() + " should drop itself (got " + Block.getDrops(state, level, pos, lantern) + ")");
		ItemStack named = new ItemStack(style.item().get());
		named.set(DataComponents.CUSTOM_NAME, name);
		lantern.applyComponentsFromItemStack(named);
		helper.assertValueEqual(lantern.name(), name, style.id() + "'s name from a named item");
		List<ItemStack> drops = Block.getDrops(state, level, pos, lantern);
		helper.assertTrue(drops.stream().anyMatch(drop -> drop.is(style.item().get()) && name.equals(drop.get(DataComponents.CUSTOM_NAME))),
				style.id() + " should drop itself with its name (got " + drops + ")");
		CompoundTag sent = lantern.getUpdateTag(level.registryAccess());
		helper.assertValueEqual(sent.getIntOr("cozy_core_reach", -1), REACH, style.id() + "'s reach as sent to players");
		helper.assertTrue(sent.contains("CustomName"), style.id() + " should send players its name (" + sent + ")");
	}
}
