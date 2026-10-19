package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.gabbymaybelle.cozycore.Rules;
import com.gabbymaybelle.cozycore.SafeAndSound;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An explosion set off by a mob in the {@code cozy_core:explosions_spare_blocks} tag (creepers and
 * ghasts; a ghast's fireball counts through the ghast that threw it) breaks no blocks at home:
 * the blocks inside a Hearth Lantern's reach come off its list, so a blast at the edge leaves a
 * crater that stops where home begins. With {@code cozy_core:mob_explosions_break_blocks} off it
 * breaks no blocks anywhere. Either way it still happens, so it still hurts. TNT, end crystals,
 * beds and the wither are vanilla. This hooks the step that breaks the blocks rather than the one
 * that finds them, which performance mods replace.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Unique
	private static final TagKey<EntityType<?>> COZY_CORE$SPARES_BLOCKS = TagKey.create(Registries.ENTITY_TYPE, CozyCore.id("explosions_spare_blocks"));

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void cozyCore$spareBlocks(List<BlockPos> blocks, CallbackInfo ci) {
		ServerExplosion explosion = (ServerExplosion) (Object) this;
		if (!cozyCore$spares(explosion.getDirectSourceEntity()) && !cozyCore$spares(explosion.getIndirectSourceEntity())) {
			return;
		}
		ServerLevel level = explosion.level();
		BlockPos centre = BlockPos.containing(explosion.center());
		if (HearthLanterns.guards(level, centre)) {
			SafeAndSound.award(level, explosion.center());
		}
		if (!Rules.is(level, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS)) {
			blocks.clear();
		} else if (HearthLanterns.any(level) && Rules.is(level, Rules.HEARTHS_PROTECT_BUILDS)) {
			blocks.removeIf(pos -> HearthLanterns.guards(level, pos));
		}
	}

	@Unique
	private static boolean cozyCore$spares(Entity entity) {
		return entity != null && entity.typeHolder().is(COZY_CORE$SPARES_BLOCKS);
	}
}
