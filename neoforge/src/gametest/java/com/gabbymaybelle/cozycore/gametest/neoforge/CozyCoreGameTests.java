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
 * loads, handing vanilla's test framework one test per check (the checks are in {@link RuleChecks}).
 * Every rule is tested both ways: in the "kind" batch (a new world's defaults) and in the "vanilla"
 * batch, where the game rules are set back to vanilla's behaviour first, so a hook that quietly
 * stops working fails a test instead of passing by doing nothing. Batches run one after another.
 */
@Mod(CozyCoreGameTests.MOD_ID)
@GameTestHolder(CozyCoreGameTests.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CozyCoreGameTests {
	static final String MOD_ID = "cozy_core_gametest";
	private static final String KIND = "kind", VANILLA = "vanilla";
	/** The rules the vanilla batch switches; the kind batch leaves them as a new world has them. */
	private static final List<GameRules.Key<?>> RULES = List.of(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS, Rules.ENDERMEN_TAKE_BLOCKS,
			Rules.FIRE_BURNS_BLOCKS, Rules.HEARTH_LANTERN_RADIUS, Rules.KEEP_HOTBAR_ON_DEATH);

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

	@GameTest(template = "explosions", batch = KIND)
	public static void explosions_spare_blocks(GameTestHelper helper) {
		RuleChecks.explosions(helper, true);
	}

	@GameTest(template = "explosions", batch = VANILLA)
	public static void explosions_break_blocks(GameTestHelper helper) {
		RuleChecks.explosions(helper, false);
	}

	@GameTest(template = "empty", batch = KIND)
	public static void endermen_leave_blocks(GameTestHelper helper) {
		RuleChecks.endermen(helper, true);
	}

	@GameTest(template = "empty", batch = VANILLA)
	public static void endermen_take_blocks(GameTestHelper helper) {
		RuleChecks.endermen(helper, false);
	}

	@GameTest(template = "fire", batch = KIND, timeoutTicks = 2000)
	public static void fire_spares_blocks(GameTestHelper helper) {
		RuleChecks.fire(helper, true);
	}

	@GameTest(template = "fire", batch = VANILLA, timeoutTicks = 2000)
	public static void fire_burns_blocks(GameTestHelper helper) {
		RuleChecks.fire(helper, false);
	}

	@GameTest(template = "lanterns", batch = KIND, timeoutTicks = 400)
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
	 * Both batches let fire tick: vanilla's test server switches doFireTick off, so without this the
	 * fire checks would pass on both sides by burning nothing. Cozy Core's rules are put back to a
	 * new world's values, in case the vanilla batch ran first.
	 */
	@BeforeBatch(batch = KIND)
	public static void kind(ServerLevel level) {
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		GameRules fresh = new GameRules();
		for (GameRules.Key<?> rule : RULES) {
			reset(level, fresh, rule);
		}
	}

	/** Every rule switched to vanilla's behaviour for the length of the batch. */
	@BeforeBatch(batch = VANILLA)
	public static void vanilla(ServerLevel level) {
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		rules.getRule(Rules.MOB_EXPLOSIONS_BREAK_BLOCKS).set(true, level.getServer());
		rules.getRule(Rules.ENDERMEN_TAKE_BLOCKS).set(true, level.getServer());
		rules.getRule(Rules.FIRE_BURNS_BLOCKS).set(true, level.getServer());
		rules.getRule(Rules.HEARTH_LANTERN_RADIUS).set(0, level.getServer());
		rules.getRule(Rules.KEEP_HOTBAR_ON_DEATH).set(false, level.getServer());
	}

	@AfterBatch(batch = VANILLA)
	public static void afterVanilla(ServerLevel level) {
		kind(level);
	}

	private static <T extends GameRules.Value<T>> void reset(ServerLevel level, GameRules fresh, GameRules.Key<T> rule) {
		level.getGameRules().getRule(rule).setFrom(fresh.getRule(rule), level.getServer());
	}
}
