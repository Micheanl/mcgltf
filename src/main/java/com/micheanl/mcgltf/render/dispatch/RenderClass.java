package com.micheanl.mcgltf.render.dispatch;

import com.micheanl.mcgltf.scene.Model;

public enum RenderClass {
	TERRAIN,
	STATIC_PROP,
	SKINNED,
	MORPHED,
	SKINNED_MORPHED;

	private static final int TERRAIN_THRESHOLD = 50_000;

	public boolean animated() {
		return this == SKINNED || this == MORPHED || this == SKINNED_MORPHED;
	}

	public static RenderClass classify(Model.Stats stats) {
		boolean skin = stats.skins() > 0;
		boolean morph = stats.morphTargets() > 0;
		if (skin && morph) {
			return SKINNED_MORPHED;
		}
		if (skin) {
			return SKINNED;
		}
		if (morph) {
			return MORPHED;
		}
		if (stats.triangles() >= TERRAIN_THRESHOLD) {
			return TERRAIN;
		}
		return STATIC_PROP;
	}
}
