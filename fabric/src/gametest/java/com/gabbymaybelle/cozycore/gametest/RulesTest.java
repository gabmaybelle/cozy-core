package com.gabbymaybelle.cozycore.gametest;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The rules and the Hearth Lantern in a real game. Each rule is checked both ways: kind (the
 * default) and switched back to vanilla with its game rule, so a check that stops working shows
 * up as a failure rather than as a rule that quietly does nothing.
 */
public class RulesTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("weather clear");
			server.runCommand("difficulty normal");
			server.runCommand("gamerule spawn_monsters true");
			BlockPos home = server.computeOnServer(s -> player(s).blockPosition());
			// Keep the whole test area loaded: the lantern room and its far lantern sit beyond a
			// small render distance, and monsters only spawn more than 24 blocks from a player.
			server.runCommand("forceload add " + (home.getX() - 64) + " " + (home.getZ() - 64) + " " + (home.getX() + 64) + " " + (home.getZ() + 64));

			defaults(server);
			explosions(ctx, server, home.offset(30, 0, 0));
			endermen(server);
			fire(ctx, server, home.offset(0, 0, 30));
			lanterns(ctx, server, home.offset(-30, 0, -30));
			screenshot(ctx, server, home.offset(0, 0, -6));
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " check(s) failed:\n - " + String.join("\n - ", failures));
		}
	}

	/** The rules exist, show under their own heading, and a new world starts kind. */
	private void defaults(TestServerContext server) {
		check(server.computeOnServer(s -> !rules(s).get(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS)), "mob explosions spare blocks by default");
		check(server.computeOnServer(s -> !rules(s).get(Rules.ENDERMEN_TAKE_BLOCKS)), "endermen leave blocks alone by default");
		check(server.computeOnServer(s -> !rules(s).get(Rules.FIRE_BURNS_BLOCKS)), "fire doesn't burn blocks by default");
		check(server.computeOnServer(s -> rules(s).get(Rules.HEARTH_LANTERN_RADIUS) == 32), "the Hearth Lantern radius defaults to 32");
		check(server.computeOnServer(s -> !rules(s).get(GameRules.SPAWN_PHANTOMS)), "a new world starts with phantoms off");
		check(server.computeOnServer(s -> rules(s).availableRules().anyMatch(r -> r.category() == Rules.CATEGORY)), "the rules have the Cozy Core heading");
		check("cozy_core:fire_burns_blocks".equals(Rules.FIRE_BURNS_BLOCKS.getIdentifier().toString()), "a rule's id is cozy_core:... (got " + Rules.FIRE_BURNS_BLOCKS.getIdentifier() + ")");
		check(server.computeOnServer(s -> s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, HearthLantern.ID)).isPresent()), "the Hearth Lantern recipe loads");
	}

	/**
	 * A creeper's explosion in a stone room: with the rule off every block stands and the zombie
	 * inside is still hurt; with it on, blocks go.
	 */
	private void explosions(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		int kind = explode(ctx, server, at);
		float hurt = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class, new AABB(at).inflate(6));
			float health = zombies.isEmpty() ? -1 : zombies.getFirst().getHealth();
			zombies.forEach(z -> z.discard());
			return health;
		});
		check(kind == 0, "with the rule off, a creeper's explosion breaks no blocks (" + kind + " gone)");
		check(hurt >= 0 && hurt < 20, "with the rule off, the explosion still hurts (zombie health " + hurt + ")");
		set(server, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS, true);
		int vanilla = explode(ctx, server, at);
		server.runCommand("kill @e[type=minecraft:zombie]");
		set(server, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS, false);
		check(vanilla > 0, "with the rule on, the explosion breaks blocks again (" + vanilla + " gone)");
	}

	/** Builds a 5x5x3 stone room around {@code at}, puts a zombie in it, sets off a creeper-sourced blast, and counts the stone that's gone. */
	private static int explode(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		int before = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
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
			Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			zombie.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5, 0, 0);
			zombie.setNoAi(true);
			level.addFreshEntity(zombie);
			return placed;
		});
		ctx.waitTicks(2);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.COMMAND);
			creeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			level.explode(creeper, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.MOB);
		});
		ctx.waitTicks(5);
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int left = 0;
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					for (int y = -1; y <= 3; y++) {
						if (level.getBlockState(at.offset(x, y, z)).is(Blocks.STONE)) {
							left++;
						}
					}
				}
			}
			return before - left;
		});
	}

	/** The goal that makes an enderman pick a block up: with the rule off it never wants to start; with it on it soon does. */
	private void endermen(TestServerContext server) {
		int kind = server.computeOnServer(s -> wantsBlocks(s, 300));
		check(kind == 0, "with the rule off, an enderman never wants to pick a block up (wanted " + kind + " times in 300)");
		set(server, Rules.ENDERMEN_TAKE_BLOCKS, true);
		int vanilla = server.computeOnServer(s -> wantsBlocks(s, 2000));
		set(server, Rules.ENDERMEN_TAKE_BLOCKS, false);
		check(vanilla > 0, "with the rule on, an enderman wants to pick a block up again (wanted " + vanilla + " times in 2000)");
	}

	/** How many times out of {@code tries} the take-block goal of a fresh enderman says it can start (vanilla: about 1 in 20). */
	private static int wantsBlocks(MinecraftServer server, int tries) {
		try {
			Enderman enderman = EntityTypes.ENDERMAN.create(server.overworld(), EntitySpawnReason.COMMAND);
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
			throw new AssertionError("couldn't make an enderman's take-block goal", e);
		}
	}

	/** Fire on a plank floor: with the rule off, no plank burns and the fire goes out; with it on, planks burn. */
	private void fire(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		int[] kind = burn(ctx, server, at);
		check(kind[0] == 0, "with the rule off, fire burns no planks (" + kind[0] + " of 49 gone)");
		check(kind[1] < 25, "with the rule off, fire still goes out (" + kind[1] + " of 25 fires left)");
		set(server, Rules.FIRE_BURNS_BLOCKS, true);
		int[] vanilla = burn(ctx, server, at);
		set(server, Rules.FIRE_BURNS_BLOCKS, false);
		check(vanilla[0] > 0, "with the rule on, fire burns planks again (" + vanilla[0] + " of 49 gone)");
	}

	/** A 7x7 oak plank floor with fire on every other plank, left to burn for 1500 ticks (at 200 ticks a second). Returns {planks gone, fires left}. */
	private static int[] burn(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					for (int y = 0; y <= 3; y++) {
						level.setBlockAndUpdate(at.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
					level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.AIR.defaultBlockState());
				}
			}
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
		});
		server.runCommand("tick rate 200");
		ctx.waitTicks(1500);
		server.runCommand("tick rate 20");
		int[] result = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int planks = 0;
			int fires = 0;
			for (int x = -3; x <= 3; x++) {
				for (int z = -3; z <= 3; z++) {
					if (level.getBlockState(at.offset(x, 0, z)).is(Blocks.OAK_PLANKS)) {
						planks++;
					}
					if (level.getBlockState(at.offset(x, 1, z)).is(Blocks.FIRE)) {
						fires++;
					}
				}
			}
			return new int[] { 49 - planks, fires };
		});
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int x = -4; x <= 4; x++) {
				for (int z = -4; z <= 4; z++) {
					for (int y = -1; y <= 3; y++) {
						level.setBlockAndUpdate(at.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		});
		return result;
	}

	/**
	 * A dark stone room where monsters can spawn. With a Hearth Lantern 10 blocks away nothing
	 * spawns; with the radius at 0, with the lantern 40 blocks away, or with it broken, monsters
	 * do. The world's record of lanterns follows placing and breaking.
	 */
	private void lanterns(ClientGameTestContext ctx, TestServerContext server, BlockPos room) {
		BlockPos near = room.offset(10, 1, 0);
		BlockPos far = room.offset(40, 1, 0);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int x = -6; x <= 6; x++) {
				for (int z = -6; z <= 6; z++) {
					for (int y = -1; y <= 4; y++) {
						boolean wall = Math.abs(x) == 6 || Math.abs(z) == 6 || y == -1 || y == 4;
						level.setBlockAndUpdate(room.offset(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
					}
				}
			}
			// Ground under the lanterns, and the lanterns.
			level.setBlockAndUpdate(near.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(far.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(near, HearthLantern.BLOCK.get().defaultBlockState());
		});
		// Let the light engine catch up with the new roof: the room has to read as dark.
		ctx.waitTicks(40);
		Set<BlockPos> known = server.computeOnServer(s -> Set.copyOf(HearthLanterns.in(s.overworld())));
		check(known.contains(near), "a placed Hearth Lantern is on the world's list (" + known + ")");

		int guarded = spawn(ctx, server, room);
		check(guarded == 0, "no monster spawns 10 blocks from a Hearth Lantern (" + guarded + " spawned)");
		set(server, Rules.HEARTH_LANTERN_RADIUS, 0);
		int off = spawn(ctx, server, room);
		set(server, Rules.HEARTH_LANTERN_RADIUS, 32);
		check(off > 0, "with the radius at 0, monsters spawn by the lantern (" + off + " spawned; " + roomState(server, room) + ")");

		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			level.setBlockAndUpdate(near, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(far, HearthLantern.BLOCK.get().defaultBlockState());
		});
		ctx.waitTicks(2);
		known = server.computeOnServer(s -> Set.copyOf(HearthLanterns.in(s.overworld())));
		check(!known.contains(near) && known.contains(far), "breaking and placing lanterns keeps the list right (" + known + ")");
		int outside = spawn(ctx, server, room);
		check(outside > 0, "a lantern 40 blocks away doesn't reach the room (" + outside + " spawned; " + roomState(server, room) + ")");

		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(room.offset(0, 8, 0), Blocks.STONE.defaultBlockState());
			// A hanging lantern, straight above the room: height doesn't matter.
			level.setBlockAndUpdate(room.offset(0, 7, 0), HearthLantern.BLOCK.get().defaultBlockState().setValue(LanternBlock.HANGING, true));
		});
		ctx.waitTicks(2);
		int above = spawn(ctx, server, room);
		check(above == 0, "a hanging lantern above the room keeps it quiet (" + above + " spawned)");
		server.runOnServer(s -> s.overworld().setBlockAndUpdate(room.offset(0, 7, 0), Blocks.AIR.defaultBlockState()));
	}

	/** Asks vanilla's natural spawner for monsters at the room's centre 80 times, then counts (and removes) what appeared. */
	private static int spawn(ClientGameTestContext ctx, TestServerContext server, BlockPos room) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int i = 0; i < 80; i++) {
				// At floor level: the spawner wants solid ground right under the spot it's given.
				NaturalSpawner.spawnCategoryForPosition(MobCategory.MONSTER, level, room);
			}
		});
		ctx.waitTicks(2);
		return server.computeOnServer(s -> {
			List<Monster> monsters = s.overworld().getEntitiesOfClass(Monster.class, new AABB(room).inflate(8));
			monsters.forEach(m -> m.discard());
			return monsters.size();
		});
	}

	/** What the room looks like to the spawner, for a failure message. */
	private static String roomState(TestServerContext server, BlockPos room) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos at = room;
			return "light " + level.getMaxLocalRawBrightness(at) + ", sky " + level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at)
					+ ", block " + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at) + ", floor " + level.getBlockState(at.below())
					+ ", difficulty " + level.getDifficulty() + ", spawn_monsters " + level.getGameRules().get(GameRules.SPAWN_MONSTERS)
					+ ", nearest player " + (level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), -1, false) == null ? "none"
							: Math.sqrt(level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), -1, false).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))) + " blocks");
		});
	}

	/** Two lanterns, one standing and one hanging, for the screenshot. */
	private static void screenshot(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Block lantern = HearthLantern.BLOCK.get();
			level.setBlockAndUpdate(at, Blocks.STONE_BRICKS.defaultBlockState());
			level.setBlockAndUpdate(at.above(), lantern.defaultBlockState());
			level.setBlockAndUpdate(at.offset(2, 3, 0), Blocks.OAK_PLANKS.defaultBlockState());
			level.setBlockAndUpdate(at.offset(2, 2, 0), lantern.defaultBlockState().setValue(LanternBlock.HANGING, true));
			level.setBlockAndUpdate(at.offset(4, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
			level.setBlockAndUpdate(at.offset(4, 1, 0), Blocks.LANTERN.defaultBlockState());
			ServerPlayer player = player(s);
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(level, at.getX() + 2.5, at.getY() + 1, at.getZ() + 4.5, java.util.Set.of(), 180, 10, false);
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("hearth_lantern");
	}

	private static GameRules rules(MinecraftServer server) {
		return server.overworld().getGameRules();
	}

	private static <T> void set(TestServerContext server, GameRule<T> rule, T value) {
		server.runOnServer(s -> rules(s).set(rule, value, s));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private void check(boolean ok, String what) {
		if (!ok) {
			failures.add(what);
		}
	}
}
