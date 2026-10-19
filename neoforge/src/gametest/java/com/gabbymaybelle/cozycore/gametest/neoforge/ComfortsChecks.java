package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.Rules;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The checks for 0.6.0's home comforts. Like {@link HomeChecks}, each places its own Hearth
 * Lantern, runs in a batch of its own, and removes everything it made, even when it fails. Each
 * compares a spot at home (10 blocks from the lantern) with a spot out in the wild (60 blocks), so
 * a hook that stops working fails the check rather than passing by doing nothing.
 */
final class ComfortsChecks {
	private static final int HOME = 10, WILD = 60;

	private ComfortsChecks() {
	}

	private static void lantern(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(pos, HearthLantern.BLOCK.get().defaultBlockState());
	}

	private static void removeLantern(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
	}

	/** A stone floor block under {@code pos}, so things stand on something. */
	private static BlockPos floor(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
		return pos;
	}

	private static <T extends Entity> T spawn(ServerLevel level, EntityType<T> type, BlockPos at) {
		T entity = type.create(level, EntitySpawnReason.COMMAND);
		entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			mob.setNoAi(true);
		}
		level.addFreshEntity(entity);
		return entity;
	}

	/**
	 * Two dropped items, one at home and one in the wild (45 blocks from the lantern), each set to a moment before vanilla's five
	 * minutes run out. After 20 ticks the wild one is gone and the one at home is still there; with
	 * {@code hearth_item_minutes} at 5 (vanilla's time) the one at home goes too.
	 */
	static void items(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// All three inside the test's own (48-block) area, where entities always tick: items only age
		// where they tick, and the test server promises that only near a test.
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 6));
		BlockPos home = floor(level, hearth.offset(HOME, 0, 0));
		BlockPos wild = floor(level, hearth.offset(45, 0, 0));
		lantern(level, hearth);
		List<Entity> made = new ArrayList<>();
		Runnable tidy = () -> {
			made.forEach(Entity::discard);
			removeLantern(level, hearth);
			level.getGameRules().set(Rules.HEARTH_ITEM_MINUTES, 60, level.getServer());
		};
		RuleChecks.orTidy(tidy, () -> helper.assertValueEqual(Rules.get(level, Rules.HEARTH_ITEM_MINUTES), 60, "hearth_item_minutes in a new world"));
		helper.runAfterDelay(1, () -> RuleChecks.orTidy(tidy, () -> {
			ItemEntity atHome = item(level, home, made);
			ItemEntity inWild = item(level, wild, made);
			helper.runAfterDelay(20, () -> RuleChecks.orTidy(tidy, () -> {
				System.out.println("[cozy_core_gametest] items: at home age " + ageOf(atHome) + ", removed " + atHome.isRemoved()
						+ "; in the wild removed " + inWild.isRemoved() + " (age " + ageOf(inWild) + ")");
				helper.assertTrue(inWild.isRemoved(), "a dropped item in the wild should be gone after five minutes");
				helper.assertFalse(atHome.isRemoved(), "a dropped item at home should last longer than five minutes");
				helper.assertTrue(ageOf(atHome) < 6000 - 10, "the item at home should have been given more time (age " + ageOf(atHome) + ")");
				level.getGameRules().set(Rules.HEARTH_ITEM_MINUTES, 5, level.getServer());
				// The first one goes first: vanilla merges matching stacks lying together and keeps the
				// younger age, so the new item would otherwise live on as part of it.
				atHome.discard();
				ItemEntity again = item(level, home, made);
				helper.runAfterDelay(20, () -> RuleChecks.orTidy(tidy, () -> {
					helper.assertTrue(again.isRemoved(), "with hearth_item_minutes at 5, an item at home should go after five minutes too (age " + ageOf(again) + ")");
					tidy.run();
					helper.succeed();
				}));
			}));
		}));
	}

	private static int ageOf(ItemEntity item) {
		try {
			Field age = ItemEntity.class.getDeclaredField("age");
			age.setAccessible(true);
			return age.getInt(item);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("couldn't read an item's age", e);
		}
	}

	/** A stack of cobblestone dropped at {@code at}, ten ticks from vanilla's five minutes. */
	private static ItemEntity item(ServerLevel level, BlockPos at, List<Entity> made) {
		ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.COBBLESTONE));
		item.setDeltaMovement(0, 0, 0);
		try {
			Field age = ItemEntity.class.getDeclaredField("age");
			age.setAccessible(true);
			age.setInt(item, 6000 - 10);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("couldn't set an item's age", e);
		}
		level.addFreshEntity(item);
		made.add(item);
		return item;
	}

	/**
	 * A cow at home and one in the wild: fire hurts only the wild one, a creeper's blast beside each
	 * hurts only the wild one, and a zombie (a monster) at home is still hurt by fire.
	 */
	static void animals(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos home = floor(level, hearth.offset(HOME, 0, 0));
		BlockPos wild = floor(level, hearth.offset(WILD, 0, 0));
		lantern(level, hearth);
		LivingEntity cowHome = spawn(level, EntityTypes.COW, home);
		LivingEntity cowWild = spawn(level, EntityTypes.COW, wild);
		LivingEntity zombieHome = spawn(level, EntityTypes.ZOMBIE, home.offset(0, 0, 3));
		floor(level, home.offset(0, 0, 3));
		Runnable tidy = () -> {
			cowHome.discard();
			cowWild.discard();
			zombieHome.discard();
			removeLantern(level, hearth);
		};
		RuleChecks.orTidy(tidy, () -> {
			helper.assertFalse(cowHome.hurtServer(level, level.damageSources().inFire(), 4), "fire should spare a cow at home");
			helper.assertTrue(cowWild.hurtServer(level, level.damageSources().inFire(), 4), "fire should hurt a cow in the wild");
			helper.assertTrue(zombieHome.hurtServer(level, level.damageSources().inFire(), 4), "fire should still hurt a monster at home");
		});
		helper.runAfterDelay(15, () -> RuleChecks.orTidy(tidy, () -> {
			float homeBefore = cowHome.getHealth(), wildBefore = cowWild.getHealth();
			for (BlockPos at : List.of(home, wild)) {
				Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
				creeper.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
				level.explode(creeper, at.getX() + 1.5, at.getY() + 0.5, at.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.MOB);
			}
			helper.assertValueEqual(cowHome.getHealth(), homeBefore, "a cow's health at home after a creeper's blast beside it");
			helper.assertTrue(cowWild.isRemoved() || cowWild.getHealth() < wildBefore, "a creeper's blast should hurt a cow in the wild");
			tidy.run();
			helper.succeed();
		}));
	}

	/**
	 * Lightning: a villager and a pig at home stay what they are; in the wild the villager becomes a
	 * witch and the pig a zombified piglin (vanilla, on any difficulty but peaceful).
	 */
	static void lightning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Inside the test's own area: a mob only turns into another where the new one can be added.
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 6));
		BlockPos home = floor(level, hearth.offset(HOME, 0, 0));
		BlockPos wild = floor(level, hearth.offset(45, 0, 0));
		floor(level, home.offset(0, 0, 3));
		floor(level, wild.offset(0, 0, 3));
		lantern(level, hearth);
		List<Entity> made = new ArrayList<>(List.of(spawn(level, EntityTypes.VILLAGER, home), spawn(level, EntityTypes.PIG, home.offset(0, 0, 3)),
				spawn(level, EntityTypes.VILLAGER, wild), spawn(level, EntityTypes.PIG, wild.offset(0, 0, 3))));
		Runnable tidy = () -> {
			made.forEach(Entity::discard);
			level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(hearth).inflate(80), e -> e.getType() == EntityTypes.WITCH
					|| e.getType() == EntityTypes.ZOMBIFIED_PIGLIN).forEach(Entity::discard);
			removeLantern(level, hearth);
		};
		RuleChecks.orTidy(tidy, () -> {
			helper.assertTrue(level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL, "the test world shouldn't be peaceful (lightning converts nothing then)");
			LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
			for (Entity entity : List.copyOf(made)) {
				entity.thunderHit(level, bolt);
			}
			System.out.println("[cozy_core_gametest] lightning: villager at home removed " + made.get(0).isRemoved() + ", pig at home removed " + made.get(1).isRemoved()
					+ ", villager in the wild removed " + made.get(2).isRemoved() + ", pig in the wild removed " + made.get(3).isRemoved());
			helper.assertFalse(made.get(0).isRemoved(), "a villager at home should still be a villager after lightning");
			helper.assertFalse(made.get(1).isRemoved(), "a pig at home should still be a pig after lightning");
			helper.assertTrue(made.get(2).isRemoved(), "a villager in the wild should turn into a witch");
			helper.assertTrue(made.get(3).isRemoved(), "a pig in the wild should turn into a zombified piglin");
		});
		tidy.run();
		helper.succeed();
	}

	/**
	 * A fall that would hurt: a tamed wolf and a player at home take none, an untamed wolf at home
	 * and a tamed wolf in the wild do.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: the only way to get a real ServerPlayer in a test
	static void falls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos home = floor(level, hearth.offset(HOME, 0, 0));
		BlockPos wild = floor(level, hearth.offset(WILD, 0, 0));
		floor(level, home.offset(0, 0, 3));
		lantern(level, hearth);
		var tameHome = spawn(level, EntityTypes.WOLF, home);
		var wildHome = spawn(level, EntityTypes.WOLF, home.offset(0, 0, 3));
		var tameWild = spawn(level, EntityTypes.WOLF, wild);
		tameHome.setTame(true, false);
		tameWild.setTame(true, false);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Runnable tidy = () -> {
			tameHome.discard();
			wildHome.discard();
			tameWild.discard();
			level.getServer().getPlayerList().remove(player);
			removeLantern(level, hearth);
		};
		RuleChecks.orTidy(tidy, () -> {
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() - 2.5, java.util.Set.of(), 0, 0, false);
			var fall = level.damageSources().fall();
			helper.assertFalse(tameHome.causeFallDamage(10, 1, fall), "a tamed wolf at home should take no fall damage");
			helper.assertFalse(player.causeFallDamage(10, 1, fall), "a player at home should take no fall damage");
			helper.assertTrue(wildHome.causeFallDamage(10, 1, fall), "an untamed wolf at home should still take fall damage");
			helper.assertTrue(tameWild.causeFallDamage(10, 1, fall), "a tamed wolf in the wild should still take fall damage");
		});
		tidy.run();
		helper.succeed();
	}

	/**
	 * What breaking glass, a pane, ice and a bookshelf drops: at home, the block itself; in the wild,
	 * vanilla's (nothing, nothing, nothing, three books). Ice broken at home leaves no water.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: ice's melting step needs a player
	static void glass(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hearth = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos home = floor(level, hearth.offset(HOME, 0, 0));
		BlockPos wild = floor(level, hearth.offset(WILD, 0, 0));
		lantern(level, hearth);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Runnable tidy = () -> {
			level.getServer().getPlayerList().remove(player);
			for (BlockPos at : List.of(home, wild)) {
				level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(3)).forEach(Entity::discard);
			}
			removeLantern(level, hearth);
		};
		RuleChecks.orTidy(tidy, () -> {
			for (Block block : List.of(Blocks.GLASS, Blocks.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.RED), Blocks.ICE, Blocks.BLUE_ICE, Blocks.BOOKSHELF)) {
				BlockState state = block.defaultBlockState();
				List<ItemStack> atHome = Block.getDrops(state, level, home, null);
				List<ItemStack> inWild = Block.getDrops(state, level, wild, null);
				helper.assertTrue(atHome.size() == 1 && atHome.getFirst().is(block.asItem()), block + " broken at home should drop itself (got " + atHome + ")");
				helper.assertTrue(inWild.stream().noneMatch(drop -> drop.is(block.asItem())), block + " broken in the wild should drop what vanilla drops (got " + inWild + ")");
			}
			// Ice broken by a player over stone turns to water in vanilla; at home it doesn't.
			for (BlockPos at : List.of(home, wild)) {
				level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				Blocks.ICE.playerDestroy(level, player, at, Blocks.ICE.defaultBlockState(), null, ItemStack.EMPTY);
			}
			helper.assertTrue(level.getBlockState(home).isAir(), "ice broken at home should leave no water (" + level.getBlockState(home) + ")");
			helper.assertTrue(level.getBlockState(wild).is(Blocks.WATER), "ice broken in the wild should leave water (" + level.getBlockState(wild) + ")");
		});
		tidy.run();
		helper.succeed();
	}
}
