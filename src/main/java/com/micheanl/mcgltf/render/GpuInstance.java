package com.micheanl.mcgltf.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;

public final class GpuInstance implements AutoCloseable {
	private final GpuBuffer[] vbo;
	private final boolean[] dynamic;

	private GpuInstance(GpuBuffer[] vbo, boolean[] dynamic) {
		this.vbo = vbo;
		this.dynamic = dynamic;
	}

	public static GpuInstance create(GpuModel model, int packedLight) {
		GpuDevice device = RenderSystem.getDevice();
		GpuModel.Part[] parts = model.parts();
		GpuBuffer[] vbo = new GpuBuffer[parts.length];
		boolean[] dynamic = new boolean[parts.length];
		for (int i = 0; i < parts.length; i++) {
			GpuModel.Part part = parts[i];
			ByteBuffer packed = EntityVertexWriter.pack(part.prim().vertices(), part.vertexCount(), packedLight);
			vbo[i] = device.createBuffer(() -> "mcgltf_entity", GpuBuffer.USAGE_VERTEX, packed);
			dynamic[i] = part.skinVertex() != null || part.morphBuffer() != null;
		}
		return new GpuInstance(vbo, dynamic);
	}

	public GpuBuffer vbo(int part) {
		return vbo[part];
	}

	public boolean dynamic(int part) {
		return dynamic[part];
	}

	@Override
	public void close() {
		for (GpuBuffer buffer : vbo) {
			buffer.close();
		}
	}
}
