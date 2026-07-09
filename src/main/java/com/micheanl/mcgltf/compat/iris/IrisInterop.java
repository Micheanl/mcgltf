package com.micheanl.mcgltf.compat.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.minecraft.client.renderer.RenderPipelines;

final class IrisInterop {
	private IrisInterop() {
	}

	static boolean shaderPackActive() {
		return IrisApi.getInstance().isShaderPackInUse();
	}

	static void copyEntity(RenderPipeline solid, RenderPipeline cutout, RenderPipeline translucent) {
		IrisPipelines.copyPipeline(RenderPipelines.ENTITY_SOLID, solid);
		IrisPipelines.copyPipeline(RenderPipelines.ENTITY_CUTOUT, cutout);
		IrisPipelines.copyPipeline(RenderPipelines.ENTITY_TRANSLUCENT, translucent);
	}
}
