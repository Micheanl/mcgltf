package com.micheanl.mcgltf.render;

import com.micheanl.mcgltf.animation.SkeletonPose;
import com.micheanl.mcgltf.render.gl.GlCapabilities;
import com.micheanl.mcgltf.render.gl.MeshShaderRenderer;
import com.micheanl.mcgltf.render.gl.SkinningCompute;
import com.micheanl.mcgltf.render.pipeline.ModelPipelines;
import com.micheanl.mcgltf.render.transparency.OitTargets;
import com.micheanl.mcgltf.compat.iris.IrisCompat;
import com.micheanl.mcgltf.compat.iris.IrisEntityRenderType;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.scene.Model;
import com.micheanl.mcgltf.scene.Model.AlphaMode;
import com.micheanl.mcgltf.scene.VertexLayout;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

public final class WorldRenderer {
	private static final Vector4f WHITE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
	private static final Vector4fc OIT_CLEAR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
	private static final Vector3f ZERO = new Vector3f();
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final Supplier<String> GBUFFER_LABEL = () -> "mcgltf gbuffer";
	private static final float TICKS_PER_SECOND = 20.0f;
	private static final int JOINT_TEXELS = 3;
	private static final int MAX_PALETTE_JOINTS = 256;
	private static final int MAX_MORPHS = 8;
	private static final int INSTANCE_UBO_SIZE = new Std140SizeCalculator()
			.putIVec4().putIVec4().putVec4().putVec4().putInt().putInt().putInt().putInt().get();
	private static final ByteBuffer PALETTE = ByteBuffer.allocateDirect(MAX_PALETTE_JOINTS * JOINT_TEXELS * 16).order(ByteOrder.nativeOrder());
	private static final ByteBuffer INSTANCE = ByteBuffer.allocateDirect(INSTANCE_UBO_SIZE).order(ByteOrder.nativeOrder());
	private static final Matrix4f JOINT_SCRATCH = new Matrix4f();
	private static final int[] MORPH_IDX = new int[MAX_MORPHS];
	private static final float[] MORPH_WT = new float[MAX_MORPHS];
	private static final Vector3f SKIN_POS = new Vector3f();
	private static final Vector3f SKIN_NORMAL = new Vector3f();
	private static final Vector3f SKIN_ACC_POS = new Vector3f();
	private static final Vector3f SKIN_ACC_NORMAL = new Vector3f();
	private static final BlockPos.MutableBlockPos LIGHT_POS = new BlockPos.MutableBlockPos();
	private static final Matrix4f BASE = new Matrix4f();
	private static final Matrix4f MODEL_VIEW = new Matrix4f();
	private static final Matrix4f MVP = new Matrix4f();
	private static final Matrix3f NORMAL_MATRIX = new Matrix3f();
	private static final Quaternionf ROTATION = new Quaternionf();
	private static Matrix4f[] jointMatrixStore = new Matrix4f[0];
	private static Matrix4f[] jointMatrixPalette = new Matrix4f[0];
	private static Call[] callPool = new Call[0];
	private static int callCount;
	private static ShaderCall[] shaderCallPool = new ShaderCall[0];
	private static int shaderCallCount;
	private static GpuBufferSlice[] palettePool = new GpuBufferSlice[0];

	private WorldRenderer() {
	}

	public static boolean toggleShaderSkinning() {
		setShaderSkinning(!EditorConfig.shaderSkinning);
		return EditorConfig.shaderSkinning;
	}

	public static void setShaderSkinning(boolean enabled) {
		EditorConfig.shaderSkinning = enabled;
		EditorConfig.save();
	}

	public static void init() {
		LevelExtractionEvents.END_EXTRACTION.register(context ->
				SceneRegistry.advance(context.deltaTracker().getGameTimeDeltaTicks() / TICKS_PER_SECOND));
		LevelRenderEvents.COLLECT_SUBMITS.register(WorldRenderer::submitFallback);
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(context -> pass(context, false));
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> pass(context, true));
	}

	private static void pass(LevelRenderContext context, boolean blendPass) {
		if (IrisCompat.shaderPackActive()) {
			if (!GlCapabilities.compute() || !EditorConfig.shaderSkinning) {
				return;
			}
			IrisEntityRenderType.ensureAssigned();
			SkinningCompute.init();
			if (!blendPass) {
				computeAll(context);
			}
			drawShader(context, blendPass);
		} else {
			if (EditorConfig.meshShader && GlCapabilities.mesh()) {
				MeshShaderRenderer.init();
				meshRender(context, blendPass);
			} else {
				render(context, blendPass);
			}
		}
	}

	private static final class Call {
		private GpuModel.Part part;
		private GpuModel.Material material;
		private GpuBufferSlice transform;
		private GpuBufferSlice joints;
		private GpuBufferSlice instance;

		private void set(GpuModel.Part part, GpuModel.Material material, GpuBufferSlice transform, GpuBufferSlice joints, GpuBufferSlice instance) {
			this.part = part;
			this.material = material;
			this.transform = transform;
			this.joints = joints;
			this.instance = instance;
		}
	}

	private static final class ShaderCall {
		private GpuModel.Part part;
		private GpuModel.Material material;
		private GpuBuffer vbo;
		private GpuBufferSlice transform;

		private void set(GpuModel.Part part, GpuModel.Material material, GpuBuffer vbo, GpuBufferSlice transform) {
			this.part = part;
			this.material = material;
			this.vbo = vbo;
			this.transform = transform;
		}
	}

	private static Call nextCall() {
		if (callCount == callPool.length) {
			int previous = callPool.length;
			callPool = Arrays.copyOf(callPool, Math.max(8, previous * 2));
			for (int i = previous; i < callPool.length; i++) {
				callPool[i] = new Call();
			}
		}
		return callPool[callCount++];
	}

	private static ShaderCall nextShaderCall() {
		if (shaderCallCount == shaderCallPool.length) {
			int previous = shaderCallPool.length;
			shaderCallPool = Arrays.copyOf(shaderCallPool, Math.max(8, previous * 2));
			for (int i = previous; i < shaderCallPool.length; i++) {
				shaderCallPool[i] = new ShaderCall();
			}
		}
		return shaderCallPool[shaderCallCount++];
	}

	private static void computeAll(LevelRenderContext context) {
		List<SceneRegistry.Instance> instances = SceneRegistry.rendered();
		if (instances.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		Frustum frustum = context.levelState().cameraRenderState.cullFrustum;
		boolean began = false;
		for (SceneRegistry.Instance instance : instances) {
			GpuModel model = instance.model();
			if (!visible(frustum, model.aabb(), instance)) {
				continue;
			}
			GpuInstance gpu = instance.gpu(light(level, instance));
			GpuModel.Part[] parts = model.parts();
			SkeletonPose pose = null;
			for (int i = 0; i < parts.length; i++) {
				if (!gpu.dynamic(i)) {
					continue;
				}
				if (!began) {
					SkinningCompute.begin();
					began = true;
				}
				if (pose == null) {
					pose = instance.animator().evaluate();
				}
				GpuModel.Part part = parts[i];
				Matrix4f[] palette = part.skinVertex() != null
						? jointMatricesShared(pose, model.model().skins()[part.skin()].joints(), model.ibm()[part.skin()]) : null;
				int morphCount = 0;
				if (part.morphBuffer() != null) {
					Arrays.fill(MORPH_IDX, 0);
					Arrays.fill(MORPH_WT, 0.0f);
					morphCount = selectMorphs(pose.weights(part.node()));
				}
				SkinningCompute.run(part.vertex(), part.skinVertex(), part.morphBuffer(), gpu.vbo(i),
						palette, part.vertexCount(), morphCount, MORPH_IDX, MORPH_WT);
			}
		}
		if (began) {
			SkinningCompute.end();
		}
	}

	private static void drawShader(LevelRenderContext context, boolean blendPass) {
		List<SceneRegistry.Instance> instances = SceneRegistry.rendered();
		if (instances.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cameraPos = camera.pos;
		Matrix4f viewRotation = camera.viewRotationMatrix;
		Frustum frustum = camera.cullFrustum;
		DynamicUniforms dynamicUniforms = RenderSystem.getDynamicUniforms();

		shaderCallCount = 0;
		for (SceneRegistry.Instance instance : instances) {
			GpuModel model = instance.model();
			if (!visible(frustum, model.aabb(), instance)) {
				continue;
			}
			GpuInstance gpu = instance.gpu(light(level, instance));
			SkeletonPose pose = instance.animator().evaluate();
			Matrix4f base = rotate(new Matrix4f(viewRotation)
					.translate((float) (instance.x() - cameraPos.x), (float) (instance.y() - cameraPos.y), (float) (instance.z() - cameraPos.z)), instance)
					.scale(instance.scale());
			GpuModel.Part[] parts = model.parts();
			for (int i = 0; i < parts.length; i++) {
				GpuModel.Part part = parts[i];
				GpuModel.Material material = model.materials()[part.material()];
				if ((material.alphaMode() == AlphaMode.BLEND) != blendPass) {
					continue;
				}
				Matrix4f modelView = part.skinVertex() != null ? base : new Matrix4f(base).mul(pose.global(part.node()));
				GpuBufferSlice transform = dynamicUniforms.writeTransform(modelView, WHITE, ZERO, IDENTITY);
				nextShaderCall().set(part, material, gpu.vbo(i), transform);
			}
		}
		if (shaderCallCount == 0) {
			return;
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GameRenderer gameRenderer = context.gameRenderer();
		RenderTarget target = gameRenderer.mainRenderTarget();
		GpuTextureView color = target.getColorTextureView();
		GpuTextureView depth = target.getDepthTextureView();
		GpuTextureView lightmap = gameRenderer.lightmap();
		GpuTextureView overlay = gameRenderer.overlayTexture().getTextureView();
		GpuSampler clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		RenderPass pass = null;
		GpuTextureView boundAlbedo = null;
		RenderPipeline current = null;
		try {
			for (int c = 0; c < shaderCallCount; c++) {
				ShaderCall call = shaderCallPool[c];
				GpuModel.Material material = call.material;
				if (pass == null || material.baseColor() != boundAlbedo) {
					if (pass != null) {
						pass.close();
					}
					pass = encoder.createRenderPass(GBUFFER_LABEL, color, Optional.empty(), depth, OptionalDouble.empty());
					boundAlbedo = material.baseColor();
					current = null;
				}
				RenderPipeline pipeline = IrisEntityRenderType.pipeline(material.alphaMode());
				if (pipeline != current) {
					pass.setPipeline(pipeline);
					RenderSystem.bindDefaultUniforms(pass);
					current = pipeline;
				}
				pass.setUniform("DynamicTransforms", call.transform);
				pass.bindTexture("Sampler0", material.baseColor(), material.baseSampler());
				pass.bindTexture("Sampler1", overlay, clamp);
				pass.bindTexture("Sampler2", lightmap, clamp);
				pass.setVertexBuffer(0, call.vbo.slice());
				pass.setIndexBuffer(call.part.index(), call.part.indexType());
				pass.drawIndexed(call.part.indexCount(), 1, 0, 0, 0);
			}
		} finally {
			if (pass != null) {
				pass.close();
			}
		}
	}

	private static void submitFallback(LevelRenderContext context) {
		if (!IrisCompat.shaderPackActive() || (GlCapabilities.compute() && EditorConfig.shaderSkinning)) {
			return;
		}
		IrisEntityRenderType.ensureAssigned();
		List<SceneRegistry.Instance> instances = SceneRegistry.rendered();
		if (instances.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cameraPos = camera.pos;
		Frustum frustum = camera.cullFrustum;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		for (SceneRegistry.Instance instance : instances) {
			GpuModel model = instance.model();
			if (!visible(frustum, model.aabb(), instance)) {
				continue;
			}
			int light = light(level, instance);
			SkeletonPose pose = instance.animator().evaluate();
			for (GpuModel.Part part : model.parts()) {
				GpuModel.Material material = model.materials()[part.material()];
				Model.Primitive prim = part.prim();
				RenderType renderType = renderType(material);
				poseStack.pushPose();
				poseStack.translate(instance.x() - cameraPos.x, instance.y() - cameraPos.y, instance.z() - cameraPos.z);
				rotate(poseStack, instance);
				poseStack.scale(instance.scale(), instance.scale(), instance.scale());
				if (part.skinVertex() != null) {
					Matrix4f[] jointMatrices = jointMatrices(pose, model.model().skins()[part.skin()].joints(), model.ibm()[part.skin()]);
					collector.submitCustomGeometry(poseStack, renderType, (renderPose, buffer) -> emitSkinned(prim, renderPose, buffer, light, jointMatrices));
				} else {
					poseStack.mulPose(pose.global(part.node()));
					collector.submitCustomGeometry(poseStack, renderType, (renderPose, buffer) -> emit(prim, renderPose, buffer, light));
				}
				poseStack.popPose();
			}
		}
	}

	public static RenderType renderType(GpuModel.Material material) {
		return switch (material.alphaMode()) {
			case BLEND -> IrisEntityRenderType.translucent(material.textureId());
			case MASK -> IrisEntityRenderType.cutout(material.textureId());
			default -> IrisEntityRenderType.solid(material.textureId());
		};
	}

	public static void emit(Model.Primitive prim, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		ByteBuffer vertices = prim.vertices().duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer indices = prim.indices().duplicate().order(ByteOrder.nativeOrder());
		boolean indices32 = prim.indices32();
		for (int i = 0; i + 2 < prim.indexCount(); i += 3) {
			int a = index(indices, indices32, i);
			int b = index(indices, indices32, i + 1);
			int c = index(indices, indices32, i + 2);
			emitVertex(vertices, a, pose, buffer, light);
			emitVertex(vertices, b, pose, buffer, light);
			emitVertex(vertices, c, pose, buffer, light);
		}
	}

	private static int index(ByteBuffer indices, boolean indices32, int i) {
		return indices32 ? indices.getInt(i * 4) : (indices.getShort(i * 2) & 0xFFFF);
	}

	private static void emitVertex(ByteBuffer vertices, int vertex, PoseStack.Pose pose, VertexConsumer buffer, int light) {
		int base = vertex * VertexLayout.STATIC_STRIDE;
		float px = vertices.getFloat(base + VertexLayout.POSITION_OFFSET);
		float py = vertices.getFloat(base + VertexLayout.POSITION_OFFSET + 4);
		float pz = vertices.getFloat(base + VertexLayout.POSITION_OFFSET + 8);
		float nx = vertices.getFloat(base + VertexLayout.NORMAL_OFFSET);
		float ny = vertices.getFloat(base + VertexLayout.NORMAL_OFFSET + 4);
		float nz = vertices.getFloat(base + VertexLayout.NORMAL_OFFSET + 8);
		float u = vertices.getFloat(base + VertexLayout.UV0_OFFSET);
		float v = vertices.getFloat(base + VertexLayout.UV0_OFFSET + 4);
		int color = vertices.getInt(base + VertexLayout.COLOR_OFFSET);
		buffer.addVertex(pose, px, py, pz)
				.setColor(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	private static Matrix4f[] jointMatrices(SkeletonPose pose, int[] joints, Matrix4f[] ibm) {
		Matrix4f[] result = new Matrix4f[joints.length];
		for (int j = 0; j < joints.length; j++) {
			result[j] = new Matrix4f(pose.global(joints[j])).mul(ibm[j]);
		}
		return result;
	}

	private static Matrix4f[] jointMatricesShared(SkeletonPose pose, int[] joints, Matrix4f[] ibm) {
		int count = joints.length;
		if (jointMatrixStore.length < count) {
			int previous = jointMatrixStore.length;
			jointMatrixStore = Arrays.copyOf(jointMatrixStore, count);
			for (int j = previous; j < count; j++) {
				jointMatrixStore[j] = new Matrix4f();
			}
		}
		if (jointMatrixPalette.length != count) {
			jointMatrixPalette = Arrays.copyOf(jointMatrixStore, count);
		}
		for (int j = 0; j < count; j++) {
			jointMatrixPalette[j].set(pose.global(joints[j])).mul(ibm[j]);
		}
		return jointMatrixPalette;
	}

	private static void emitSkinned(Model.Primitive prim, PoseStack.Pose pose, VertexConsumer buffer, int light, Matrix4f[] jointMatrices) {
		ByteBuffer vertices = prim.vertices().duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer skin = prim.skin().duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer indices = prim.indices().duplicate().order(ByteOrder.nativeOrder());
		boolean indices32 = prim.indices32();
		for (int i = 0; i + 2 < prim.indexCount(); i += 3) {
			int a = index(indices, indices32, i);
			int b = index(indices, indices32, i + 1);
			int c = index(indices, indices32, i + 2);
			emitSkinnedVertex(vertices, skin, a, pose, buffer, light, jointMatrices);
			emitSkinnedVertex(vertices, skin, b, pose, buffer, light, jointMatrices);
			emitSkinnedVertex(vertices, skin, c, pose, buffer, light, jointMatrices);
		}
	}

	private static void emitSkinnedVertex(ByteBuffer vertices, ByteBuffer skin, int vertex, PoseStack.Pose pose, VertexConsumer buffer, int light, Matrix4f[] jointMatrices) {
		int base = vertex * VertexLayout.STATIC_STRIDE;
		float px = vertices.getFloat(base + VertexLayout.POSITION_OFFSET);
		float py = vertices.getFloat(base + VertexLayout.POSITION_OFFSET + 4);
		float pz = vertices.getFloat(base + VertexLayout.POSITION_OFFSET + 8);
		float nx = vertices.getFloat(base + VertexLayout.NORMAL_OFFSET);
		float ny = vertices.getFloat(base + VertexLayout.NORMAL_OFFSET + 4);
		float nz = vertices.getFloat(base + VertexLayout.NORMAL_OFFSET + 8);
		float u = vertices.getFloat(base + VertexLayout.UV0_OFFSET);
		float v = vertices.getFloat(base + VertexLayout.UV0_OFFSET + 4);
		int color = vertices.getInt(base + VertexLayout.COLOR_OFFSET);
		int skinBase = vertex * VertexLayout.SKIN_STRIDE;
		SKIN_ACC_POS.set(0.0f, 0.0f, 0.0f);
		SKIN_ACC_NORMAL.set(0.0f, 0.0f, 0.0f);
		float total = 0.0f;
		for (int k = 0; k < 4; k++) {
			float weight = (skin.getShort(skinBase + VertexLayout.WEIGHTS_OFFSET + k * 2) & 0xFFFF) / 65535.0f;
			if (weight <= 0.0f) {
				continue;
			}
			int joint = skin.getShort(skinBase + VertexLayout.JOINTS_OFFSET + k * 2) & 0xFFFF;
			if (joint >= jointMatrices.length) {
				continue;
			}
			Matrix4f matrix = jointMatrices[joint];
			matrix.transformPosition(px, py, pz, SKIN_POS);
			matrix.transformDirection(nx, ny, nz, SKIN_NORMAL);
			SKIN_ACC_POS.add(SKIN_POS.mul(weight));
			SKIN_ACC_NORMAL.add(SKIN_NORMAL.mul(weight));
			total += weight;
		}
		if (total <= 0.0f) {
			SKIN_ACC_POS.set(px, py, pz);
			SKIN_ACC_NORMAL.set(nx, ny, nz);
		}
		buffer.addVertex(pose, SKIN_ACC_POS.x, SKIN_ACC_POS.y, SKIN_ACC_POS.z)
				.setColor(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF, (color >> 24) & 0xFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, SKIN_ACC_NORMAL.x, SKIN_ACC_NORMAL.y, SKIN_ACC_NORMAL.z);
	}

	private static void render(LevelRenderContext context, boolean blendPass) {
		List<SceneRegistry.Instance> instances = SceneRegistry.rendered();
		if (instances.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cameraPos = camera.pos;
		Matrix4f viewRotation = camera.viewRotationMatrix;
		Frustum frustum = camera.cullFrustum;
		DynamicUniforms dynamicUniforms = RenderSystem.getDynamicUniforms();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		int alignment = RenderSystem.getDevice().getDeviceInfo().limits().minUniformOffsetAlignment();

		callCount = 0;
		for (SceneRegistry.Instance instance : instances) {
			GpuModel model = instance.model();
			if (!visible(frustum, model.aabb(), instance)) {
				continue;
			}
			int light = light(level, instance);
			Vector3f lightCoords = new Vector3f(light & 0xFFFF, (light >>> 16) & 0xFFFF, 0.0f);
			Matrix4f base = rotate(new Matrix4f(viewRotation)
					.translate((float) (instance.x() - cameraPos.x), (float) (instance.y() - cameraPos.y), (float) (instance.z() - cameraPos.z)), instance)
					.scale(instance.scale());
			SkeletonPose pose = instance.animator().evaluate();
			int skinCount = model.model().skins().length;
			if (palettePool.length < skinCount) {
				palettePool = new GpuBufferSlice[skinCount];
			}
			for (int s = 0; s < skinCount; s++) {
				palettePool[s] = null;
			}
			for (GpuModel.Part part : model.parts()) {
				GpuModel.Material material = model.materials()[part.material()];
				if ((material.alphaMode() == AlphaMode.BLEND) != blendPass) {
					continue;
				}
				GpuBufferSlice joints = null;
				Matrix4f modelView;
				if (part.skinVertex() != null) {
					int skin = part.skin();
					if (palettePool[skin] == null) {
						palettePool[skin] = palette(encoder, pose, model.model().skins()[skin].joints(), model.ibm()[skin], alignment);
					}
					joints = palettePool[skin];
					modelView = base;
				} else {
					modelView = new Matrix4f(base).mul(pose.global(part.node()));
				}
				GpuBufferSlice morphInstance = part.morphBuffer() != null
						? morphInstance(encoder, pose.weights(part.node()), part.vertexCount(), alignment) : null;
				GpuBufferSlice transform = dynamicUniforms.writeTransform(modelView, WHITE, lightCoords, IDENTITY);
				nextCall().set(part, material, transform, joints, morphInstance);
			}
		}
		if (callCount == 0) {
			return;
		}

		GameRenderer gameRenderer = context.gameRenderer();
		RenderTarget target = gameRenderer.mainRenderTarget();
		GpuTextureView color = target.getColorTextureView();
		GpuTextureView depth = target.getDepthTextureView();
		GpuTextureView lightmap = gameRenderer.lightmap();
		GpuSampler lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		if (blendPass) {
			renderOit(encoder, callPool, callCount, color, depth, lightmap, lightmapSampler);
			return;
		}

		try (RenderPass pass = encoder.createRenderPass(() -> "mcgltf world", color, Optional.empty(), depth, OptionalDouble.empty())) {
			RenderPipeline current = null;
			for (int c = 0; c < callCount; c++) {
				Call call = callPool[c];
				boolean skinned = call.joints != null;
				boolean morphed = call.instance != null;
				RenderPipeline pipeline = ModelPipelines.get(call.material.alphaMode(), call.material.doubleSided(), skinned, morphed);
				if (pipeline != current) {
					pass.setPipeline(pipeline);
					RenderSystem.bindDefaultUniforms(pass);
					current = pipeline;
				}
				bindAndDraw(pass, call, lightmap, lightmapSampler);
			}
		}
	}

	private static void renderOit(CommandEncoder encoder, Call[] calls, int count, GpuTextureView color, GpuTextureView depth, GpuTextureView lightmap, GpuSampler lightmapSampler) {
		int width = color.getWidth(0);
		int height = color.getHeight(0);
		OitTargets targets = OitTargets.ensure(width, height);
		RenderPassDescriptor accumulate = RenderPassDescriptor.create(() -> "mcgltf oit accum")
				.withColorAttachment(targets.accumView(), Optional.of(OIT_CLEAR))
				.withColorAttachment(targets.revealView(), Optional.of(OIT_CLEAR))
				.withDepthAttachment(depth, OptionalDouble.empty())
				.withRenderArea(new RenderPass.RenderArea(0, 0, width, height));
		try (RenderPass pass = encoder.createRenderPass(accumulate)) {
			RenderPipeline current = null;
			for (int c = 0; c < count; c++) {
				Call call = calls[c];
				boolean skinned = call.joints != null;
				boolean morphed = call.instance != null;
				RenderPipeline pipeline = ModelPipelines.oit(call.material.doubleSided(), skinned, morphed);
				if (pipeline != current) {
					pass.setPipeline(pipeline);
					RenderSystem.bindDefaultUniforms(pass);
					current = pipeline;
				}
				bindAndDraw(pass, call, lightmap, lightmapSampler);
			}
		}
		GpuSampler oitSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (RenderPass pass = encoder.createRenderPass(() -> "mcgltf oit resolve", color, Optional.empty(), null, OptionalDouble.empty())) {
			pass.setPipeline(ModelPipelines.resolve());
			pass.bindTexture("AccumSampler", targets.accumView(), oitSampler);
			pass.bindTexture("RevealSampler", targets.revealView(), oitSampler);
			pass.draw(3, 1, 0, 0);
		}
	}

	private static void bindAndDraw(RenderPass pass, Call call, GpuTextureView lightmap, GpuSampler lightmapSampler) {
		boolean skinned = call.joints != null;
		boolean morphed = call.instance != null;
		pass.setUniform("DynamicTransforms", call.transform);
		pass.setUniform("GltfMaterial", call.material.ubo());
		pass.bindTexture("Sampler0", call.material.baseColor(), call.material.baseSampler());
		pass.bindTexture("Sampler2", lightmap, lightmapSampler);
		pass.setVertexBuffer(0, call.part.vertex().slice());
		if (skinned) {
			pass.setUniform("GltfJoints", call.joints);
			pass.setVertexBuffer(1, call.part.skinVertex().slice());
		} else {
			pass.setVertexBuffer(1, null);
		}
		if (morphed) {
			pass.setUniform("GltfInstance", call.instance);
			pass.setUniform("GltfMorph", call.part.morphBuffer());
		}
		pass.setIndexBuffer(call.part.index(), call.part.indexType());
		pass.drawIndexed(call.part.indexCount(), 1, 0, 0, 0);
	}

	private static void meshRender(LevelRenderContext context, boolean blendPass) {
		List<SceneRegistry.Instance> instances = SceneRegistry.rendered();
		if (instances.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cameraPos = camera.pos;
		Matrix4f viewRotation = camera.viewRotationMatrix;
		Matrix4f projection = camera.projectionMatrix;
		Frustum frustum = camera.cullFrustum;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GameRenderer gameRenderer = context.gameRenderer();
		RenderTarget target = gameRenderer.mainRenderTarget();
		GpuTextureView color = target.getColorTextureView();
		GpuTextureView depth = target.getDepthTextureView();
		GpuTextureView lightmap = gameRenderer.lightmap();

		try (RenderPass pass = encoder.createRenderPass(() -> "mcgltf mesh", color, Optional.empty(), depth, OptionalDouble.empty())) {
			MeshShaderRenderer.begin(lightmap);
			for (SceneRegistry.Instance instance : instances) {
				GpuModel model = instance.model();
				if (!visible(frustum, model.aabb(), instance)) {
					continue;
				}
				int light = light(level, instance);
				SkeletonPose pose = instance.animator().evaluate();
				Matrix4f base = rotate(BASE.set(viewRotation)
						.translate((float) (instance.x() - cameraPos.x), (float) (instance.y() - cameraPos.y), (float) (instance.z() - cameraPos.z)), instance)
						.scale(instance.scale());
				for (GpuModel.Part part : model.parts()) {
					GpuModel.Material material = model.materials()[part.material()];
					if ((material.alphaMode() == AlphaMode.BLEND) != blendPass) {
						continue;
					}
					Matrix4f modelView = part.skinVertex() != null ? base : MODEL_VIEW.set(base).mul(pose.global(part.node()));
					projection.mul(modelView, MVP);
					NORMAL_MATRIX.set(modelView);
					Matrix4f[] joints = part.skinVertex() != null
							? jointMatricesShared(pose, model.model().skins()[part.skin()].joints(), model.ibm()[part.skin()]) : null;
					int morphCount = 0;
					if (part.morphBuffer() != null) {
						Arrays.fill(MORPH_IDX, 0);
						Arrays.fill(MORPH_WT, 0.0f);
						morphCount = selectMorphs(pose.weights(part.node()));
					}
					MeshShaderRenderer.draw(part, material, MVP, NORMAL_MATRIX, light, joints, morphCount, MORPH_IDX, MORPH_WT);
				}
			}
			MeshShaderRenderer.end();
		}
	}

	private static GpuBufferSlice palette(CommandEncoder encoder, SkeletonPose pose, int[] joints, Matrix4f[] ibm, int alignment) {
		int count = Math.min(joints.length, MAX_PALETTE_JOINTS);
		PALETTE.clear();
		for (int j = 0; j < count; j++) {
			Matrix4f m = JOINT_SCRATCH.set(pose.global(joints[j])).mul(ibm[j]);
			PALETTE.putFloat(m.m00()).putFloat(m.m10()).putFloat(m.m20()).putFloat(m.m30());
			PALETTE.putFloat(m.m01()).putFloat(m.m11()).putFloat(m.m21()).putFloat(m.m31());
			PALETTE.putFloat(m.m02()).putFloat(m.m12()).putFloat(m.m22()).putFloat(m.m32());
		}
		PALETTE.flip();
		return encoder.transientMemory().uploadStaging(PALETTE, alignment, GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER);
	}

	private static GpuBufferSlice morphInstance(CommandEncoder encoder, float[] weights, int vertexCount, int alignment) {
		Arrays.fill(MORPH_IDX, 0);
		Arrays.fill(MORPH_WT, 0.0f);
		int count = selectMorphs(weights);
		INSTANCE.clear();
		ByteBuffer built = Std140Builder.intoBuffer(INSTANCE)
				.putIVec4(MORPH_IDX[0], MORPH_IDX[1], MORPH_IDX[2], MORPH_IDX[3])
				.putIVec4(MORPH_IDX[4], MORPH_IDX[5], MORPH_IDX[6], MORPH_IDX[7])
				.putVec4(MORPH_WT[0], MORPH_WT[1], MORPH_WT[2], MORPH_WT[3])
				.putVec4(MORPH_WT[4], MORPH_WT[5], MORPH_WT[6], MORPH_WT[7])
				.putInt(count).putInt(vertexCount).putInt(0).putInt(0)
				.get();
		return encoder.transientMemory().uploadStaging(built, alignment, GpuBuffer.USAGE_UNIFORM);
	}

	private static int selectMorphs(float[] weights) {
		if (weights == null) {
			return 0;
		}
		int count = 0;
		for (int t = 0; t < weights.length; t++) {
			float weight = weights[t];
			if (weight == 0.0f) {
				continue;
			}
			if (count < MAX_MORPHS) {
				MORPH_IDX[count] = t;
				MORPH_WT[count] = weight;
				count++;
			} else {
				int min = 0;
				for (int s = 1; s < MAX_MORPHS; s++) {
					if (Math.abs(MORPH_WT[s]) < Math.abs(MORPH_WT[min])) {
						min = s;
					}
				}
				if (Math.abs(weight) > Math.abs(MORPH_WT[min])) {
					MORPH_IDX[min] = t;
					MORPH_WT[min] = weight;
				}
			}
		}
		return count;
	}

	private static Matrix4f rotate(Matrix4f matrix, SceneRegistry.Instance instance) {
		if (instance.rotated()) {
			matrix.rotateXYZ((float) Math.toRadians(instance.rotationX()),
					(float) Math.toRadians(instance.rotationY()),
					(float) Math.toRadians(instance.rotationZ()));
		}
		return matrix;
	}

	private static void rotate(PoseStack poseStack, SceneRegistry.Instance instance) {
		if (instance.rotated()) {
			poseStack.mulPose(ROTATION.rotationXYZ((float) Math.toRadians(instance.rotationX()),
					(float) Math.toRadians(instance.rotationY()),
					(float) Math.toRadians(instance.rotationZ())));
		}
	}

	private static boolean visible(Frustum frustum, float[] aabb, SceneRegistry.Instance instance) {
		float scale = instance.scale();
		return frustum.isVisible(new AABB(
				instance.x() + aabb[0] * scale, instance.y() + aabb[1] * scale, instance.z() + aabb[2] * scale,
				instance.x() + aabb[3] * scale, instance.y() + aabb[4] * scale, instance.z() + aabb[5] * scale));
	}

	private static int light(ClientLevel level, SceneRegistry.Instance instance) {
		LIGHT_POS.set(Mth.floor(instance.x()), Mth.floor(instance.y()), Mth.floor(instance.z()));
		return level.hasChunkAt(LIGHT_POS) ? LightCoordsUtil.getLightCoords(level, LIGHT_POS) : LightCoordsUtil.FULL_BRIGHT;
	}
}
