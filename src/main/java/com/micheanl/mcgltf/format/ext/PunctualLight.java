package com.micheanl.mcgltf.format.ext;

public record PunctualLight(
		LightType type,
		float[] color,
		float intensity,
		float range,
		float innerConeAngle,
		float outerConeAngle,
		String name) {
}
