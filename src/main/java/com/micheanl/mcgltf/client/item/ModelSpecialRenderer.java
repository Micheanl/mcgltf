package com.micheanl.mcgltf.client.item;

import com.micheanl.mcgltf.block.ModelObjects;
import com.micheanl.mcgltf.client.ModelCache;
import com.micheanl.mcgltf.compat.iris.IrisEntityRenderType;
import com.micheanl.mcgltf.render.GpuModel;
import com.micheanl.mcgltf.render.WorldRenderer;
import com.micheanl.mcgltf.scene.Model.AlphaMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

public final class ModelSpecialRenderer implements SpecialModelRenderer<String> {
	private static final float ITEM_FILL = 0.6f;

	@Override
	public String extractArgument(ItemStack stack) {
		String source = stack.get(ModelObjects.MODEL_SOURCE);
		if (source == null || source.isEmpty()) {
			return null;
		}
		if (ModelCache.get(source) == null) {
			ModelCache.request(source);
			return null;
		}
		return source;
	}

	@Override
	public void submit(String source, PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
		if (source == null || source.isEmpty()) {
			return;
		}
		GpuModel model = ModelCache.get(source);
		if (model == null) {
			return;
		}
		float[] aabb = model.aabb();
		float dx = aabb[3] - aabb[0];
		float dy = aabb[4] - aabb[1];
		float dz = aabb[5] - aabb[2];
		float centerX = (aabb[0] + aabb[3]) * 0.5f;
		float centerY = (aabb[1] + aabb[4]) * 0.5f;
		float centerZ = (aabb[2] + aabb[5]) * 0.5f;
		float extent = Math.max(dx, Math.max(dy, dz));
		float norm = extent > 1.0e-4f ? ITEM_FILL / extent : ITEM_FILL;
		poseStack.pushPose();
		poseStack.translate(0.5f, 0.5f, 0.5f);
		poseStack.scale(norm, norm, norm);
		poseStack.translate(-centerX, -centerY, -centerZ);
		for (GpuModel.Part part : model.parts()) {
			GpuModel.Material material = model.materials()[part.material()];
			RenderType renderType = material.alphaMode() == AlphaMode.MASK
					? IrisEntityRenderType.cutout(material.textureId())
					: IrisEntityRenderType.solid(material.textureId());
			collector.submitCustomGeometry(poseStack, renderType,
					(pose, buffer) -> WorldRenderer.emit(part.prim(), pose, buffer, lightCoords));
		}
		poseStack.popPose();
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		output.accept(new Vector3f(0.0f, 0.0f, 0.0f));
		output.accept(new Vector3f(1.0f, 1.0f, 1.0f));
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<String> {
		public static final MapCodec<ModelSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new ModelSpecialRenderer.Unbaked());

		@Override
		public MapCodec<ModelSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<String> bake(SpecialModelRenderer.BakingContext context) {
			return new ModelSpecialRenderer();
		}
	}
}
