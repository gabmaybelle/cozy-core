package com.gabbymaybelle.cozycore;

import com.gabbymaybelle.cozycore.platform.Services;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Hearth Lantern: a lantern (made from one, with four amethyst shards) that no monster spawns
 * naturally near. It hangs, stands and shines like vanilla's. How far it reaches is the
 * {@code cozy_core:hearth_lantern_radius} game rule; the check is in {@link HearthLanterns}.
 */
public final class HearthLantern {
	public static final ResourceLocation ID = CozyCore.id("hearth_lantern");
	public static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, ID);
	public static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);

	public static final Supplier<Block> BLOCK = Services.PLATFORM.register(Registries.BLOCK, ID,
			() -> new HearthLanternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LANTERN)));
	public static final Supplier<Item> ITEM = Services.PLATFORM.register(Registries.ITEM, ID,
			() -> new BlockItem(BLOCK.get(), new Item.Properties()));
	/** The block entity does nothing but tell {@link HearthLanterns} when a lantern is in a loaded chunk. */
	public static final Supplier<BlockEntityType<?>> BLOCK_ENTITY = Services.PLATFORM.register(Registries.BLOCK_ENTITY_TYPE, ID,
			() -> BlockEntityType.Builder.of(HearthLanternBlockEntity::new, BLOCK.get()).build(null));

	private HearthLantern() {
	}

	/** Touching the class registers the block; this puts it in the creative menu next to the lanterns. */
	public static void register() {
		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("functional_blocks"));
		Services.PLATFORM.addToCreativeTab(tab, ITEM);
	}
}
