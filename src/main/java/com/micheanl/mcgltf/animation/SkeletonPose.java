package com.micheanl.mcgltf.animation;

import com.micheanl.mcgltf.scene.Model;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class SkeletonPose {
	private static final Quaternionf FROM_ROT = new Quaternionf();
	private static final Quaternionf TO_ROT = new Quaternionf();

	private final Model model;
	final float[] local;
	final float[][] weights;
	private final Matrix4f[] globals;

	public SkeletonPose(Model model) {
		this.model = model;
		int count = model.nodes().length;
		this.local = new float[count * 10];
		this.globals = new Matrix4f[count];
		this.weights = new float[count][];
		for (int i = 0; i < count; i++) {
			globals[i] = new Matrix4f();
			int targets = morphTargets(model, i);
			if (targets > 0) {
				weights[i] = new float[targets];
			}
		}
	}

	public void reset() {
		Model.Node[] nodes = model.nodes();
		for (int i = 0; i < nodes.length; i++) {
			System.arraycopy(nodes[i].trs(), 0, local, i * 10, 10);
			if (weights[i] != null) {
				float[] defaults = defaultWeights(nodes[i]);
				for (int t = 0; t < weights[i].length; t++) {
					weights[i][t] = defaults != null && t < defaults.length ? defaults[t] : 0.0f;
				}
			}
		}
	}

	public void computeGlobals() {
		Model.Node[] nodes = model.nodes();
		for (int idx : model.topoOrder()) {
			Matrix4f global = globals[idx];
			if (nodes[idx].matrix() != null) {
				global.set(nodes[idx].matrix());
			} else {
				int b = idx * 10;
				global.translationRotateScale(
						local[b], local[b + 1], local[b + 2],
						local[b + 3], local[b + 4], local[b + 5], local[b + 6],
						local[b + 7], local[b + 8], local[b + 9]);
			}
			int parent = nodes[idx].parent();
			if (parent >= 0) {
				global.mulLocal(globals[parent]);
			}
		}
	}

	public Matrix4f global(int node) {
		return globals[node];
	}

	public void blend(SkeletonPose from, float weight) {
		for (int i = 0; i < local.length; i += 10) {
			for (int c = 0; c < 3; c++) {
				local[i + c] = lerp(from.local[i + c], local[i + c], weight);
				local[i + 7 + c] = lerp(from.local[i + 7 + c], local[i + 7 + c], weight);
			}
			FROM_ROT.set(from.local[i + 3], from.local[i + 4], from.local[i + 5], from.local[i + 6]);
			TO_ROT.set(local[i + 3], local[i + 4], local[i + 5], local[i + 6]);
			FROM_ROT.slerp(TO_ROT, weight);
			local[i + 3] = FROM_ROT.x;
			local[i + 4] = FROM_ROT.y;
			local[i + 5] = FROM_ROT.z;
			local[i + 6] = FROM_ROT.w;
		}
		for (int i = 0; i < weights.length; i++) {
			if (weights[i] != null && from.weights[i] != null) {
				for (int t = 0; t < weights[i].length; t++) {
					weights[i][t] = lerp(from.weights[i][t], weights[i][t], weight);
				}
			}
		}
	}

	private static float lerp(float from, float to, float weight) {
		return from + (to - from) * weight;
	}

	public float[] weights(int node) {
		return weights[node];
	}

	private float[] defaultWeights(Model.Node node) {
		if (node.weights() != null) {
			return node.weights();
		}
		return node.mesh() >= 0 ? model.meshes()[node.mesh()].weights() : null;
	}

	private static int morphTargets(Model model, int nodeIndex) {
		Model.Node node = model.nodes()[nodeIndex];
		if (node.mesh() < 0) {
			return 0;
		}
		int max = 0;
		for (Model.Primitive prim : model.meshes()[node.mesh()].prims()) {
			if (prim.morph() != null) {
				max = Math.max(max, prim.morph().targets());
			}
		}
		return max;
	}
}
