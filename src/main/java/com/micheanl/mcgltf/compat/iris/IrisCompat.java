package com.micheanl.mcgltf.compat.iris;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class IrisCompat {
	private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("iris");

	private IrisCompat() {
	}

	public static boolean available() {
		return AVAILABLE;
	}

	public static boolean shaderPackActive() {
		return AVAILABLE && IrisInterop.shaderPackActive();
	}

	public static void copyEntity(RenderPipeline solid, RenderPipeline cutout, RenderPipeline translucent) {
		if (AVAILABLE) {
			IrisInterop.copyEntity(solid, cutout, translucent);
		}
	}

	public static void registerPbr(AbstractTexture albedo, AbstractTexture normal, AbstractTexture specular) {
		if (AVAILABLE) {
			IrisPbrTextures.register(((GlTexture) albedo.getTexture()).glId(), normal, specular);
		}
	}

	public static void unregisterPbr(AbstractTexture albedo) {
		if (AVAILABLE) {
			IrisPbrTextures.unregister(((GlTexture) albedo.getTexture()).glId());
		}
	}
}
