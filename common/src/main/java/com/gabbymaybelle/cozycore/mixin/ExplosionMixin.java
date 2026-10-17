package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.CozyCore;
import com.gabbymaybelle.cozycore.Rules;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With {@code cozy_core:mob_explosions_break_blocks} off, an explosion set off by a mob in the
 * {@code cozy_core:explosions_spare_blocks} tag (creepers and ghasts; a ghast's fireball counts
 * through the ghast that threw it) breaks no blocks and lights no fires. It still happens, so it
 * still hurts. TNT, end crystals, beds and the wither are vanilla. This hooks the step that breaks
 * the blocks rather than the one that finds them, which performance mods replace; the server sends
 * players the emptied list, so their game breaks nothing either.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	@Unique
	private static final TagKey<EntityType<?>> COZY_CORE$SPARES_BLOCKS = TagKey.create(Registries.ENTITY_TYPE, CozyCore.id("explosions_spare_blocks"));

	@Shadow
	@Final
	private Level level;

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void cozyCore$spareBlocks(boolean spawnParticles, CallbackInfo ci) {
		Explosion explosion = (Explosion) (Object) this;
		if (level instanceof ServerLevel server && !Rules.is(server, Rules.MOB_EXPLOSIONS_BREAK_BLOCKS)
				&& (cozyCore$spares(explosion.getDirectSourceEntity()) || cozyCore$spares(explosion.getIndirectSourceEntity()))) {
			explosion.getToBlow().clear();
		}
	}

	@Unique
	private static boolean cozyCore$spares(Entity entity) {
		return entity != null && entity.getType().is(COZY_CORE$SPARES_BLOCKS);
	}
}
