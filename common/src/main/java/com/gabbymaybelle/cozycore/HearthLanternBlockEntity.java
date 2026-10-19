package com.gabbymaybelle.cozycore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Never ticks. The chunk tells it when it comes into the world (placed, or its chunk loaded) and
 * when it leaves (broken, or its chunk unloaded), and it passes that on to {@link HearthLanterns},
 * on the server and on each player's computer alike. The chunk file is the only record of where
 * lanterns are. It keeps the name the lantern was given on an anvil, which is the home's name, and
 * sends players that name and the reach (the server's {@code hearth_lantern_radius}), since game
 * rules don't reach players' computers; the "Home" sign and the edge glow use both.
 */
public class HearthLanternBlockEntity extends BlockEntity {
	private static final String NAME = "CustomName";
	/** Only in what's sent to players, never saved: the reach the server had when it sent the chunk. */
	private static final String REACH = "cozy_core_reach";

	private @Nullable Component name;
	private int reach = -1;

	public HearthLanternBlockEntity(BlockPos pos, BlockState state) {
		super(HearthLantern.BLOCK_ENTITY.get(), pos, state);
	}

	/** The home's name, or null for plain "Home". */
	public @Nullable Component name() {
		return name;
	}

	/** On a player's computer: how far this lantern reaches, as the server last said (-1 before it has). */
	public int reach() {
		return reach;
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (getLevel() != null) {
			HearthLanterns.add(getLevel(), this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (getLevel() != null) {
			HearthLanterns.remove(getLevel(), getBlockPos());
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (name != null) {
			tag.putString(NAME, Component.Serializer.toJson(name, registries));
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		name = tag.contains(NAME, CompoundTag.TAG_STRING) ? parseCustomNameSafe(tag.getString(NAME), registries) : null;
		if (tag.contains(REACH, CompoundTag.TAG_INT)) {
			reach = tag.getInt(REACH);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = saveWithoutMetadata(registries);
		if (getLevel() instanceof ServerLevel level) {
			tag.putInt(REACH, Rules.get(level, Rules.HEARTH_LANTERN_RADIUS));
		}
		return tag;
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
		super.applyImplicitComponents(components);
		name = components.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(DataComponents.CUSTOM_NAME, name);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void removeComponentsFromTag(CompoundTag tag) {
		tag.remove(NAME);
	}
}
