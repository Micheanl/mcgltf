package com.micheanl.mcgltf.entity;

import com.micheanl.mcgltf.block.ModelObjects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public final class ModelEntity extends Entity {
	private static final EntityDataAccessor<String> DATA_SOURCE = SynchedEntityData.defineId(ModelEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(ModelEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_ROTATION_X = SynchedEntityData.defineId(ModelEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_ROTATION_Y = SynchedEntityData.defineId(ModelEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_ROTATION_Z = SynchedEntityData.defineId(ModelEntity.class, EntityDataSerializers.FLOAT);
	private static final String SOURCE = "Source";
	private static final String SCALE = "Scale";
	private static final String ROTATION_X = "RotationX";
	private static final String ROTATION_Y = "RotationY";
	private static final String ROTATION_Z = "RotationZ";
	private static final double GRAVITY = 0.04;
	private static final double AIR_DRAG = 0.98;

	private static Consumer<ModelEntity> syncListener = entity -> {
	};

	public ModelEntity(EntityType<? extends ModelEntity> type, Level level) {
		super(type, level);
	}

	public static void syncListener(Consumer<ModelEntity> listener) {
		syncListener = listener;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		Vec3 motion = isNoGravity() ? getDeltaMovement() : getDeltaMovement().add(0.0, -GRAVITY, 0.0);
		move(MoverType.SELF, motion);
		setDeltaMovement(onGround() ? Vec3.ZERO : motion.scale(AIR_DRAG));
	}

	public void configure(String source, float scale, float rotationX, float rotationY, float rotationZ) {
		SynchedEntityData data = getEntityData();
		data.set(DATA_SOURCE, source);
		data.set(DATA_SCALE, scale);
		data.set(DATA_ROTATION_X, rotationX);
		data.set(DATA_ROTATION_Y, rotationY);
		data.set(DATA_ROTATION_Z, rotationZ);
	}

	public String source() {
		return getEntityData().get(DATA_SOURCE);
	}

	public float modelScale() {
		return getEntityData().get(DATA_SCALE);
	}

	public float rotationX() {
		return getEntityData().get(DATA_ROTATION_X);
	}

	public float rotationY() {
		return getEntityData().get(DATA_ROTATION_Y);
	}

	public float rotationZ() {
		return getEntityData().get(DATA_ROTATION_Z);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SOURCE, "");
		builder.define(DATA_SCALE, 1.0f);
		builder.define(DATA_ROTATION_X, 0.0f);
		builder.define(DATA_ROTATION_Y, 0.0f);
		builder.define(DATA_ROTATION_Z, 0.0f);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (level().isClientSide() && DATA_SOURCE.equals(accessor)) {
			syncListener.accept(this);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		configure(input.getStringOr(SOURCE, ""), input.getFloatOr(SCALE, 1.0f),
				input.getFloatOr(ROTATION_X, 0.0f), input.getFloatOr(ROTATION_Y, 0.0f), input.getFloatOr(ROTATION_Z, 0.0f));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString(SOURCE, source());
		output.putFloat(SCALE, modelScale());
		output.putFloat(ROTATION_X, rotationX());
		output.putFloat(ROTATION_Y, rotationY());
		output.putFloat(ROTATION_Z, rotationZ());
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}
}
