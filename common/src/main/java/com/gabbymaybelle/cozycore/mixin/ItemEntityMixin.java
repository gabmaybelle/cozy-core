package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A dropped item at home lasts {@code cozy_core:hearth_item_minutes} (an hour in new worlds)
 * instead of vanilla's five minutes. Vanilla removes an item once its age reaches 6000 ticks, and
 * saves the age as a short (at most about 27 minutes), so instead of letting it grow, an item at
 * home that's about to go is set back a minute, and the minutes it was given are kept beside it
 * (and saved with it) until it has had its full time. The check runs only when an item is about
 * to go, so at most once a minute per item.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@Unique
	private static final String COZY_CORE$EXTRA = "cozy_core_extra_ticks";
	@Unique
	private static final int COZY_CORE$LIFETIME = 6000, COZY_CORE$MINUTE = 1200;

	@Shadow
	private int age;

	/** Ticks this item has been given back at home so far. */
	@Unique
	private int cozyCore$extra;

	@Inject(method = "tick", at = @At("HEAD"))
	private void cozyCore$lastLongerAtHome(CallbackInfo ci) {
		ItemEntity item = (ItemEntity) (Object) this;
		if (age < COZY_CORE$LIFETIME - 1 || !(item.level() instanceof ServerLevel level)) {
			return;
		}
		int lifetime = Rules.get(level, Rules.HEARTH_ITEM_MINUTES) * COZY_CORE$MINUTE;
		// Vanilla would remove it at the end of this tick, having lived 6000 ticks plus what it was
		// given; another minute only if that's still short of its time at home.
		if (COZY_CORE$LIFETIME + cozyCore$extra < lifetime && HearthLanterns.protects(level, item.blockPosition())) {
			age -= COZY_CORE$MINUTE;
			cozyCore$extra += COZY_CORE$MINUTE;
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void cozyCore$saveExtra(CompoundTag tag, CallbackInfo ci) {
		if (cozyCore$extra > 0) {
			tag.putInt(COZY_CORE$EXTRA, cozyCore$extra);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void cozyCore$readExtra(CompoundTag tag, CallbackInfo ci) {
		cozyCore$extra = tag.getInt(COZY_CORE$EXTRA);
	}
}
