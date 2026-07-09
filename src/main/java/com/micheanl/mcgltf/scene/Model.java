package com.micheanl.mcgltf.scene;

import com.micheanl.mcgltf.format.ext.PunctualLight;

import java.nio.ByteBuffer;

public record Model(
		String name,
		Node[] nodes,
		int[] topoOrder,
		int[] sceneRoots,
		Mesh[] meshes,
		Skin[] skins,
		Clip[] clips,
		Material[] materials,
		Texture[] textures,
		Sampler[] samplers,
		Image[] images,
		Camera[] cameras,
		PunctualLight[] lights,
		String[] variantNames,
		float[] aabb,
		Stats stats) {

	public enum TopologyType {
		TRIANGLES,
		LINES,
		POINTS
	}

	public enum AlphaMode {
		OPAQUE,
		MASK,
		BLEND
	}

	public enum AnimationPath {
		TRANSLATION,
		ROTATION,
		SCALE,
		WEIGHTS
	}

	public enum Interpolation {
		LINEAR,
		STEP,
		CUBIC
	}

	public record Node(
			String name,
			int parent,
			int[] children,
			float[] trs,
			float[] matrix,
			int mesh,
			int skin,
			int camera,
			int light,
			float[] weights) {
	}

	public record Mesh(String name, Primitive[] prims, float[] weights) {
	}

	public record Primitive(
			ByteBuffer vertices,
			ByteBuffer skin,
			ByteBuffer indices,
			boolean indices32,
			int indexCount,
			int vertexCount,
			TopologyType topo,
			int material,
			int[] variantMaterials,
			float[] aabb,
			Morph morph) {
	}

	public record Morph(int targets, boolean normals, boolean tangents, float[] data) {
		public int floatsPerTarget(int vertexCount) {
			int slots = 1 + (normals ? 1 : 0) + (tangents ? 1 : 0);
			return slots * vertexCount * 3;
		}
	}

	public record Skin(String name, int[] joints, float[] inverseBindMatrices) {
	}

	public record Clip(String name, float duration, Channel[] channels) {
	}

	public record Channel(int node, AnimationPath path, Interpolation interp, float[] times, float[] values, int comps) {
	}

	public record Material(
			String name,
			float[] baseColor,
			TextureBinding baseColorTexture,
			float metallic,
			float roughness,
			TextureBinding metallicRoughnessTexture,
			TextureBinding normalTexture,
			float normalScale,
			TextureBinding occlusionTexture,
			float occlusionStrength,
			TextureBinding emissiveTexture,
			float[] emissiveFactor,
			float emissiveStrength,
			AlphaMode alphaMode,
			float alphaCutoff,
			boolean doubleSided,
			boolean unlit,
			float ior,
			Specular specular,
			Clearcoat clearcoat,
			Sheen sheen) {
	}

	public record Specular(float factor, float[] colorFactor, TextureBinding factorTexture, TextureBinding colorTexture) {
	}

	public record Clearcoat(float factor, float roughness, TextureBinding factorTexture, TextureBinding roughnessTexture, TextureBinding normalTexture, float normalScale) {
	}

	public record Sheen(float[] colorFactor, float roughness, TextureBinding colorTexture, TextureBinding roughnessTexture) {
	}

	public record TextureBinding(int texture, int uv, float[] transform) {
	}

	public record Texture(int image, int sampler) {
	}

	public record Sampler(int magFilter, int minFilter, int wrapS, int wrapT) {
	}

	public record Image(byte[] bytes, String mime, String name) {
	}

	public record Camera(String name, boolean perspective, float yfov, float aspect, float znear, float zfar, float xmag, float ymag) {
	}

	public record Stats(
			int nodes,
			int meshes,
			int prims,
			long triangles,
			int materials,
			int textures,
			int skins,
			int maxJoints,
			int morphTargets,
			String[] clipNames,
			float[] clipSeconds) {
	}
}
