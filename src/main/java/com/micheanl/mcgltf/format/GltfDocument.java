package com.micheanl.mcgltf.format;

import com.jsoniter.any.Any;
import com.micheanl.mcgltf.format.ext.Clearcoat;
import com.micheanl.mcgltf.format.ext.LightType;
import com.micheanl.mcgltf.format.ext.MaterialExtensions;
import com.micheanl.mcgltf.format.ext.PunctualLight;
import com.micheanl.mcgltf.format.ext.Sheen;
import com.micheanl.mcgltf.format.ext.Specular;
import com.micheanl.mcgltf.format.ext.TextureTransform;
import com.micheanl.mcgltf.format.ext.VariantMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class GltfDocument {
	public Asset asset;
	public List<String> extensionsUsed;
	public List<String> extensionsRequired;
	public List<Buffer> buffers;
	public List<BufferView> bufferViews;
	public List<Accessor> accessors;
	public Integer scene;
	public List<Scene> scenes;
	public List<Node> nodes;
	public List<Mesh> meshes;
	public List<Skin> skins;
	public List<Animation> animations;
	public List<Material> materials;
	public List<Texture> textures;
	public List<Sampler> samplers;
	public List<Image> images;
	public List<Camera> cameras;
	public List<PunctualLight> punctualLights;
	public List<String> materialVariants;

	public static GltfDocument read(Any a) {
		GltfDocument g = new GltfDocument();
		g.asset = Asset.read(JsonReader.get(a, "asset"));
		g.extensionsUsed = JsonReader.strings(a, "extensionsUsed");
		g.extensionsRequired = JsonReader.strings(a, "extensionsRequired");
		g.buffers = list(a, "buffers", Buffer::read);
		g.bufferViews = list(a, "bufferViews", BufferView::read);
		g.accessors = list(a, "accessors", Accessor::read);
		g.scene = JsonReader.optInt(a, "scene");
		g.scenes = list(a, "scenes", Scene::read);
		g.nodes = list(a, "nodes", Node::read);
		g.meshes = list(a, "meshes", Mesh::read);
		g.skins = list(a, "skins", Skin::read);
		g.animations = list(a, "animations", Animation::read);
		g.materials = list(a, "materials", Material::read);
		g.textures = list(a, "textures", Texture::read);
		g.samplers = list(a, "samplers", Sampler::read);
		g.images = list(a, "images", Image::read);
		g.cameras = list(a, "cameras", Camera::read);
		Any extensions = JsonReader.get(a, "extensions");
		g.punctualLights = readPunctualLights(extensions);
		g.materialVariants = readMaterialVariants(extensions);
		return g;
	}

	private static <T> List<T> list(Any parent, String key, Function<Any, T> reader) {
		Any array = JsonReader.get(parent, key);
		if (array == null) {
			return null;
		}
		List<T> out = new ArrayList<>(array.size());
		for (int i = 0; i < array.size(); i++) {
			out.add(reader.apply(array.get(i)));
		}
		return out;
	}

	private static Map<String, Integer> intMap(Any obj) {
		if (obj == null) {
			return null;
		}
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Any> entry : obj.asMap().entrySet()) {
			out.put(entry.getKey(), entry.getValue().toInt());
		}
		return out;
	}

	private static MaterialExtensions readMaterialExtensions(Any extensions) {
		boolean unlit = JsonReader.has(extensions, GltfExtensions.UNLIT);
		float emissiveStrength = 1.0f;
		Any strength = JsonReader.get(extensions, GltfExtensions.EMISSIVE_STRENGTH);
		if (strength != null) {
			emissiveStrength = JsonReader.floatOrDefault(strength, "emissiveStrength", 1.0f);
		}
		float indexOfRefraction = 1.5f;
		Any refraction = JsonReader.get(extensions, GltfExtensions.INDEX_OF_REFRACTION);
		if (refraction != null) {
			indexOfRefraction = JsonReader.floatOrDefault(refraction, "ior", 1.5f);
		}
		return new MaterialExtensions(unlit, emissiveStrength, indexOfRefraction,
				readSpecular(JsonReader.get(extensions, GltfExtensions.SPECULAR)),
				readClearcoat(JsonReader.get(extensions, GltfExtensions.CLEARCOAT)),
				readSheen(JsonReader.get(extensions, GltfExtensions.SHEEN)));
	}

	private static Specular readSpecular(Any a) {
		if (a == null) {
			return null;
		}
		float[] colorFactor = JsonReader.floats(a, "specularColorFactor");
		return new Specular(
				JsonReader.floatOrDefault(a, "specularFactor", 1.0f),
				colorFactor != null ? colorFactor : new float[]{1.0f, 1.0f, 1.0f},
				TextureInfo.read(JsonReader.get(a, "specularTexture")),
				TextureInfo.read(JsonReader.get(a, "specularColorTexture")));
	}

	private static Clearcoat readClearcoat(Any a) {
		if (a == null) {
			return null;
		}
		return new Clearcoat(
				JsonReader.floatOrDefault(a, "clearcoatFactor", 0.0f),
				JsonReader.floatOrDefault(a, "clearcoatRoughnessFactor", 0.0f),
				TextureInfo.read(JsonReader.get(a, "clearcoatTexture")),
				TextureInfo.read(JsonReader.get(a, "clearcoatRoughnessTexture")),
				NormalTextureInfo.read(JsonReader.get(a, "clearcoatNormalTexture")));
	}

	private static Sheen readSheen(Any a) {
		if (a == null) {
			return null;
		}
		float[] colorFactor = JsonReader.floats(a, "sheenColorFactor");
		return new Sheen(
				colorFactor != null ? colorFactor : new float[]{0.0f, 0.0f, 0.0f},
				JsonReader.floatOrDefault(a, "sheenRoughnessFactor", 0.0f),
				TextureInfo.read(JsonReader.get(a, "sheenColorTexture")),
				TextureInfo.read(JsonReader.get(a, "sheenRoughnessTexture")));
	}

	private static TextureTransform readTextureTransform(Any a) {
		if (a == null) {
			return null;
		}
		float[] offset = JsonReader.floats(a, "offset");
		float[] scale = JsonReader.floats(a, "scale");
		return new TextureTransform(
				offset != null ? offset[0] : 0.0f,
				offset != null ? offset[1] : 0.0f,
				JsonReader.floatOrDefault(a, "rotation", 0.0f),
				scale != null ? scale[0] : 1.0f,
				scale != null ? scale[1] : 1.0f,
				JsonReader.optInt(a, "texCoord"));
	}

	private static List<PunctualLight> readPunctualLights(Any extensions) {
		Any punctual = JsonReader.get(extensions, GltfExtensions.LIGHTS_PUNCTUAL);
		Any lights = JsonReader.get(punctual, "lights");
		if (lights == null) {
			return null;
		}
		List<PunctualLight> out = new ArrayList<>(lights.size());
		for (int i = 0; i < lights.size(); i++) {
			out.add(readPunctualLight(lights.get(i)));
		}
		return out;
	}

	private static PunctualLight readPunctualLight(Any a) {
		String type = JsonReader.string(a, "type");
		LightType lightType = switch (type == null ? "" : type) {
			case "directional" -> LightType.DIRECTIONAL;
			case "spot" -> LightType.SPOT;
			default -> LightType.POINT;
		};
		float[] color = JsonReader.floats(a, "color");
		float innerConeAngle = 0.0f;
		float outerConeAngle = (float) (Math.PI / 4.0);
		Any spot = JsonReader.get(a, "spot");
		if (spot != null) {
			innerConeAngle = JsonReader.floatOrDefault(spot, "innerConeAngle", 0.0f);
			outerConeAngle = JsonReader.floatOrDefault(spot, "outerConeAngle", (float) (Math.PI / 4.0));
		}
		return new PunctualLight(lightType,
				color != null ? color : new float[]{1.0f, 1.0f, 1.0f},
				JsonReader.floatOrDefault(a, "intensity", 1.0f),
				JsonReader.floatOrDefault(a, "range", 0.0f),
				innerConeAngle, outerConeAngle, JsonReader.string(a, "name"));
	}

	private static int readNodeLight(Any extensions) {
		Integer light = JsonReader.optInt(JsonReader.get(extensions, GltfExtensions.LIGHTS_PUNCTUAL), "light");
		return light == null ? -1 : light;
	}

	private static Integer readBasisuSource(Any extensions) {
		return JsonReader.optInt(JsonReader.get(extensions, GltfExtensions.TEXTURE_BASISU), "source");
	}

	private static List<String> readMaterialVariants(Any extensions) {
		Any variants = JsonReader.get(JsonReader.get(extensions, GltfExtensions.MATERIALS_VARIANTS), "variants");
		if (variants == null) {
			return null;
		}
		List<String> out = new ArrayList<>(variants.size());
		for (int i = 0; i < variants.size(); i++) {
			out.add(JsonReader.string(variants.get(i), "name"));
		}
		return out;
	}

	private static List<VariantMapping> readVariantMappings(Any extensions) {
		Any mappings = JsonReader.get(JsonReader.get(extensions, GltfExtensions.MATERIALS_VARIANTS), "mappings");
		if (mappings == null) {
			return null;
		}
		List<VariantMapping> out = new ArrayList<>(mappings.size());
		for (int i = 0; i < mappings.size(); i++) {
			Any mapping = mappings.get(i);
			int[] variants = JsonReader.ints(mapping, "variants");
			out.add(new VariantMapping(JsonReader.intOrDefault(mapping, "material", -1),
					variants != null ? variants : new int[0]));
		}
		return out;
	}

	public static final class Asset {
		public String version;
		public String minVersion;
		public String generator;

		static Asset read(Any a) {
			if (a == null) {
				return null;
			}
			Asset out = new Asset();
			out.version = JsonReader.string(a, "version");
			out.minVersion = JsonReader.string(a, "minVersion");
			out.generator = JsonReader.string(a, "generator");
			return out;
		}
	}

	public static final class Buffer {
		public String uri;
		public long byteLength;
		public String name;

		static Buffer read(Any a) {
			Buffer out = new Buffer();
			out.uri = JsonReader.string(a, "uri");
			out.byteLength = JsonReader.longOrDefault(a, "byteLength", 0L);
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class BufferView {
		public int buffer;
		public long byteOffset;
		public long byteLength;
		public Integer byteStride;
		public String name;
		public Any extensions;

		static BufferView read(Any a) {
			BufferView out = new BufferView();
			out.buffer = JsonReader.intOrDefault(a, "buffer", 0);
			out.byteOffset = JsonReader.longOrDefault(a, "byteOffset", 0L);
			out.byteLength = JsonReader.longOrDefault(a, "byteLength", 0L);
			out.byteStride = JsonReader.optInt(a, "byteStride");
			out.name = JsonReader.string(a, "name");
			out.extensions = JsonReader.get(a, "extensions");
			return out;
		}
	}

	public static final class Accessor {
		public Integer bufferView;
		public long byteOffset;
		public int componentType;
		public Boolean normalized;
		public int count;
		public String type;
		public float[] max;
		public float[] min;
		public Sparse sparse;
		public String name;

		static Accessor read(Any a) {
			Accessor out = new Accessor();
			out.bufferView = JsonReader.optInt(a, "bufferView");
			out.byteOffset = JsonReader.longOrDefault(a, "byteOffset", 0L);
			out.componentType = JsonReader.intOrDefault(a, "componentType", 0);
			out.normalized = JsonReader.optBoolean(a, "normalized");
			out.count = JsonReader.intOrDefault(a, "count", 0);
			out.type = JsonReader.string(a, "type");
			out.max = JsonReader.floats(a, "max");
			out.min = JsonReader.floats(a, "min");
			out.sparse = Sparse.read(JsonReader.get(a, "sparse"));
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class Sparse {
		public int count;
		public SparseIndices indices;
		public SparseValues values;

		static Sparse read(Any a) {
			if (a == null) {
				return null;
			}
			Sparse out = new Sparse();
			out.count = JsonReader.intOrDefault(a, "count", 0);
			Any indices = JsonReader.get(a, "indices");
			if (indices != null) {
				out.indices = new SparseIndices();
				out.indices.bufferView = JsonReader.intOrDefault(indices, "bufferView", 0);
				out.indices.byteOffset = JsonReader.longOrDefault(indices, "byteOffset", 0L);
				out.indices.componentType = JsonReader.intOrDefault(indices, "componentType", 0);
			}
			Any values = JsonReader.get(a, "values");
			if (values != null) {
				out.values = new SparseValues();
				out.values.bufferView = JsonReader.intOrDefault(values, "bufferView", 0);
				out.values.byteOffset = JsonReader.longOrDefault(values, "byteOffset", 0L);
			}
			return out;
		}
	}

	public static final class SparseIndices {
		public int bufferView;
		public long byteOffset;
		public int componentType;
	}

	public static final class SparseValues {
		public int bufferView;
		public long byteOffset;
	}

	public static final class Scene {
		public int[] nodes;
		public String name;

		static Scene read(Any a) {
			Scene out = new Scene();
			out.nodes = JsonReader.ints(a, "nodes");
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class Node {
		public int[] children;
		public Integer mesh;
		public Integer skin;
		public Integer camera;
		public float[] matrix;
		public float[] translation;
		public float[] rotation;
		public float[] scale;
		public float[] weights;
		public String name;
		public int light;

		static Node read(Any a) {
			Node out = new Node();
			out.children = JsonReader.ints(a, "children");
			out.mesh = JsonReader.optInt(a, "mesh");
			out.skin = JsonReader.optInt(a, "skin");
			out.camera = JsonReader.optInt(a, "camera");
			out.matrix = JsonReader.floats(a, "matrix");
			out.translation = JsonReader.floats(a, "translation");
			out.rotation = JsonReader.floats(a, "rotation");
			out.scale = JsonReader.floats(a, "scale");
			out.weights = JsonReader.floats(a, "weights");
			out.name = JsonReader.string(a, "name");
			out.light = readNodeLight(JsonReader.get(a, "extensions"));
			return out;
		}
	}

	public static final class Mesh {
		public List<Primitive> primitives;
		public float[] weights;
		public String name;

		static Mesh read(Any a) {
			Mesh out = new Mesh();
			out.primitives = list(a, "primitives", Primitive::read);
			out.weights = JsonReader.floats(a, "weights");
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class Primitive {
		public Map<String, Integer> attributes;
		public Integer indices;
		public Integer material;
		public Integer mode;
		public List<Map<String, Integer>> targets;
		public Any extensions;
		public List<VariantMapping> variantMappings;

		static Primitive read(Any a) {
			Primitive out = new Primitive();
			out.attributes = intMap(JsonReader.get(a, "attributes"));
			out.indices = JsonReader.optInt(a, "indices");
			out.material = JsonReader.optInt(a, "material");
			out.mode = JsonReader.optInt(a, "mode");
			Any targets = JsonReader.get(a, "targets");
			if (targets != null) {
				out.targets = new ArrayList<>(targets.size());
				for (int i = 0; i < targets.size(); i++) {
					out.targets.add(intMap(targets.get(i)));
				}
			}
			out.extensions = JsonReader.get(a, "extensions");
			out.variantMappings = readVariantMappings(out.extensions);
			return out;
		}
	}

	public static final class Skin {
		public Integer inverseBindMatrices;
		public Integer skeleton;
		public int[] joints;
		public String name;

		static Skin read(Any a) {
			Skin out = new Skin();
			out.inverseBindMatrices = JsonReader.optInt(a, "inverseBindMatrices");
			out.skeleton = JsonReader.optInt(a, "skeleton");
			out.joints = JsonReader.ints(a, "joints");
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class Animation {
		public List<AnimationChannel> channels;
		public List<AnimationSampler> samplers;
		public String name;

		static Animation read(Any a) {
			Animation out = new Animation();
			out.channels = list(a, "channels", AnimationChannel::read);
			out.samplers = list(a, "samplers", AnimationSampler::read);
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class AnimationChannel {
		public int sampler;
		public AnimationChannelTarget target;

		static AnimationChannel read(Any a) {
			AnimationChannel out = new AnimationChannel();
			out.sampler = JsonReader.intOrDefault(a, "sampler", 0);
			Any target = JsonReader.get(a, "target");
			if (target != null) {
				out.target = new AnimationChannelTarget();
				out.target.node = JsonReader.optInt(target, "node");
				out.target.path = JsonReader.string(target, "path");
			}
			return out;
		}
	}

	public static final class AnimationChannelTarget {
		public Integer node;
		public String path;
	}

	public static final class AnimationSampler {
		public int input;
		public String interpolation;
		public int output;

		static AnimationSampler read(Any a) {
			AnimationSampler out = new AnimationSampler();
			out.input = JsonReader.intOrDefault(a, "input", 0);
			out.interpolation = JsonReader.string(a, "interpolation");
			out.output = JsonReader.intOrDefault(a, "output", 0);
			return out;
		}
	}

	public static final class Material {
		public String name;
		public PbrMetallicRoughness pbrMetallicRoughness;
		public NormalTextureInfo normalTexture;
		public OcclusionTextureInfo occlusionTexture;
		public TextureInfo emissiveTexture;
		public float[] emissiveFactor;
		public String alphaMode;
		public Float alphaCutoff;
		public Boolean doubleSided;
		public MaterialExtensions materialExtensions;

		static Material read(Any a) {
			Material out = new Material();
			out.name = JsonReader.string(a, "name");
			out.pbrMetallicRoughness = PbrMetallicRoughness.read(JsonReader.get(a, "pbrMetallicRoughness"));
			out.normalTexture = NormalTextureInfo.read(JsonReader.get(a, "normalTexture"));
			out.occlusionTexture = OcclusionTextureInfo.read(JsonReader.get(a, "occlusionTexture"));
			out.emissiveTexture = TextureInfo.read(JsonReader.get(a, "emissiveTexture"));
			out.emissiveFactor = JsonReader.floats(a, "emissiveFactor");
			out.alphaMode = JsonReader.string(a, "alphaMode");
			out.alphaCutoff = JsonReader.optFloat(a, "alphaCutoff");
			out.doubleSided = JsonReader.optBoolean(a, "doubleSided");
			out.materialExtensions = readMaterialExtensions(JsonReader.get(a, "extensions"));
			return out;
		}
	}

	public static final class PbrMetallicRoughness {
		public float[] baseColorFactor;
		public TextureInfo baseColorTexture;
		public Float metallicFactor;
		public Float roughnessFactor;
		public TextureInfo metallicRoughnessTexture;

		static PbrMetallicRoughness read(Any a) {
			if (a == null) {
				return null;
			}
			PbrMetallicRoughness out = new PbrMetallicRoughness();
			out.baseColorFactor = JsonReader.floats(a, "baseColorFactor");
			out.baseColorTexture = TextureInfo.read(JsonReader.get(a, "baseColorTexture"));
			out.metallicFactor = JsonReader.optFloat(a, "metallicFactor");
			out.roughnessFactor = JsonReader.optFloat(a, "roughnessFactor");
			out.metallicRoughnessTexture = TextureInfo.read(JsonReader.get(a, "metallicRoughnessTexture"));
			return out;
		}
	}

	public static class TextureInfo {
		public int index;
		public Integer texCoord;
		public TextureTransform transform;

		static TextureInfo read(Any a) {
			if (a == null) {
				return null;
			}
			TextureInfo out = new TextureInfo();
			out.fill(a);
			return out;
		}

		void fill(Any a) {
			this.index = JsonReader.intOrDefault(a, "index", 0);
			this.texCoord = JsonReader.optInt(a, "texCoord");
			this.transform = readTextureTransform(JsonReader.get(JsonReader.get(a, "extensions"), GltfExtensions.TEXTURE_TRANSFORM));
		}
	}

	public static final class NormalTextureInfo extends TextureInfo {
		public Float scale;

		static NormalTextureInfo read(Any a) {
			if (a == null) {
				return null;
			}
			NormalTextureInfo out = new NormalTextureInfo();
			out.fill(a);
			out.scale = JsonReader.optFloat(a, "scale");
			return out;
		}
	}

	public static final class OcclusionTextureInfo extends TextureInfo {
		public Float strength;

		static OcclusionTextureInfo read(Any a) {
			if (a == null) {
				return null;
			}
			OcclusionTextureInfo out = new OcclusionTextureInfo();
			out.fill(a);
			out.strength = JsonReader.optFloat(a, "strength");
			return out;
		}
	}

	public static final class Texture {
		public Integer sampler;
		public Integer source;
		public String name;
		public Integer basisuSource;

		static Texture read(Any a) {
			Texture out = new Texture();
			out.sampler = JsonReader.optInt(a, "sampler");
			out.source = JsonReader.optInt(a, "source");
			out.name = JsonReader.string(a, "name");
			out.basisuSource = readBasisuSource(JsonReader.get(a, "extensions"));
			return out;
		}
	}

	public static final class Sampler {
		public Integer magFilter;
		public Integer minFilter;
		public Integer wrapS;
		public Integer wrapT;

		static Sampler read(Any a) {
			Sampler out = new Sampler();
			out.magFilter = JsonReader.optInt(a, "magFilter");
			out.minFilter = JsonReader.optInt(a, "minFilter");
			out.wrapS = JsonReader.optInt(a, "wrapS");
			out.wrapT = JsonReader.optInt(a, "wrapT");
			return out;
		}
	}

	public static final class Image {
		public String uri;
		public String mimeType;
		public Integer bufferView;
		public String name;

		static Image read(Any a) {
			Image out = new Image();
			out.uri = JsonReader.string(a, "uri");
			out.mimeType = JsonReader.string(a, "mimeType");
			out.bufferView = JsonReader.optInt(a, "bufferView");
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class Camera {
		public String type;
		public Perspective perspective;
		public Orthographic orthographic;
		public String name;

		static Camera read(Any a) {
			Camera out = new Camera();
			out.type = JsonReader.string(a, "type");
			Any perspective = JsonReader.get(a, "perspective");
			if (perspective != null) {
				out.perspective = new Perspective();
				out.perspective.aspectRatio = JsonReader.optFloat(perspective, "aspectRatio");
				out.perspective.yfov = JsonReader.floatOrDefault(perspective, "yfov", 0.0f);
				out.perspective.zfar = JsonReader.optFloat(perspective, "zfar");
				out.perspective.znear = JsonReader.floatOrDefault(perspective, "znear", 0.0f);
			}
			Any orthographic = JsonReader.get(a, "orthographic");
			if (orthographic != null) {
				out.orthographic = new Orthographic();
				out.orthographic.xmag = JsonReader.floatOrDefault(orthographic, "xmag", 0.0f);
				out.orthographic.ymag = JsonReader.floatOrDefault(orthographic, "ymag", 0.0f);
				out.orthographic.zfar = JsonReader.floatOrDefault(orthographic, "zfar", 0.0f);
				out.orthographic.znear = JsonReader.floatOrDefault(orthographic, "znear", 0.0f);
			}
			out.name = JsonReader.string(a, "name");
			return out;
		}
	}

	public static final class Perspective {
		public Float aspectRatio;
		public float yfov;
		public Float zfar;
		public float znear;
	}

	public static final class Orthographic {
		public float xmag;
		public float ymag;
		public float zfar;
		public float znear;
	}
}
