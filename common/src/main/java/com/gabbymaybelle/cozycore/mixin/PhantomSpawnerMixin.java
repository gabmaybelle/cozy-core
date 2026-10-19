package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Phantoms don't spawn over a player who is at home, inside a Hearth Lantern's reach (Gabby's
 * call, 2026-10-19: in place of 0.4.0's phantoms off everywhere). Vanilla goes through the players
 * one by one and only spawns phantoms over one standing under the open sky at sea level or
 * higher; for a player at home, the spot it checks is moved to the bottom of the world, so that
 * check says no and the next player is looked at. NeoForge passes the same spot to its phantom
 * event, whose default check is the same. A phantom that spawned while you were out can still
 * follow you home.
 *
 * <p>On 26.3 this is a redirect of the spectator check; here it can't be: Loom remaps a mixin's
 * annotations in place on 1.21.1 and writes a redirect's single {@code at} as a list, which
 * MixinExtras' redirect handling (bundled with Fabric Loader) can't read, and the Fabric server
 * crashes loading this class (found 2026-10-19).
 */
@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
	@ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
	private BlockPos cozyCore$passOverPlayersAtHome(BlockPos playerPos, ServerLevel level) {
		return HearthLanterns.protects(level, playerPos) ? playerPos.atY(level.getMinBuildHeight()) : playerPos;
	}
}
