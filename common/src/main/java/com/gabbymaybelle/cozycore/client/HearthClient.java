package com.gabbymaybelle.cozycore.client;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanternBlockEntity;
import com.gabbymaybelle.cozycore.HearthLanterns;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/**
 * What a player sees of home, worked out on their own computer from the lanterns the game has
 * already loaded there (nothing runs on the server):
 * <ul>
 * <li>Walking into a Hearth Lantern's reach shows "Home" (or the lantern's anvil name) above the
 * hotbar for a moment, with a soft fireplace crackle. Walking from one named home into another
 * shows the new name.</li>
 * <li>While a Hearth Lantern is in either hand, a few small flames on the ground mark where the
 * nearby homes end.</li>
 * </ul>
 * The loader calls {@link #tick} once per client tick; both jobs are cheap checks against the
 * short list of loaded lanterns.
 */
public final class HearthClient {
	/** How often (in ticks) to check whether the player is home. */
	private static final int HOME_CHECK = 5;
	/** How far along the edge (in blocks, either side of the player) the glow shows. */
	private static final int EDGE_VIEW = 20;
	/** Flames per lantern per tick while one is held. */
	private static final int EDGE_FLAMES = 3;

	/** The home the player was last in, or null when they were out in the wild (or just joined). */
	private static @Nullable BlockPos home;
	private static @Nullable Component homeName;
	private static @Nullable ClientLevel lastLevel;

	private HearthClient() {
	}

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			home = null;
			lastLevel = null;
			return;
		}
		if (level != lastLevel) {
			// A new world or dimension: start out of home, so arriving at one says so.
			lastLevel = level;
			home = null;
			homeName = null;
		}
		if (player.tickCount % HOME_CHECK == 0) {
			checkHome(minecraft, level, player);
		}
		if (HearthLanterns.any(level) && (HearthLantern.isHearth(player.getItemInHand(InteractionHand.MAIN_HAND).getItem())
				|| HearthLantern.isHearth(player.getItemInHand(InteractionHand.OFF_HAND).getItem()))) {
			showEdges(level, player);
		}
	}

	private static void checkHome(Minecraft minecraft, ClientLevel level, LocalPlayer player) {
		HearthLanternBlockEntity lantern = HearthLanterns.homeAt(level, player.blockPosition());
		if (lantern == null) {
			home = null;
			homeName = null;
			return;
		}
		Component name = lantern.name();
		boolean arrived = home == null;
		boolean newName = !arrived && name != null && !Objects.equals(name, homeName);
		home = lantern.getBlockPos();
		if (arrived || newName) {
			homeName = name;
			minecraft.gui.hud.setOverlayMessage(name != null ? name : Component.translatable("cozy_core.hearth.home"), false);
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.AMBIENT, 0.6F, 1.0F, false);
		}
	}

	/** A few flames on the part of each nearby home's edge that's closest to the player. */
	private static void showEdges(ClientLevel level, LocalPlayer player) {
		RandomSource random = level.getRandom();
		double px = player.getX(), pz = player.getZ();
		for (HearthLanternBlockEntity lantern : HearthLanterns.lanterns(level)) {
			int radius = lantern.reach();
			if (radius <= 0) {
				continue;
			}
			double cx = lantern.getBlockPos().getX() + 0.5, cz = lantern.getBlockPos().getZ() + 0.5;
			double away = Math.sqrt((px - cx) * (px - cx) + (pz - cz) * (pz - cz));
			if (Math.abs(away - radius) > EDGE_VIEW) {
				continue;
			}
			double facing = Math.atan2(pz - cz, px - cx);
			double spread = Math.min(Math.PI, EDGE_VIEW / (double) radius);
			for (int i = 0; i < EDGE_FLAMES; i++) {
				double angle = facing + (random.nextDouble() * 2 - 1) * spread;
				double x = cx + Math.cos(angle) * radius, z = cz + Math.sin(angle) * radius;
				Integer ground = groundNear(level, Mth.floor(x), Mth.floor(player.getY()), Mth.floor(z));
				if (ground != null) {
					level.addParticle(HearthLantern.styleOf(lantern.getBlockState().getBlock()).flame(), x, ground + 0.1, z, 0, 0.01, 0);
				}
			}
		}
	}

	/** The top of the ground at this column near the player's height: a block with open air above it. */
	private static @Nullable Integer groundNear(ClientLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 6; dy >= -8; dy--) {
			pos.set(x, y + dy, z);
			if (level.getBlockState(pos).isAir() && !level.getBlockState(pos.move(0, -1, 0)).isAir()) {
				return y + dy;
			}
		}
		return null;
	}
}
