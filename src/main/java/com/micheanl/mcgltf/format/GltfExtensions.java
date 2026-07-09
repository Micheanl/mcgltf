package com.micheanl.mcgltf.format;

import java.util.Set;

public final class GltfExtensions {
	public static final String UNLIT = "KHR_materials_unlit";
	public static final String EMISSIVE_STRENGTH = "KHR_materials_emissive_strength";
	public static final String TEXTURE_TRANSFORM = "KHR_texture_transform";
	public static final String INDEX_OF_REFRACTION = "KHR_materials_ior";
	public static final String SPECULAR = "KHR_materials_specular";
	public static final String CLEARCOAT = "KHR_materials_clearcoat";
	public static final String SHEEN = "KHR_materials_sheen";
	public static final String LIGHTS_PUNCTUAL = "KHR_lights_punctual";
	public static final String MATERIALS_VARIANTS = "KHR_materials_variants";
	public static final String TEXTURE_BASISU = "KHR_texture_basisu";
	public static final String DRACO = "KHR_draco_mesh_compression";
	public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
			UNLIT, EMISSIVE_STRENGTH, TEXTURE_TRANSFORM, INDEX_OF_REFRACTION, SPECULAR,
			CLEARCOAT, SHEEN, LIGHTS_PUNCTUAL, MATERIALS_VARIANTS, TEXTURE_BASISU);

	private GltfExtensions() {
	}
}
