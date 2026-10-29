package com.gabbymaybelle.cozycore.paper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * "Home": walking into a Hearth Lantern's reach shows "Home" (or the name the lantern was given on
 * an anvil) above the hotbar for a moment, with a soft fireplace crackle; walking from one named
 * home into another shows the new name. The mod works this out on each player's computer; a plugin
 * has to do it on the server, so a task looks at every player every five ticks against the short
 * list of loaded lanterns.
 */
final class HomeSign implements Listener, Runnable {
	/** How often (in ticks) to check whether a player is home. */
	static final int HOME_CHECK = 5;

	/** The home a player was last in: where its lantern is and what it's called (null name for plain "Home"). */
	private record Seen(Hearths.Pos home, @Nullable Component name) {
	}

	private final Hearths hearths;
	private final Map<UUID, Seen> seen = new HashMap<>();

	HomeSign(Hearths hearths) {
		this.hearths = hearths;
	}

	@Override
	public void run() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			check(player);
		}
	}

	private void check(Player player) {
		Hearths.Hearth lantern = hearths.homeAt(player.getWorld(), player.getX(), player.getZ());
		if (lantern == null) {
			seen.remove(player.getUniqueId());
			return;
		}
		Seen was = seen.get(player.getUniqueId());
		boolean arrived = was == null;
		boolean newName = !arrived && lantern.name() != null && !Objects.equals(lantern.name(), was.name());
		seen.put(player.getUniqueId(), new Seen(lantern.pos(), arrived || newName ? lantern.name() : was.name()));
		if (arrived || newName) {
			player.sendActionBar(lantern.name() != null ? lantern.name() : Component.text("Home"));
			player.playSound(player.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.AMBIENT, 0.6f, 1.0f);
		}
	}

	/** A new world or dimension: start out of home, so arriving at one says so. */
	@EventHandler
	public void onChangedWorld(PlayerChangedWorldEvent event) {
		seen.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		seen.remove(event.getPlayer().getUniqueId());
	}
}
