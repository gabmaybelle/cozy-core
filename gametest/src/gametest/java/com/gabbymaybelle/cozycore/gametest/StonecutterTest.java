package com.gabbymaybelle.cozycore.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cozy Core's stonecutter recipes through the real stonecutter screen. The expected recipes are
 * read from the Cozy Core jar under test (only the wood-mod ones whose mods are loaded), so the
 * test and the pack can't drift apart. For each input it puts a stack in the stonecutter, checks
 * every expected output is on offer exactly once, clicks each one (with the real mouse for the
 * first page of buttons) and checks the result and its count, then takes the first result out.
 */
public class StonecutterTest implements FabricClientGameTest {
	/** The stonecutter screen's recipe buttons: 4 columns, 3 rows, 16 x 18 pixels, from (52, 14). */
	private static final int COLUMNS = 4, ROWS = 3, BUTTON_W = 16, BUTTON_H = 18, RECIPES_X = 52, RECIPES_Y = 14;

	/** One expected recipe: its id, the input as written ("item" or "#tag"), and the result. */
	private record Expected(String id, String input, Identifier result, int count) {
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		List<Expected> expected = readExpected();
		check(!expected.isEmpty(), "the Cozy Core jar has stonecutter recipes");
		List<String> failures = new ArrayList<>();
		int inputs = 0, recipes = 0;
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("gamemode survival @a");
			BlockPos cutter = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition().offset(2, 0, 0));
			server.runCommand("setblock " + cutter.getX() + " " + cutter.getY() + " " + cutter.getZ() + " minecraft:stonecutter");
			ctx.waitTicks(2);
			ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(cutter), Direction.UP, cutter, false)));
			ctx.waitForScreen(StonecutterScreen.class);

			// Group the recipes by the item that tests them: the input item itself, or the first item of an input tag.
			Map<Item, List<Expected>> byItem = new LinkedHashMap<>();
			for (Expected e : expected) {
				Item item = ctx.computeOnClient(mc -> firstItem(mc, e.input()));
				if (item == null) {
					failures.add(e.id() + ": its input " + e.input() + " has no items");
					continue;
				}
				byItem.computeIfAbsent(item, k -> new ArrayList<>());
			}
			for (Map.Entry<Item, List<Expected>> group : byItem.entrySet()) {
				Item item = group.getKey();
				for (Expected e : expected) {
					if (ctx.computeOnClient(mc -> accepts(mc, e.input(), item))) {
						group.getValue().add(e);
					}
				}
			}

			boolean screenshot = true;
			for (Map.Entry<Item, List<Expected>> group : byItem.entrySet()) {
				Item item = group.getKey();
				List<Expected> wanted = group.getValue();
				inputs++;
				recipes += wanted.size();
				putInInput(ctx, server, item);
				List<Identifier> offered = ctx.computeOnClient(StonecutterTest::offered);
				String name = BuiltInRegistries.ITEM.getKey(item).toString();
				for (Expected e : wanted) {
					long times = offered.stream().filter(e.result()::equals).count();
					if (times == 0) {
						failures.add(name + ": " + e.result() + " isn't offered (" + e.id() + ")");
					} else if (times > 1) {
						failures.add(name + ": " + e.result() + " is offered " + times + " times");
					}
				}
				if (screenshot) {
					ctx.takeScreenshot("stonecutter_" + BuiltInRegistries.ITEM.getKey(item).getPath());
					screenshot = false;
				}
				// Pick each expected output, the way a player would, and check what the result slot holds.
				for (Expected e : wanted) {
					int index = offered.indexOf(e.result());
					if (index < 0) {
						continue;
					}
					select(ctx, index);
					ItemStack result = ctx.computeOnClient(mc -> ((StonecutterMenu) mc.player.containerMenu).getSlot(StonecutterMenu.RESULT_SLOT).getItem().copy());
					Identifier got = BuiltInRegistries.ITEM.getKey(result.getItem());
					if (!got.equals(e.result()) || result.getCount() != e.count()) {
						failures.add(name + ": picking " + e.result() + " gave " + result.getCount() + " " + got + " (wanted " + e.count() + ")");
					}
				}
				// Take the first result out: it lands in the inventory and uses up input.
				Expected first = wanted.stream().filter(e -> offered.contains(e.result())).findFirst().orElse(null);
				if (first != null) {
					select(ctx, offered.indexOf(first.result()));
					Item resultItem = BuiltInRegistries.ITEM.getValue(first.result());
					ctx.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,
							StonecutterMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player));
					ctx.waitTicks(3);
					int taken = server.computeOnServer(s -> player(s).getInventory().countItem(resultItem));
					int left = server.computeOnServer(s -> player(s).containerMenu.getSlot(StonecutterMenu.INPUT_SLOT).getItem().getCount());
					if (taken < first.count()) {
						failures.add(name + ": taking " + first.result() + " put " + taken + " in the inventory");
					} else if (left >= 64) {
						failures.add(name + ": taking " + first.result() + " didn't use up any input");
					}
				}
			}
		}
		System.out.println("[cozy_core_gametest] stonecutter: " + inputs + " inputs, " + recipes + " recipes checked, " + failures.size() + " problem(s)");
		check(failures.isEmpty(), "stonecutter:\n  " + String.join("\n  ", failures));
	}

	/** Puts 64 of the item in the stonecutter's input (clearing the inventory first) and waits for the screen to update. */
	private static void putInInput(ClientGameTestContext ctx, TestServerContext server, Item item) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.getInventory().clearContent();
			StonecutterMenu menu = (StonecutterMenu) player.containerMenu;
			menu.getSlot(StonecutterMenu.RESULT_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(StonecutterMenu.INPUT_SLOT).set(new ItemStack(item, 64));
			menu.broadcastChanges();
		});
		ctx.waitFor(mc -> mc.player.containerMenu instanceof StonecutterMenu menu
				&& menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem().is(item) && menu.getNumberOfVisibleRecipes() > 0, 40);
		ctx.waitTicks(2);
	}

	/** Picks the recipe button at this index: with the real mouse when it's on the first page, else as the screen would. */
	private static void select(ClientGameTestContext ctx, int index) {
		if (index < COLUMNS * ROWS) {
			int[] at = ctx.computeOnClient(mc -> {
				AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
				int left = field(screen, "leftPos"), top = field(screen, "topPos");
				return new int[] { left + RECIPES_X + (index % COLUMNS) * BUTTON_W + BUTTON_W / 2, top + RECIPES_Y + (index / COLUMNS) * BUTTON_H + BUTTON_H / 2 };
			});
			double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
			ctx.getInput().setCursorPos(at[0] * scale, at[1] * scale);
			ctx.waitTick();
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		} else {
			ctx.runOnClient(mc -> mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, index));
		}
		try {
			ctx.waitFor(mc -> ((StonecutterMenu) mc.player.containerMenu).getSelectedRecipeIndex() == index, 40);
		} catch (AssertionError e) {
			throw new AssertionError("Failed: " + (index < COLUMNS * ROWS ? "clicking" : "picking") + " recipe button " + index
					+ " didn't select it (the screen's buttons aren't where the test expects, or the click was ignored)", e);
		}
		ctx.waitTicks(2);
	}

	/** The outputs the stonecutter screen is offering, in button order. */
	private static List<Identifier> offered(Minecraft mc) {
		StonecutterMenu menu = (StonecutterMenu) mc.player.containerMenu;
		List<Identifier> out = new ArrayList<>();
		for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : menu.getVisibleRecipes().entries()) {
			ItemStack stack = entry.recipe().optionDisplay().resolveForFirstStack(SlotDisplayContext.fromLevel(mc.level));
			out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
		}
		return out;
	}

	private static Item firstItem(Minecraft mc, String input) {
		if (!input.startsWith("#")) {
			Identifier id = Identifier.parse(input);
			return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
		}
		TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(input.substring(1)));
		return mc.level.registryAccess().lookupOrThrow(Registries.ITEM).get(tag)
				.flatMap(set -> set.stream().findFirst()).map(Holder::value).orElse(null);
	}

	private static boolean accepts(Minecraft mc, String input, Item item) {
		if (!input.startsWith("#")) {
			return BuiltInRegistries.ITEM.getKey(item).equals(Identifier.parse(input));
		}
		TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(input.substring(1)));
		return item.builtInRegistryHolder().is(tag);
	}

	/** Every stonecutting recipe in the Cozy Core jar (or the -Pexpect pack folder), skipping wood-mod ones whose mods aren't loaded. */
	private static List<Expected> readExpected() {
		List<Expected> out = new ArrayList<>();
		// -Pexpect=<pack dir> reads them from a pack folder instead, so a jar missing recipes fails.
		String expect = System.getProperty("cozy.expect");
		List<Path> roots = expect != null ? List.of(Path.of(expect))
				: FabricLoader.getInstance().getModContainer("cozy_core").orElseThrow().getRootPaths();
		System.out.println("[cozy_core_gametest] expected recipes from " + (expect != null ? expect : "the Cozy Core jar"));
		for (Path root : roots) {
			Path recipes = root.resolve("data/cozy_core/recipe");
			if (!Files.isDirectory(recipes)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(recipes)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
					JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
					if (!"minecraft:stonecutting".equals(json.get("type").getAsString()) || !conditionsMet(json)) {
						continue;
					}
					JsonElement ingredient = json.get("ingredient");
					String input = ingredient.isJsonArray() ? ingredient.getAsJsonArray().get(0).getAsString() : ingredient.getAsString();
					JsonObject result = json.getAsJsonObject("result");
					int count = result.has("count") ? result.get("count").getAsInt() : 1;
					String name = recipes.relativize(file).toString().replace('\\', '/').replace(".json", "");
					out.add(new Expected("cozy_core:" + name, input, Identifier.parse(result.get("id").getAsString()), count));
				}
			} catch (IOException e) {
				throw new AssertionError("couldn't read Cozy Core's recipes", e);
			}
		}
		return out;
	}

	/** Fabric load conditions: every listed mod (all_mods_loaded) or any of them (any_mods_loaded). */
	private static boolean conditionsMet(JsonObject json) {
		if (!json.has("fabric:load_conditions")) {
			return true;
		}
		for (JsonElement element : json.getAsJsonArray("fabric:load_conditions")) {
			JsonObject condition = element.getAsJsonObject();
			String type = condition.get("condition").getAsString();
			JsonArray values = condition.getAsJsonArray("values");
			List<String> mods = new ArrayList<>();
			values.forEach(v -> mods.add(v.getAsString()));
			boolean met = switch (type) {
				case "fabric:all_mods_loaded" -> mods.stream().allMatch(FabricLoader.getInstance()::isModLoaded);
				case "fabric:any_mods_loaded" -> mods.stream().anyMatch(FabricLoader.getInstance()::isModLoaded);
				default -> throw new AssertionError("the test doesn't know the load condition " + type);
			};
			if (!met) {
				return false;
			}
		}
		return true;
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static int field(Object screen, String name) {
		try {
			Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.getInt(screen);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Failed: " + what);
		}
	}
}
