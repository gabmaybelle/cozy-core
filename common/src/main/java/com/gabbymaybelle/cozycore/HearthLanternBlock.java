package com.gabbymaybelle.cozycore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vanilla lantern with a block entity, so the world knows where every Hearth Lantern is. It
 * also gets random ticks, which now and then bring a visitor in the evening ({@link HomeLife}).
 */
public class HearthLanternBlock extends LanternBlock implements EntityBlock {
	public HearthLanternBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HearthLanternBlockEntity(pos, state);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		HomeLife.tickLantern(level, pos, random, HomeLife.VISIT_CHANCE);
	}
}
