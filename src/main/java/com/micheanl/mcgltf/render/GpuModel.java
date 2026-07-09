package com.micheanl.mcgltf.render;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.compat.iris.IrisCompat;
import com.micheanl.mcgltf.render.texture.LabPbrEncoder;
import com.micheanl.mcgltf.render.texture.MipmappedTexture;
import com.micheanl.mcgltf.render.texture.TextureFactory;
import com.micheanl.mcgltf.scene.Model;
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

import java.nio.ByteBuffer;
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

	public record Material(GpuBuffer ubo, GpuTextureView baseColor, GpuSampler baseSampler,
			Identifier textureId, DynamicTexture albedo,
			Model.AlphaMode alphaMode, boolean doubleSided, Model.Material src) {
	}

	private final Model model;
	private final Part[] parts;
	private final Material[] materials;
	private final Matrix4f[][] ibm;
	private final List<AbstractTexture> ownedTextures;

	private GpuModel(Model model, Part[] parts, Material[] materials, Matrix4f[][] ibm, List<AbstractTexture> ownedTextures) {
		this.model = model;
		this.parts = parts;
		this.materials = materials;
		this.ibm = ibm;
		this.ownedTextures = ownedTextures;
	}

	public Model model() {
		return model;
	}

	public Part[] parts() {
		return parts;
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
		return new GpuModel(model, parts.toArray(Part[]::new), materials, ibm, ownedTextures);
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
