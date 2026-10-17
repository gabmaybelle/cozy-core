package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.Trades;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * One more offer when a mason reaches level 1 or 2 (three instead of two), so Cozy Core's builder
 * stone joins the pool without pushing vanilla's trades out.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin {
	@ModifyArg(method = "updateTrades", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/npc/Villager;addOffersFromItemListings(Lnet/minecraft/world/item/trading/MerchantOffers;[Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;I)V"),
			index = 2)
	private int cozyCore$oneMoreOffer(int count) {
		VillagerData data = ((Villager) (Object) this).getVillagerData();
		return Trades.hasExtraSlot(data.getProfession(), data.getLevel()) ? count + Trades.EXTRA_SLOTS : count;
	}
}
