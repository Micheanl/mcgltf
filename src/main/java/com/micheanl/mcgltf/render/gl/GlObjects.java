package com.micheanl.mcgltf.render.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlBuffer;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;

public final class GlObjects {
	private GlObjects() {
	}

	public static int bufferId(GpuBuffer buffer) {
		return ((GlBuffer) buffer).handle();
	}

	public static void bindSsbo(int index, GpuBuffer buffer) {
		GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, index, bufferId(buffer));
	}

	public static void unbindSsbo(int index) {
		GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, index, 0);
	}

	public static void barrier() {
		GL42C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT | GL42C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT);
	}
}
