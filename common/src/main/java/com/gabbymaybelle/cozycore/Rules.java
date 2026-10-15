package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.mixin.GameRuleAccessor;
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
 * saved with the world, so there is no settings file and nothing to sync. Every rule defaults to
 * the kind setting. The code that applies each one is in {@code mixin/}.
 */
public final class Rules {
	public static final GameRuleCategory CATEGORY = GameRuleCategory.register(CozyCore.id("cozy_core"));

	/** Creepers' and ghasts' explosions (anything in the {@code cozy_core:explosions_spare_blocks} tag) still hurt, but break nothing. */
	public static final GameRule<Boolean> MOB_EXPLOSIONS_BREAK_BLOCKS = bool("mob_explosions_break_blocks", false);
	/** Endermen don't pick blocks up. */
	public static final GameRule<Boolean> ENDERMEN_TAKE_BLOCKS = bool("endermen_take_blocks", false);
	/** Fire still burns and goes out, but never spreads onto a block or destroys one. */
	public static final GameRule<Boolean> FIRE_BURNS_BLOCKS = bool("fire_burns_blocks", false);
	/** How far around a Hearth Lantern monsters don't spawn naturally (any height). 0 turns the lanterns off. */
	public static final GameRule<Integer> HEARTH_LANTERN_RADIUS = integer("hearth_lantern_radius", 32, 0, 128);
	/** The hotbar, armour and off-hand stay with a player who dies; the rest drops. Vanilla's {@code keep_inventory} keeps everything and wins when it's on. */
	public static final GameRule<Boolean> KEEP_HOTBAR_ON_DEATH = bool("keep_hotbar_on_death", true);

	private Rules() {
	}

	/** Touching the class registers the rules; this also makes phantoms off the default for new worlds. */
	public static void register() {
		// Vanilla's own spawn_phantoms rule does the job; only its default changes. Worlds that
		// already exist keep whatever they have saved.
		((GameRuleAccessor) (Object) GameRules.SPAWN_PHANTOMS).cozyCore$setDefaultValue(false);
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
