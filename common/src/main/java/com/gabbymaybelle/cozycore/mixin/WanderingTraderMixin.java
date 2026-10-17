package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.Trades;
import net.minecraft.world.entity.npc.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * One more common offer from a wandering trader (six instead of five), so Cozy Core's plants and
 * calcite join the pool without pushing vanilla's trades out.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixin {
	@ModifyArg(method = "updateTrades", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/npc/WanderingTrader;addOffersFromItemListings(Lnet/minecraft/world/item/trading/MerchantOffers;[Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;I)V"),
			index = 2)
	private int cozyCore$oneMoreOffer(int count) {
		return count + Trades.EXTRA_SLOTS;
	}
}
