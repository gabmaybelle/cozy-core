package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No monster spawns naturally near a Hearth Lantern. Only natural spawning goes through here:
 * spawners, raids and patrols are vanilla. Phantoms have their own spawner: PhantomSpawnerMixin.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	@Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"), cancellable = true)
	private static void cozyCore$hearthLanterns(ServerLevel level, MobCategory category, StructureManager structures, ChunkGenerator generator,
			MobSpawnSettings.SpawnerData data, BlockPos.MutableBlockPos pos, double distance, CallbackInfoReturnable<Boolean> cir) {
		if (category == MobCategory.MONSTER && HearthLanterns.protects(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
