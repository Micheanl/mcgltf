package com.micheanl.mcgltf.compat.iris;

import net.irisshaders.iris.pbr.texture.PBRTextureHolder;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IrisPbrTextures {
	private static final Map<Integer, PBRTextureHolder> HOLDERS = new ConcurrentHashMap<>();

	private IrisPbrTextures() {
	}

	public static void register(int albedoGlId, AbstractTexture normal, AbstractTexture specular) {
		HOLDERS.put(albedoGlId, new Holder(normal, specular));
	}

	public static void unregister(int albedoGlId) {
		HOLDERS.remove(albedoGlId);
	}

	public static PBRTextureHolder get(int albedoGlId) {
		return HOLDERS.get(albedoGlId);
	}

	private record Holder(AbstractTexture normalTexture, AbstractTexture specularTexture) implements PBRTextureHolder {
	}
}
