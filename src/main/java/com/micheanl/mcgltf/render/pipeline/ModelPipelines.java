package com.micheanl.mcgltf.render.pipeline;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.scene.Model.AlphaMode;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;

import java.util.Optional;

public final class ModelPipelines {
	public static final VertexFormat STATIC_FORMAT = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Normal", GpuFormat.RGB32_FLOAT)
			.addAttribute("Tangent", GpuFormat.RGBA32_FLOAT)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.build();

	public static final VertexFormat SKIN_FORMAT = VertexFormat.builder(0)
			.addAttribute("Joints", GpuFormat.RGBA16_UINT)
			.addAttribute("Weights", GpuFormat.RGBA16_UNORM)
			.build();

	private static final BindGroupLayout MATERIAL_LAYOUT = BindGroupLayout.builder()
			.withUniform("GltfMaterial", UniformType.UNIFORM_BUFFER)
			.withSampler("Sampler0")
			.withSampler("Sampler2")
			.build();

	private static final BindGroupLayout JOINTS_LAYOUT = BindGroupLayout.builder()
			.withUniform("GltfJoints", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
			.build();

	private static final BindGroupLayout MORPH_LAYOUT = BindGroupLayout.builder()
			.withUniform("GltfInstance", UniformType.UNIFORM_BUFFER)
			.withUniform("GltfMorph", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
			.build();

	private static final BindGroupLayout OIT_RESOLVE_LAYOUT = BindGroupLayout.builder()
			.withSampler("AccumSampler")
			.withSampler("RevealSampler")
			.build();

	private static final BlendFunction OIT_BLEND = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE);

	private static final int MODES = AlphaMode.values().length;
	private static final RenderPipeline[] CACHE = new RenderPipeline[MODES * 2 * 2 * 2];
	private static final RenderPipeline[] LOD_CACHE = new RenderPipeline[MODES * 2];
	private static final RenderPipeline[] OIT_CACHE = new RenderPipeline[2 * 2 * 2];
	private static RenderPipeline resolve;

	private ModelPipelines() {
	}

	public static RenderPipeline get(AlphaMode mode, boolean doubleSided, boolean skinned, boolean morphed) {
		int index = (((mode.ordinal() * 2 + (doubleSided ? 1 : 0)) * 2 + (skinned ? 1 : 0)) * 2) + (morphed ? 1 : 0);
		RenderPipeline pipeline = CACHE[index];
		if (pipeline == null) {
			pipeline = build(mode, doubleSided, skinned, morphed);
			CACHE[index] = pipeline;
		}
		return pipeline;
	}

	public static RenderPipeline lod(AlphaMode mode, boolean doubleSided) {
		int index = mode.ordinal() * 2 + (doubleSided ? 1 : 0);
		RenderPipeline pipeline = LOD_CACHE[index];
		if (pipeline == null) {
			pipeline = buildLod(mode, doubleSided);
			LOD_CACHE[index] = pipeline;
		}
		return pipeline;
	}

	public static RenderPipeline oit(boolean doubleSided, boolean skinned, boolean morphed) {
		int index = ((doubleSided ? 1 : 0) * 2 + (skinned ? 1 : 0)) * 2 + (morphed ? 1 : 0);
		RenderPipeline pipeline = OIT_CACHE[index];
		if (pipeline == null) {
			pipeline = buildOit(doubleSided, skinned, morphed);
			OIT_CACHE[index] = pipeline;
		}
		return pipeline;
	}

	public static RenderPipeline resolve() {
		if (resolve == null) {
			resolve = buildResolve();
		}
		return resolve;
	}

	private static RenderPipeline buildLod(AlphaMode mode, boolean doubleSided) {
		String variant = "lod_" + mode.name().toLowerCase() + (doubleSided ? "_nocull" : "_cull");
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(MCglTF.id("pipeline/gltf_" + variant))
				.withVertexShader(MCglTF.id("core/gltf"))
				.withFragmentShader(MCglTF.id("core/gltf"))
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.FOG)
				.withBindGroupLayout(MATERIAL_LAYOUT)
				.withVertexBinding(0, STATIC_FORMAT)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(!doubleSided)
				.withShaderDefine("LOD_SIMPLE");
		if (mode == AlphaMode.MASK) {
			builder.withShaderDefine("ALPHA_MASK");
		}
		if (mode == AlphaMode.BLEND) {
			builder.withShaderDefine("ALPHA_BLEND")
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
		} else {
			builder.withDepthStencilState(DepthStencilState.DEFAULT);
		}
		return builder.build();
	}

	private static RenderPipeline build(AlphaMode mode, boolean doubleSided, boolean skinned, boolean morphed) {
		String variant = mode.name().toLowerCase()
				+ (doubleSided ? "_nocull" : "_cull")
				+ (skinned ? "_skinned" : "")
				+ (morphed ? "_morphed" : "");
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(MCglTF.id("pipeline/gltf_" + variant))
				.withVertexShader(MCglTF.id("core/gltf"))
				.withFragmentShader(MCglTF.id("core/gltf"))
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.FOG)
				.withBindGroupLayout(MATERIAL_LAYOUT)
				.withVertexBinding(0, STATIC_FORMAT)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(!doubleSided);
		if (skinned) {
			builder.withShaderDefine("SKINNED")
					.withVertexBinding(1, SKIN_FORMAT)
					.withBindGroupLayout(JOINTS_LAYOUT);
		}
		if (morphed) {
			builder.withShaderDefine("MORPHED")
					.withBindGroupLayout(MORPH_LAYOUT);
		}
		if (mode == AlphaMode.MASK) {
			builder.withShaderDefine("ALPHA_MASK");
		}
		if (mode == AlphaMode.BLEND) {
			builder.withShaderDefine("ALPHA_BLEND")
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
		} else {
			builder.withDepthStencilState(DepthStencilState.DEFAULT);
		}
		return builder.build();
	}

	private static RenderPipeline buildOit(boolean doubleSided, boolean skinned, boolean morphed) {
		String variant = "oit"
				+ (doubleSided ? "_nocull" : "_cull")
				+ (skinned ? "_skinned" : "")
				+ (morphed ? "_morphed" : "");
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(MCglTF.id("pipeline/gltf_" + variant))
				.withVertexShader(MCglTF.id("core/gltf"))
				.withFragmentShader(MCglTF.id("core/gltf"))
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.withBindGroupLayout(BindGroupLayouts.FOG)
				.withBindGroupLayout(MATERIAL_LAYOUT)
				.withVertexBinding(0, STATIC_FORMAT)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(!doubleSided)
				.withShaderDefine("OIT")
				.withColorTargetState(0, new ColorTargetState(Optional.of(OIT_BLEND), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
				.withColorTargetState(1, new ColorTargetState(Optional.of(OIT_BLEND), GpuFormat.R16_FLOAT, ColorTargetState.WRITE_ALL))
				.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
		if (skinned) {
			builder.withShaderDefine("SKINNED")
					.withVertexBinding(1, SKIN_FORMAT)
					.withBindGroupLayout(JOINTS_LAYOUT);
		}
		if (morphed) {
			builder.withShaderDefine("MORPHED")
					.withBindGroupLayout(MORPH_LAYOUT);
		}
		return builder.build();
	}

	private static RenderPipeline buildResolve() {
		return RenderPipeline.builder()
				.withLocation(MCglTF.id("pipeline/gltf_oit_resolve"))
				.withVertexShader(MCglTF.id("core/oit_resolve"))
				.withFragmentShader(MCglTF.id("core/oit_resolve"))
				.withBindGroupLayout(OIT_RESOLVE_LAYOUT)
				.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
				.withCull(false)
				.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.SRC_ALPHA)))
				.build();
	}
}
