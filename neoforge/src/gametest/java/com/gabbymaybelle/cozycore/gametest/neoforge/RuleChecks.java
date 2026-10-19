package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.KeepOnDeath;
import com.gabbymaybelle.cozycore.Rules;
import com.gabbymaybelle.cozycore.Trades;
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
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
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

	/**
	 * A new world starts kind at home and vanilla in the wild: hearths keep builds safe, the three
	 * "everywhere" switches start at vanilla's behaviour, phantoms are vanilla's (they leave you
	 * alone at home instead), and the rules are named cozy_core:...
	 */
	static void defaults(GameTestHelper helper) {
		GameRules rules = helper.getLevel().getGameRules();
		helper.assertTrue(rules.getBoolean(Rules.HEARTHS_PROTECT_BUILDS), "hearths should keep builds safe by default");
		helper.assertTrue(rules.getBoolean(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS), "out in the wild, mob explosions should break blocks by default");
		helper.assertTrue(rules.getBoolean(Rules.ENDERMEN_TAKE_BLOCKS), "out in the wild, endermen should take blocks by default");
		helper.assertTrue(rules.getBoolean(Rules.FIRE_BURNS_BLOCKS), "out in the wild, fire should burn blocks by default");
		helper.assertValueEqual(rules.getInt(Rules.HEARTH_LANTERN_RADIUS), 32, "the Hearth Lantern radius");
		helper.assertTrue(rules.getBoolean(GameRules.RULE_DOINSOMNIA), "phantoms should be vanilla in a new world");
		helper.assertTrue(new GameRules().getBoolean(GameRules.RULE_DOINSOMNIA), "phantoms should be vanilla in a brand-new set of rules");
		helper.assertTrue(rules.getBoolean(Rules.KEEP_HOTBAR_ON_DEATH), "the hotbar should be kept on death by default");
		helper.assertValueEqual(Rules.FIRE_BURNS_BLOCKS.getId(), "cozy_core:fire_burns_blocks", "a rule's id");
		helper.assertValueEqual(Rules.KEEP_HOTBAR_ON_DEATH.getCategory(), GameRules.Category.PLAYER, "keep_hotbar_on_death's heading");
		for (HearthLantern.Style style : HearthLantern.STYLES) {
			helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(style.id()).isPresent(),
					"the " + style.id() + " recipe should load");
		}
		helper.succeed();
	}

	/**
	 * The builder trades: in the pools (NeoForge's trade events), and the extra slot. A new mason at
	 * level 1 offers three trades (vanilla two) from a pool of four, so at least one is Cozy Core's;
	 * the same at level 2. A wandering trader offers six common trades (vanilla five) and one rare
	 * (checked only without the experimental trade rebalance, see below).
	 */
	static void trades(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(pool(VillagerTrades.TRADES.get(VillagerProfession.MASON).get(1), level, Items.TUFF), "the mason's level-1 pool should have tuff");
		helper.assertTrue(pool(VillagerTrades.TRADES.get(VillagerProfession.MASON).get(2), level, Items.PACKED_MUD), "the mason's level-2 pool should have packed mud");
		helper.assertTrue(pool(VillagerTrades.WANDERING_TRADER_TRADES.get(1), level, Items.FLOWERING_AZALEA), "the wandering trader's pool should have flowering azalea");
		Predicate<MerchantOffer> ours = offer -> offer.getResult().is(Items.TUFF) || offer.getResult().is(Items.MUD_BRICKS)
				|| offer.getResult().is(Items.CALCITE) || offer.getResult().is(Items.PACKED_MUD);
		for (int tier = 1; tier <= 2; tier++) {
			Villager mason = EntityType.VILLAGER.create(level);
			mason.setVillagerData(mason.getVillagerData().setType(VillagerType.PLAINS).setProfession(VillagerProfession.MASON).setLevel(tier));
			MerchantOffers offers = mason.getOffers();
			helper.assertValueEqual(offers.size(), 2 + Trades.EXTRA_SLOTS, "offers from a new level-" + tier + " mason");
			helper.assertTrue(offers.stream().anyMatch(ours), "a level-" + tier + " mason should offer builder stone (" + describe(offers) + ")");
		}
		// Vanilla's test server switches every experiment on, and with the trade rebalance a wandering
		// trader picks its offers another way (the extra slot is for the usual way). The Fabric test,
		// in an ordinary world, counts them.
		if (!level.enabledFeatures().contains(FeatureFlags.TRADE_REBALANCE)) {
			WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
			helper.assertValueEqual(trader.getOffers().size(), 5 + Trades.EXTRA_SLOTS + 1, "offers from a new wandering trader");
		}
		helper.succeed();
	}

	/** Whether one of the offers in this pool gives the item. */
	private static boolean pool(VillagerTrades.ItemListing[] listings, ServerLevel level, Item item) {
		WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
		for (VillagerTrades.ItemListing listing : listings) {
			MerchantOffer offer = listing.getOffer(trader, level.getRandom());
			if (offer != null && offer.getResult().is(item)) {
				return true;
			}
		}
		return false;
	}

	private static String describe(MerchantOffers offers) {
		return offers.stream().map(o -> o.getResult().getCount() + " " + o.getResult().getItem()).toList().toString();
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
				boolean present = manager.byKey(ResourceLocation.fromNamespaceAndPath("cozy_core", name)).isPresent();
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
		// A wood-mod group's run names its mods, so jars that didn't load fail here rather than
		// leaving all their recipes "waiting" and passing.
		for (String id : System.getProperty("cozy.expectMods", "").split(",")) {
			helper.assertTrue(id.isBlank() || ModList.get().isLoaded(id.strip()), "the mod " + id.strip() + " should be loaded for this run");
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " recipe(s) wrong: " + String.join(", ", wrong.subList(0, Math.min(wrong.size(), 10))));
		System.out.println("[cozy_core_gametest] " + expected + " recipes in the game, " + waiting + " waiting on mods that aren't here");
		helper.succeed();
	}

	/** NeoForge load conditions: the pack only uses {@code neoforge:mod_loaded} and {@code neoforge:item_exists}. */
	private static boolean conditionsMet(JsonObject json) {
		if (!json.has("neoforge:conditions")) {
			return true;
		}
		for (JsonElement element : json.getAsJsonArray("neoforge:conditions")) {
			JsonObject condition = element.getAsJsonObject();
			String type = condition.get("type").getAsString();
			boolean met = switch (type) {
				case "neoforge:mod_loaded" -> ModList.get().isLoaded(condition.get("modid").getAsString());
				case "neoforge:item_exists" -> BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(condition.get("item").getAsString()));
				default -> throw new IllegalStateException("the test doesn't know the load condition " + type);
			};
			if (!met) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The Cozy Core advancements tab: every advancement file in the pack folder is in the game, and
	 * every recipe a "craft this" step names (one per criterion) is a recipe the game has.
	 */
	static void advancements(GameTestHelper helper) {
		Path tab = Path.of(System.getProperty("cozy.expect")).resolve("data/cozy_core/advancement/cozy");
		helper.assertTrue(Files.isDirectory(tab), "no advancements tab at " + tab);
		var server = helper.getLevel().getServer();
		RecipeManager manager = server.getRecipeManager();
		int advancements = 0;
		int recipes = 0;
		List<String> wrong = new ArrayList<>();
		try (Stream<Path> files = Files.list(tab)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
				String name = "cozy/" + file.getFileName().toString().replace(".json", "");
				advancements++;
				if (server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("cozy_core", name)) == null) {
					wrong.add("the advancement cozy_core:" + name + " isn't in the game");
				}
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				for (var criterion : json.getAsJsonObject("criteria").entrySet()) {
					JsonObject conditions = criterion.getValue().getAsJsonObject().getAsJsonObject("conditions");
					if (conditions == null || !conditions.has("recipe_id")) {
						continue;
					}
					recipes++;
					String id = conditions.get("recipe_id").getAsString();
					if (manager.byKey(ResourceLocation.parse(id)).isEmpty()) {
						wrong.add(name + " names the recipe " + id + ", which isn't in the game");
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("couldn't read the advancements in " + tab, e);
		}
		if (advancements != 8) {
			wrong.add(advancements + " advancements in the tab, not 8 (the root and seven steps)");
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " problem(s): " + String.join(", ", wrong.subList(0, Math.min(wrong.size(), 10))));
		System.out.println("[cozy_core_gametest] advancements tab: " + advancements + " advancements, " + recipes + " recipes named, all in the game");
		helper.succeed();
	}

	/**
	 * A creeper's blast in a 5x5x5 stone room with a zombie inside, with no Hearth Lantern near.
	 * Kind (the "everywhere" switch off): every block stands and the zombie is still hurt. Otherwise
	 * (a new world's default, out in the wild): blocks go.
	 */
	static void explosions(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(Rules.is(level, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS), !kind, "the mob_explosions_break_blocks rule for this test");
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		stoneRoom(level, at);
		blast(helper, level, at, (gone, hurt) -> {
			if (kind) {
				helper.assertValueEqual(gone, 0, "stone blocks broken by a creeper's blast");
				helper.assertTrue(hurt, "the blast should still hurt the zombie");
			} else {
				helper.assertTrue(gone > 0, "with the rule on, the blast should break blocks again (" + gone + " gone)");
			}
			helper.succeed();
		});
	}

	/** The 5x5x5 stone room (hollow, 3x3x3 inside) around {@code at}; returns how many stone blocks it has. */
	static int stoneRoom(ServerLevel level, BlockPos at) {
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
		return placed;
	}

	/**
	 * A creeper's blast of power 3 at the centre of the stone room at {@code at}, with a zombie
	 * beside it; after it, {@code then} gets how many of the room's stone blocks are gone and whether
	 * the zombie was hurt.
	 */
	static void blast(GameTestHelper helper, ServerLevel level, BlockPos at, BiConsumer<Integer, Boolean> then) {
		int before = count(level, at, -2, -1, -2, 2, 3, 2, Blocks.STONE);
		Zombie zombie = EntityType.ZOMBIE.create(level);
		zombie.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5, 0, 0);
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		helper.runAfterDelay(2, () -> {
			Creeper creeper = EntityType.CREEPER.create(level);
			creeper.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			level.explode(creeper, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.MOB);
			helper.runAfterDelay(5, () -> {
				int gone = before - count(level, at, -2, -1, -2, 2, 3, 2, Blocks.STONE);
				boolean hurt = zombie.getHealth() < zombie.getMaxHealth();
				zombie.discard();
				then.accept(gone, hurt);
			});
		});
	}

	/** The goal that makes an enderman pick a block up, with no Hearth Lantern near. Kind (the "everywhere" switch off): it never wants to start. Otherwise: it soon does. */
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
		return wantsBlocks(level, null, tries);
	}

	/** {@link #wantsBlocks(ServerLevel, int)} for an enderman standing at {@code at} (null: wherever it's made). */
	static int wantsBlocks(ServerLevel level, BlockPos at, int tries) {
		try {
			EnderMan enderman = EntityType.ENDERMAN.create(level);
			if (at != null) {
				enderman.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			}
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
			throw new IllegalStateException("couldn't make an enderman's take-block goal", e);
		}
	}

	/**
	 * A 7x7 oak-plank floor with fire on every other plank (16 fires), left 1500 ticks at 200 ticks
	 * a second, with no Hearth Lantern near. Kind (the "everywhere" switch off): no plank burns and
	 * the fires go out, the way fire on stone does. Otherwise: planks burn. The test server starts with doFireTick off, so both batches switch it on.
	 */
	static void fire(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		helper.assertValueEqual(Rules.is(level, Rules.FIRE_BURNS_BLOCKS), !kind, "the fire_burns_blocks rule for this test");
		helper.assertTrue(level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK), "doFireTick should be on for this test (fire must tick)");
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		burningFloor(level, at);
		server.tickRateManager().setTickRate(200.0F);
		helper.runAfterDelay(1500, () -> {
			server.tickRateManager().setTickRate(20.0F);
			int gone = 49 - count(level, at, -3, 0, -3, 3, 0, 3, Blocks.OAK_PLANKS);
			int fires = count(level, at, -3, 1, -3, 3, 1, 3, Blocks.FIRE);
			System.out.println("[cozy_core_gametest] fire, " + (kind ? "kind" : "vanilla") + ": " + gone + " of 49 planks gone, " + fires + " of 16 fires left");
			if (kind) {
				helper.assertValueEqual(gone, 0, "planks burnt away");
				// A fire over planks it can't burn ages past 3 and goes out, like fire on stone; each
				// tick has a 1 in 3 chance of ageing it, so in 1500 ticks a straggler or two is possible.
				helper.assertTrue(fires <= 2, "the fires should still go out (" + fires + " of 16 fires left)");
			} else {
				helper.assertTrue(gone > 0, "with the rule on, fire should burn planks again (" + gone + " of 49 gone)");
			}
			helper.succeed();
		});
	}

	/** The 7x7 plank floor on stone around {@code at}, with fire on every other plank (16 fires). */
	static void burningFloor(ServerLevel level, BlockPos at) {
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
	}

	/**
	 * Runs {@code checks}; if one fails, runs {@code tidy} before passing the failure on. Batches run
	 * one after another in the same part of the world, so a lantern or stand-in player left behind by
	 * a failed check would guard or quiet a later batch's test and make it fail too.
	 */
	static void orTidy(Runnable tidy, Runnable checks) {
		try {
			checks.run();
		} catch (RuntimeException | Error e) {
			tidy.run();
			throw e;
		}
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
		BlockPos spawnPoint = level.getSharedSpawnPos();
		float spawnAngle = level.getSharedSpawnAngle();
		level.setDefaultSpawnPos(room.offset(1000, 0, 0), 0);
		ServerPlayer watcher = helper.makeMockServerPlayerInLevel();
		watcher.teleportTo(level, room.getX() + 0.5, room.getY() + 40, room.getZ() + 0.5, Set.of(), 0, 90);
		// Every lantern this test places goes again at the end: one left standing would guard the other
		// lantern test's room in the same run (and in later runs, which reuse the world).
		Runnable tidy = () -> {
			level.getServer().getPlayerList().remove(watcher);
			level.setDefaultSpawnPos(spawnPoint, spawnAngle);
			for (BlockPos placed : List.of(near, far, above)) {
				level.setBlockAndUpdate(placed, Blocks.AIR.defaultBlockState());
			}
		};
		// Let the light engine catch up with the new roof: the room has to read as dark.
		helper.runAfterDelay(40, () -> orTidy(tidy, () -> {
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
			helper.runAfterDelay(2, () -> orTidy(tidy, () -> {
				Set<BlockPos> known = HearthLanterns.in(level);
				helper.assertTrue(!known.contains(near) && known.contains(far), "breaking and placing lanterns should keep the list right (" + known + ")");
				int outside = spawn(level, room);
				helper.assertTrue(outside > 0, "a lantern 40 blocks away shouldn't reach the room (" + outside + " spawned; " + roomState(level, room) + ")");
				level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(above.above(), Blocks.STONE.defaultBlockState());
				// A hanging lantern straight above the room: height doesn't matter.
				level.setBlockAndUpdate(above, lantern.defaultBlockState().setValue(LanternBlock.HANGING, true));
				helper.runAfterDelay(2, () -> orTidy(tidy, () -> {
					helper.assertValueEqual(spawn(level, room), 0, "monsters spawned under a hanging Hearth Lantern");
					tidy.run();
					helper.succeed();
				}));
			}));
		}));
	}

	/**
	 * A survival player dies with a pickaxe on the hotbar, cobblestone in the main inventory and a
	 * shield in the off-hand, is saved and loaded as if they'd logged out while dead, and respawns.
	 * Kind: the cobblestone drops, the pickaxe doesn't, the kept items are in the saved data, and the
	 * pickaxe and shield are back in their slots after respawning. Vanilla (rule off): the pickaxe
	 * drops like anything else and nothing comes back.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: the only way to get a real ServerPlayer in a test
	static void keepOnDeath(GameTestHelper helper, boolean kind) {
		ServerLevel level = helper.getLevel();
		helper.assertValueEqual(Rules.is(level, Rules.KEEP_HOTBAR_ON_DEATH), kind, "the keep_hotbar_on_death rule for this test");
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
			}
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerPlayer loaded = helper.makeMockServerPlayerInLevel();
		ServerPlayer respawned = helper.makeMockServerPlayerInLevel();
		boolean loadedListed = true;
		try {
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), 0, 0);
			Inventory inventory = player.getInventory();
			inventory.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
			inventory.setItem(20, new ItemStack(Items.COBBLESTONE, 10));
			inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
			player.die(level.damageSources().genericKill());
			helper.assertTrue(dropped(level, at, Items.COBBLESTONE), "the cobblestone should drop where the player fell");
			helper.assertValueEqual(dropped(level, at, Items.DIAMOND_PICKAXE), !kind, "whether the hotbar's pickaxe dropped");
			// Saved and loaded, as when a player logs out while dead and comes back later.
			CompoundTag saved = player.saveWithoutId(new CompoundTag());
			helper.assertValueEqual(saved.contains(KeepOnDeath.SAVE_KEY), kind, "whether the kept items are in the player's saved data");
			// The loaded player takes on the dead one's identity, so it leaves the player list first.
			level.getServer().getPlayerList().remove(loaded);
			loadedListed = false;
			loaded.load(saved);
			respawned.restoreFrom(loaded, false);
			Inventory after = respawned.getInventory();
			helper.assertValueEqual(after.getItem(0).is(Items.DIAMOND_PICKAXE), kind, "whether the pickaxe is back on the hotbar after respawning");
			helper.assertValueEqual(after.getItem(Inventory.SLOT_OFFHAND).is(Items.SHIELD), kind, "whether the off-hand shield is back after respawning");
			helper.assertFalse(after.contains(new ItemStack(Items.COBBLESTONE)), "the rest of the inventory shouldn't come back");
		} finally {
			level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(6)).forEach(ItemEntity::discard);
			level.getServer().getPlayerList().remove(player);
			if (loadedListed) {
				level.getServer().getPlayerList().remove(loaded);
			}
			level.getServer().getPlayerList().remove(respawned);
		}
		helper.succeed();
	}

	/** Whether an item of that kind is lying within 4 blocks of {@code at}. */
	private static boolean dropped(ServerLevel level, BlockPos at, Item item) {
		return !level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4), e -> e.getItem().is(item)).isEmpty();
	}

	/** Asks vanilla's natural spawner for monsters at the room's centre 80 times, then counts (and removes) what appeared. */
	static int spawn(ServerLevel level, BlockPos room) {
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
				+ ", difficulty " + level.getDifficulty() + ", spawn point " + level.getSharedSpawnPos()
				+ ", nearest player " + (player == null ? "none" : Math.sqrt(player.distanceToSqr(Vec3.atCenterOf(at))) + " blocks");
	}

	/** How many blocks of a kind are in the box {@code at + (x1,y1,z1)} to {@code at + (x2,y2,z2)}. */
	static int count(ServerLevel level, BlockPos at, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
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
