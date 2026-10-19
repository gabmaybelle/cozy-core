package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

/**
 * The builder-kind rules, as ordinary game rules: they show in the Game Rules screen when a world
 * is made (under a "Cozy Core" heading on Fabric; with vanilla's own headings on NeoForge, which
 * has no way to add one), are set with {@code /gamerule cozy_core:...}, and are saved with the
 * world, so there is no settings file and nothing to sync. The code that applies each one is in
 * {@code mixin/}.
 *
 * <p>Since 0.5.0 builds are kept safe at home: inside a Hearth Lantern's reach while
 * {@link #HEARTHS_PROTECT_BUILDS} is on. The three "everywhere" switches start at vanilla's
 * behaviour in new worlds, and turning one off spares blocks in the whole world as before.
 * Worlds made with 0.4.0 or earlier saved every rule ({@code GameRules.createTag} writes them
 * all), so they keep sparing blocks everywhere.
 */
public final class Rules {
	/** Inside a Hearth Lantern's reach, creeper and ghast blasts, fire and endermen leave blocks alone. */
	public static final GameRules.Key<GameRules.BooleanValue> HEARTHS_PROTECT_BUILDS =
			Services.PLATFORM.booleanRule(name("hearths_protect_builds"), GameRules.Category.MOBS, true);
	/** Off: creepers' and ghasts' explosions (anything in the {@code cozy_core:explosions_spare_blocks} tag) still hurt, but break nothing anywhere. */
	public static final GameRules.Key<GameRules.BooleanValue> MOB_EXPLOSIONS_BREAK_BLOCKS =
			Services.PLATFORM.booleanRule(name("mob_explosions_break_blocks"), GameRules.Category.MOBS, true);
	/** Off: endermen don't pick blocks up anywhere. */
	public static final GameRules.Key<GameRules.BooleanValue> ENDERMEN_TAKE_BLOCKS =
			Services.PLATFORM.booleanRule(name("endermen_take_blocks"), GameRules.Category.MOBS, true);
	/** Off: fire still burns and goes out, but never spreads onto a block or destroys one anywhere. */
	public static final GameRules.Key<GameRules.BooleanValue> FIRE_BURNS_BLOCKS =
			Services.PLATFORM.booleanRule(name("fire_burns_blocks"), GameRules.Category.UPDATES, true);
	/** How far a Hearth Lantern reaches (sideways, any height): no natural monster spawns, no phantoms, and builds kept safe. 0 turns the lanterns off. */
	public static final GameRules.Key<GameRules.IntegerValue> HEARTH_LANTERN_RADIUS =
			Services.PLATFORM.integerRule(name("hearth_lantern_radius"), GameRules.Category.SPAWNING, 32, 0, 128);
	/** How many minutes a dropped item lasts at home (inside a Hearth Lantern's reach); 5 is vanilla's five minutes. */
	public static final GameRules.Key<GameRules.IntegerValue> HEARTH_ITEM_MINUTES =
			Services.PLATFORM.integerRule(name("hearth_item_minutes"), GameRules.Category.DROPS, 60, 5, 1440);
	/** The hotbar, armour and off-hand stay with a player who dies; the rest drops. Vanilla's {@code keepInventory} keeps everything and wins when it's on. */
	public static final GameRules.Key<GameRules.BooleanValue> KEEP_HOTBAR_ON_DEATH =
			Services.PLATFORM.booleanRule(name("keep_hotbar_on_death"), GameRules.Category.PLAYER, true);

	/** The lowest and highest {@link #HEARTH_LANTERN_RADIUS} counts; NeoForge's rules have no bounds, so a value outside is read as the nearest end. */
	public static final int MIN_RADIUS = 0, MAX_RADIUS = 128;

	private Rules() {
	}

	/**
	 * Touching the class registers the rules. (Up to 0.4.0 this also made vanilla's doInsomnia
	 * start off in new worlds; since 0.5.0 phantoms only leave you alone at home, see
	 * PhantomSpawnerMixin. Worlds made before keep the value they saved.)
	 */
	public static void register() {
	}

	public static boolean is(ServerLevel level, GameRules.Key<GameRules.BooleanValue> rule) {
		return level.getGameRules().getBoolean(rule);
	}

	public static int get(ServerLevel level, GameRules.Key<GameRules.IntegerValue> rule) {
		int value = level.getGameRules().getInt(rule);
		// NeoForge's number rules have no bounds, so a value outside is read as the nearest end.
		if (rule == HEARTH_ITEM_MINUTES) {
			return Math.clamp(value, 5, 1440);
		}
		return rule == HEARTH_LANTERN_RADIUS ? Math.clamp(value, MIN_RADIUS, MAX_RADIUS) : value;
	}

	/** Rule ids are {@code cozy_core:<name>}, the same as on Minecraft 26.x. */
	private static String name(String path) {
		return CozyCore.MOD_ID + ":" + path;
	}
}
