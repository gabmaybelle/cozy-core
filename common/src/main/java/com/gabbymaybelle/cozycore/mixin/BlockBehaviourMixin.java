package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.HomeComforts;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Glass, ice and bookshelves ({@code cozy_core:keeps_at_home}) broken at home drop themselves, as
 * with Silk Touch: glass comes back instead of shattering, a bookshelf instead of three books.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {
	@Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
	private void cozyCore$keepAtHome(BlockState state, LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
		if (!state.is(HomeComforts.KEEPS_AT_HOME)) {
			return;
		}
		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin != null && HearthLanterns.protects(params.getLevel(), BlockPos.containing(origin))) {
			cir.setReturnValue(List.of(new ItemStack(state.getBlock())));
		}
	}
}
