package com.micheanl.mcgltf.format;

import java.nio.ByteBuffer;

public final class AccessorReader {
	public static final int BYTE = 5120;
	public static final int UNSIGNED_BYTE = 5121;
	public static final int SHORT = 5122;
	public static final int UNSIGNED_SHORT = 5123;
	public static final int UNSIGNED_INT = 5125;
	public static final int FLOAT = 5126;

	private static final int COLUMN_ALIGN = 4;

	private AccessorReader() {
	}

	public static int componentCount(String type) {
		return switch (type) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4", "MAT2" -> 4;
			case "MAT3" -> 9;
			case "MAT4" -> 16;
			default -> throw new GltfException("accessor type 无效: " + type);
		};
	}

	public static int componentSize(int componentType) {
		return switch (componentType) {
			case BYTE, UNSIGNED_BYTE -> 1;
			case SHORT, UNSIGNED_SHORT -> 2;
			case UNSIGNED_INT, FLOAT -> 4;
			default -> throw new GltfException("componentType 无效: " + componentType);
		};
	}

	public static boolean validComponentType(int componentType) {
		return componentType == BYTE || componentType == UNSIGNED_BYTE || componentType == SHORT
				|| componentType == UNSIGNED_SHORT || componentType == UNSIGNED_INT || componentType == FLOAT;
	}

	private static int matrixRows(String type) {
		return switch (type) {
			case "MAT2" -> 2;
			case "MAT3" -> 3;
			case "MAT4" -> 4;
			default -> 0;
		};
	}

	public static int tightElementSize(String type, int componentType) {
		int size = componentSize(componentType);
		int rows = matrixRows(type);
		if (rows == 0) {
			return componentCount(type) * size;
		}
		int columnStride = align(rows * size);
		return rows * columnStride;
	}

	private static int align(int bytes) {
		return (bytes + COLUMN_ALIGN - 1) / COLUMN_ALIGN * COLUMN_ALIGN;
	}

	private static int componentOffset(String type, int componentType, int component) {
		int size = componentSize(componentType);
		int rows = matrixRows(type);
		if (rows == 0) {
			return component * size;
		}
		int columnStride = align(rows * size);
		return component / rows * columnStride + component % rows * size;
	}

	public static float[] readFloats(GltfDocument gltf, BufferResolver sources, int accessorIndex) {
		GltfDocument.Accessor accessor = gltf.accessors.get(accessorIndex);
		int comps = componentCount(accessor.type);
		float[] out = new float[accessor.count * comps];
		boolean normalized = Boolean.TRUE.equals(accessor.normalized);
		if (accessor.bufferView != null) {
			ByteBuffer view = sources.resolveBufferView(accessor.bufferView);
			GltfDocument.BufferView bv = gltf.bufferViews.get(accessor.bufferView);
			int stride = bv.byteStride != null ? bv.byteStride : tightElementSize(accessor.type, accessor.componentType);
			fillFloats(out, view, (int) accessor.byteOffset, stride, accessor.count, accessor.type, accessor.componentType, normalized);
		}
		if (accessor.sparse != null) {
			applySparseFloats(gltf, sources, accessor, out, comps, normalized);
		}
		return out;
	}

	public static int[] readInts(GltfDocument gltf, BufferResolver sources, int accessorIndex) {
		GltfDocument.Accessor accessor = gltf.accessors.get(accessorIndex);
		int comps = componentCount(accessor.type);
		int[] out = new int[accessor.count * comps];
		if (accessor.bufferView != null) {
			ByteBuffer view = sources.resolveBufferView(accessor.bufferView);
			GltfDocument.BufferView bv = gltf.bufferViews.get(accessor.bufferView);
			int stride = bv.byteStride != null ? bv.byteStride : tightElementSize(accessor.type, accessor.componentType);
			fillInts(out, view, (int) accessor.byteOffset, stride, accessor.count, accessor.type, accessor.componentType);
		}
		if (accessor.sparse != null) {
			applySparseInts(gltf, sources, accessor, out, comps);
		}
		return out;
	}

	public static Cursor cursor(GltfDocument gltf, BufferResolver sources, int accessorIndex) {
		GltfDocument.Accessor accessor = gltf.accessors.get(accessorIndex);
		ByteBuffer view = sources.resolveBufferView(accessor.bufferView);
		GltfDocument.BufferView bufferView = gltf.bufferViews.get(accessor.bufferView);
		int stride = bufferView.byteStride != null ? bufferView.byteStride
				: tightElementSize(accessor.type, accessor.componentType);
		boolean normalized = Boolean.TRUE.equals(accessor.normalized);
		return new Cursor(view, (int) accessor.byteOffset, stride, accessor.type, accessor.componentType, normalized);
	}

	private static void fillFloats(float[] out, ByteBuffer view, int base, int stride, int count,
			String type, int componentType, boolean normalized) {
		int comps = componentCount(type);
		for (int i = 0; i < count; i++) {
			int elementBase = base + i * stride;
			for (int c = 0; c < comps; c++) {
				out[i * comps + c] = readFloat(view, elementBase + componentOffset(type, componentType, c), componentType, normalized);
			}
		}
	}

	private static void fillInts(int[] out, ByteBuffer view, int base, int stride, int count,
			String type, int componentType) {
		int comps = componentCount(type);
		for (int i = 0; i < count; i++) {
			int elementBase = base + i * stride;
			for (int c = 0; c < comps; c++) {
				out[i * comps + c] = readInt(view, elementBase + componentOffset(type, componentType, c), componentType);
			}
		}
	}

	private static float readFloat(ByteBuffer view, int position, int componentType, boolean normalized) {
		return switch (componentType) {
			case FLOAT -> view.getFloat(position);
			case UNSIGNED_BYTE -> {
				int v = view.get(position) & 0xFF;
				yield normalized ? v / 255.0f : v;
			}
			case BYTE -> {
				byte v = view.get(position);
				yield normalized ? Math.max(v / 127.0f, -1.0f) : v;
			}
			case UNSIGNED_SHORT -> {
				int v = view.getShort(position) & 0xFFFF;
				yield normalized ? v / 65535.0f : v;
			}
			case SHORT -> {
				short v = view.getShort(position);
				yield normalized ? Math.max(v / 32767.0f, -1.0f) : v;
			}
			case UNSIGNED_INT -> Integer.toUnsignedLong(view.getInt(position));
			default -> throw new GltfException("componentType 无效: " + componentType);
		};
	}

	private static int readInt(ByteBuffer view, int position, int componentType) {
		return switch (componentType) {
			case UNSIGNED_BYTE -> view.get(position) & 0xFF;
			case BYTE -> view.get(position);
			case UNSIGNED_SHORT -> view.getShort(position) & 0xFFFF;
			case SHORT -> view.getShort(position);
			case UNSIGNED_INT, FLOAT -> view.getInt(position);
			default -> throw new GltfException("componentType 无效: " + componentType);
		};
	}

	private static int[] sparseIndices(GltfDocument gltf, BufferResolver sources, GltfDocument.Sparse sparse) {
		int[] indices = new int[sparse.count];
		ByteBuffer view = sources.resolveBufferView(sparse.indices.bufferView);
		int size = componentSize(sparse.indices.componentType);
		int base = (int) sparse.indices.byteOffset;
		for (int i = 0; i < sparse.count; i++) {
			indices[i] = readInt(view, base + i * size, sparse.indices.componentType);
		}
		return indices;
	}

	private static void applySparseFloats(GltfDocument gltf, BufferResolver sources, GltfDocument.Accessor accessor,
			float[] out, int comps, boolean normalized) {
		GltfDocument.Sparse sparse = accessor.sparse;
		int[] indices = sparseIndices(gltf, sources, sparse);
		ByteBuffer values = sources.resolveBufferView(sparse.values.bufferView);
		int elementSize = tightElementSize(accessor.type, accessor.componentType);
		int base = (int) sparse.values.byteOffset;
		for (int i = 0; i < sparse.count; i++) {
			int target = indices[i] * comps;
			int elementBase = base + i * elementSize;
			for (int c = 0; c < comps; c++) {
				out[target + c] = readFloat(values, elementBase + componentOffset(accessor.type, accessor.componentType, c),
						accessor.componentType, normalized);
			}
		}
	}

	private static void applySparseInts(GltfDocument gltf, BufferResolver sources, GltfDocument.Accessor accessor,
			int[] out, int comps) {
		GltfDocument.Sparse sparse = accessor.sparse;
		int[] indices = sparseIndices(gltf, sources, sparse);
		ByteBuffer values = sources.resolveBufferView(sparse.values.bufferView);
		int elementSize = tightElementSize(accessor.type, accessor.componentType);
		int base = (int) sparse.values.byteOffset;
		for (int i = 0; i < sparse.count; i++) {
			int target = indices[i] * comps;
			int elementBase = base + i * elementSize;
			for (int c = 0; c < comps; c++) {
				out[target + c] = readInt(values, elementBase + componentOffset(accessor.type, accessor.componentType, c),
						accessor.componentType);
			}
		}
	}

	public static final class Cursor {
		private final ByteBuffer view;
		private final int baseOffset;
		private final int stride;
		private final String type;
		private final int componentType;
		private final boolean normalized;

		private Cursor(ByteBuffer view, int baseOffset, int stride, String type, int componentType, boolean normalized) {
			this.view = view;
			this.baseOffset = baseOffset;
			this.stride = stride;
			this.type = type;
			this.componentType = componentType;
			this.normalized = normalized;
		}

		public float readFloatComponent(int element, int component) {
			return readFloat(view, baseOffset + element * stride + componentOffset(type, componentType, component),
					componentType, normalized);
		}

		public int readIntComponent(int element, int component) {
			return readInt(view, baseOffset + element * stride + componentOffset(type, componentType, component),
					componentType);
		}
	}
}
