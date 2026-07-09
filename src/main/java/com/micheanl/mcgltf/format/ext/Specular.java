package com.micheanl.mcgltf.format.ext;

import com.micheanl.mcgltf.format.GltfDocument;

public record Specular(
		float factor,
		float[] colorFactor,
		GltfDocument.TextureInfo factorTexture,
		GltfDocument.TextureInfo colorTexture) {
}
