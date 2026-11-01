package com.gabbymaybelle.cozycore.gametest.neoforge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Keeps one wood mod's library from crashing the test server (learned 2026-11-01). FrozenLib, which
 * Wilder Wild brings, tags a player with their item cooldowns whenever the player is saved, and on
 * the player's next tick sends those cooldowns to their game. A game test's stand-in player has no
 * real connection, so NeoForge refuses that packet and the whole server stops ("Payload
 * frozenlib:serializable_item_cooldowns_sync may not be sent to the client"). It only struck when a
 * stand-in player happened to tick right after a save (the keep-on-death check saves one, and the
 * server saves every player now and then), so the Wilder Wild CI group failed at random. Before each
 * stand-in player's tick, the tag is dropped. Real players are never touched, and nothing here is in
 * the released mod.
 */
final class MockPlayerGuard {
	/** The name every stand-in player from {@code makeMockServerPlayerInLevel} has. */
	private static final String MOCK_NAME = "test-mock-player";

	private static Object attachment;
	private static Method remove;

	private MockPlayerGuard() {
	}

	static void install() {
		if (!ModList.get().isLoaded("frozenlib")) {
			return;
		}
		try {
			Class<?> cooldowns = Class.forName("net.frozenblock.lib.item.impl.cooldown.SerializableItemCooldowns");
			Field field = cooldowns.getField("ATTACHMENT");
			attachment = field.get(null);
			Class<?> target = Class.forName("net.frozenblock.lib.platform.api.attachment.DataAttachmentTarget");
			remove = field.getType().getMethod("remove", target);
		} catch (ReflectiveOperationException e) {
			// A FrozenLib that works differently: nothing to guard against, or a new crash to look at.
			System.out.println("[cozy_core_gametest] FrozenLib's cooldown tag wasn't found (" + e + "); the stand-in player guard is off");
			return;
		}
		NeoForge.EVENT_BUS.addListener(EntityTickEvent.Pre.class, MockPlayerGuard::beforeTick);
	}

	private static void beforeTick(EntityTickEvent.Pre event) {
		if (event.getEntity() instanceof ServerPlayer player && MOCK_NAME.equals(player.getGameProfile().name())) {
			try {
				remove.invoke(attachment, player);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("couldn't drop FrozenLib's cooldown tag from a stand-in player", e);
			}
		}
	}
}
