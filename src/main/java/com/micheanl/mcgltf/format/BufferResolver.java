package com.micheanl.mcgltf.format;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class BufferResolver {
	private static final String DATA_PREFIX = "data:";
	private static final String BASE64_MARKER = ";base64,";

	private final GltfDocument gltf;
	private final Path dir;
	private final ByteBuffer glbBin;
	private final ByteBuffer[] buffers;

	public BufferResolver(GltfDocument gltf, Path dir, ByteBuffer glbBin) {
		this.gltf = gltf;
		this.dir = dir == null ? null : dir.toAbsolutePath().normalize();
		this.glbBin = glbBin;
		this.buffers = new ByteBuffer[gltf.buffers == null ? 0 : gltf.buffers.size()];
	}

	public ByteBuffer resolveBuffer(int index) {
		ByteBuffer cached = buffers[index];
		if (cached != null) {
			return cached;
		}
		GltfDocument.Buffer buffer = gltf.buffers.get(index);
		ByteBuffer resolved;
		if (buffer.uri == null) {
			if (glbBin == null) {
				throw new GltfException("buffer[" + index + "] 无 uri 且无 GLB BIN chunk");
			}
			resolved = glbBin.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		} else {
			resolved = ByteBuffer.wrap(readUri(buffer.uri)).order(ByteOrder.LITTLE_ENDIAN);
		}
		if (resolved.remaining() < buffer.byteLength) {
			throw new GltfException("buffer[" + index + "] 实际数据小于声明 byteLength");
		}
		buffers[index] = resolved;
		return resolved;
	}

	public ByteBuffer resolveBufferView(int bufferViewIndex) {
		GltfDocument.BufferView bv = gltf.bufferViews.get(bufferViewIndex);
		ByteBuffer buffer = resolveBuffer(bv.buffer);
		return buffer.slice((int) bv.byteOffset, (int) bv.byteLength).order(ByteOrder.LITTLE_ENDIAN);
	}

	public byte[] readUri(String uri) {
		if (uri.startsWith(DATA_PREFIX)) {
			int marker = uri.indexOf(BASE64_MARKER);
			if (marker < 0) {
				throw new GltfException("data URI 非 base64 编码");
			}
			return Base64.getDecoder().decode(uri.substring(marker + BASE64_MARKER.length()));
		}
		if (dir == null) {
			throw new GltfException("无基准目录，无法解析外部 uri: " + uri);
		}
		Path resolved = dir.resolve(percentDecode(uri)).normalize();
		if (!resolved.startsWith(dir)) {
			throw new GltfException("uri 越出模型目录: " + uri);
		}
		try {
			return Files.readAllBytes(resolved);
		} catch (IOException e) {
			throw new GltfException("读取外部文件失败: " + uri, e);
		}
	}

	public byte[] readBufferView(int bufferViewIndex) {
		ByteBuffer view = resolveBufferView(bufferViewIndex);
		byte[] out = new byte[view.remaining()];
		view.duplicate().get(out);
		return out;
	}

	private static String percentDecode(String uri) {
		if (uri.indexOf('%') < 0) {
			return uri;
		}
		StringBuilder out = new StringBuilder(uri.length());
		java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();
		for (int i = 0; i < uri.length(); i++) {
			char c = uri.charAt(i);
			if (c == '%' && i + 2 < uri.length()) {
				pending.write(Integer.parseInt(uri.substring(i + 1, i + 3), 16));
				i += 2;
			} else {
				if (pending.size() > 0) {
					out.append(pending.toString(java.nio.charset.StandardCharsets.UTF_8));
					pending.reset();
				}
				out.append(c);
			}
		}
		if (pending.size() > 0) {
			out.append(pending.toString(java.nio.charset.StandardCharsets.UTF_8));
		}
		return out.toString();
	}
}
