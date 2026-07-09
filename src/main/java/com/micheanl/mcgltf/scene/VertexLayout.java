package com.micheanl.mcgltf.scene;

public final class VertexLayout {
	public static final int STATIC_STRIDE = 60;
	public static final int POSITION_OFFSET = 0;
	public static final int NORMAL_OFFSET = 12;
	public static final int TANGENT_OFFSET = 24;
	public static final int UV0_OFFSET = 40;
	public static final int UV1_OFFSET = 48;
	public static final int COLOR_OFFSET = 56;

	public static final int SKIN_STRIDE = 16;
	public static final int JOINTS_OFFSET = 0;
	public static final int WEIGHTS_OFFSET = 8;

	private VertexLayout() {
	}
}
