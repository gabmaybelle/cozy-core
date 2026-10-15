package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.KeepOnDeath;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The {@code keep_hotbar_on_death} rule, second half: a player carries the set-aside items from
 * dying to respawning. They're saved with the player (so logging out while dead loses nothing)
 * and handed to the respawned player as vanilla copies everything else across.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements KeepOnDeath.Holder {
	@Unique
	private List<KeepOnDeath.Kept> cozyCore$keptItems;

	@Override
	public List<KeepOnDeath.Kept> cozyCore$keptItems() {
		if (cozyCore$keptItems == null) {
			cozyCore$keptItems = new ArrayList<>();
		}
		return cozyCore$keptItems;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void cozyCore$saveKeptItems(ValueOutput output, CallbackInfo ci) {
		KeepOnDeath.save((ServerPlayer) (Object) this, output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void cozyCore$loadKeptItems(ValueInput input, CallbackInfo ci) {
		KeepOnDeath.load((ServerPlayer) (Object) this, input);
	}

	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void cozyCore$handBackKeptItems(ServerPlayer dead, boolean keepEverything, CallbackInfo ci) {
		KeepOnDeath.handBack(dead, (ServerPlayer) (Object) this);
	}
}
