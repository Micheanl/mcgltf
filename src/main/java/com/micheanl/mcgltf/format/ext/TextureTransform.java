package com.micheanl.mcgltf.format.ext;

public record TextureTransform(
		float offsetU,
		float offsetV,
		float rotation,
		float scaleU,
		float scaleV,
		Integer texCoord) {
}
