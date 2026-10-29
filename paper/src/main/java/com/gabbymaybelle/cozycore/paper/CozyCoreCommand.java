package com.gabbymaybelle.cozycore.paper;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code /cozycore}, for operators (permission {@code cozycore.admin}):
 * <ul>
 * <li>{@code give [style] [count]}: hands you Hearth Lanterns (a player only).</li>
 * <li>{@code place <x> <y> <z> [style] [name...]}: puts a Hearth Lantern in the world, for a home
 * in a world that already exists, or a server console with no one on.</li>
 * <li>{@code selftest}: checks the nearest loaded lantern's home through the real spawn event (a
 * natural zombie spawn at home is refused, one in the wild goes through) and says pass or fail.
 * It's what the headless server test runs.</li>
 * </ul>
 */
final class CozyCoreCommand implements BasicCommand {
	private final CozyCorePlugin plugin;
	private final HearthItems items;
	private final Hearths hearths;

	CozyCoreCommand(CozyCorePlugin plugin, HearthItems items, Hearths hearths) {
		this.plugin = plugin;
		this.items = items;
		this.hearths = hearths;
	}

	@Override
	public String permission() {
		return "cozycore.admin";
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		if (args.length <= 1) {
			return List.of("give", "place", "selftest");
		}
		if (args[0].equals("give") && args.length == 2 || args[0].equals("place") && args.length == 5) {
			return HearthStyle.words();
		}
		return List.of();
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		if (args.length == 0) {
			fail(sender, "Usage: /cozycore give [style] [count], /cozycore place <x> <y> <z> [style] [name], /cozycore selftest");
			return;
		}
		switch (args[0]) {
			case "give" -> give(source, args);
			case "place" -> place(source, args);
			case "selftest" -> selftest(source);
			default -> fail(sender, "Unknown: " + args[0] + ". Try give, place or selftest.");
		}
	}

	private void give(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		if (!(source.getExecutor() instanceof Player player)) {
			fail(sender, "Only a player can be given a Hearth Lantern; use /cozycore place from here.");
			return;
		}
		HearthStyle style = args.length >= 2 ? HearthStyle.byWord(args[1]) : HearthStyle.CLASSIC;
		if (style == null) {
			fail(sender, "No such style: " + args[1] + ". Styles: " + String.join(", ", HearthStyle.words()));
			return;
		}
		int count = 1;
		if (args.length >= 3) {
			try {
				count = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
			} catch (NumberFormatException e) {
				fail(sender, "The count should be a number.");
				return;
			}
		}
		int given = count;
		player.getInventory().addItem(items.make(style, count)).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
		say(sender, "Cozy Core: gave " + player.getName() + " " + given + " " + style.label + (given == 1 ? "" : "s") + ".");
	}

	private void place(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		if (args.length < 4) {
			fail(sender, "Usage: /cozycore place <x> <y> <z> [style] [name]");
			return;
		}
		int x, y, z;
		try {
			x = Integer.parseInt(args[1]);
			y = Integer.parseInt(args[2]);
			z = Integer.parseInt(args[3]);
		} catch (NumberFormatException e) {
			fail(sender, "The position should be three whole numbers.");
			return;
		}
		HearthStyle style = args.length >= 5 ? HearthStyle.byWord(args[4]) : HearthStyle.CLASSIC;
		if (style == null) {
			fail(sender, "No such style: " + args[4] + ". Styles: " + String.join(", ", HearthStyle.words()));
			return;
		}
		String name = args.length >= 6 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : null;
		World world = source.getLocation().getWorld();
		Block block = world.getBlockAt(x, y, z);
		if (!block.getType().isAir() && !block.isLiquid() && hearths.at(block) == null) {
			fail(sender, "Cozy Core: there is " + block.getType().getKey().getKey() + " at " + x + " " + y + " " + z + "; the lantern needs an empty spot.");
			return;
		}
		if (hearths.at(block) != null) {
			hearths.remove(block, false);
		}
		block.setType(style.block, true);
		hearths.add(block, style, name == null ? null : Component.text(name));
		say(sender, "Cozy Core: placed a " + style.label + " at " + x + " " + y + " " + z + (name == null ? "" : ", the home called \"" + name + "\"") + ".");
	}

	/** The checks the headless test reads: each line says what it found, and the last says passed or failed. */
	private void selftest(CommandSourceStack source) {
		CommandSender sender = source.getSender();
		World world = source.getLocation().getWorld();
		Hearths.Hearth lantern = nearest(world, source.getLocation());
		if (lantern == null) {
			fail(sender, "Cozy Core selftest: no Hearth Lantern is loaded in " + world.getName() + ". Place one with /cozycore place.");
			return;
		}
		int radius = plugin.radius();
		List<String> failed = new ArrayList<>();
		Location home = new Location(world, lantern.pos().x() + 0.5, lantern.pos().y() + 1, lantern.pos().z() + 0.5);
		Location wild = home.clone().add(radius + 12, 0, 0);
		check(failed, sender, "the lantern's spot is home", hearths.protects(home));
		check(failed, sender, "a spot " + (radius + 12) + " blocks out is the wild", !hearths.protects(wild));
		Zombie atHome = spawnNaturally(world, home);
		check(failed, sender, "a natural monster spawn at home is refused", atHome == null);
		if (atHome != null) {
			atHome.remove();
		}
		Zombie inWild = spawnNaturally(world, wild);
		check(failed, sender, "a natural monster spawn in the wild goes through", inWild != null);
		if (inWild != null) {
			inWild.remove();
		}
		if (failed.isEmpty()) {
			say(sender, "Cozy Core selftest: passed (4 checks, radius " + radius + ", lantern at " + lantern.pos().x() + " " + lantern.pos().y() + " " + lantern.pos().z() + ")");
		} else {
			fail(sender, "Cozy Core selftest: failed: " + String.join("; ", failed));
		}
	}

	/** A zombie spawned the way the world spawns monsters at night, so the spawn handler sees it; null when the spawn was refused. */
	private static @Nullable Zombie spawnNaturally(World world, Location at) {
		Zombie zombie = world.spawn(at, Zombie.class, CreatureSpawnEvent.SpawnReason.NATURAL);
		return zombie.isValid() ? zombie : null;
	}

	private static void check(List<String> failed, CommandSender sender, String name, boolean ok) {
		say(sender, (ok ? "ok   " : "FAIL ") + name);
		if (!ok) {
			failed.add(name);
		}
	}

	private Hearths.@Nullable Hearth nearest(World world, Location to) {
		Hearths.Hearth nearest = null;
		long best = Long.MAX_VALUE;
		for (Hearths.Hearth hearth : hearths.in(world)) {
			long d = hearth.pos().distanceSqr(to.getX(), to.getZ());
			if (d < best) {
				nearest = hearth;
				best = d;
			}
		}
		return nearest;
	}

	private static void say(CommandSender to, String text) {
		to.sendMessage(Component.text(text));
	}

	private static void fail(CommandSender to, String text) {
		to.sendMessage(Component.text(text, NamedTextColor.RED));
	}
}
