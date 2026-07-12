package com.micheanl.mcgltf.format;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public final class GltfValidator {
	private static final String VERSION_MAJOR = "2";

	private GltfValidator() {
	}

	public static List<ValidationIssue> validate(GltfDocument g) {
		List<ValidationIssue> issues = new ArrayList<>();
		checkAsset(g, issues);
		checkRequiredExtensions(g, issues);
		checkBuffers(g, issues);
		checkBufferViews(g, issues);
		checkAccessors(g, issues);
		checkScenes(g, issues);
		checkNodes(g, issues);
		checkMeshes(g, issues);
		checkSkins(g, issues);
		checkAnimations(g, issues);
		checkMaterials(g, issues);
		checkTextures(g, issues);
		checkSamplers(g, issues);
		checkImages(g, issues);
		checkCameras(g, issues);
		return issues;
	}

	private static <T> int size(List<T> list) {
		return list == null ? 0 : list.size();
	}

	private static boolean invalid(Integer index, int size) {
		return index != null && (index < 0 || index >= size);
	}

	private static void checkAsset(GltfDocument g, List<ValidationIssue> issues) {
		if (g.asset == null || g.asset.version == null) {
			issues.add(ValidationIssue.error("missing asset.version"));
			return;
		}
		if (!g.asset.version.startsWith(VERSION_MAJOR + ".")) {
			issues.add(ValidationIssue.error("unsupported glTF version: " + g.asset.version));
		}
	}

	private static void checkRequiredExtensions(GltfDocument g, List<ValidationIssue> issues) {
		if (g.extensionsRequired == null) {
			return;
		}
		for (String ext : g.extensionsRequired) {
			if (!GltfExtensions.SUPPORTED_EXTENSIONS.contains(ext)) {
				String hint = GltfExtensions.DRACO.equals(ext) ? " (use meshopt compression or uncompressed)" : "";
				issues.add(ValidationIssue.error("model requires unsupported extension: " + ext + hint));
			}
		}
	}

	private static void checkBuffers(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.buffers); i++) {
			if (g.buffers.get(i).byteLength <= 0) {
				issues.add(ValidationIssue.error("buffer[" + i + "] byteLength invalid"));
			}
		}
	}

	private static void checkBufferViews(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.bufferViews); i++) {
			GltfDocument.BufferView bv = g.bufferViews.get(i);
			if (bv.buffer < 0 || bv.buffer >= size(g.buffers)) {
				issues.add(ValidationIssue.error("bufferView[" + i + "] buffer index out of bounds"));
				continue;
			}
			GltfDocument.Buffer buffer = g.buffers.get(bv.buffer);
			if (bv.byteOffset < 0 || bv.byteLength <= 0 || bv.byteOffset + bv.byteLength > buffer.byteLength) {
				issues.add(ValidationIssue.error("bufferView[" + i + "] exceeds buffer range"));
			}
			if (bv.byteStride != null && (bv.byteStride < 4 || bv.byteStride > 252 || bv.byteStride % 4 != 0)) {
				issues.add(ValidationIssue.error("bufferView[" + i + "] byteStride invalid: " + bv.byteStride));
			}
		}
	}

	private static void checkAccessors(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.accessors); i++) {
			GltfDocument.Accessor a = g.accessors.get(i);
			if (!AccessorReader.validComponentType(a.componentType)) {
				issues.add(ValidationIssue.error("accessor[" + i + "] componentType invalid: " + a.componentType));
				continue;
			}
			int elementSize;
			try {
				elementSize = AccessorReader.tightElementSize(a.type, a.componentType);
			} catch (GltfException e) {
				issues.add(ValidationIssue.error("accessor[" + i + "] " + e.getMessage()));
				continue;
			}
			if (a.count <= 0) {
				issues.add(ValidationIssue.error("accessor[" + i + "] count invalid"));
				continue;
			}
			if (a.bufferView != null) {
				if (invalid(a.bufferView, size(g.bufferViews))) {
					issues.add(ValidationIssue.error("accessor[" + i + "] bufferView index out of bounds"));
					continue;
				}
				GltfDocument.BufferView bv = g.bufferViews.get(a.bufferView);
				long stride = bv.byteStride != null ? bv.byteStride : elementSize;
				if (a.byteOffset < 0 || a.byteOffset + stride * (a.count - 1) + elementSize > bv.byteLength) {
					issues.add(ValidationIssue.error("accessor[" + i + "] exceeds bufferView range"));
				}
			}
			if (a.sparse != null) {
				checkSparse(g, i, a, issues);
			}
		}
	}

	private static void checkSparse(GltfDocument g, int index, GltfDocument.Accessor a, List<ValidationIssue> issues) {
		GltfDocument.Sparse sparse = a.sparse;
		if (sparse.count <= 0 || sparse.count > a.count || sparse.indices == null || sparse.values == null) {
			issues.add(ValidationIssue.error("accessor[" + index + "] sparse structure invalid"));
			return;
		}
		if (sparse.indices.componentType != AccessorReader.UNSIGNED_BYTE && sparse.indices.componentType != AccessorReader.UNSIGNED_SHORT
				&& sparse.indices.componentType != AccessorReader.UNSIGNED_INT) {
			issues.add(ValidationIssue.error("accessor[" + index + "] sparse indices componentType invalid"));
		}
		if (invalid(sparse.indices.bufferView, size(g.bufferViews))
				|| invalid(sparse.values.bufferView, size(g.bufferViews))) {
			issues.add(ValidationIssue.error("accessor[" + index + "] sparse bufferView index out of bounds"));
			return;
		}
		GltfDocument.BufferView iv = g.bufferViews.get(sparse.indices.bufferView);
		if (sparse.indices.byteOffset + (long) sparse.count * AccessorReader.componentSize(sparse.indices.componentType) > iv.byteLength) {
			issues.add(ValidationIssue.error("accessor[" + index + "] sparse indices out of range"));
		}
		GltfDocument.BufferView vv = g.bufferViews.get(sparse.values.bufferView);
		if (sparse.values.byteOffset + (long) sparse.count * AccessorReader.tightElementSize(a.type, a.componentType) > vv.byteLength) {
			issues.add(ValidationIssue.error("accessor[" + index + "] sparse values out of range"));
		}
	}

	private static void checkScenes(GltfDocument g, List<ValidationIssue> issues) {
		if (invalid(g.scene, size(g.scenes))) {
			issues.add(ValidationIssue.error("default scene index out of bounds"));
		}
		for (int i = 0; i < size(g.scenes); i++) {
			int[] nodes = g.scenes.get(i).nodes;
			if (nodes == null) {
				continue;
			}
			for (int node : nodes) {
				if (node < 0 || node >= size(g.nodes)) {
					issues.add(ValidationIssue.error("scene[" + i + "] node index out of bounds: " + node));
				}
			}
		}
	}

	private static void checkNodes(GltfDocument g, List<ValidationIssue> issues) {
		int count = size(g.nodes);
		int[] parents = new int[count];
		java.util.Arrays.fill(parents, -1);
		for (int i = 0; i < count; i++) {
			GltfDocument.Node node = g.nodes.get(i);
			if (invalid(node.mesh, size(g.meshes))) {
				issues.add(ValidationIssue.error("node[" + i + "] mesh index out of bounds"));
			}
			if (invalid(node.skin, size(g.skins))) {
				issues.add(ValidationIssue.error("node[" + i + "] skin index out of bounds"));
			}
			if (invalid(node.camera, size(g.cameras))) {
				issues.add(ValidationIssue.error("node[" + i + "] camera index out of bounds"));
			}
			if (node.skin != null && node.mesh == null) {
				issues.add(ValidationIssue.error("node[" + i + "] skin declared but no mesh"));
			}
			if (node.matrix != null) {
				if (node.matrix.length != 16) {
					issues.add(ValidationIssue.error("node[" + i + "] matrix length not 16"));
				}
				if (node.translation != null || node.rotation != null || node.scale != null) {
					issues.add(ValidationIssue.error("node[" + i + "] matrix and TRS are mutually exclusive"));
				}
			}
			if (node.rotation != null && node.rotation.length != 4) {
				issues.add(ValidationIssue.error("node[" + i + "] rotation length not 4"));
			}
			if (node.children == null) {
				continue;
			}
			for (int child : node.children) {
				if (child < 0 || child >= count) {
					issues.add(ValidationIssue.error("node[" + i + "] child index out of bounds: " + child));
				} else if (parents[child] != -1) {
					issues.add(ValidationIssue.error("node[" + child + "] has multiple parents"));
				} else {
					parents[child] = i;
				}
			}
		}
		boolean[] visited = new boolean[count];
		Deque<Integer> stack = new ArrayDeque<>();
		for (int i = 0; i < count; i++) {
			if (parents[i] == -1) {
				stack.push(i);
			}
		}
		int reached = 0;
		while (!stack.isEmpty()) {
			int node = stack.pop();
			if (visited[node]) {
				continue;
			}
			visited[node] = true;
			reached++;
			int[] children = g.nodes.get(node).children;
			if (children != null) {
				for (int child : children) {
					if (child >= 0 && child < count && !visited[child]) {
						stack.push(child);
					}
				}
			}
		}
		if (reached < count) {
			issues.add(ValidationIssue.error("node graph contains cycle"));
		}
	}

	private static void checkMeshes(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.meshes); i++) {
			GltfDocument.Mesh mesh = g.meshes.get(i);
			if (size(mesh.primitives) == 0) {
				issues.add(ValidationIssue.error("mesh[" + i + "] has no primitives"));
				continue;
			}
			for (int p = 0; p < mesh.primitives.size(); p++) {
				checkPrimitive(g, i, p, mesh.primitives.get(p), issues);
			}
		}
	}

	private static void checkPrimitive(GltfDocument g, int meshIndex, int primIndex, GltfDocument.Primitive prim, List<ValidationIssue> issues) {
		String where = "mesh[" + meshIndex + "].prim[" + primIndex + "] ";
		if (prim.attributes == null || prim.attributes.isEmpty()) {
			issues.add(ValidationIssue.error(where + "no attributes"));
			return;
		}
		int mode = prim.mode == null ? GltfConstants.MODE_TRIANGLES : prim.mode;
		if (mode < GltfConstants.MODE_POINTS || mode > GltfConstants.MODE_TRIANGLE_FAN) {
			issues.add(ValidationIssue.error(where + "invalid mode: " + mode));
		}
		if (JsonReader.has(prim.extensions, GltfExtensions.DRACO)) {
			issues.add(ValidationIssue.error(where + "uses " + GltfExtensions.DRACO + ", unsupported (use meshopt or uncompressed)"));
		}
		int vertexCount = -1;
		for (Map.Entry<String, Integer> attr : prim.attributes.entrySet()) {
			if (invalid(attr.getValue(), size(g.accessors))) {
				issues.add(ValidationIssue.error(where + "attribute " + attr.getKey() + " accessor out of bounds"));
				continue;
			}
			GltfDocument.Accessor accessor = g.accessors.get(attr.getValue());
			if (vertexCount == -1) {
				vertexCount = accessor.count;
			} else if (accessor.count != vertexCount) {
				issues.add(ValidationIssue.error(where + "attribute " + attr.getKey() + " count inconsistent with other attributes"));
			}
			if (attr.getKey().startsWith("JOINTS_")) {
				String weights = "WEIGHTS_" + attr.getKey().substring("JOINTS_".length());
				if (!prim.attributes.containsKey(weights)) {
					issues.add(ValidationIssue.error(where + attr.getKey() + " missing pair " + weights));
				}
			}
		}
		Integer position = prim.attributes.get("POSITION");
		if (position != null && !invalid(position, size(g.accessors))) {
			GltfDocument.Accessor pos = g.accessors.get(position);
			if (!"VEC3".equals(pos.type) || pos.componentType != AccessorReader.FLOAT) {
				issues.add(ValidationIssue.error(where + "POSITION must be VEC3 float"));
			}
		}
		if (prim.indices != null) {
			if (invalid(prim.indices, size(g.accessors))) {
				issues.add(ValidationIssue.error(where + "indices accessor out of bounds"));
			} else {
				GltfDocument.Accessor indices = g.accessors.get(prim.indices);
				boolean unsignedScalar = "SCALAR".equals(indices.type)
						&& (indices.componentType == AccessorReader.UNSIGNED_BYTE || indices.componentType == AccessorReader.UNSIGNED_SHORT
								|| indices.componentType == AccessorReader.UNSIGNED_INT);
				if (!unsignedScalar) {
					issues.add(ValidationIssue.error(where + "indices must be unsigned SCALAR"));
				}
			}
		}
		if (invalid(prim.material, size(g.materials))) {
			issues.add(ValidationIssue.error(where + "material index out of bounds"));
		}
		if (prim.targets == null) {
			return;
		}
		for (int t = 0; t < prim.targets.size(); t++) {
			for (Map.Entry<String, Integer> attr : prim.targets.get(t).entrySet()) {
				if (invalid(attr.getValue(), size(g.accessors))) {
					issues.add(ValidationIssue.error(where + "target[" + t + "] " + attr.getKey() + " accessor out of bounds"));
				} else if (vertexCount != -1 && g.accessors.get(attr.getValue()).count != vertexCount) {
					issues.add(ValidationIssue.error(where + "target[" + t + "] " + attr.getKey() + " count inconsistent"));
				}
			}
		}
	}

	private static void checkSkins(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.skins); i++) {
			GltfDocument.Skin skin = g.skins.get(i);
			if (skin.joints == null || skin.joints.length == 0) {
				issues.add(ValidationIssue.error("skin[" + i + "] joints is empty"));
				continue;
			}
			for (int joint : skin.joints) {
				if (joint < 0 || joint >= size(g.nodes)) {
					issues.add(ValidationIssue.error("skin[" + i + "] joint index out of bounds: " + joint));
				}
			}
			if (invalid(skin.skeleton, size(g.nodes))) {
				issues.add(ValidationIssue.error("skin[" + i + "] skeleton index out of bounds"));
			}
			if (skin.inverseBindMatrices != null) {
				if (invalid(skin.inverseBindMatrices, size(g.accessors))) {
					issues.add(ValidationIssue.error("skin[" + i + "] inverseBindMatrices out of bounds"));
				} else {
					GltfDocument.Accessor ibm = g.accessors.get(skin.inverseBindMatrices);
					if (!"MAT4".equals(ibm.type) || ibm.componentType != AccessorReader.FLOAT || ibm.count < skin.joints.length) {
						issues.add(ValidationIssue.error("skin[" + i + "] inverseBindMatrices type or count invalid"));
					}
				}
			}
		}
	}

	private static void checkAnimations(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.animations); i++) {
			GltfDocument.Animation anim = g.animations.get(i);
			String where = "animation[" + i + "] ";
			if (size(anim.channels) == 0 || size(anim.samplers) == 0) {
				issues.add(ValidationIssue.error(where + "channels or samplers is empty"));
				continue;
			}
			for (int s = 0; s < anim.samplers.size(); s++) {
				GltfDocument.AnimationSampler sampler = anim.samplers.get(s);
				if (invalid(sampler.input, size(g.accessors)) || invalid(sampler.output, size(g.accessors))) {
					issues.add(ValidationIssue.error(where + "sampler[" + s + "] accessor out of bounds"));
					continue;
				}
				GltfDocument.Accessor input = g.accessors.get(sampler.input);
				if (!"SCALAR".equals(input.type) || input.componentType != AccessorReader.FLOAT) {
					issues.add(ValidationIssue.error(where + "sampler[" + s + "] input must be SCALAR float"));
				}
				if (sampler.interpolation != null && !sampler.interpolation.equals("LINEAR")
						&& !sampler.interpolation.equals("STEP") && !sampler.interpolation.equals("CUBICSPLINE")) {
					issues.add(ValidationIssue.error(where + "sampler[" + s + "] interpolation invalid: " + sampler.interpolation));
				}
				GltfDocument.Accessor output = g.accessors.get(sampler.output);
				boolean cubic = "CUBICSPLINE".equals(sampler.interpolation);
				int expectedBase = cubic ? input.count * 3 : input.count;
				if (expectedBase == 0 || output.count % expectedBase != 0) {
					issues.add(ValidationIssue.error(where + "sampler[" + s + "] output count does not match input"));
				}
			}
			for (int c = 0; c < anim.channels.size(); c++) {
				GltfDocument.AnimationChannel channel = anim.channels.get(c);
				if (channel.sampler < 0 || channel.sampler >= anim.samplers.size()) {
					issues.add(ValidationIssue.error(where + "channel[" + c + "] sampler out of bounds"));
				}
				if (channel.target == null || channel.target.path == null) {
					issues.add(ValidationIssue.error(where + "channel[" + c + "] target missing"));
					continue;
				}
				if (invalid(channel.target.node, size(g.nodes))) {
					issues.add(ValidationIssue.error(where + "channel[" + c + "] target node out of bounds"));
				}
				String path = channel.target.path;
				if (!path.equals("translation") && !path.equals("rotation") && !path.equals("scale") && !path.equals("weights")) {
					issues.add(ValidationIssue.error(where + "channel[" + c + "] path invalid: " + path));
				}
			}
		}
	}

	private static void checkTextureRef(GltfDocument g, String where, GltfDocument.TextureInfo ref, List<ValidationIssue> issues) {
		if (ref != null && (ref.index < 0 || ref.index >= size(g.textures))) {
			issues.add(ValidationIssue.error(where + " texture index out of bounds"));
		}
	}

	private static void checkMaterials(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.materials); i++) {
			GltfDocument.Material m = g.materials.get(i);
			String where = "material[" + i + "]";
			if (m.alphaMode != null && !m.alphaMode.equals("OPAQUE") && !m.alphaMode.equals("MASK") && !m.alphaMode.equals("BLEND")) {
				issues.add(ValidationIssue.error(where + " alphaMode invalid: " + m.alphaMode));
			}
			if (m.pbrMetallicRoughness != null) {
				checkTextureRef(g, where + ".baseColor", m.pbrMetallicRoughness.baseColorTexture, issues);
				checkTextureRef(g, where + ".metallicRoughness", m.pbrMetallicRoughness.metallicRoughnessTexture, issues);
				float[] baseColor = m.pbrMetallicRoughness.baseColorFactor;
				if (baseColor != null && baseColor.length != 4) {
					issues.add(ValidationIssue.error(where + " baseColorFactor length not 4"));
				}
			}
			checkTextureRef(g, where + ".normal", m.normalTexture, issues);
			checkTextureRef(g, where + ".occlusion", m.occlusionTexture, issues);
			checkTextureRef(g, where + ".emissive", m.emissiveTexture, issues);
			if (m.emissiveFactor != null && m.emissiveFactor.length != 3) {
				issues.add(ValidationIssue.error(where + " emissiveFactor length not 3"));
			}
		}
	}

	private static void checkTextures(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.textures); i++) {
			GltfDocument.Texture texture = g.textures.get(i);
			if (invalid(texture.source, size(g.images)) || invalid(texture.basisuSource, size(g.images))) {
				issues.add(ValidationIssue.error("texture[" + i + "] source out of bounds"));
			}
			if (invalid(texture.sampler, size(g.samplers))) {
				issues.add(ValidationIssue.error("texture[" + i + "] sampler out of bounds"));
			}
			if (texture.source == null && texture.basisuSource == null) {
				issues.add(ValidationIssue.warning("texture[" + i + "] no source (possibly unsupported texture extension), using placeholder"));
			}
		}
	}

	private static void checkSamplers(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.samplers); i++) {
			GltfDocument.Sampler s = g.samplers.get(i);
			if (s.wrapS != null && !GltfConstants.validWrap(s.wrapS) || s.wrapT != null && !GltfConstants.validWrap(s.wrapT)) {
				issues.add(ValidationIssue.warning("sampler[" + i + "] unknown wrap mode, using REPEAT"));
			}
			if (s.magFilter != null && !GltfConstants.validMagFilter(s.magFilter)
					|| s.minFilter != null && !GltfConstants.validMinFilter(s.minFilter)) {
				issues.add(ValidationIssue.warning("sampler[" + i + "] unknown filter, using LINEAR"));
			}
		}
	}

	private static void checkImages(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.images); i++) {
			GltfDocument.Image image = g.images.get(i);
			if (image.uri == null && image.bufferView == null) {
				issues.add(ValidationIssue.error("image[" + i + "] missing uri or bufferView"));
			}
			if (invalid(image.bufferView, size(g.bufferViews))) {
				issues.add(ValidationIssue.error("image[" + i + "] bufferView out of bounds"));
			}
		}
	}

	private static void checkCameras(GltfDocument g, List<ValidationIssue> issues) {
		for (int i = 0; i < size(g.cameras); i++) {
			GltfDocument.Camera camera = g.cameras.get(i);
			boolean perspective = "perspective".equals(camera.type) && camera.perspective != null;
			boolean orthographic = "orthographic".equals(camera.type) && camera.orthographic != null;
			if (!perspective && !orthographic) {
				issues.add(ValidationIssue.warning("camera[" + i + "] invalid type, ignored"));
			}
		}
	}
}
