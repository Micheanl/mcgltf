package com.micheanl.mcgltf.format.ext;

import com.micheanl.mcgltf.format.GltfDocument;

public record Sheen(
		float[] colorFactor,
		float roughness,
		GltfDocument.TextureInfo colorTexture,
		GltfDocument.TextureInfo roughnessTexture) {
}
