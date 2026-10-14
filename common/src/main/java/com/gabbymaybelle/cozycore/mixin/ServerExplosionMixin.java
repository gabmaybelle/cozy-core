package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.Rules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
 * With {@code cozy_core:mob_explosions_break_blocks} off, an explosion set off by a mob in the
 * {@code cozy_core:explosions_spare_blocks} tag (creepers and ghasts; a ghast's fireball counts
 * through the ghast that threw it) breaks no blocks. It still happens, so it still hurts. TNT,
 * end crystals, beds and the wither are vanilla. This hooks the step that breaks the blocks rather
 * than the one that finds them, which performance mods replace.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Unique
	private static final TagKey<EntityType<?>> COZY_CORE$SPARES_BLOCKS = TagKey.create(Registries.ENTITY_TYPE, CozyCore.id("explosions_spare_blocks"));

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void cozyCore$spareBlocks(List<BlockPos> blocks, CallbackInfo ci) {
		ServerExplosion explosion = (ServerExplosion) (Object) this;
		if (!Rules.is(explosion.level(), Rules.MOB_EXPLOSIONS_BREAK_BLOCKS)
				&& (cozyCore$spares(explosion.getDirectSourceEntity()) || cozyCore$spares(explosion.getIndirectSourceEntity()))) {
			blocks.clear();
		}
	}

	@Unique
	private static boolean cozyCore$spares(Entity entity) {
		return entity != null && entity.typeHolder().is(COZY_CORE$SPARES_BLOCKS);
	}
}
