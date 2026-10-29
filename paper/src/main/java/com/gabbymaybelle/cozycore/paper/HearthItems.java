package com.gabbymaybelle.cozycore.paper;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * The Hearth Lantern as an item: a vanilla lantern named "Hearth Lantern" (its item name, so an
 * anvil can still give it a custom name, which becomes the home's name), with a glint and the
 * plugin's tag saying which style it is. The six recipes are the mod's: four amethyst shards
 * around the matching lantern. The tag is what makes it a hearth; the name and glint are for the
 * player, who otherwise sees a plain lantern.
 */
final class HearthItems {
	/** The item tag: {@code cozycore:hearth} = the style's id. */
	private final NamespacedKey tag;
	private final Plugin plugin;

	HearthItems(Plugin plugin) {
		this.plugin = plugin;
		this.tag = new NamespacedKey(plugin, "hearth");
	}

	/** A new Hearth Lantern of this style, with no custom name. */
	ItemStack make(HearthStyle style, int count) {
		ItemStack item = ItemStack.of(style.block, count);
		item.editMeta(meta -> meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING, style.id));
		item.setData(DataComponentTypes.ITEM_NAME, Component.text(style.label));
		item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return item;
	}

	/** The same, carrying the name a home was given (null for none). */
	ItemStack make(HearthStyle style, @Nullable Component name) {
		ItemStack item = make(style, 1);
		if (name != null) {
			item.setData(DataComponentTypes.CUSTOM_NAME, name);
		}
		return item;
	}

	/** The style this item is, or null for anything that isn't a Hearth Lantern. */
	@Nullable HearthStyle styleOf(@Nullable ItemStack item) {
		if (item == null || item.getType() == Material.AIR) {
			return null;
		}
		String id = item.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
		return id == null ? null : HearthStyle.byId(id);
	}

	/** The six recipes, one per style, keyed {@code cozycore:<style id>}. */
	void registerRecipes() {
		for (HearthStyle style : HearthStyle.values()) {
			ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, style.id), make(style, 1));
			recipe.shape(" X ", "X#X", " X ");
			recipe.setIngredient('X', Material.AMETHYST_SHARD);
			recipe.setIngredient('#', new RecipeChoice.MaterialChoice(style.madeFrom));
			recipe.setCategory(CraftingBookCategory.BUILDING);
			Bukkit.addRecipe(recipe);
		}
	}
}
