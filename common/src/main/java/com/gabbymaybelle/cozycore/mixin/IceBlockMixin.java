package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ice broken at home drops itself ({@link BlockBehaviourMixin}), so it doesn't also leave water
 * behind: the melting step after the drop is skipped there.
 */
@Mixin(IceBlock.class)
public abstract class IceBlockMixin {
	@Inject(method = "playerDestroy", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;hasTag(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/tags/TagKey;)Z"),
			cancellable = true)
	private void cozyCore$noMeltingAtHome(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, BlockEntity blockEntity, ItemStack tool,
			CallbackInfo ci) {
		if (HearthLanterns.protects(level, pos)) {
			ci.cancel();
		}
	}
}
