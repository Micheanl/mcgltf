package com.micheanl.mcgltf.compat.iris;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.scene.Model.AlphaMode;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IrisEntityRenderType {
	private static final VertexFormat FLOAT_ENTITY = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG16_SINT)
			.addAttribute("UV2", GpuFormat.RG16_SINT)
			.addAttribute("Normal", GpuFormat.RGBA32_FLOAT)
			.addAttribute("iris_Entity", GpuFormat.RGBA16_UINT)
			.addAttribute("mc_midTexCoord", GpuFormat.RG32_FLOAT)
			.addAttribute("at_tangent", GpuFormat.RGBA32_FLOAT)
			.build();

	private static final RenderPipeline SOLID = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation(MCglTF.id("pipeline/iris_entity_solid"))
			.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
			.withVertexBinding(0, FLOAT_ENTITY)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();

	private static final RenderPipeline CUTOUT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation(MCglTF.id("pipeline/iris_entity_cutout"))
			.withShaderDefine("ALPHA_CUTOUT", 0.1f)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
			.withVertexBinding(0, FLOAT_ENTITY)
			.withCull(false)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();

	private static final RenderPipeline TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
			.withLocation(MCglTF.id("pipeline/iris_entity_translucent"))
			.withShaderDefine("ALPHA_CUTOUT", 0.1f)
			.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
			.withVertexBinding(0, FLOAT_ENTITY)
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withCull(false)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.build();

	private static final Map<Identifier, RenderType> SOLID_CACHE = new ConcurrentHashMap<>();
	private static final Map<Identifier, RenderType> CUTOUT_CACHE = new ConcurrentHashMap<>();
	private static final Map<Identifier, RenderType> TRANSLUCENT_CACHE = new ConcurrentHashMap<>();

	private static boolean assigned;

	private IrisEntityRenderType() {
	}

	public static void ensureAssigned() {
		if (assigned) {
			return;
		}
		assigned = true;
		IrisCompat.copyEntity(SOLID, CUTOUT, TRANSLUCENT);
	}

	public static RenderPipeline pipeline(AlphaMode mode) {
		return switch (mode) {
			case BLEND -> TRANSLUCENT;
			case MASK -> CUTOUT;
			default -> SOLID;
		};
	}

	public static RenderType solid(Identifier texture) {
		return SOLID_CACHE.computeIfAbsent(texture, t -> RenderType.create("gltf_iris_solid",
				RenderSetup.builder(SOLID).withTexture("Sampler0", t).useLightmap().useOverlay().createRenderSetup()));
	}

	public static RenderType cutout(Identifier texture) {
		return CUTOUT_CACHE.computeIfAbsent(texture, t -> RenderType.create("gltf_iris_cutout",
				RenderSetup.builder(CUTOUT).withTexture("Sampler0", t).useLightmap().useOverlay().createRenderSetup()));
	}

	public static RenderType translucent(Identifier texture) {
		return TRANSLUCENT_CACHE.computeIfAbsent(texture, t -> RenderType.create("gltf_iris_translucent",
				RenderSetup.builder(TRANSLUCENT).withTexture("Sampler0", t).useLightmap().useOverlay().sortOnUpload().createRenderSetup()));
	}
}
