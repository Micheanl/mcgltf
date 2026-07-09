package com.micheanl.mcgltf.client.thumbnail;

import com.micheanl.mcgltf.client.ModelCache;
import com.micheanl.mcgltf.compat.iris.IrisEntityRenderType;
import com.micheanl.mcgltf.render.GpuModel;
import com.micheanl.mcgltf.render.WorldRenderer;
import com.micheanl.mcgltf.scene.Model.AlphaMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.LightCoordsUtil;

public final class ThumbnailRenderer extends PictureInPictureRenderer<ThumbnailRenderState> {
	private static final float PITCH_DEGREES = 180.0f;

	public ThumbnailRenderer() {
	}

	@Override
	public Class<ThumbnailRenderState> getRenderStateClass() {
		return ThumbnailRenderState.class;
	}

	@Override
	protected String getTextureLabel() {
		return "mcgltf thumbnail";
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2.0f;
	}

	@Override
	protected void renderToTexture(ThumbnailRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector) {
		GpuModel model = ModelCache.get(renderState.source());
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
		float fit = Math.max((float) Math.sqrt(dx * dx + dz * dz), dy);
		float norm = fit > 1.0e-4f ? 1.0f / fit : 1.0f;
		poseStack.mulPose(Axis.ZP.rotationDegrees(PITCH_DEGREES));
		poseStack.mulPose(Axis.YP.rotationDegrees(renderState.angle()));
		poseStack.scale(norm, norm, norm);
		poseStack.translate(-centerX, -centerY, -centerZ);
		for (GpuModel.Part part : model.parts()) {
			GpuModel.Material material = model.materials()[part.material()];
			RenderType renderType = material.alphaMode() == AlphaMode.MASK
					? IrisEntityRenderType.cutout(material.textureId())
					: IrisEntityRenderType.solid(material.textureId());
			collector.submitCustomGeometry(poseStack, renderType,
					(pose, buffer) -> WorldRenderer.emit(part.prim(), pose, buffer, LightCoordsUtil.FULL_BRIGHT));
		}
	}
}
