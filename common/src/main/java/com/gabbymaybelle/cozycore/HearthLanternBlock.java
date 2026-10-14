package com.gabbymaybelle.cozycore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A vanilla lantern with a block entity, so the world knows where every Hearth Lantern is. */
public class HearthLanternBlock extends LanternBlock implements EntityBlock {
	public HearthLanternBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HearthLanternBlockEntity(pos, state);
	}
}
