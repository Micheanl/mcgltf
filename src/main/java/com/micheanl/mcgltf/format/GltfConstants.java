package com.micheanl.mcgltf.format;

public final class GltfConstants {
	public static final int NEAREST = 9728;
	public static final int LINEAR = 9729;
	public static final int NEAREST_MIPMAP_NEAREST = 9984;
	public static final int LINEAR_MIPMAP_NEAREST = 9985;
	public static final int NEAREST_MIPMAP_LINEAR = 9986;
	public static final int LINEAR_MIPMAP_LINEAR = 9987;
	public static final int CLAMP_TO_EDGE = 33071;
	public static final int MIRRORED_REPEAT = 33648;
	public static final int REPEAT = 10497;

	public static final int MODE_POINTS = 0;
	public static final int MODE_LINES = 1;
	public static final int MODE_LINE_LOOP = 2;
	public static final int MODE_LINE_STRIP = 3;
	public static final int MODE_TRIANGLES = 4;
	public static final int MODE_TRIANGLE_STRIP = 5;
	public static final int MODE_TRIANGLE_FAN = 6;

	private GltfConstants() {
	}

	public static boolean validWrap(int wrap) {
		return wrap == CLAMP_TO_EDGE || wrap == MIRRORED_REPEAT || wrap == REPEAT;
	}

	public static boolean validMagFilter(int filter) {
		return filter == NEAREST || filter == LINEAR;
	}

	public static boolean validMinFilter(int filter) {
		return filter == NEAREST || filter == LINEAR
				|| (filter >= NEAREST_MIPMAP_NEAREST && filter <= LINEAR_MIPMAP_LINEAR);
	}
}
