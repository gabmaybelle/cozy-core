package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The builder-kind rules, as ordinary game rules under a "Cozy Core" heading: they show in the
 * Game Rules screen when a world is made, are set with {@code /gamerule cozy_core:...}, and are
 * saved with the world, so there is no settings file and nothing to sync. The code that applies
 * each one is in {@code mixin/}.
 *
 * <p>Since 0.5.0 builds are kept safe at home: inside a Hearth Lantern's reach while
 * {@link #HEARTHS_PROTECT_BUILDS} is on. The three "everywhere" switches start at vanilla's
 * behaviour in new worlds, and turning one off spares blocks in the whole world as before.
 * Worlds made with 0.4.0 or earlier saved every rule when they were made, so they keep sparing
 * blocks everywhere.
 */
public final class Rules {
	public static final GameRuleCategory CATEGORY = GameRuleCategory.register(CozyCore.id("cozy_core"));

	/** Inside a Hearth Lantern's reach, creeper and ghast blasts, fire and endermen leave blocks alone. */
	public static final GameRule<Boolean> HEARTHS_PROTECT_BUILDS = bool("hearths_protect_builds", true);
	/** Off: creepers' and ghasts' explosions (anything in the {@code cozy_core:explosions_spare_blocks} tag) still hurt, but break nothing anywhere. */
	public static final GameRule<Boolean> MOB_EXPLOSIONS_BREAK_BLOCKS = bool("mob_explosions_break_blocks", true);
	/** Off: endermen don't pick blocks up anywhere. */
	public static final GameRule<Boolean> ENDERMEN_TAKE_BLOCKS = bool("endermen_take_blocks", true);
	/** Off: fire still burns and goes out, but never spreads onto a block or destroys one anywhere. */
	public static final GameRule<Boolean> FIRE_BURNS_BLOCKS = bool("fire_burns_blocks", true);
	/** How far a Hearth Lantern reaches (sideways, any height): no natural monster spawns, and builds kept safe. 0 turns the lanterns off. */
	public static final GameRule<Integer> HEARTH_LANTERN_RADIUS = integer("hearth_lantern_radius", 32, 0, 128);
	/** How many minutes a dropped item lasts at home (inside a Hearth Lantern's reach); 5 is vanilla's five minutes. */
	public static final GameRule<Integer> HEARTH_ITEM_MINUTES = integer("hearth_item_minutes", 60, 5, 1440);
	/** The hotbar, armour and off-hand stay with a player who dies; the rest drops. Vanilla's {@code keep_inventory} keeps everything and wins when it's on. */
	public static final GameRule<Boolean> KEEP_HOTBAR_ON_DEATH = bool("keep_hotbar_on_death", true);

	private Rules() {
	}

	/**
	 * Touching the class registers the rules. (Up to 0.4.0 this also made vanilla's spawn_phantoms
	 * start off in new worlds; since 0.5.0 phantoms only leave you alone at home, see
	 * PhantomSpawnerMixin. Worlds made before keep the value they saved.)
	 */
	public static void register() {
	}

	public static boolean is(ServerLevel level, GameRule<Boolean> rule) {
		return level.getGameRules().get(rule);
	}

	public static int get(ServerLevel level, GameRule<Integer> rule) {
		return level.getGameRules().get(rule);
	}

	private static GameRule<Boolean> bool(String name, boolean fallback) {
		return register(name, new GameRule<>(CATEGORY, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
				Codec.BOOL, value -> value ? 1 : 0, fallback, FeatureFlagSet.of()));
	}

	private static GameRule<Integer> integer(String name, int fallback, int min, int max) {
		return register(name, new GameRule<>(CATEGORY, GameRuleType.INT, IntegerArgumentType.integer(min, max), GameRuleTypeVisitor::visitInteger,
				Codec.intRange(min, max), Integer::intValue, fallback, FeatureFlagSet.of()));
	}

	private static <T> GameRule<T> register(String name, GameRule<T> rule) {
		// The rule object is the key everywhere (the map of values, the command, the screen), so
		// it's kept here and the loader only needs to put it in the registry.
		Services.PLATFORM.register(Registries.GAME_RULE, CozyCore.id(name), () -> rule);
		return rule;
	}
}
