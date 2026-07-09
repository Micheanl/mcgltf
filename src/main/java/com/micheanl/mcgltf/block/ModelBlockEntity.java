package com.micheanl.mcgltf.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Consumer;

public final class ModelBlockEntity extends BlockEntity {
	private static final String SOURCE = "Source";
	private static final String SCALE = "Scale";
	private static final String ROTATION_X = "RotationX";
	private static final String ROTATION_Y = "RotationY";
	private static final String ROTATION_Z = "RotationZ";
	private static final int BLOCK_UPDATE_FLAGS = 3;

	private static Consumer<ModelBlockEntity> syncListener = entity -> {
	};

	private String source = "";
	private float scale = 1.0f;
	private float rotationX;
	private float rotationY;
	private float rotationZ;

	public ModelBlockEntity(BlockPos worldPosition, BlockState blockState) {
		super(ModelObjects.MODEL_BLOCK_ENTITY, worldPosition, blockState);
	}

	public static void syncListener(Consumer<ModelBlockEntity> listener) {
		syncListener = listener;
	}

	public String source() {
		return source;
	}

	public float scale() {
		return scale;
	}

	public float rotationX() {
		return rotationX;
	}

	public float rotationY() {
		return rotationY;
	}

	public float rotationZ() {
		return rotationZ;
	}

	public void configure(String source, float scale, float rotationX, float rotationY, float rotationZ) {
		this.source = source;
		this.scale = scale;
		this.rotationX = rotationX;
		this.rotationY = rotationY;
		this.rotationZ = rotationZ;
		setChanged();
		Level level = getLevel();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), BLOCK_UPDATE_FLAGS);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		output.putString(SOURCE, source);
		output.putFloat(SCALE, scale);
		output.putFloat(ROTATION_X, rotationX);
		output.putFloat(ROTATION_Y, rotationY);
		output.putFloat(ROTATION_Z, rotationZ);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		source = input.getStringOr(SOURCE, "");
		scale = input.getFloatOr(SCALE, 1.0f);
		rotationX = input.getFloatOr(ROTATION_X, 0.0f);
		rotationY = input.getFloatOr(ROTATION_Y, 0.0f);
		rotationZ = input.getFloatOr(ROTATION_Z, 0.0f);
		Level level = getLevel();
		if (level != null && level.isClientSide()) {
			syncListener.accept(this);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		String value = components.get(ModelObjects.MODEL_SOURCE);
		if (value != null) {
			source = value;
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		components.set(ModelObjects.MODEL_SOURCE, source);
	}
}
