package com.micheanl.mcgltf.render.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

public final class SkinningCompute {
	private static final int GROUP = 64;
	private static final int MAX_JOINTS = 256;
	private static final String PATH = "/assets/mcgltf/shaders/compute/skin.comp";

	private static ShaderProgram program;
	private static int jointsSsbo;
	private static ByteBuffer palette;
	private static int savedProgram;

	private SkinningCompute() {
	}

	public static void init() {
		if (program != null) {
			return;
		}
		program = ShaderProgram.link(Map.of(GL43C.GL_COMPUTE_SHADER, ShaderProgram.read(PATH)));
		jointsSsbo = GL15C.glGenBuffers();
		palette = ByteBuffer.allocateDirect(MAX_JOINTS * 64).order(ByteOrder.nativeOrder());
	}

	public static void begin() {
		savedProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
		program.use();
	}

	public static void run(GpuBuffer src, GpuBuffer skin, GpuBuffer morph, GpuBuffer dst,
			Matrix4f[] joints, int vertexCount, int morphCount, int[] morphIdx, float[] morphWt) {
		boolean skinned = joints != null;
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
			GlObjects.bindSsbo(1, skin);
		} else {
			GlObjects.bindSsbo(1, src);
			GlObjects.bindSsbo(2, src);
		}
		GlObjects.bindSsbo(0, src);
		GlObjects.bindSsbo(3, morphCount > 0 ? morph : src);
		GlObjects.bindSsbo(4, dst);

		GL20C.glUniform1i(program.uniform("VertexCount"), vertexCount);
		GL20C.glUniform1i(program.uniform("Skinned"), skinned ? 1 : 0);
		GL20C.glUniform1i(program.uniform("MorphCount"), morphCount);
		GL20C.glUniform4i(program.uniform("MorphIdx0"), morphIdx[0], morphIdx[1], morphIdx[2], morphIdx[3]);
		GL20C.glUniform4i(program.uniform("MorphIdx1"), morphIdx[4], morphIdx[5], morphIdx[6], morphIdx[7]);
		GL20C.glUniform4f(program.uniform("MorphWt0"), morphWt[0], morphWt[1], morphWt[2], morphWt[3]);
		GL20C.glUniform4f(program.uniform("MorphWt1"), morphWt[4], morphWt[5], morphWt[6], morphWt[7]);

		GL43C.glDispatchCompute((vertexCount + GROUP - 1) / GROUP, 1, 1);
	}

	public static void end() {
		GlObjects.barrier();
		GL20C.glUseProgram(savedProgram);
	}
}
