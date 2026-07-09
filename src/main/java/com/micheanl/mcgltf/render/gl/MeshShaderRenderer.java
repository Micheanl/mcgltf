package com.micheanl.mcgltf.render.gl;

import com.micheanl.mcgltf.render.GpuModel;
import com.micheanl.mcgltf.scene.Model;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.NVMeshShader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public final class MeshShaderRenderer {
	private static final int GROUP = 32;
	private static final int MAX_JOINTS = 256;
	private static final float[] MVP_SCRATCH = new float[16];
	private static final float[] NRM_SCRATCH = new float[9];

	private static ShaderProgram program;
	private static int jointsSsbo;
	private static ByteBuffer palette;
	private static int savedProgram;
	private static int lightmapId;

	private MeshShaderRenderer() {
	}

	public static void init() {
		if (program != null) {
			return;
		}
		program = ShaderProgram.link(Map.of(
				NVMeshShader.GL_MESH_SHADER_NV, ShaderProgram.read("/assets/mcgltf/shaders/mesh/gltf.mesh"),
				GL20C.GL_FRAGMENT_SHADER, ShaderProgram.read("/assets/mcgltf/shaders/mesh/gltf.frag")));
		jointsSsbo = GL15C.glGenBuffers();
		palette = ByteBuffer.allocateDirect(MAX_JOINTS * 64).order(ByteOrder.nativeOrder());
	}

	public static void begin(GpuTextureView lightmap) {
		lightmapId = ((GlTexture) lightmap.texture()).glId();
		savedProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
		program.use();
		GlStateManager._enableDepthTest();
		GlStateManager._depthFunc(GL11C.GL_LEQUAL);
		GlStateManager._depthMask(true);
	}

	public static void end() {
		GlStateManager._depthMask(true);
		GlStateManager._enableCull();
		GlStateManager._disableBlend(0);
		GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
		GL20C.glUseProgram(savedProgram);
	}

	public static void draw(GpuModel.Part part, GpuModel.Material material, Matrix4f mvp, Matrix3f normalMat, int light,
			Matrix4f[] joints, int morphCount, int[] morphIdx, float[] morphWt) {
		boolean skinned = joints != null;
		if (material.doubleSided()) {
			GlStateManager._disableCull();
		} else {
			GlStateManager._enableCull();
		}
		if (material.alphaMode() == Model.AlphaMode.BLEND) {
			GlStateManager._enableBlend(0);
			GlStateManager._blendFuncSeparate(770, 771, 1, 771);
			GlStateManager._depthMask(false);
		} else {
			GlStateManager._disableBlend(0);
			GlStateManager._depthMask(true);
		}

		if (skinned) {
			palette.clear();
			int count = Math.min(joints.length, MAX_JOINTS);
			for (int j = 0; j < count; j++) {
				joints[j].get(j * 64, palette);
			}
			palette.position(count * 64).flip();
			GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, jointsSsbo);
			GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, palette, GL15C.GL_DYNAMIC_DRAW);
			GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, jointsSsbo);
			GlObjects.bindSsbo(1, part.skinVertex());
		} else {
			GlObjects.bindSsbo(1, part.vertex());
			GlObjects.bindSsbo(2, part.vertex());
		}
		GlObjects.bindSsbo(0, part.vertex());
		GlObjects.bindSsbo(3, morphCount > 0 ? part.morphBuffer() : part.vertex());
		GlObjects.bindSsbo(5, part.index());

		GL20C.glUniformMatrix4fv(program.uniform("MVP"), false, mvp.get(MVP_SCRATCH));
		GL20C.glUniformMatrix3fv(program.uniform("NormalMat"), false, normalMat.get(NRM_SCRATCH));
		GL20C.glUniform1i(program.uniform("TriangleCount"), part.indexCount() / 3);
		GL20C.glUniform1i(program.uniform("VertexCount"), part.vertexCount());
		GL20C.glUniform1i(program.uniform("Skinned"), skinned ? 1 : 0);
		GL20C.glUniform1i(program.uniform("Indices32"), part.indexType() == IndexType.INT ? 1 : 0);
		GL20C.glUniform1i(program.uniform("MorphCount"), morphCount);
		GL20C.glUniform4i(program.uniform("MorphIdx0"), morphIdx[0], morphIdx[1], morphIdx[2], morphIdx[3]);
		GL20C.glUniform4i(program.uniform("MorphIdx1"), morphIdx[4], morphIdx[5], morphIdx[6], morphIdx[7]);
		GL20C.glUniform4f(program.uniform("MorphWt0"), morphWt[0], morphWt[1], morphWt[2], morphWt[3]);
		GL20C.glUniform4f(program.uniform("MorphWt1"), morphWt[4], morphWt[5], morphWt[6], morphWt[7]);
		GL20C.glUniform2f(program.uniform("LightUv"), (light & 0xFFFF) / 256.0f, ((light >>> 16) & 0xFFFF) / 256.0f);

		Model.Material src = material.src();
		if (src != null) {
			float[] bc = src.baseColor();
			float[] e = src.emissiveFactor();
			float s = src.emissiveStrength();
			GL20C.glUniform4f(program.uniform("BaseColorFactor"), bc[0], bc[1], bc[2], bc[3]);
			GL20C.glUniform3f(program.uniform("Emissive"), e[0] * s, e[1] * s, e[2] * s);
			GL20C.glUniform1f(program.uniform("Metallic"), src.metallic());
			GL20C.glUniform1f(program.uniform("Roughness"), src.roughness());
			GL20C.glUniform1f(program.uniform("AlphaCutoff"), src.alphaCutoff());
		} else {
			GL20C.glUniform4f(program.uniform("BaseColorFactor"), 1.0f, 1.0f, 1.0f, 1.0f);
			GL20C.glUniform3f(program.uniform("Emissive"), 0.0f, 0.0f, 0.0f);
			GL20C.glUniform1f(program.uniform("Metallic"), 0.0f);
			GL20C.glUniform1f(program.uniform("Roughness"), 1.0f);
			GL20C.glUniform1f(program.uniform("AlphaCutoff"), 0.5f);
		}
		GL20C.glUniform1i(program.uniform("MaskMode"), material.alphaMode() == Model.AlphaMode.MASK ? 1 : 0);

		GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
		GlStateManager._bindTexture(((GlTexture) material.baseColor().texture()).glId());
		GlStateManager._activeTexture(GL13C.GL_TEXTURE0 + 1);
		GlStateManager._bindTexture(lightmapId);
		GL20C.glUniform1i(program.uniform("Sampler0"), 0);
		GL20C.glUniform1i(program.uniform("Lightmap"), 1);

		NVMeshShader.glDrawMeshTasksNV(0, (part.indexCount() / 3 + GROUP - 1) / GROUP);
	}
}
