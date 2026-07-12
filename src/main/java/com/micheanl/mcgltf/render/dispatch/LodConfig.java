package com.micheanl.mcgltf.render.dispatch;

import com.micheanl.mcgltf.config.EditorConfig;

public record LodConfig(float renderDist, float animationDist, float transparencyDist) {

	private static final float[] TERRAIN_MESH_LOD_DISTS = {0.0f, 96.0f, 256.0f};

	public int meshLod(float distance) {
		for (int i = TERRAIN_MESH_LOD_DISTS.length - 1; i >= 0; i--) {
			if (distance >= TERRAIN_MESH_LOD_DISTS[i]) {
				return i;
			}
		}
		return 0;
	}

	public boolean useSimpleShader(float distance) {
		return distance >= TERRAIN_MESH_LOD_DISTS[1];
	}

	public static LodConfig of(RenderClass rc) {
		return switch (rc) {
			case TERRAIN -> new LodConfig(
					EditorConfig.lodTerrainRenderDist, Float.MAX_VALUE, EditorConfig.lodTerrainTransparencyDist);
			case STATIC_PROP -> new LodConfig(
					EditorConfig.lodStaticRenderDist, Float.MAX_VALUE, EditorConfig.lodStaticTransparencyDist);
			case SKINNED -> new LodConfig(
					EditorConfig.lodSkinnedRenderDist, EditorConfig.lodSkinnedAnimationDist, EditorConfig.lodSkinnedTransparencyDist);
			case MORPHED -> new LodConfig(
					EditorConfig.lodMorphedRenderDist, EditorConfig.lodMorphedAnimationDist, EditorConfig.lodMorphedTransparencyDist);
			case SKINNED_MORPHED -> new LodConfig(
					EditorConfig.lodSkinnedMorphedRenderDist, EditorConfig.lodSkinnedMorphedAnimationDist,
					EditorConfig.lodSkinnedMorphedTransparencyDist);
		};
	}
}
