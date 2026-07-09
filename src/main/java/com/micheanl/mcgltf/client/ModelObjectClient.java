package com.micheanl.mcgltf.client;

import com.micheanl.mcgltf.animation.Animator;
import com.micheanl.mcgltf.asset.LoadResult;
import com.micheanl.mcgltf.asset.ModelLoader;
import com.micheanl.mcgltf.block.ModelBlockEntity;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.entity.ModelEntity;
import com.micheanl.mcgltf.render.GpuModel;
import com.micheanl.mcgltf.render.SceneRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelObjectClient {
	private static final Map<Integer, ModelEntity> TRACKED = new ConcurrentHashMap<>();

	private ModelObjectClient() {
	}

	public static void install() {
		ModelBlockEntity.syncListener(ModelObjectClient::mirrorBlock);
		ModelEntity.syncListener(ModelObjectClient::mirrorEntity);

		ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof ModelBlockEntity entity) {
				mirrorBlock(entity);
			}
		});
		ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> {
			if (blockEntity instanceof ModelBlockEntity) {
				SceneRegistry.removeManaged(blockEntity.getBlockPos());
			}
		});
		ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ModelEntity model) {
				mirrorEntity(model);
			}
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ModelEntity) {
				TRACKED.remove(entity.getId());
				SceneRegistry.removeManaged(entity.getId());
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> trackEntities());
	}

	private static void trackEntities() {
		for (ModelEntity entity : TRACKED.values()) {
			SceneRegistry.Instance instance = SceneRegistry.managed(entity.getId());
			if (instance != null) {
				instance.position(entity.getX(), entity.getY(), entity.getZ());
			}
		}
	}

	private static void mirrorBlock(ModelBlockEntity entity) {
		mirror(entity.getBlockPos().immutable(), entity.source(),
				entity.getBlockPos().getX(), entity.getBlockPos().getY(), entity.getBlockPos().getZ(),
				entity.scale(), entity.rotationX(), entity.rotationY(), entity.rotationZ());
	}

	private static void mirrorEntity(ModelEntity entity) {
		TRACKED.put(entity.getId(), entity);
		mirror(entity.getId(), entity.source(), entity.getX(), entity.getY(), entity.getZ(),
				entity.modelScale(), entity.rotationX(), entity.rotationY(), entity.rotationZ());
	}

	private static void mirror(Object key, String source, double x, double y, double z,
			float scale, float rotationX, float rotationY, float rotationZ) {
		if (source == null || source.isEmpty() || SceneRegistry.hasManaged(key)) {
			return;
		}
		Path path = EditorConfig.directory().resolve(source);
		if (!Files.isRegularFile(path)) {
			return;
		}
		ModelLoader.loadAsync(path).whenComplete((result, error) ->
				Minecraft.getInstance().execute(() -> {
					if (error != null || !(result instanceof LoadResult.Success ok) || SceneRegistry.hasManaged(key)) {
						return;
					}
					try {
						GpuModel model = GpuModel.upload(ok.model());
						SceneRegistry.Instance instance = new SceneRegistry.Instance(model, source, x, y, z, scale, new Animator(model.model()));
						instance.rotation(rotationX, rotationY, rotationZ);
						SceneRegistry.putManaged(key, instance);
					} catch (RuntimeException ignored) {
					}
				}));
	}
}
