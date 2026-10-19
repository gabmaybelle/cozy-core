package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.Rules;
import java.util.List;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Cozy Core's NeoForge game tests: a little mod of its own that only {@code :neoforge:runGameTestServer}
 * loads, handing vanilla's test framework one test per check (the checks are in {@link RuleChecks}
 * and, for home, {@link HomeChecks}). Every rule is tested both ways, so a hook that quietly stops
 * working fails a test instead of passing by doing nothing. The batches:
 * <ul>
 * <li>"kind": a new world's defaults (home is safe, the wild is vanilla);</li>
 * <li>"everywhere": the three world-wide switches off, as worlds made before 0.5.0 have them;</li>
 * <li>"vanilla": every rule at vanilla's behaviour (hearths off, radius 0);</li>
 * <li>one "home_..." batch per test that places Hearth Lanterns, with the kind rules (and
 * "hearths_off" with hearths switched off). Batches run one after another, so a lantern can never
 * guard another test's blocks.</li>
 * </ul>
 */
@Mod(CozyCoreGameTests.MOD_ID)
@GameTestHolder(CozyCoreGameTests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CozyCoreGameTests {
	static final String MOD_ID = "cozy_core_gametest";
	private static final String KIND = "kind", EVERYWHERE = "everywhere", VANILLA = "vanilla", HEARTHS_OFF = "hearths_off";
	private static final String HOME_EXPLOSIONS = "home_explosions", HOME_EDGE = "home_explosion_edge", HOME_FIRE = "home_fire",
			HOME_FIRE_EDGE = "home_fire_edge", HOME_ENDERMEN = "home_endermen", HOME_STYLES = "home_styles",
			HOME_PHANTOMS = "home_phantoms", HOME_SPAWNING = "home_spawning";
	/** The rules a batch may change; each batch starts from a new world's values of them. */
	private static final List<GameRules.Key<?>> RULES = List.of(Rules.HEARTHS_PROTECT_BUILDS, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS,
			Rules.ENDERMEN_TAKE_BLOCKS, Rules.FIRE_BURNS_BLOCKS, Rules.HEARTH_LANTERN_RADIUS, Rules.KEEP_HOTBAR_ON_DEATH, GameRules.RULE_DOINSOMNIA);

	public CozyCoreGameTests(IEventBus modBus) {
		modBus.addListener(RegisterGameTestsEvent.class, event -> event.register(CozyCoreGameTests.class));
	}

	// The templates (tools/generate-test-structures.js) are only sizes; each test builds its own scene.

	@GameTest(template = "empty", batch = KIND)
	public static void defaults(GameTestHelper helper) {
		RuleChecks.defaults(helper);
	}

	@GameTest(template = "empty", batch = KIND)
	public static void recipes(GameTestHelper helper) {
		RuleChecks.recipes(helper);
	}

	@GameTest(template = "empty", batch = KIND)
	public static void advancements(GameTestHelper helper) {
		RuleChecks.advancements(helper);
	}

	@GameTest(template = "explosions", batch = EVERYWHERE)
	public static void explosions_spare_blocks_everywhere(GameTestHelper helper) {
		RuleChecks.explosions(helper, true);
	}

	@GameTest(template = "explosions", batch = KIND)
	public static void explosions_break_blocks_in_the_wild(GameTestHelper helper) {
		RuleChecks.explosions(helper, false);
	}

	@GameTest(template = "empty", batch = EVERYWHERE)
	public static void endermen_leave_blocks_everywhere(GameTestHelper helper) {
		RuleChecks.endermen(helper, true);
	}

	@GameTest(template = "empty", batch = KIND)
	public static void endermen_take_blocks_in_the_wild(GameTestHelper helper) {
		RuleChecks.endermen(helper, false);
	}

	@GameTest(template = "fire", batch = EVERYWHERE, timeoutTicks = 2000)
	public static void fire_spares_blocks_everywhere(GameTestHelper helper) {
		RuleChecks.fire(helper, true);
	}

	@GameTest(template = "fire", batch = KIND, timeoutTicks = 2000)
	public static void fire_burns_blocks_in_the_wild(GameTestHelper helper) {
		RuleChecks.fire(helper, false);
	}

	@GameTest(template = "explosions", batch = HOME_EXPLOSIONS)
	public static void explosions_spare_home(GameTestHelper helper) {
		HomeChecks.explosions(helper, true);
	}

	@GameTest(template = "explosions", batch = HEARTHS_OFF)
	public static void explosions_break_with_hearths_off(GameTestHelper helper) {
		HomeChecks.explosions(helper, false);
	}

	@GameTest(template = "explosions", batch = HOME_EDGE)
	public static void explosions_stop_at_the_edge(GameTestHelper helper) {
		HomeChecks.explosionEdge(helper);
	}

	@GameTest(template = "fire", batch = HOME_FIRE, timeoutTicks = 2000)
	public static void fire_spares_home(GameTestHelper helper) {
		HomeChecks.fire(helper, false);
	}

	@GameTest(template = "fire", batch = HOME_FIRE_EDGE, timeoutTicks = 2000)
	public static void fire_stops_at_the_edge(GameTestHelper helper) {
		HomeChecks.fire(helper, true);
	}

	@GameTest(template = "empty", batch = HOME_ENDERMEN)
	public static void endermen_leave_home(GameTestHelper helper) {
		HomeChecks.endermen(helper);
	}

	@GameTest(template = "empty", batch = HOME_STYLES)
	public static void lantern_styles(GameTestHelper helper) {
		HomeChecks.styles(helper);
	}

	@GameTest(template = "empty", batch = HOME_PHANTOMS, timeoutTicks = 100)
	public static void phantoms_leave_home_alone(GameTestHelper helper) {
		HomeChecks.phantoms(helper);
	}

	@GameTest(template = "lanterns", batch = HOME_SPAWNING, timeoutTicks = 400)
	public static void lantern_keeps_the_night_quiet(GameTestHelper helper) {
		RuleChecks.lanterns(helper, true);
	}

	@GameTest(template = "lanterns", batch = VANILLA, timeoutTicks = 400)
	public static void lantern_off_at_radius_zero(GameTestHelper helper) {
		RuleChecks.lanterns(helper, false);
	}

	@GameTest(template = "explosions", batch = KIND)
	public static void hotbar_kept_on_death(GameTestHelper helper) {
		RuleChecks.keepOnDeath(helper, true);
	}

	@GameTest(template = "explosions", batch = VANILLA)
	public static void hotbar_dropped_on_death(GameTestHelper helper) {
		RuleChecks.keepOnDeath(helper, false);
	}

	@GameTest(template = "empty", batch = KIND)
	public static void builder_trades(GameTestHelper helper) {
		RuleChecks.trades(helper);
	}

	/**
	 * Every batch lets fire tick: vanilla's test server switches doFireTick off, so without this the
	 * fire checks would pass on both sides by burning nothing. Cozy Core's rules are put back to a
	 * new world's values first, whichever batch ran before.
	 */
	@BeforeBatch(batch = KIND)
	public static void kind(ServerLevel level) {
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		GameRules fresh = new GameRules();
		for (GameRules.Key<?> rule : RULES) {
			reset(level, fresh, rule);
		}
	}

	/** The world-wide switches off, as worlds made before 0.5.0 have them. */
	@BeforeBatch(batch = EVERYWHERE)
	public static void everywhere(ServerLevel level) {
		kind(level);
		GameRules rules = level.getGameRules();
		rules.getRule(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS).set(false, level.getServer());
		rules.getRule(Rules.ENDERMEN_TAKE_BLOCKS).set(false, level.getServer());
		rules.getRule(Rules.FIRE_BURNS_BLOCKS).set(false, level.getServer());
	}

	/** Every rule at vanilla's behaviour for the length of the batch. */
	@BeforeBatch(batch = VANILLA)
	public static void vanilla(ServerLevel level) {
		kind(level);
		GameRules rules = level.getGameRules();
		rules.getRule(Rules.HEARTHS_PROTECT_BUILDS).set(false, level.getServer());
		rules.getRule(Rules.HEARTH_LANTERN_RADIUS).set(0, level.getServer());
		rules.getRule(Rules.KEEP_HOTBAR_ON_DEATH).set(false, level.getServer());
	}

	@BeforeBatch(batch = HEARTHS_OFF)
	public static void hearthsOff(ServerLevel level) {
		kind(level);
		level.getGameRules().getRule(Rules.HEARTHS_PROTECT_BUILDS).set(false, level.getServer());
	}

	// Each home batch has a new world's rules; it's a batch of its own only so it runs alone.
	@BeforeBatch(batch = HOME_EXPLOSIONS)
	public static void homeExplosions(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_EDGE)
	public static void homeEdge(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_FIRE)
	public static void homeFire(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_FIRE_EDGE)
	public static void homeFireEdge(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_ENDERMEN)
	public static void homeEndermen(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_STYLES)
	public static void homeStyles(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_PHANTOMS)
	public static void homePhantoms(ServerLevel level) {
		kind(level);
	}

	@BeforeBatch(batch = HOME_SPAWNING)
	public static void homeSpawning(ServerLevel level) {
		kind(level);
	}

	@AfterBatch(batch = VANILLA)
	public static void afterVanilla(ServerLevel level) {
		kind(level);
	}

	private static <T extends GameRules.Value<T>> void reset(ServerLevel level, GameRules fresh, GameRules.Key<T> rule) {
		level.getGameRules().getRule(rule).setFrom(fresh.getRule(rule), level.getServer());
	}
}
