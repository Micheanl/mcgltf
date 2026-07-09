package com.micheanl.mcgltf.render.gl;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

public final class GlCapabilities {
	private static boolean probed;
	private static boolean compute;
	private static boolean ssbo;
	private static boolean dsa;
	private static boolean meshNV;
	private static boolean meshEXT;

	private GlCapabilities() {
	}

	private static void ensure() {
		if (probed) {
			return;
		}
		RenderSystem.assertOnRenderThread();
		probed = true;
		GLCapabilities caps = GL.getCapabilities();
		ssbo = caps.GL_ARB_shader_storage_buffer_object;
		compute = ssbo && caps.glDispatchCompute != MemoryUtil.NULL;
		dsa = caps.GL_ARB_direct_state_access;
		meshNV = caps.glDrawMeshTasksNV != MemoryUtil.NULL;
		meshEXT = caps.glDrawMeshTasksEXT != MemoryUtil.NULL;
	}

	public static boolean compute() {
		ensure();
		return compute;
	}

	public static boolean mesh() {
		ensure();
		return meshNV || meshEXT;
	}

	public static boolean meshNV() {
		ensure();
		return meshNV;
	}

	public static boolean meshEXT() {
		ensure();
		return meshEXT;
	}
}
