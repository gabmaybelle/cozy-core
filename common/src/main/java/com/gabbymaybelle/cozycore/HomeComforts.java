package com.gabbymaybelle.cozycore;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

/**
 * 0.6.0's "Home comforts", the small kindnesses of being at home (inside a Hearth Lantern's reach).
 * The hooks are in {@code mixin/}; this says who and what they're for (Gabby's calls, 2026-10-19):
 * <ul>
 * <li>Animals: every passive animal and pet, and villagers, are spared creeper and ghast blasts,
 * fire and lightning at home, and lightning doesn't turn them into something else.</li>
 * <li>Footing: players and their tamed pets take no fall damage at home.</li>
 * <li>Glass, ice and bookshelves ({@link #KEEPS_AT_HOME}) drop themselves when broken at home.</li>
 * <li>Dropped items last {@code cozy_core:hearth_item_minutes} at home instead of five minutes.</li>
 * </ul>
 */
public final class HomeComforts {
	/** Blocks that drop themselves when broken at home, with or without Silk Touch. */
	public static final TagKey<Block> KEEPS_AT_HOME = TagKey.create(Registries.BLOCK, CozyCore.id("keeps_at_home"));
	/** The mobs whose blasts break nothing at home (creepers, ghasts); the same list spares animals. */
	private static final TagKey<EntityType<?>> SPARING_BLASTS = TagKey.create(Registries.ENTITY_TYPE, CozyCore.id("explosions_spare_blocks"));

	private HomeComforts() {
	}

	/** Every passive animal and pet, and villagers. Hoglins are animals by class, but they're monsters. */
	public static boolean isHomeAnimal(Entity entity) {
		return entity instanceof Animal && !(entity instanceof Enemy) || entity instanceof AbstractVillager;
	}

	/** A player, or a pet someone has tamed. */
	public static boolean isPlayerOrPet(Entity entity) {
		return entity instanceof Player
				|| entity instanceof TamableAnimal pet && pet.isTame()
				|| entity instanceof AbstractHorse horse && horse.isTamed();
	}

	/**
	 * Whether this damage is one an animal or villager at home is spared: a creeper's or ghast's
	 * blast, fire (and lava), or lightning, while {@code cozy_core:hearths_protect_builds} is on.
	 */
	public static boolean sparesAnimal(ServerLevel level, Entity entity, DamageSource source) {
		if (!isHomeAnimal(entity)) {
			return false;
		}
		boolean blast = source.is(DamageTypeTags.IS_EXPLOSION) && (fromSparingMob(source.getEntity()) || fromSparingMob(source.getDirectEntity()));
		boolean kind = blast || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_LIGHTNING);
		return kind && HearthLanterns.guards(level, entity.blockPosition());
	}

	private static boolean fromSparingMob(Entity entity) {
		return entity != null && entity.getType().is(SPARING_BLASTS);
	}
}
