package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The builder trades. Before Minecraft 26.x villager trades are code, not data, so they're added through the
 * loader ({@code Platform}), and the extra slot each pool gets is two mixins: {@code
 * WanderingTraderMixin} (six common offers instead of five) and {@code VillagerMixin} (three mason
 * offers at levels 1 and 2 instead of two), so vanilla's own trades are never pushed out.
 */
public final class Trades {
	/** How many more offers a pool gets than vanilla gives. */
	public static final int EXTRA_SLOTS = 1;

	/** Wandering trader, common pool: the plants it doesn't sell yet, priced like its saplings, and calcite priced like its sand. */
	public static final List<VillagerTrades.ItemListing> WANDERING_TRADER = List.of(
			sells(Items.AZALEA, 5, 1, 8, 1),
			sells(Items.FLOWERING_AZALEA, 5, 1, 8, 1),
			sells(Items.CRIMSON_FUNGUS, 5, 1, 8, 1),
			sells(Items.WARPED_FUNGUS, 5, 1, 8, 1),
			sells(Items.CALCITE, 1, 8, 8, 1));
	/** Mason, level 1: builder stone priced between vanilla's bricks and chiseled stone bricks. */
	public static final List<VillagerTrades.ItemListing> MASON_1 = List.of(
			sells(Items.TUFF, 1, 8, 16, 1),
			sells(Items.MUD_BRICKS, 1, 8, 16, 1));
	/** Mason, level 2. */
	public static final List<VillagerTrades.ItemListing> MASON_2 = List.of(
			sells(Items.CALCITE, 1, 6, 16, 5),
			sells(Items.PACKED_MUD, 1, 8, 16, 5));

	// No cartographer maps: maps can only point at structures, not biomes, and a badlands
	// mineshaft map was tried and dropped: badlands are usually farther than the search reaches.

	private Trades() {
	}

	public static void register() {
		Services.PLATFORM.addWanderingTraderTrades(WANDERING_TRADER);
		Services.PLATFORM.addVillagerTrades(VillagerProfession.MASON, 1, MASON_1);
		Services.PLATFORM.addVillagerTrades(VillagerProfession.MASON, 2, MASON_2);
	}

	/** Whether this villager's pool at this level gets the extra slot. */
	public static boolean hasExtraSlot(VillagerProfession profession, int level) {
		return profession == VillagerProfession.MASON && (level == 1 || level == 2);
	}

	/** {@code count} of an item for some emeralds, with vanilla's usual 0.05 price change from reputation. */
	private static VillagerTrades.ItemListing sells(Item item, int emeralds, int count, int maxUses, int xp) {
		return (trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), new ItemStack(item, count), maxUses, xp, 0.05F);
	}
}
