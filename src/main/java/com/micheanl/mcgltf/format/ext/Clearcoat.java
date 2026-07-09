package com.micheanl.mcgltf.format.ext;

import com.micheanl.mcgltf.format.GltfDocument;

public record Clearcoat(
		float factor,
		float roughness,
		GltfDocument.TextureInfo factorTexture,
		GltfDocument.TextureInfo roughnessTexture,
		GltfDocument.NormalTextureInfo normalTexture) {
}
