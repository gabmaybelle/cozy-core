package com.gabbymaybelle.cozycore;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The "Safe and sound" advancement (Gabby's name, 2026-10-19): a creeper or ghast blast went off
 * at home, inside a Hearth Lantern's reach, and broke nothing there. No vanilla trigger sees that,
 * so the advancement has one {@code minecraft:impossible} criterion and the explosion hook hands
 * it to every player close enough to have seen it.
 */
public final class SafeAndSound {
	public static final String CRITERION = "safe_and_sound";
	/** Players this close to the blast (any direction) get it. */
	private static final double NEAR = 24;

	private SafeAndSound() {
	}

	public static void award(ServerLevel level, Vec3 centre) {
		AdvancementHolder advancement = level.getServer().getAdvancements().get(CozyCore.id("cozy/" + CRITERION));
		if (advancement == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.position().closerThan(centre, NEAR)) {
				player.getAdvancements().award(advancement, CRITERION);
			}
		}
	}
}
