package com.micheanl.mcgltf.format;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public record GlbContainer(byte[] json, ByteBuffer binaryChunk) {
	private static final int MAGIC = 0x46546C67;
	private static final int VERSION = 2;
	private static final int CHUNK_JSON = 0x4E4F534A;
	private static final int CHUNK_BIN = 0x004E4942;
	private static final int HEADER_BYTES = 12;

	public static boolean isGlb(ByteBuffer source) {
		return source.remaining() >= HEADER_BYTES
				&& source.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(source.position()) == MAGIC;
	}

	public static GlbContainer parse(ByteBuffer source) {
		ByteBuffer buf = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		long fileSize = buf.remaining();
		if (fileSize < HEADER_BYTES || buf.getInt() != MAGIC) {
			throw new GltfException("invalid GLB magic");
		}
		int version = buf.getInt();
		if (version != VERSION) {
			throw new GltfException("unsupported GLB version: " + version);
		}
		long declared = Integer.toUnsignedLong(buf.getInt());
		if (declared > fileSize) {
			throw new GltfException("GLB declared length exceeds file size");
		}
		byte[] jsonBytes = null;
		ByteBuffer bin = null;
		while (buf.remaining() >= 8) {
			int chunkLength = buf.getInt();
			int chunkType = buf.getInt();
			if (chunkLength < 0 || chunkLength > buf.remaining()) {
				throw new GltfException("GLB chunk length out of bounds");
			}
			int start = buf.position();
			if (chunkType == CHUNK_JSON && jsonBytes == null) {
				jsonBytes = new byte[chunkLength];
				buf.get(start, jsonBytes);
			} else if (chunkType == CHUNK_BIN && bin == null) {
				bin = buf.slice(start, chunkLength).order(ByteOrder.LITTLE_ENDIAN);
			}
			buf.position(start + chunkLength);
		}
		if (jsonBytes == null) {
			throw new GltfException("GLB missing JSON chunk");
		}
		return new GlbContainer(jsonBytes, bin);
	}
}
