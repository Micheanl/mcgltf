package com.micheanl.mcgltf.render;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.compat.iris.IrisCompat;
import com.micheanl.mcgltf.render.dispatch.RenderClass;
import com.micheanl.mcgltf.render.texture.LabPbrEncoder;
import com.micheanl.mcgltf.render.texture.MipmappedTexture;
import com.micheanl.mcgltf.render.texture.TextureFactory;
import com.micheanl.mcgltf.scene.Model;
import com.micheanl.mcgltf.scene.VertexLayout;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.meshoptimizer.MeshOptimizer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class GpuModel implements AutoCloseable {
	private static final int MATERIAL_UBO_SIZE = new Std140SizeCalculator().putVec4().putFloat().putInt().putInt().get();
	private static final float[] WHITE_COLOR = {1.0f, 1.0f, 1.0f};
	private static final AtomicInteger TEXTURE_ID = new AtomicInteger();

	public record Part(GpuBuffer vertex, GpuBuffer index, IndexType indexType, int indexCount, int material, int node,
			GpuBuffer skinVertex, int skin, GpuBuffer morphBuffer, int morphTargets, int vertexCount, Model.Primitive prim) {
	}

	public record LodPart(GpuBuffer index, IndexType indexType, int indexCount, int lod) {
	}

	public record Material(GpuBuffer ubo, GpuTextureView baseColor, GpuSampler baseSampler,
			Identifier textureId, DynamicTexture albedo,
			Model.AlphaMode alphaMode, boolean doubleSided, Model.Material src) {
	}

	private static final int LOD_LEVELS = 3;
	private static final float[] LOD_RATIOS = {1.0f, 0.25f, 0.05f};
	private static final float LOD_ERROR = 0.02f;
	private static final int MIN_TRIANGLES = 12;

	private final Model model;
	private final Part[] parts;
	private final Material[] materials;
	private final Matrix4f[][] ibm;
	private final List<AbstractTexture> ownedTextures;
	private final RenderClass renderClass;
	private final LodPart[][] lodParts;

	private GpuModel(Model model, Part[] parts, Material[] materials, Matrix4f[][] ibm, List<AbstractTexture> ownedTextures, RenderClass renderClass, LodPart[][] lodParts) {
		this.model = model;
		this.parts = parts;
		this.materials = materials;
		this.ibm = ibm;
		this.ownedTextures = ownedTextures;
		this.renderClass = renderClass;
		this.lodParts = lodParts;
	}

	public Model model() {
		return model;
	}

	public Part[] parts() {
		return parts;
	}

	public LodPart[] lodPart(int partIndex) {
		return lodParts[partIndex];
	}

	public int lodLevels() {
		return lodParts.length > 0 ? lodParts[0].length : 1;
	}

	public Material[] materials() {
		return materials;
	}

	public Matrix4f[][] ibm() {
		return ibm;
	}

	public float[] aabb() {
		return model.aabb();
	}

	public RenderClass renderClass() {
		return renderClass;
	}

	private static LodPart[][] fallbackLods(Part[] parts) {
		LodPart[][] lods = new LodPart[parts.length][];
		for (int p = 0; p < parts.length; p++) {
			lods[p] = new LodPart[]{new LodPart(parts[p].index(), parts[p].indexType(), parts[p].indexCount(), 0)};
		}
		return lods;
	}

	private static LodPart[][] generateLods(GpuDevice device, Part[] parts, RenderClass rc) {
		LodPart[][] lods = new LodPart[parts.length][];
		for (int p = 0; p < parts.length; p++) {
			lods[p] = buildLodChain(device, parts[p], rc);
		}
		return lods;
	}

	private static LodPart[] buildLodChain(GpuDevice device, Part part, RenderClass rc) {
		if (rc == RenderClass.TERRAIN) {
			int triangles = part.indexCount() / 3;
			LodPart[] chain = new LodPart[LOD_LEVELS];
			chain[0] = new LodPart(part.index(), part.indexType(), part.indexCount(), 0);
			for (int lod = 1; lod < LOD_LEVELS; lod++) {
				int target = Math.max(MIN_TRIANGLES, (int)(triangles * LOD_RATIOS[lod]));
				if (target >= triangles) {
					chain[lod] = chain[lod - 1];
				} else {
					chain[lod] = simplify(device, part, target * 3, lod);
				}
			}
			return chain;
		}
		return new LodPart[]{new LodPart(part.index(), part.indexType(), part.indexCount(), 0)};
	}

	private static LodPart simplify(GpuDevice device, Part part, int targetIndexCount, int lod) {
		Model.Primitive prim = part.prim();
		ByteBuffer vertexData = prim.vertices().duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer indexData = prim.indices().duplicate().order(ByteOrder.nativeOrder());
		boolean indices32 = prim.indices32();
		int vertexCount = part.vertexCount();
		int srcIndexCount = prim.indexCount();
		int[] sourceIndices = new int[srcIndexCount];
		for (int i = 0; i < srcIndexCount; i++) {
			sourceIndices[i] = indices32 ? indexData.getInt(i * 4) : (indexData.getShort(i * 2) & 0xFFFF);
		}
		FloatBuffer positions = MemoryUtil.memAllocFloat(vertexCount * 3);
		try {
			for (int v = 0; v < vertexCount; v++) {
				int base = v * VertexLayout.STATIC_STRIDE;
				positions.put(vertexData.getFloat(base + VertexLayout.POSITION_OFFSET));
				positions.put(vertexData.getFloat(base + VertexLayout.POSITION_OFFSET + 4));
				positions.put(vertexData.getFloat(base + VertexLayout.POSITION_OFFSET + 8));
			}
			positions.flip();
			int[] destIndices = new int[targetIndexCount];
			IntBuffer source = MemoryUtil.memAllocInt(srcIndexCount);
			IntBuffer dest = MemoryUtil.memAllocInt(targetIndexCount);
			try {
				source.put(sourceIndices).flip();
				int result = (int) MeshOptimizer.meshopt_simplify(dest, source, positions, vertexCount, 12L, targetIndexCount, LOD_ERROR, 0, null);
				dest.get(destIndices);
				int actualCount = result < targetIndexCount ? result : targetIndexCount;
				if (actualCount < 3) {
					return new LodPart(part.index(), part.indexType(), part.indexCount(), lod);
				}
				MeshOptimizer.meshopt_optimizeVertexCache(dest, dest, vertexCount);
				dest.position(0).limit(actualCount);
				MeshOptimizer.meshopt_optimizeOverdraw(dest, dest, positions, vertexCount, 12, 1.05f);
				dest.position(0).limit(actualCount);
				boolean out32 = vertexCount > 0xFFFF;
				ByteBuffer outBuf = ByteBuffer.allocateDirect(actualCount * (out32 ? 4 : 2)).order(ByteOrder.nativeOrder());
				if (out32) {
					for (int i = 0; i < actualCount; i++) {
						outBuf.putInt(destIndices[i]);
					}
				} else {
					for (int i = 0; i < actualCount; i++) {
						outBuf.putShort((short) destIndices[i]);
					}
				}
				outBuf.flip();
				GpuBuffer indexBuffer = device.createBuffer(() -> "mcgltf_lod" + lod, GpuBuffer.USAGE_INDEX, outBuf);
				return new LodPart(indexBuffer, out32 ? IndexType.INT : IndexType.SHORT, actualCount, lod);
			} finally {
				MemoryUtil.memFree(dest);
				MemoryUtil.memFree(source);
			}
		} finally {
			MemoryUtil.memFree(positions);
		}
	}

	public static GpuModel upload(Model model) {
		GpuDevice device = RenderSystem.getDevice();
		List<AbstractTexture> ownedTextures = new ArrayList<>();

		Model.Material[] source = model.materials();
		Material[] materials = new Material[source.length + 1];
		for (int i = 0; i < source.length; i++) {
			materials[i] = material(device, model, source[i], ownedTextures);
		}
		int fallback = source.length;
		materials[fallback] = fallbackMaterial(device);

		Matrix4f[][] ibm = inverseBindMatrices(model);

		List<Part> parts = new ArrayList<>();
		for (int nodeIndex : model.topoOrder()) {
			Model.Node node = model.nodes()[nodeIndex];
			if (node.mesh() < 0) {
				continue;
			}
			for (Model.Primitive prim : model.meshes()[node.mesh()].prims()) {
				if (prim.topo() != Model.TopologyType.TRIANGLES) {
					continue;
				}
				GpuBuffer vertex = device.createBuffer(() -> "mcgltf_vbo", GpuBuffer.USAGE_VERTEX, prim.vertices());
				GpuBuffer index = device.createBuffer(() -> "mcgltf_ibo", GpuBuffer.USAGE_INDEX, prim.indices());
				int material = prim.material() < 0 ? fallback : prim.material();
				IndexType indexType = prim.indices32() ? IndexType.INT : IndexType.SHORT;
				GpuBuffer skinVertex = null;
				int skin = -1;
				if (node.skin() >= 0 && prim.skin() != null) {
					skinVertex = device.createBuffer(() -> "mcgltf_skin", GpuBuffer.USAGE_VERTEX, prim.skin());
					skin = node.skin();
				}
				GpuBuffer morphBuffer = null;
				int morphTargets = 0;
				if (prim.morph() != null) {
					morphBuffer = morphTexels(device, prim);
					morphTargets = prim.morph().targets();
				}
				parts.add(new Part(vertex, index, indexType, prim.indexCount(), material, nodeIndex,
						skinVertex, skin, morphBuffer, morphTargets, prim.vertexCount(), prim));
			}
		}
		Part[] partArray = parts.toArray(Part[]::new);
		RenderClass rc = RenderClass.classify(model.stats());
		LodPart[][] lodParts;
		try {
			lodParts = generateLods(device, partArray, rc);
		} catch (Exception e) {
			lodParts = fallbackLods(partArray);
		}
		return new GpuModel(model, partArray, materials, ibm, ownedTextures, rc, lodParts);
	}

	private static Matrix4f[][] inverseBindMatrices(Model model) {
		Model.Skin[] skins = model.skins();
		Matrix4f[][] ibm = new Matrix4f[skins.length][];
		float[] scratch = new float[16];
		for (int s = 0; s < skins.length; s++) {
			int joints = skins[s].joints().length;
			float[] data = skins[s].inverseBindMatrices();
			ibm[s] = new Matrix4f[joints];
			for (int j = 0; j < joints; j++) {
				System.arraycopy(data, j * 16, scratch, 0, 16);
				ibm[s][j] = new Matrix4f().set(scratch);
			}
		}
		return ibm;
	}

	private static GpuBuffer morphTexels(GpuDevice device, Model.Primitive prim) {
		Model.Morph morph = prim.morph();
		int targets = morph.targets();
		int vertexCount = prim.vertexCount();
		int perTarget = morph.floatsPerTarget(vertexCount);
		float[] data = morph.data();
		ByteBuffer buffer = ByteBuffer.allocateDirect(targets * vertexCount * 16).order(java.nio.ByteOrder.nativeOrder());
		for (int t = 0; t < targets; t++) {
			int base = t * perTarget;
			for (int v = 0; v < vertexCount; v++) {
				buffer.putFloat(data[base + v * 3]).putFloat(data[base + v * 3 + 1]).putFloat(data[base + v * 3 + 2]).putFloat(0.0f);
			}
		}
		buffer.flip();
		return device.createBuffer(() -> "mcgltf_morph", GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, buffer);
	}

	private static Material material(GpuDevice device, Model model, Model.Material material,
			List<AbstractTexture> ownedTextures) {
		DynamicTexture albedo = albedo(model, material, ownedTextures);
		Identifier textureId = MCglTF.id("mat/" + TEXTURE_ID.getAndIncrement());
		Minecraft.getInstance().getTextureManager().register(textureId, albedo);
		ownedTextures.add(albedo);
		GpuTextureView baseColor = albedo.getTextureView();
		GpuSampler baseSampler = sampler(model, material.baseColorTexture());
		GpuBuffer ubo = materialUniform(device, material);
		return new Material(ubo, baseColor, baseSampler, textureId, albedo,
				material.alphaMode(), material.doubleSided(), material);
	}

	private static GpuBuffer materialUniform(GpuDevice device, Model.Material material) {
		int flags = material.unlit() ? 1 : 0;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer buffer = Std140Builder.onStack(stack, MATERIAL_UBO_SIZE)
					.putVec4(1.0f, 1.0f, 1.0f, 1.0f)
					.putFloat(material.alphaCutoff())
					.putInt(flags)
					.putInt(0)
					.get();
			return device.createBuffer(() -> "mcgltf_mat", GpuBuffer.USAGE_UNIFORM, buffer);
		}
	}

	private static GpuSampler sampler(Model model, Model.TextureBinding ref) {
		if (ref == null) {
			return TextureFactory.defaultSampler();
		}
		int sampler = model.textures()[ref.texture()].sampler();
		return sampler >= 0 ? TextureFactory.sampler(model.samplers()[sampler]) : TextureFactory.defaultSampler();
	}

	private static DynamicTexture albedo(Model model, Model.Material material, List<AbstractTexture> ownedTextures) {
		NativeImage albedoImage = LabPbrEncoder.albedo(imageBytes(model, material.baseColorTexture()),
				material.baseColor(),
				imageBytes(model, material.occlusionTexture()), material.occlusionStrength(),
				imageBytes(model, material.emissiveTexture()), material.emissiveFactor(), material.emissiveStrength());
		DynamicTexture albedo = new DynamicTexture(() -> "mcgltf_albedo", albedoImage);
		NativeImage normalImage = LabPbrEncoder.normal(imageBytes(model, material.normalTexture()), material.normalScale());
		Model.Specular specular = material.specular();
		Model.Clearcoat clearcoat = material.clearcoat();
		Model.Sheen sheen = material.sheen();
		float specularFactor = specular != null ? specular.factor() : 1.0f;
		float[] specularColor = specular != null ? specular.colorFactor() : WHITE_COLOR;
		byte[] specularTextureBytes = specular != null ? imageBytes(model, specular.factorTexture()) : null;
		float clearcoatFactor = clearcoat != null ? clearcoat.factor() : 0.0f;
		float clearcoatRoughness = clearcoat != null ? clearcoat.roughness() : 0.0f;
		float[] sheenColor = sheen != null ? sheen.colorFactor() : null;
		float sheenRoughness = sheen != null ? sheen.roughness() : 0.0f;
		boolean needSpecular = material.metallicRoughnessTexture() != null || material.emissiveTexture() != null
				|| specular != null || clearcoat != null || sheen != null;
		NativeImage specularImage = needSpecular
				? LabPbrEncoder.specular(imageBytes(model, material.metallicRoughnessTexture()), material.metallic(), material.roughness(), material.ior(),
						specularFactor, specularColor, specularTextureBytes,
						clearcoatFactor, clearcoatRoughness, sheenColor, sheenRoughness,
						imageBytes(model, material.emissiveTexture()), material.emissiveFactor(), material.emissiveStrength())
				: null;
		AbstractTexture normal = normalImage != null ? new MipmappedTexture(() -> "mcgltf_n", normalImage) : null;
		AbstractTexture specularTexture = specularImage != null ? new MipmappedTexture(() -> "mcgltf_s", specularImage) : null;
		if (normal != null) {
			ownedTextures.add(normal);
		}
		if (specularTexture != null) {
			ownedTextures.add(specularTexture);
		}
		if (normal != null || specularTexture != null) {
			IrisCompat.registerPbr(albedo, normal, specularTexture);
		}
		return albedo;
	}

	private static byte[] imageBytes(Model model, Model.TextureBinding ref) {
		if (ref == null) {
			return null;
		}
		int image = model.textures()[ref.texture()].image();
		return image >= 0 ? model.images()[image].bytes() : null;
	}

	private static Material fallbackMaterial(GpuDevice device) {
		GpuBuffer ubo;
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer buffer = Std140Builder.onStack(stack, MATERIAL_UBO_SIZE)
					.putVec4(1.0f, 1.0f, 1.0f, 1.0f)
					.putFloat(0.5f)
					.putInt(0)
					.putInt(0)
					.get();
			ubo = device.createBuffer(() -> "mcgltf_mat_default", GpuBuffer.USAGE_UNIFORM, buffer);
		}
		GpuTextureView white = TextureFactory.white().getTextureView();
		GpuSampler sampler = TextureFactory.defaultSampler();
		return new Material(ubo, white, sampler, TextureFactory.whiteId(), null,
				Model.AlphaMode.OPAQUE, false, null);
	}

	@Override
	public void close() {
		for (Part part : parts) {
			part.vertex().close();
			part.index().close();
			if (part.skinVertex() != null) {
				part.skinVertex().close();
			}
			if (part.morphBuffer() != null) {
				part.morphBuffer().close();
			}
		}
		for (LodPart[] chain : lodParts) {
			for (int lod = 1; lod < chain.length; lod++) {
				if (chain[lod].index() != chain[0].index()) {
					chain[lod].index().close();
				}
			}
		}
		for (Material material : materials) {
			material.ubo().close();
			if (material.albedo() != null) {
				IrisCompat.unregisterPbr(material.albedo());
			}
		}
		for (AbstractTexture texture : ownedTextures) {
			texture.close();
		}
	}
}
