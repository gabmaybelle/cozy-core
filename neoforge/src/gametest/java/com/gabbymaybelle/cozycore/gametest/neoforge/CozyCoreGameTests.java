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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Cozy Core's NeoForge game tests: a little mod of its own that only {@code :neoforge:runGameTestServer}
 * loads, handing vanilla's test framework one test per check (the checks are in {@link RuleChecks}).
 * Every rule is tested both ways: in the "kind" environment (a new world's defaults) and in the
 * "vanilla" environment, where the game rules are set back to vanilla's behaviour, so a hook that
 * quietly stops working fails a test instead of passing by doing nothing.
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
		FUNCTIONS.put("explosions_spare_blocks", helper -> RuleChecks.explosions(helper, true));
		FUNCTIONS.put("explosions_break_blocks", helper -> RuleChecks.explosions(helper, false));
		FUNCTIONS.put("endermen_leave_blocks", helper -> RuleChecks.endermen(helper, true));
		FUNCTIONS.put("endermen_take_blocks", helper -> RuleChecks.endermen(helper, false));
		FUNCTIONS.put("fire_spares_blocks", helper -> RuleChecks.fire(helper, true));
		FUNCTIONS.put("fire_burns_blocks", helper -> RuleChecks.fire(helper, false));
		FUNCTIONS.put("lantern_keeps_the_night_quiet", helper -> RuleChecks.lanterns(helper, true));
		FUNCTIONS.put("lantern_off_at_radius_zero", helper -> RuleChecks.lanterns(helper, false));
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
		// Kind: nothing changed, so these tests also prove a new world starts with the kind settings.
		Holder<TestEnvironmentDefinition<?>> kind = event.registerEnvironment(id("kind"), new TestEnvironmentDefinition.AllOf());
		// Vanilla: every rule switched to vanilla's behaviour for the length of the test.
		GameRuleMap vanillaRules = GameRuleMap.of();
		vanillaRules.set(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS, true);
		vanillaRules.set(Rules.ENDERMEN_TAKE_BLOCKS, true);
		vanillaRules.set(Rules.FIRE_BURNS_BLOCKS, true);
		vanillaRules.set(Rules.HEARTH_LANTERN_RADIUS, 0);
		Holder<TestEnvironmentDefinition<?>> vanilla = event.registerEnvironment(id("vanilla"), new TestEnvironmentDefinition.SetGameRules(vanillaRules));

		// The templates (tools/generate-test-structures.js) are only sizes; each test builds its own scene.
		test(event, "defaults", kind, EMPTY, 20);
		test(event, "recipes", kind, EMPTY, 20);
		test(event, "explosions_spare_blocks", kind, id("explosions"), 100);
		test(event, "explosions_break_blocks", vanilla, id("explosions"), 100);
		test(event, "endermen_leave_blocks", kind, EMPTY, 20);
		test(event, "endermen_take_blocks", vanilla, EMPTY, 20);
		test(event, "fire_spares_blocks", kind, id("fire"), 2000);
		test(event, "fire_burns_blocks", vanilla, id("fire"), 2000);
		test(event, "lantern_keeps_the_night_quiet", kind, id("lanterns"), 400);
		test(event, "lantern_off_at_radius_zero", vanilla, id("lanterns"), 400);
	}

	private static void test(RegisterGameTestsEvent event, String name, Holder<TestEnvironmentDefinition<?>> environment, Identifier structure, int maxTicks) {
		ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id(name));
		event.registerTest(id(name), new FunctionGameTestInstance(function, new TestData<>(environment, structure, maxTicks, 0, true)));
	}
}
