package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.mixin.BooleanValueInvoker;
import com.gabbymaybelle.cozycore.mixin.GameRulesAccessor;
import com.gabbymaybelle.cozycore.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * The builder-kind rules, as ordinary game rules: they show in the Game Rules screen when a world
 * is made (under a "Cozy Core" heading on Fabric; with vanilla's own headings on Forge, which
 * has no way to add one), are set with {@code /gamerule cozy_core:...}, and are saved with the
 * world, so there is no settings file and nothing to sync. Every rule defaults to the kind
 * setting. The code that applies each one is in {@code mixin/}.
 */
public final class Rules {
	/** Creepers' and ghasts' explosions (anything in the {@code cozy_core:explosions_spare_blocks} tag) still hurt, but break nothing. */
	public static final GameRules.Key<GameRules.BooleanValue> MOB_EXPLOSIONS_BREAK_BLOCKS =
			Services.PLATFORM.booleanRule(name("mob_explosions_break_blocks"), GameRules.Category.MOBS, false);
	/** Endermen don't pick blocks up. */
	public static final GameRules.Key<GameRules.BooleanValue> ENDERMEN_TAKE_BLOCKS =
			Services.PLATFORM.booleanRule(name("endermen_take_blocks"), GameRules.Category.MOBS, false);
	/** Fire still burns and goes out, but never spreads onto a block or destroys one. */
	public static final GameRules.Key<GameRules.BooleanValue> FIRE_BURNS_BLOCKS =
			Services.PLATFORM.booleanRule(name("fire_burns_blocks"), GameRules.Category.UPDATES, false);
	/** How far around a Hearth Lantern monsters don't spawn naturally (any height). 0 turns the lanterns off. */
	public static final GameRules.Key<GameRules.IntegerValue> HEARTH_LANTERN_RADIUS =
			Services.PLATFORM.integerRule(name("hearth_lantern_radius"), GameRules.Category.SPAWNING, 32, 0, 128);
	/** The hotbar, armour and off-hand stay with a player who dies; the rest drops. Vanilla's {@code keepInventory} keeps everything and wins when it's on. */
	public static final GameRules.Key<GameRules.BooleanValue> KEEP_HOTBAR_ON_DEATH =
			Services.PLATFORM.booleanRule(name("keep_hotbar_on_death"), GameRules.Category.PLAYER, true);

	/** The lowest and highest {@link #HEARTH_LANTERN_RADIUS} counts; Forge's rules have no bounds, so a value outside is read as the nearest end. */
	public static final int MIN_RADIUS = 0, MAX_RADIUS = 128;

	private Rules() {
	}

	/** Touching the class registers the rules; this also makes phantoms off the default for new worlds. */
	public static void register() {
		// Vanilla's own doInsomnia rule does the job; only its default changes. A rule's type makes
		// its starting value for every new world, and a saved world loads its own value over it, so
		// worlds that already exist keep whatever they have.
		GameRulesAccessor.cozyCore$types().put(GameRules.RULE_DOINSOMNIA, BooleanValueInvoker.cozyCore$create(false));
	}

	public static boolean is(ServerLevel level, GameRules.Key<GameRules.BooleanValue> rule) {
		return level.getGameRules().getBoolean(rule);
	}

	public static int get(ServerLevel level, GameRules.Key<GameRules.IntegerValue> rule) {
		int value = level.getGameRules().getInt(rule);
		return rule == HEARTH_LANTERN_RADIUS ? Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, value)) : value;
	}

	/** Rule ids are {@code cozy_core:<name>}, the same as on Minecraft 26.x. */
	private static String name(String path) {
		return CozyCore.MOD_ID + ":" + path;
	}
}
