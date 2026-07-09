package com.micheanl.mcgltf.render;

import com.micheanl.mcgltf.scene.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class EntityVertexWriter {
	public static final int STRIDE = 80;

	private EntityVertexWriter() {
	}

	public static ByteBuffer pack(ByteBuffer source, int count, int packedLight) {
		ByteBuffer src = source.duplicate().order(ByteOrder.nativeOrder());
		ByteBuffer out = ByteBuffer.allocateDirect(count * STRIDE).order(ByteOrder.nativeOrder());
		short block = (short) (packedLight & 0xFFFF);
		short sky = (short) ((packedLight >>> 16) & 0xFFFF);
		for (int v = 0; v < count; v++) {
			int s = v * VertexLayout.STATIC_STRIDE;
			int d = v * STRIDE;
			float u = src.getFloat(s + VertexLayout.UV0_OFFSET);
			float w = src.getFloat(s + VertexLayout.UV0_OFFSET + 4);
			out.putFloat(d, src.getFloat(s + VertexLayout.POSITION_OFFSET));
			out.putFloat(d + 4, src.getFloat(s + VertexLayout.POSITION_OFFSET + 4));
			out.putFloat(d + 8, src.getFloat(s + VertexLayout.POSITION_OFFSET + 8));
			out.putInt(d + 12, src.getInt(s + VertexLayout.COLOR_OFFSET));
			out.putFloat(d + 16, u);
			out.putFloat(d + 20, w);
			out.putShort(d + 24, (short) 0);
			out.putShort(d + 26, (short) 10);
			out.putShort(d + 28, block);
			out.putShort(d + 30, sky);
			out.putFloat(d + 32, src.getFloat(s + VertexLayout.NORMAL_OFFSET));
			out.putFloat(d + 36, src.getFloat(s + VertexLayout.NORMAL_OFFSET + 4));
			out.putFloat(d + 40, src.getFloat(s + VertexLayout.NORMAL_OFFSET + 8));
			out.putFloat(d + 44, 0.0f);
			out.putLong(d + 48, 0L);
			out.putFloat(d + 56, u);
			out.putFloat(d + 60, w);
			out.putFloat(d + 64, src.getFloat(s + VertexLayout.TANGENT_OFFSET));
			out.putFloat(d + 68, src.getFloat(s + VertexLayout.TANGENT_OFFSET + 4));
			out.putFloat(d + 72, src.getFloat(s + VertexLayout.TANGENT_OFFSET + 8));
			out.putFloat(d + 76, -src.getFloat(s + VertexLayout.TANGENT_OFFSET + 12));
		}
		out.position(0).limit(count * STRIDE);
		return out;
	}
}
