package com.gabbymaybelle.cozycore.gametest;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import com.gabbymaybelle.cozycore.Trades;
import com.gabbymaybelle.cozycore.fabric.FabricPlatform;
import com.gabbymaybelle.cozycore.gametest.harness.ClientGameTest;
import com.gabbymaybelle.cozycore.gametest.harness.ClientGameTestContext;
import com.gabbymaybelle.cozycore.gametest.harness.TestServerContext;
import com.gabbymaybelle.cozycore.gametest.harness.TestSingleplayerContext;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gamerule.v1.CustomGameRuleCategory;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.phys.AABB;

/**
 * The rules and the Hearth Lantern in a real game. Each rule is checked both ways: kind (the
 * default) and switched back to vanilla with its game rule, so a check that stops working shows
 * up as a failure rather than as a rule that quietly does nothing.
 */
public class RulesTest implements ClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode creative @a");
			server.runCommand("weather clear");
			server.runCommand("difficulty normal");
			server.runCommand("gamerule doMobSpawning true");
			BlockPos home = server.computeOnServer(s -> player(s).blockPosition());
			// Keep the whole test area loaded: the lantern room and its far lantern sit beyond a
			// small render distance, and monsters only spawn more than 24 blocks from a player.
			server.runCommand("forceload add " + (home.getX() - 64) + " " + (home.getZ() - 64) + " " + (home.getX() + 64) + " " + (home.getZ() + 64));

			defaults(server);
			explosions(ctx, server, home.offset(30, 0, 0));
			endermen(server);
			fire(ctx, server, home.offset(0, 0, 30));
			lanterns(ctx, server, home.offset(-30, 0, -30));
			keepOnDeath(ctx, server, home.offset(-30, 0, 30));
			trades(server);
			screenshot(ctx, server, home.offset(0, 0, -6));
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " check(s) failed:\n - " + String.join("\n - ", failures));
		}
	}

	/** The rules exist, show under their own heading, and a new world starts kind. */
	private void defaults(TestServerContext server) {
		check(server.computeOnServer(s -> !rules(s).getBoolean(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS)), "mob explosions spare blocks by default");
		check(server.computeOnServer(s -> !rules(s).getBoolean(Rules.ENDERMEN_TAKE_BLOCKS)), "endermen leave blocks alone by default");
		check(server.computeOnServer(s -> !rules(s).getBoolean(Rules.FIRE_BURNS_BLOCKS)), "fire doesn't burn blocks by default");
		check(server.computeOnServer(s -> rules(s).getInt(Rules.HEARTH_LANTERN_RADIUS) == 32), "the Hearth Lantern radius defaults to 32");
		check(server.computeOnServer(s -> !rules(s).getBoolean(GameRules.RULE_DOINSOMNIA)), "a new world starts with phantoms off");
		check(server.computeOnServer(s -> rules(s).getBoolean(Rules.KEEP_HOTBAR_ON_DEATH)), "the hotbar is kept on death by default");
		check(CustomGameRuleCategory.getCategory(Rules.FIRE_BURNS_BLOCKS).orElse(null) == FabricPlatform.CATEGORY, "the rules have the Cozy Core heading");
		check("cozy_core:fire_burns_blocks".equals(Rules.FIRE_BURNS_BLOCKS.getId()), "a rule's id is cozy_core:... (got " + Rules.FIRE_BURNS_BLOCKS.getId() + ")");
		check(server.computeOnServer(s -> s.getRecipeManager().byKey(HearthLantern.ID).isPresent()), "the Hearth Lantern recipe loads");
	}

	/**
	 * The builder trades, in an ordinary world: a new mason at level 1 and at level 2 offers three
	 * trades (vanilla two) from a pool of four, so at least one is Cozy Core's builder stone, and a
	 * new wandering trader offers seven (vanilla six).
	 */
	private void trades(TestServerContext server) {
		for (int tier = 1; tier <= 2; tier++) {
			int level = tier;
			MerchantOffers offers = server.computeOnServer(s -> {
				Villager mason = EntityType.VILLAGER.create(s.overworld());
				mason.setVillagerData(mason.getVillagerData().setType(VillagerType.PLAINS).setProfession(VillagerProfession.MASON).setLevel(level));
				return mason.getOffers();
			});
			check(offers.size() == 2 + Trades.EXTRA_SLOTS, "a new level-" + tier + " mason offers 3 trades (got " + offers.size() + ")");
			check(offers.stream().anyMatch(o -> o.getResult().is(Items.TUFF) || o.getResult().is(Items.MUD_BRICKS) || o.getResult().is(Items.CALCITE)
					|| o.getResult().is(Items.PACKED_MUD)), "a new level-" + tier + " mason offers builder stone");
		}
		int wandering = server.computeOnServer(s -> EntityType.WANDERING_TRADER.create(s.overworld()).getOffers().size());
		check(wandering == 5 + Trades.EXTRA_SLOTS + 1, "a new wandering trader offers 7 trades (got " + wandering + ")");
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
			Zombie zombie = EntityType.ZOMBIE.create(level);
			zombie.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5, 0, 0);
			zombie.setNoAi(true);
			level.addFreshEntity(zombie);
			return placed;
		});
		ctx.waitTicks(2);
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			Creeper creeper = EntityType.CREEPER.create(level);
			creeper.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
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
			EnderMan enderman = EntityType.ENDERMAN.create(server.overworld());
			Class<?> type = Class.forName("net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal");
			Constructor<?> constructor = type.getDeclaredConstructor(EnderMan.class);
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
		// A fire over planks it can't burn ages past 3 and goes out, like fire on stone; each tick has
		// a 1 in 3 chance of ageing it, so in 1500 ticks a straggler or two is possible.
		check(kind[1] <= 2, "with the rule off, fire still goes out (" + kind[1] + " of 16 fires left)");
		set(server, Rules.FIRE_BURNS_BLOCKS, true);
		int[] vanilla = burn(ctx, server, at);
		set(server, Rules.FIRE_BURNS_BLOCKS, false);
		check(vanilla[0] > 0, "with the rule on, fire burns planks again (" + vanilla[0] + " of 49 gone)");
	}

	/** A 7x7 oak plank floor with fire on every other plank (16 fires), left to burn for 1500 ticks (at 200 ticks a second). Returns {planks gone, fires left}. */
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
					+ ", difficulty " + level.getDifficulty()
					+ ", nearest player " + (level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), -1, false) == null ? "none"
							: Math.sqrt(level.getNearestPlayer(at.getX(), at.getY(), at.getZ(), -1, false).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))) + " blocks");
		});
	}

	/**
	 * Dying in survival with a pickaxe on the hotbar, cobblestone in the main inventory and a shield
	 * in the off-hand. Kind: the cobblestone drops where the player fell, the pickaxe doesn't, and
	 * after respawning the pickaxe and shield are back in their slots while the cobblestone isn't.
	 * Vanilla (rule off): the pickaxe drops too and nothing comes back.
	 */
	private void keepOnDeath(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
					for (int y = 0; y <= 2; y++) {
						level.setBlockAndUpdate(at.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		});
		Death kind = die(ctx, server, at);
		check(kind.cobbleDropped(), "the cobblestone from the inventory drops where the player fell");
		check(!kind.pickaxeDropped(), "with the rule on, the hotbar's pickaxe doesn't drop");
		check(kind.pickaxeBack(), "with the rule on, the pickaxe is back on the hotbar after respawning");
		check(kind.shieldBack(), "with the rule on, the off-hand shield is back after respawning");
		check(!kind.cobbleBack(), "the rest of the inventory doesn't come back");
		set(server, Rules.KEEP_HOTBAR_ON_DEATH, false);
		Death vanilla = die(ctx, server, at);
		set(server, Rules.KEEP_HOTBAR_ON_DEATH, true);
		check(vanilla.pickaxeDropped(), "with the rule off, the pickaxe drops like anything else");
		check(!vanilla.pickaxeBack() && !vanilla.shieldBack(), "with the rule off, nothing comes back after respawning");
		server.runCommand("gamemode creative @a");
	}

	private record Death(boolean cobbleDropped, boolean pickaxeDropped, boolean pickaxeBack, boolean shieldBack, boolean cobbleBack) {
	}

	/** Kills the player at {@code at} in survival, respawns them, and reports what dropped and what came back. */
	private static Death die(ClientGameTestContext ctx, TestServerContext server, BlockPos at) {
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.getInventory().clearContent();
			player.teleportTo(s.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0);
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
			player.getInventory().setItem(20, new ItemStack(Items.COBBLESTONE, 10));
			player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
		});
		ctx.waitTicks(5);
		server.runCommand("kill @a");
		ctx.waitForScreen(DeathScreen.class);
		boolean cobbleDropped = dropped(server, at, Items.COBBLESTONE);
		boolean pickaxeDropped = dropped(server, at, Items.DIAMOND_PICKAXE);
		ctx.runOnClient(mc -> mc.player.respawn());
		ctx.waitForScreen(null);
		for (int i = 0; i < 200 && !server.computeOnServer(s -> player(s).isAlive()); i++) {
			ctx.waitTicks(1);
		}
		ctx.waitTicks(5);
		Death death = server.computeOnServer(s -> {
			Inventory inventory = player(s).getInventory();
			return new Death(cobbleDropped, pickaxeDropped, inventory.getItem(0).is(Items.DIAMOND_PICKAXE),
					inventory.getItem(Inventory.SLOT_OFFHAND).is(Items.SHIELD), inventory.contains(new ItemStack(Items.COBBLESTONE)));
		});
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("kill @e[type=minecraft:experience_orb]");
		return death;
	}

	/** Whether an item of that kind is lying within 4 blocks of {@code at}. */
	private static boolean dropped(TestServerContext server, BlockPos at, Item item) {
		return server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4), e -> e.getItem().is(item)).isEmpty());
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
			player.teleportTo(level, at.getX() + 2.5, at.getY() + 1, at.getZ() + 4.5, java.util.Set.of(), 180, 10);
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("hearth_lantern");
	}

	private static GameRules rules(MinecraftServer server) {
		return server.overworld().getGameRules();
	}

	private static void set(TestServerContext server, GameRules.Key<GameRules.BooleanValue> rule, boolean value) {
		server.runOnServer(s -> rules(s).getRule(rule).set(value, s));
	}

	private static void set(TestServerContext server, GameRules.Key<GameRules.IntegerValue> rule, int value) {
		server.runOnServer(s -> rules(s).getRule(rule).set(value, s));
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
