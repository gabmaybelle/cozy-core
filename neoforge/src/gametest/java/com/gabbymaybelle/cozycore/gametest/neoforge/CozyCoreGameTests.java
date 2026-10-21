package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.Rules;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gamerules.GameRuleMap;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Cozy Core's NeoForge game tests: a little mod of its own that only {@code :neoforge:runGameTestServer}
 * loads, handing vanilla's test framework one test per check (the checks are in {@link RuleChecks}
 * and, for home, {@link HomeChecks}). Every rule is tested both ways, so a hook that quietly stops
 * working fails a test instead of passing by doing nothing. The environments:
 * <ul>
 * <li>"kind": a new world's defaults (home is safe, the wild is vanilla);</li>
 * <li>"everywhere": the three world-wide switches off, as worlds made before 0.5.0 have them;</li>
 * <li>"vanilla": every rule at vanilla's behaviour (hearths off, radius 0);</li>
 * <li>one "home_..." environment per test that places Hearth Lanterns, with the kind rules (and
 * "hearths_off" with hearths switched off). Vanilla runs each environment as its own batch, one
 * after another, so a lantern can never guard another test's blocks.</li>
 * </ul>
 */
@Mod(CozyCoreGameTests.MOD_ID)
public final class CozyCoreGameTests {
	static final String MOD_ID = "cozy_core_gametest";
	/** Vanilla's 1x1x1 template, for tests that build nothing. */
	private static final Identifier EMPTY = Identifier.withDefaultNamespace("empty");

	/** The test functions by name; the test instance of the same name runs each. */
	private static final Map<String, Consumer<GameTestHelper>> FUNCTIONS = new LinkedHashMap<>();

	static {
		FUNCTIONS.put("defaults", RuleChecks::defaults);
		FUNCTIONS.put("recipes", RuleChecks::recipes);
		FUNCTIONS.put("advancements", RuleChecks::advancements);
		FUNCTIONS.put("explosions_spare_blocks_everywhere", helper -> RuleChecks.explosions(helper, true));
		FUNCTIONS.put("explosions_break_blocks_in_the_wild", helper -> RuleChecks.explosions(helper, false));
		FUNCTIONS.put("endermen_leave_blocks_everywhere", helper -> RuleChecks.endermen(helper, true));
		FUNCTIONS.put("endermen_take_blocks_in_the_wild", helper -> RuleChecks.endermen(helper, false));
		FUNCTIONS.put("fire_spares_blocks_everywhere", helper -> RuleChecks.fire(helper, true));
		FUNCTIONS.put("fire_burns_blocks_in_the_wild", helper -> RuleChecks.fire(helper, false));
		FUNCTIONS.put("explosions_spare_home", helper -> HomeChecks.explosions(helper, true));
		FUNCTIONS.put("explosions_break_with_hearths_off", helper -> HomeChecks.explosions(helper, false));
		FUNCTIONS.put("explosions_stop_at_the_edge", HomeChecks::explosionEdge);
		FUNCTIONS.put("fire_spares_home", helper -> HomeChecks.fire(helper, false));
		FUNCTIONS.put("fire_stops_at_the_edge", helper -> HomeChecks.fire(helper, true));
		FUNCTIONS.put("endermen_leave_home", HomeChecks::endermen);
		FUNCTIONS.put("lantern_styles", HomeChecks::styles);
		FUNCTIONS.put("phantoms_leave_home_alone", HomeChecks::phantoms);
		FUNCTIONS.put("items_last_at_home", ComfortsChecks::items);
		FUNCTIONS.put("animals_safe_at_home", ComfortsChecks::animals);
		FUNCTIONS.put("lightning_spares_home", ComfortsChecks::lightning);
		FUNCTIONS.put("no_falls_at_home", ComfortsChecks::falls);
		FUNCTIONS.put("glass_comes_back_at_home", ComfortsChecks::glass);
		FUNCTIONS.put("lantern_keeps_the_night_quiet", helper -> RuleChecks.lanterns(helper, true));
		FUNCTIONS.put("lantern_off_at_radius_zero", helper -> RuleChecks.lanterns(helper, false));
		FUNCTIONS.put("hotbar_kept_on_death", helper -> RuleChecks.keepOnDeath(helper, true));
		FUNCTIONS.put("hotbar_dropped_on_death", helper -> RuleChecks.keepOnDeath(helper, false));
		FUNCTIONS.put("reach_is_64", LifeChecks::reach);
		FUNCTIONS.put("candles_light_at_dusk", LifeChecks::lights);
		FUNCTIONS.put("garden_grows_at_home", LifeChecks::garden);
		FUNCTIONS.put("pets_settle_by_the_hearth", LifeChecks::pets);
	}

	public CozyCoreGameTests(IEventBus modBus) {
		modBus.addListener(RegisterEvent.class, CozyCoreGameTests::registerFunctions);
		modBus.addListener(RegisterGameTestsEvent.class, CozyCoreGameTests::registerTests);
	}

	static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Test functions live in a built-in registry, so they go in with the mod's other registrations. */
	private static void registerFunctions(RegisterEvent event) {
		if (event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
			FUNCTIONS.forEach((name, function) -> event.register(Registries.TEST_FUNCTION, id(name), () -> function));
		}
	}

	/** Test instances and their environments are registered as the test world's data loads. */
	private static void registerTests(RegisterGameTestsEvent event) {
		// Kind: none of Cozy Core's rules changed, so these tests also prove a new world starts with the kind settings.
		Holder<TestEnvironmentDefinition<?>> kind = environment(event, "kind", rules -> {
		});
		// Everywhere: the world-wide switches off, so blocks are spared with no lantern near.
		Holder<TestEnvironmentDefinition<?>> everywhere = environment(event, "everywhere", rules -> {
			rules.set(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS, false);
			rules.set(Rules.ENDERMEN_TAKE_BLOCKS, false);
			rules.set(Rules.FIRE_BURNS_BLOCKS, false);
		});
		// Vanilla: every rule switched to vanilla's behaviour for the length of the test.
		Holder<TestEnvironmentDefinition<?>> vanilla = environment(event, "vanilla", rules -> {
			rules.set(Rules.HEARTHS_PROTECT_BUILDS, false);
			rules.set(Rules.HEARTH_LANTERN_RADIUS, 0);
			rules.set(Rules.KEEP_HOTBAR_ON_DEATH, false);
		});
		Holder<TestEnvironmentDefinition<?>> hearthsOff = environment(event, "hearths_off", rules -> rules.set(Rules.HEARTHS_PROTECT_BUILDS, false));

		// The templates (tools/generate-test-structures.js) are only sizes; each test builds its own scene.
		test(event, "defaults", kind, EMPTY, 20);
		test(event, "recipes", kind, EMPTY, 20);
		test(event, "advancements", kind, EMPTY, 20);
		test(event, "explosions_spare_blocks_everywhere", everywhere, id("explosions"), 100);
		test(event, "explosions_break_blocks_in_the_wild", kind, id("explosions"), 100);
		test(event, "endermen_leave_blocks_everywhere", everywhere, EMPTY, 20);
		test(event, "endermen_take_blocks_in_the_wild", kind, EMPTY, 20);
		test(event, "fire_spares_blocks_everywhere", everywhere, id("fire"), 2000);
		test(event, "fire_burns_blocks_in_the_wild", kind, id("fire"), 2000);
		test(event, "explosions_spare_home", home(event, "home_explosions"), id("explosions"), 100);
		test(event, "explosions_break_with_hearths_off", hearthsOff, id("explosions"), 100);
		test(event, "explosions_stop_at_the_edge", home(event, "home_explosion_edge"), id("explosions"), 100);
		test(event, "fire_spares_home", home(event, "home_fire"), id("fire"), 2000);
		test(event, "fire_stops_at_the_edge", home(event, "home_fire_edge"), id("fire"), 2000);
		test(event, "endermen_leave_home", home(event, "home_endermen"), EMPTY, 20);
		test(event, "lantern_styles", home(event, "home_styles"), EMPTY, 20);
		test(event, "phantoms_leave_home_alone", home(event, "home_phantoms"), EMPTY, 100);
		// 0.6.0's home comforts (ComfortsChecks), each alone in its batch too.
		test(event, "items_last_at_home", home(event, "home_items"), id("lanterns"), 100);
		test(event, "animals_safe_at_home", home(event, "home_animals"), EMPTY, 100);
		test(event, "lightning_spares_home", home(event, "home_lightning"), id("lanterns"), 20);
		test(event, "no_falls_at_home", home(event, "home_falls"), EMPTY, 20);
		test(event, "glass_comes_back_at_home", home(event, "home_glass"), EMPTY, 20);
		test(event, "lantern_keeps_the_night_quiet", home(event, "home_spawning"), id("lanterns"), 400);
		test(event, "lantern_off_at_radius_zero", vanilla, id("lanterns"), 400);
		test(event, "hotbar_kept_on_death", kind, id("explosions"), 100);
		test(event, "hotbar_dropped_on_death", vanilla, id("explosions"), 100);
		// 0.7.0, home comes alive (LifeChecks): the reach in a new world, then each new thing alone in its batch.
		test(event, "reach_is_64", environment(event, "reach", rules -> {
		}), EMPTY, 20);
		test(event, "candles_light_at_dusk", home(event, "home_lights"), EMPTY, 20);
		test(event, "garden_grows_at_home", home(event, "home_garden"), EMPTY, 20);
		test(event, "pets_settle_by_the_hearth", home(event, "home_pets"), id("lanterns"), 400);
	}

	/**
	 * A "home_..." environment: the kind rules, with the reach at 32 blocks. These checks were laid
	 * out for the 32-block reach the lantern had until 0.7.0 (a spot 45 or 60 blocks out stands in the
	 * wild, which a 64-block reach would take in), so they keep it; "defaults" checks the new 64.
	 */
	private static Holder<TestEnvironmentDefinition<?>> home(RegisterGameTestsEvent event, String name) {
		return environment(event, name, rules -> rules.set(Rules.HEARTH_LANTERN_RADIUS, 32));
	}

	/**
	 * A test environment that sets these rules for its tests. Every one lets fire tick everywhere:
	 * since 26.3 vanilla's fire only ticks (ages, burns, spreads) within
	 * fire_spread_radius_around_player blocks of a player, 128 by default, and the test server has
	 * no players. Before this the fire checks only saw fire tick while the lantern test's mock player
	 * existed, about 40 ticks, so the vanilla-side check failed at random and the kind-side check
	 * proved nothing.
	 */
	private static Holder<TestEnvironmentDefinition<?>> environment(RegisterGameTestsEvent event, String name, Consumer<GameRuleMap> rules) {
		GameRuleMap map = GameRuleMap.of();
		map.set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1);
		rules.accept(map);
		return event.registerEnvironment(id(name), new TestEnvironmentDefinition.SetGameRules(map));
	}

	private static void test(RegisterGameTestsEvent event, String name, Holder<TestEnvironmentDefinition<?>> environment, Identifier structure, int maxTicks) {
		ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(name));
		event.registerTest(id(name), new FunctionGameTestInstance(function, new TestData<>(environment, structure, maxTicks, 0, true)));
	}
}
