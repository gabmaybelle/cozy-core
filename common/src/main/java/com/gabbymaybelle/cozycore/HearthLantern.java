package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
 * rule; the checks are in {@link HearthLanterns}. It comes in six styles, one per vanilla lantern
 * it can be made from; they all work the same. Renamed on an anvil, it gives the home its name.
 */
public final class HearthLantern {
	/** One look of the lantern. The ids, recipes and art are made by {@code tools/generate-art.js}, which lists the same styles. */
	public record Style(Identifier id, Supplier<Block> block, Supplier<Item> item, SimpleParticleType flame) {
	}

	public static final Style CLASSIC = style("hearth_lantern", Blocks.LANTERN, ParticleTypes.SMALL_FLAME);
	public static final List<Style> STYLES = List.of(
			CLASSIC,
			style("soul_hearth_lantern", Blocks.SOUL_LANTERN, ParticleTypes.SOUL_FIRE_FLAME),
			// The copper ones come in vanilla's four copper colours, but never weather.
			style("copper_hearth_lantern", Blocks.LANTERN, ParticleTypes.COPPER_FIRE_FLAME),
			style("exposed_copper_hearth_lantern", Blocks.LANTERN, ParticleTypes.COPPER_FIRE_FLAME),
			style("weathered_copper_hearth_lantern", Blocks.LANTERN, ParticleTypes.COPPER_FIRE_FLAME),
			style("oxidized_copper_hearth_lantern", Blocks.LANTERN, ParticleTypes.COPPER_FIRE_FLAME));

	public static final Identifier ID = CLASSIC.id();
	public static final Supplier<Block> BLOCK = CLASSIC.block();
	public static final Supplier<Item> ITEM = CLASSIC.item();
	/** The block entity does nothing but tell {@link HearthLanterns} when a lantern is in a loaded chunk, and carry its name. */
	public static final Supplier<BlockEntityType<?>> BLOCK_ENTITY = Services.PLATFORM.register(Registries.BLOCK_ENTITY_TYPE, ID,
			() -> new BlockEntityType<>(HearthLanternBlockEntity::new, STYLES.stream().map(style -> style.block().get()).collect(Collectors.toSet())));

	private HearthLantern() {
	}

	private static Style style(String name, Block copyOf, SimpleParticleType flame) {
		Identifier id = CozyCore.id(name);
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		// A full copy keeps the lantern's light (the soul one is dimmer, like vanilla's); the id
		// gives it its own name and loot table.
		Supplier<Block> block = Services.PLATFORM.register(Registries.BLOCK, id,
				() -> new HearthLanternBlock(BlockBehaviour.Properties.ofFullCopy(copyOf).setId(blockKey)));
		Supplier<Item> item = Services.PLATFORM.register(Registries.ITEM, id,
				() -> new BlockItem(block.get(), new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
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
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("functional_blocks"));
		for (Style style : STYLES) {
			Services.PLATFORM.addToCreativeTab(tab, style.item());
		}
	}
}
