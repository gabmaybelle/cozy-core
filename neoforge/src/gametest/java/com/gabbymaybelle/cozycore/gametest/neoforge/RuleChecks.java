package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * The checks behind each test in {@link CozyCoreGameTests}, the same ones the Fabric client game
 * test makes. Positions are relative to the test's template (0,0,0 is its north-west bottom
 * corner); every test lays its own stone floor at y=0 and builds above it. Each check for a rule
 * takes {@code kind}: true in the kind environment, false with the rule switched to vanilla.
 */
final class RuleChecks {
	private RuleChecks() {
	}

	/** A new world starts kind: every rule at its kind value, phantoms off, the rules under their own heading. */
	static void defaults(GameTestHelper helper) {
		GameRules rules = helper.getLevel().getGameRules();
		helper.assertFalse(rules.get(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS), "mob explosions should spare blocks by default");
		helper.assertFalse(rules.get(Rules.ENDERMEN_TAKE_BLOCKS), "endermen should leave blocks alone by default");
		helper.assertFalse(rules.get(Rules.FIRE_BURNS_BLOCKS), "fire shouldn't burn blocks by default");
		helper.assertValueEqual(rules.get(Rules.HEARTH_LANTERN_RADIUS), 32, "the Hearth Lantern radius");
		helper.assertFalse(rules.get(GameRules.SPAWN_PHANTOMS), "a new world should start with phantoms off");
		helper.assertTrue(rules.availableRules().anyMatch(rule -> rule.category() == Rules.CATEGORY), "the rules should have the Cozy Core heading");
		helper.assertValueEqual(Rules.FIRE_BURNS_BLOCKS.getIdentifier().toString(), "cozy_core:fire_burns_blocks", "a rule's id");
		helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, HearthLantern.ID)).isPresent(),
				"the Hearth Lantern recipe should load");
		helper.succeed();
	}

	/**
	 * Every recipe in the pack folder ({@code -Dcozy.expect}; the run sets it to the repo's pack/)
	 * is in the game, unless its load condition names a mod that isn't here, in which case it
	 * must not be. So the jar carries the recipes, and NeoForge's conditions on them work.
	 */
	static void recipes(GameTestHelper helper) {
		Path recipes = Path.of(System.getProperty("cozy.expect")).resolve("data/cozy_core/recipe");
		helper.assertTrue(Files.isDirectory(recipes), "no recipe folder at " + recipes);
		RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
		int expected = 0;
		int waiting = 0;
		List<String> wrong = new ArrayList<>();
		try (Stream<Path> files = Files.walk(recipes)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				String name = recipes.relativize(file).toString().replace('\\', '/').replace(".json", "");
				boolean present = manager.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("cozy_core", name))).isPresent();
				boolean wanted = conditionsMet(json);
				if (wanted) {
					expected++;
				} else {
					waiting++;
				}
				if (present != wanted) {
					wrong.add(name + (wanted ? " is missing" : " loaded although its mod isn't here"));
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("couldn't read the recipes in " + recipes, e);
		}
		helper.assertTrue(expected > 0, "no recipes expected from " + recipes);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " recipe(s) wrong: " + String.join(", ", wrong.subList(0, Math.min(wrong.size(), 10))));
		System.out.println("[cozy_core_gametest] " + expected + " recipes in the game, " + waiting + " waiting on mods that aren't here");
		helper.succeed();
	}

	/** NeoForge load conditions: the pack only uses {@code neoforge:mod_loaded}. */
	private static boolean conditionsMet(JsonObject json) {
		if (!json.has("neoforge:conditions")) {
			return true;
		}
		for (JsonElement element : json.getAsJsonArray("neoforge:conditions")) {
			JsonObject condition = element.getAsJsonObject();
			String type = condition.get("type").getAsString();
			if (!"neoforge:mod_loaded".equals(type)) {
				throw new IllegalStateException("the test doesn't know the load condition " + type);
			}
			if (!ModList.get().isLoaded(condition.get("modid").getAsString())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A creeper's blast in a 5x5x5 stone room with a zombie inside. Kind: every block stands and
	 * the zombie is still hurt. Vanilla: blocks go.
	 */
	static void explosions(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(Rules.is(level, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS), !kind, "the mob_explosions_break_blocks rule for this test");
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		int placed = 0;
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = -1; y <= 3; y++) {
					boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
					level.setBlockAndUpdate(at.offset(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
					if (wall) {
						placed++;
					}
				}
			}
		}
		int before = placed;
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		zombie.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5, 0, 0);
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		helper.runAfterDelay(2, () -> {
			Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
			creeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			level.explode(creeper, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.MOB);
			helper.runAfterDelay(5, () -> {
				int gone = before - count(level, at, -2, -1, -2, 2, 3, 2, Blocks.STONE);
				float health = zombie.getHealth();
				zombie.discard();
				if (kind) {
					helper.assertValueEqual(gone, 0, "stone blocks broken by a creeper's blast");
					helper.assertTrue(health < zombie.getMaxHealth(), "the blast should still hurt the zombie (health " + health + ")");
				} else {
					helper.assertTrue(gone > 0, "with the rule on, the blast should break blocks again (" + gone + " gone)");
				}
				helper.succeed();
			});
		});
	}

	/** The goal that makes an enderman pick a block up. Kind: it never wants to start. Vanilla: it soon does. */
	static void endermen(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(Rules.is(level, Rules.ENDERMEN_TAKE_BLOCKS), !kind, "the endermen_take_blocks rule for this test");
		if (kind) {
			helper.assertValueEqual(wantsBlocks(level, 300), 0, "times in 300 an enderman wanted to pick a block up");
		} else {
			helper.assertTrue(wantsBlocks(level, 2000) > 0, "with the rule on, an enderman should want to pick a block up again (never in 2000 tries)");
		}
		helper.succeed();
	}

	/** How many times out of {@code tries} the take-block goal of a fresh enderman says it can start (vanilla: about 1 in 20). */
	private static int wantsBlocks(ServerLevel level, int tries) {
		try {
			Enderman enderman = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.COMMAND);
			Class<?> type = Class.forName("net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal");
			Constructor<?> constructor = type.getDeclaredConstructor(Enderman.class);
			constructor.setAccessible(true);
			Goal goal = (Goal) constructor.newInstance(enderman);
			int wanted = 0;
			for (int i = 0; i < tries; i++) {
				if (goal.canUse()) {
					wanted++;
				}
			}
			return wanted;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("couldn't make an enderman's take-block goal", e);
		}
	}

	/**
	 * A 7x7 oak-plank floor with fire on every other plank, left 1500 ticks at 200 ticks a second.
	 * Kind: no plank burns and the fire goes out. Vanilla: planks burn.
	 */
	static void fire(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		helper.assertValueEqual(Rules.is(level, Rules.FIRE_BURNS_BLOCKS), !kind, "the fire_burns_blocks rule for this test");
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(at.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		for (int x = -3; x <= 3; x += 2) {
			for (int z = -3; z <= 3; z += 2) {
				level.setBlockAndUpdate(at.offset(x, 1, z), Blocks.FIRE.defaultBlockState());
			}
		}
		server.tickRateManager().setTickRate(200.0F);
		helper.runAfterDelay(1500, () -> {
			server.tickRateManager().setTickRate(20.0F);
			int gone = 49 - count(level, at, -3, 0, -3, 3, 0, 3, Blocks.OAK_PLANKS);
			int fires = count(level, at, -3, 1, -3, 3, 1, 3, Blocks.FIRE);
			if (kind) {
				helper.assertValueEqual(gone, 0, "planks burnt away");
				helper.assertTrue(fires < 25, "the fire should still go out (" + fires + " of 25 fires left)");
			} else {
				helper.assertTrue(gone > 0, "with the rule on, fire should burn planks again (" + gone + " of 49 gone)");
			}
			helper.succeed();
		});
	}

	/**
	 * A dark 13x13 stone room monsters can spawn in, asked to spawn 80 times at a go. Kind (radius
	 * 32): nothing spawns with a Hearth Lantern 10 blocks away; breaking it and placing one 40
	 * blocks away lets monsters spawn; a hanging one straight above the room stops them again, and
	 * the world's list of lanterns follows each change. Vanilla (radius 0): the lantern 10 blocks
	 * away stops nothing.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: vanilla only spawns monsters when a player is in the world
	static void lanterns(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(Rules.get(level, Rules.HEARTH_LANTERN_RADIUS), kind ? 32 : 0, "the hearth_lantern_radius rule for this test");
		BlockPos room = helper.absolutePos(new BlockPos(6, 1, 6));
		BlockPos near = helper.absolutePos(new BlockPos(16, 1, 6));
		BlockPos far = helper.absolutePos(new BlockPos(46, 1, 6));
		BlockPos above = helper.absolutePos(new BlockPos(6, 7, 6));
		Block lantern = HearthLantern.BLOCK.get();
		for (int x = -6; x <= 6; x++) {
			for (int z = -6; z <= 6; z++) {
				for (int y = -1; y <= 4; y++) {
					boolean wall = Math.abs(x) == 6 || Math.abs(z) == 6 || y == -1 || y == 4;
					level.setBlockAndUpdate(room.offset(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		level.setBlockAndUpdate(near.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(far.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(near, lantern.defaultBlockState());
		// Vanilla only spawns monsters more than 24 blocks from the nearest player and from the
		// world's spawn point, so for this test there's a player high above the room and the
		// spawn point is moved away.
		LevelData.RespawnData spawnPoint = level.getRespawnData();
		level.setRespawnData(LevelData.RespawnData.of(level.dimension(), room.offset(1000, 0, 0), 0, 0));
		ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
		watcher.teleportTo(level, room.getX() + 0.5, room.getY() + 40, room.getZ() + 0.5, Set.of(), 0, 90, false);
		Runnable tidy = () -> {
			level.getServer().getPlayerList().remove(watcher);
			level.setRespawnData(spawnPoint);
		};
		// Let the light engine catch up with the new roof: the room has to read as dark.
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(HearthLanterns.in(level).contains(near), "a placed Hearth Lantern should be on the world's list (" + HearthLanterns.in(level) + ")");
			int first = spawn(level, room);
			if (!kind) {
				helper.assertTrue(first > 0, "with the radius at 0, monsters should spawn by the lantern (" + first + " spawned; " + roomState(level, room) + ")");
				tidy.run();
				helper.succeed();
				return;
			}
			helper.assertValueEqual(first, 0, "monsters spawned 10 blocks from a Hearth Lantern");
			level.setBlockAndUpdate(near, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(far, lantern.defaultBlockState());
			helper.runAfterDelay(2, () -> {
				Set<BlockPos> known = HearthLanterns.in(level);
				helper.assertTrue(!known.contains(near) && known.contains(far), "breaking and placing lanterns should keep the list right (" + known + ")");
				int outside = spawn(level, room);
				helper.assertTrue(outside > 0, "a lantern 40 blocks away shouldn't reach the room (" + outside + " spawned; " + roomState(level, room) + ")");
				level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(above.above(), Blocks.STONE.defaultBlockState());
				// A hanging lantern straight above the room: height doesn't matter.
				level.setBlockAndUpdate(above, lantern.defaultBlockState().setValue(LanternBlock.HANGING, true));
				helper.runAfterDelay(2, () -> {
					helper.assertValueEqual(spawn(level, room), 0, "monsters spawned under a hanging Hearth Lantern");
					tidy.run();
					helper.succeed();
				});
			});
		});
	}

	/** Asks vanilla's natural spawner for monsters at the room's centre 80 times, then counts (and removes) what appeared. */
	private static int spawn(ServerLevel level, BlockPos room) {
		for (int i = 0; i < 80; i++) {
			// At floor level: the spawner wants solid ground right under the spot it's given.
			NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, room);
		}
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, new AABB(room).inflate(8));
		monsters.forEach(Monster::discard);
		return monsters.size();
	}

	/** What the room looks like to the spawner, for a failure message. */
	private static String roomState(ServerLevel level, BlockPos at) {
		var player = level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), -1, false);
		return "light " + level.getMaxLocalRawBrightness(at) + ", sky " + level.getBrightness(LightLayer.SKY, at)
				+ ", block " + level.getBrightness(LightLayer.BLOCK, at) + ", floor " + level.getBlockState(at.below())
				+ ", difficulty " + level.getDifficulty() + ", spawn_monsters " + level.getGameRules().get(GameRules.SPAWN_MONSTERS)
				+ ", spawn point " + level.getRespawnData().pos()
				+ ", nearest player " + (player == null ? "none" : Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(at))) + " blocks");
	}

	/** How many blocks of a kind are in the box {@code at + (x1,y1,z1)} to {@code at + (x2,y2,z2)}. */
	private static int count(ServerLevel level, BlockPos at, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
		int found = 0;
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				for (int y = y1; y <= y2; y++) {
					if (level.getBlockState(at.offset(x, y, z)).is(block)) {
						found++;
					}
				}
			}
		}
		return found;
	}
}
