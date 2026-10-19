package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Hearth Lantern: a lantern (made from one, with four amethyst shards) that makes the ground
 * around it home. No monster spawns naturally there, and while {@code cozy_core:hearths_protect_builds}
 * is on, creeper and ghast blasts, fire and endermen leave its blocks alone. It hangs, stands and
 * shines like vanilla's. How far it reaches is the {@code cozy_core:hearth_lantern_radius} game
 * rule; the checks are in {@link HearthLanterns}. On this branch it comes in two styles, classic
 * and soul (26.3 also has copper, which 1.21.1 doesn't have lanterns of); they work the same.
 * Renamed on an anvil, it gives the home its name.
 */
public final class HearthLantern {
	/** One look of the lantern. The ids, recipes and art are made by {@code tools/generate-art.js}, which lists the same styles. */
	public record Style(ResourceLocation id, Supplier<Block> block, Supplier<Item> item, SimpleParticleType flame) {
	}

	public static final Style CLASSIC = style("hearth_lantern", Blocks.LANTERN, ParticleTypes.SMALL_FLAME);
	public static final List<Style> STYLES = List.of(
			CLASSIC,
			style("soul_hearth_lantern", Blocks.SOUL_LANTERN, ParticleTypes.SOUL_FIRE_FLAME));

	public static final ResourceLocation ID = CLASSIC.id();
	public static final Supplier<Block> BLOCK = CLASSIC.block();
	public static final Supplier<Item> ITEM = CLASSIC.item();
	/** The block entity does nothing but tell {@link HearthLanterns} when a lantern is in a loaded chunk, and carry its name. */
	public static final Supplier<BlockEntityType<?>> BLOCK_ENTITY = Services.PLATFORM.register(Registries.BLOCK_ENTITY_TYPE, ID,
			() -> BlockEntityType.Builder.of(HearthLanternBlockEntity::new, STYLES.stream().map(style -> style.block().get()).toArray(Block[]::new)).build(null));

	private HearthLantern() {
	}

	private static Style style(String name, Block copyOf, SimpleParticleType flame) {
		ResourceLocation id = CozyCore.id(name);
		// A full copy keeps the lantern's light (the soul one is dimmer, like vanilla's); the block's
		// own id gives it its own name and loot table.
		Supplier<Block> block = Services.PLATFORM.register(Registries.BLOCK, id,
				() -> new HearthLanternBlock(BlockBehaviour.Properties.ofFullCopy(copyOf)));
		Supplier<Item> item = Services.PLATFORM.register(Registries.ITEM, id,
				() -> new BlockItem(block.get(), new Item.Properties()));
		return new Style(id, block, item, flame);
	}

	/** The style a lantern block is, for its particles. */
	public static Style styleOf(Block block) {
		for (Style style : STYLES) {
			if (style.block().get() == block) {
				return style;
			}
		}
		return CLASSIC;
	}

	/** Whether an item is one of the Hearth Lanterns. */
	public static boolean isHearth(Item item) {
		for (Style style : STYLES) {
			if (style.item().get() == item) {
				return true;
			}
		}
		return false;
	}

	/** Touching the class registers the blocks; this puts them in the creative menu next to the lanterns. */
	public static void register() {
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("functional_blocks"));
		for (Style style : STYLES) {
			Services.PLATFORM.addToCreativeTab(tab, style.item());
		}
	}
}
