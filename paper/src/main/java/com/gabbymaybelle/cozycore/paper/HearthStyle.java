package com.gabbymaybelle.cozycore.paper;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * The six looks of the Hearth Lantern, the same as the mod's: each is made from its own vanilla
 * lantern and four amethyst shards, and they all work the same. On Paper a Hearth Lantern is the
 * vanilla lantern block itself, carrying the plugin's tag, so the copper ones are placed as the
 * waxed copper lanterns: those look like copper and never weather, which is what the mod's do.
 */
enum HearthStyle {
	CLASSIC("hearth_lantern", "Hearth Lantern", Material.LANTERN, List.of(Material.LANTERN)),
	SOUL("soul_hearth_lantern", "Soul Hearth Lantern", Material.SOUL_LANTERN, List.of(Material.SOUL_LANTERN)),
	COPPER("copper_hearth_lantern", "Copper Hearth Lantern", Material.WAXED_COPPER_LANTERN,
			List.of(Material.COPPER_LANTERN, Material.WAXED_COPPER_LANTERN)),
	EXPOSED_COPPER("exposed_copper_hearth_lantern", "Exposed Copper Hearth Lantern", Material.WAXED_EXPOSED_COPPER_LANTERN,
			List.of(Material.EXPOSED_COPPER_LANTERN, Material.WAXED_EXPOSED_COPPER_LANTERN)),
	WEATHERED_COPPER("weathered_copper_hearth_lantern", "Weathered Copper Hearth Lantern", Material.WAXED_WEATHERED_COPPER_LANTERN,
			List.of(Material.WEATHERED_COPPER_LANTERN, Material.WAXED_WEATHERED_COPPER_LANTERN)),
	OXIDIZED_COPPER("oxidized_copper_hearth_lantern", "Oxidized Copper Hearth Lantern", Material.WAXED_OXIDIZED_COPPER_LANTERN,
			List.of(Material.OXIDIZED_COPPER_LANTERN, Material.WAXED_OXIDIZED_COPPER_LANTERN));

	/** The mod's id for this style (its recipe key here too), the name shown on the item, the block it is placed as, and the lanterns it is made from. */
	final String id, label;
	final Material block;
	final List<Material> madeFrom;

	HearthStyle(String id, String label, Material block, List<Material> madeFrom) {
		this.id = id;
		this.label = label;
		this.block = block;
		this.madeFrom = madeFrom;
	}

	/** The style with this id, or null. */
	static @Nullable HearthStyle byId(String id) {
		for (HearthStyle style : values()) {
			if (style.id.equals(id)) {
				return style;
			}
		}
		return null;
	}

	/** The style a command names: its id, or a short word (classic, soul, copper, exposed, weathered, oxidized). */
	static @Nullable HearthStyle byWord(String word) {
		String w = word.toLowerCase(Locale.ROOT);
		HearthStyle byId = byId(w);
		if (byId != null) {
			return byId;
		}
		return switch (w) {
			case "classic", "lantern" -> CLASSIC;
			case "soul" -> SOUL;
			case "copper" -> COPPER;
			case "exposed", "exposed_copper" -> EXPOSED_COPPER;
			case "weathered", "weathered_copper" -> WEATHERED_COPPER;
			case "oxidized", "oxidised", "oxidized_copper" -> OXIDIZED_COPPER;
			default -> null;
		};
	}

	/** The style whose placed block this is, or null for a lantern that isn't one of ours. */
	static @Nullable HearthStyle ofBlock(Material material) {
		for (HearthStyle style : values()) {
			if (style.block == material) {
				return style;
			}
		}
		return null;
	}

	/** The short words a command suggests. */
	static List<String> words() {
		return List.of("classic", "soul", "copper", "exposed", "weathered", "oxidized");
	}
}
