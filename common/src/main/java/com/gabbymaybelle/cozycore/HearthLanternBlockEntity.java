package com.gabbymaybelle.cozycore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds nothing and never ticks. The chunk tells it when it comes into the world (placed, or its
 * chunk loaded) and when it leaves (broken, or its chunk unloaded), and it passes that on to
 * {@link HearthLanterns}. The chunk file is the only record of where lanterns are.
 */
public class HearthLanternBlockEntity extends BlockEntity {
	public HearthLanternBlockEntity(BlockPos pos, BlockState state) {
		super(HearthLantern.BLOCK_ENTITY.get(), pos, state);
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (getLevel() instanceof ServerLevel level) {
			HearthLanterns.add(level, getBlockPos());
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (getLevel() instanceof ServerLevel level) {
			HearthLanterns.remove(level, getBlockPos());
		}
	}
}
