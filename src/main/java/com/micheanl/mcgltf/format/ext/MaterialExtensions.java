package com.micheanl.mcgltf.format.ext;

public record MaterialExtensions(
		boolean unlit,
		float emissiveStrength,
		float ior,
		Specular specular,
		Clearcoat clearcoat,
		Sheen sheen) {
}
