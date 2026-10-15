package com.gabbymaybelle.cozycore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The {@code keep_hotbar_on_death} rule: when a player dies, their hotbar, armour and off-hand
 * are set aside before anything drops and handed back to the respawned player. Everything else
 * drops where they fell (into a grave, with a graves mod), so there's still a trip back for the
 * rest. The set-aside items live on the dead player ({@code ServerPlayerMixin} adds the list) and
 * are saved with them, so logging out while dead and coming back later loses nothing. Vanilla's
 * {@code keep_inventory} keeps everything and wins when it's on; Curse of Vanishing still vanishes.
 */
public final class KeepOnDeath {
	/** The key of the list in the player's saved data. */
	public static final String SAVE_KEY = "cozy_core:kept_items";

	/** Hotbar 0..8, then feet, legs, chest, head (36..39) and the off-hand (40). */
	private static final int[] KEPT_SLOTS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 36, 37, 38, 39, Inventory.SLOT_OFFHAND };

	/** A set-aside stack and the slot it came from. */
	public record Kept(int slot, ItemStack stack) {
		public static final Codec<Kept> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("slot").forGetter(Kept::slot),
				ItemStack.CODEC.fieldOf("stack").forGetter(Kept::stack)).apply(instance, Kept::new));
	}

	/** What a {@link ServerPlayer} carries between dying and respawning; the mixin implements it. */
	public interface Holder {
		List<Kept> cozyCore$keptItems();
	}

	private KeepOnDeath() {
	}

	private static List<Kept> kept(ServerPlayer player) {
		return ((Holder) player).cozyCore$keptItems();
	}

	/** Called at the very start of a player's death drops, before any mod or vanilla code touches the inventory. */
	public static void stash(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!Rules.is(level, Rules.KEEP_HOTBAR_ON_DEATH) || level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
			return;
		}
		// Anything still waiting from an earlier death stays in the list.
		List<Kept> kept = kept(player);
		Inventory inventory = player.getInventory();
		for (int slot : KEPT_SLOTS) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			kept.add(new Kept(slot, stack.copy()));
			inventory.setItem(slot, ItemStack.EMPTY);
		}
	}

	/** Called as the respawned player is made from the dead one: the set-aside items go back in their slots. */
	public static void handBack(ServerPlayer dead, ServerPlayer respawned) {
		List<Kept> kept = kept(dead);
		if (kept.isEmpty()) {
			return;
		}
		Inventory inventory = respawned.getInventory();
		for (Kept item : kept) {
			if (inventory.getItem(item.slot()).isEmpty()) {
				inventory.setItem(item.slot(), item.stack());
			} else if (!inventory.add(item.stack())) {
				respawned.spawnAtLocation(respawned.level(), item.stack());
			}
		}
		kept.clear();
	}

	public static void save(ServerPlayer player, ValueOutput output) {
		List<Kept> kept = kept(player);
		if (!kept.isEmpty()) {
			ValueOutput.TypedOutputList<Kept> list = output.list(SAVE_KEY, Kept.CODEC);
			kept.forEach(list::add);
		}
	}

	public static void load(ServerPlayer player, ValueInput input) {
		List<Kept> kept = kept(player);
		kept.clear();
		input.listOrEmpty(SAVE_KEY, Kept.CODEC).forEach(kept::add);
	}
}
