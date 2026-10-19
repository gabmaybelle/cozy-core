package com.gabbymaybelle.cozycore.mixin;

import com.gabbymaybelle.cozycore.HearthLanterns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Phantoms don't spawn over a player who is at home, inside a Hearth Lantern's reach (Gabby's
 * call, 2026-10-19: in place of 0.4.0's phantoms off everywhere). Vanilla goes through the players
 * one by one and passes over spectators; a player at home is passed over the same way, so out in
 * the wild phantoms come as usual. NeoForge keeps that spectator check around its own phantom
 * event, so this one hook works on both loaders. A phantom that spawned while you were out can
 * still follow you home.
 */
@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
	private boolean cozyCore$passOverPlayersAtHome(ServerPlayer player) {
		return player.isSpectator() || HearthLanterns.protects(player.level(), player.blockPosition());
	}
}
