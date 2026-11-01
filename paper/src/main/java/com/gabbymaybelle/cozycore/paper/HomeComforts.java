package com.gabbymaybelle.cozycore.paper;

import com.destroystokyo.paper.event.entity.EntityZapEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.tag.DamageTypeTags;

/**
 * The mod's "Home comforts", the small kindnesses of being at home (inside a Hearth Lantern's
 * reach), and the two advancements only the plugin can see happen:
 * <ul>
 * <li>Dropped items last {@code hearthItemMinutes} at home instead of five minutes.</li>
 * <li>Every passive animal and pet, and villagers, are spared creeper and ghast blasts, fire and
 * lightning at home (while {@code hearthsProtectBuilds} is on), and lightning doesn't turn them
 * into something else.</li>
 * <li>Players and their tamed pets take no fall damage at home.</li>
 * <li>Glass, ice and bookshelves (the mod's {@code keeps_at_home} tag) drop themselves when a
 * player breaks them at home, and ice leaves no water.</li>
 * <li>With {@code keepHotbarOnDeath} on, the hotbar, armour and off-hand stay with a player who
 * dies; the rest drops. Vanilla's keepInventory keeps everything and wins when it's on.</li>
 * <li>"The kettle's on" (a Hearth Lantern crafted) and "Safe and sound" (a creeper or ghast blast
 * at home broke nothing, awarded from {@link HomeGuard}).</li>
 * </ul>
 */
final class HomeComforts implements Listener {
	/** Vanilla's lifetime of a dropped item, five minutes; the despawn event comes when it's up. */
	private static final int LIFETIME = 6000, MINUTE = 1200;
	/** Hotbar 0..8, then boots, leggings, chestplate, helmet (36..39) and the off-hand (40). */
	private static final int[] KEPT_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 36, 37, 38, 39, 40 };
	/** Players this close to a blast (any direction) get "Safe and sound". */
	private static final double NEAR = 24;
	/** Ticks after a lightning bolt appears by which it has struck and gone. */
	private static final long BOLT_GONE = 10;

	private final CozyCorePlugin plugin;
	private final Hearths hearths;
	private final HearthItems items;
	private final Set<Material> keepsAtHome;
	/** Ticks an item has been given back at home so far, kept on the item so a restart doesn't lose it. */
	private final NamespacedKey extraKey;

	HomeComforts(CozyCorePlugin plugin, Hearths hearths, HearthItems items) {
		this.plugin = plugin;
		this.hearths = hearths;
		this.items = items;
		this.keepsAtHome = readKeepsAtHome(plugin);
		this.extraKey = new NamespacedKey(plugin, "extra_ticks");
	}

	/** The mod's {@code keeps_at_home} block tag, which the build puts in the jar beside this class's plugin. */
	private static Set<Material> readKeepsAtHome(CozyCorePlugin plugin) {
		Set<Material> blocks = EnumSet.noneOf(Material.class);
		try (InputStream in = plugin.getResource("keeps_at_home.json")) {
			if (in == null) {
				plugin.getLogger().warning("keeps_at_home.json is missing from the jar, so no blocks drop themselves at home");
				return blocks;
			}
			for (JsonElement value : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values")) {
				Material block = Material.matchMaterial(value.getAsString());
				if (block != null) {
					blocks.add(block);
				}
			}
		} catch (Exception e) {
			plugin.getLogger().warning("Couldn't read keeps_at_home.json, so no blocks drop themselves at home: " + e);
		}
		return blocks;
	}

	/** How many blocks drop themselves at home (the selftest checks it's the mod's whole list). */
	int keepsAtHomeCount() {
		return keepsAtHome.size();
	}

	/** Every passive animal and pet, and villagers. Hoglins are animals by class, but they're monsters. */
	static boolean isHomeAnimal(Entity entity) {
		return entity instanceof Animals && !(entity instanceof Enemy) || entity instanceof AbstractVillager;
	}

	/** A player, or a pet someone has tamed (horses and llamas included). */
	static boolean isPlayerOrPet(Entity entity) {
		return entity instanceof Player || entity instanceof Tameable pet && pet.isTamed();
	}

	/**
	 * An item at home that would vanish now gets another minute, until it has lived
	 * {@code hearthItemMinutes}. Cancelling the event starts the item's five minutes again, so the
	 * next tick sets its age to leave it only the time it's owed.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onDespawn(ItemDespawnEvent event) {
		Item item = event.getEntity();
		int extra = item.getPersistentDataContainer().getOrDefault(extraKey, PersistentDataType.INTEGER, 0);
		int owed = plugin.hearthItemMinutes() * MINUTE - LIFETIME - extra;
		if (owed <= 0 || !hearths.protects(item.getLocation())) {
			return;
		}
		int given = Math.min(MINUTE, owed);
		event.setCancelled(true);
		item.getPersistentDataContainer().set(extraKey, PersistentDataType.INTEGER, extra + given);
		Bukkit.getScheduler().runTask(plugin, () -> {
			if (item.isValid()) {
				item.setTicksLived(LIFETIME - given);
			}
		});
	}

	/**
	 * Damage at home: animals and villagers aren't hurt by creeper and ghast blasts, fire or
	 * lightning (while builds are protected), and players and pets take no fall damage.
	 */
	@EventHandler(ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
			if (isPlayerOrPet(entity) && hearths.protects(entity.getLocation())) {
				event.setCancelled(true);
			}
			return;
		}
		if (isHomeAnimal(entity) && sparesAnimal(event.getDamageSource()) && hearths.guards(entity.getLocation().getBlock())) {
			event.setCancelled(true);
		}
	}

	private static boolean sparesAnimal(DamageSource source) {
		DamageType type = source.getDamageType();
		if (DamageTypeTags.IS_FIRE.isTagged(type) || DamageTypeTags.IS_LIGHTNING.isTagged(type)) {
			return true;
		}
		return DamageTypeTags.IS_EXPLOSION.isTagged(type) && (sparingMob(source.getCausingEntity()) || sparingMob(source.getDirectEntity()));
	}

	private static boolean sparingMob(Entity entity) {
		return entity != null && HomeGuard.sparingMob(entity);
	}

	/** Lightning at home doesn't turn a villager into a witch, and so on. */
	@EventHandler(ignoreCancelled = true)
	public void onTransform(EntityTransformEvent event) {
		if (event.getTransformReason() == EntityTransformEvent.TransformReason.LIGHTNING
				&& isHomeAnimal(event.getEntity()) && hearths.guards(event.getEntity().getLocation().getBlock())) {
			event.setCancelled(true);
		}
	}

	/** A pig struck at home stays a pig: Paper asks about that with its own zap event. */
	@EventHandler(ignoreCancelled = true)
	public void onZap(EntityZapEvent event) {
		if (isHomeAnimal(event.getEntity()) && hearths.guards(event.getEntity().getLocation().getBlock())) {
			event.setCancelled(true);
		}
	}

	/**
	 * A mooshroom struck at home keeps its colour. Paper has no event for that swap, so the colour of
	 * every mooshroom near the strike is noted when the bolt appears and put back once it's gone (a
	 * bolt strikes on its own ticks, after this event, and swaps a mooshroom only once).
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLightning(LightningStrikeEvent event) {
		for (Entity entity : event.getLightning().getNearbyEntities(4, 4, 4)) {
			if (entity instanceof MushroomCow cow && hearths.guards(cow.getLocation().getBlock())) {
				MushroomCow.Variant variant = cow.getVariant();
				Bukkit.getScheduler().runTaskLater(plugin, () -> {
					if (cow.isValid()) {
						cow.setVariant(variant);
					}
				}, BOLT_GONE);
			}
		}
	}

	/**
	 * Glass, ice and bookshelves broken by a player at home drop themselves: the vanilla drop is
	 * switched off and the block's own item is dropped, and the water broken ice leaves is cleared.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (!keepsAtHome.contains(block.getType()) || event.getPlayer().getGameMode() == GameMode.CREATIVE || !hearths.protects(block)) {
			return;
		}
		Material type = block.getType();
		event.setDropItems(false);
		block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), ItemStack.of(type));
		// Paper still melts ice into water after the break; at home the ice was picked up whole, so
		// the water goes.
		if (type == Material.ICE) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (block.getType() == Material.WATER) {
					block.setType(Material.AIR);
				}
			});
		}
	}

	/**
	 * Keep your hotbar: those slots' items go on the event's keep list, which Paper leaves in their
	 * slots, and come off the drops. Items with Curse of Vanishing still vanish.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDeath(PlayerDeathEvent event) {
		if (!plugin.keepHotbarOnDeath() || event.getKeepInventory()) {
			return;
		}
		PlayerInventory inventory = event.getEntity().getInventory();
		List<ItemStack> drops = event.getDrops();
		for (int slot : KEPT_SLOTS) {
			ItemStack stack = inventory.getItem(slot);
			if (stack == null || stack.isEmpty() || stack.containsEnchantment(Enchantment.VANISHING_CURSE)) {
				continue;
			}
			event.getItemsToKeep().add(stack);
			for (int i = 0; i < drops.size(); i++) {
				if (drops.get(i).equals(stack)) {
					drops.remove(i);
					break;
				}
			}
		}
	}

	/** "The kettle's on": the tab's step for crafting a Hearth Lantern. */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCraft(CraftItemEvent event) {
		if (items.styleOf(event.getRecipe().getResult()) != null && event.getWhoClicked() instanceof Player player) {
			award(player, "the_kettles_on");
		}
	}

	/** "Safe and sound": every player near a creeper or ghast blast that broke nothing at home. */
	void safeAndSound(org.bukkit.Location centre) {
		for (Player player : centre.getWorld().getPlayers()) {
			if (player.getLocation().distanceSquared(centre) < NEAR * NEAR) {
				award(player, "safe_and_sound");
			}
		}
	}

	/** Each of the plugin's advancements has one criterion named like the advancement. */
	private static void award(Player player, String name) {
		Advancement advancement = Bukkit.getAdvancement(new NamespacedKey("cozy_core", "cozy/" + name));
		if (advancement != null) {
			player.getAdvancementProgress(advancement).awardCriteria(name);
		}
	}
}
