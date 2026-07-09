package com.micheanl.mcgltf.render;

import com.micheanl.mcgltf.MCglTF;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class GizmoRenderer {
	public enum Mode {
		MOVE,
		ROTATE,
		SCALE
	}

	private static final int AXIS_X = 0xF0424F;
	private static final int AXIS_Y = 0x88D63A;
	private static final int AXIS_Z = 0x437FED;
	private static final int PIVOT = 0xE6E6E6;
	private static final int HIGHLIGHT = 0xFFC93B;
	private static final int CONE_SEGMENTS = 12;
	private static final int RING_SEGMENTS = 48;

	private static boolean enabled;
	private static Mode mode = Mode.MOVE;

	private static final Matrix4f MVP = new Matrix4f();
	private static final Vector4f CLIP = new Vector4f();
	private static final float[] TIP_X = new float[3];
	private static final float[] TIP_Y = new float[3];
	private static float originX;
	private static float originY;
	private static float gizmoLength;
	private static boolean projected;
	private static int highlightAxis = -1;

	private static final RenderPipeline OVERLAY_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(MCglTF.id("pipeline/gizmo"))
			.withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build();
	private static final RenderType OVERLAY = RenderType.create("mcgltf_gizmo",
			RenderSetup.builder(OVERLAY_PIPELINE).createRenderSetup());

	private GizmoRenderer() {
	}

	public static void install() {
		LevelRenderEvents.COLLECT_SUBMITS.register(GizmoRenderer::submit);
	}

	public static void show(Mode value) {
		mode = value;
		enabled = true;
	}

	public static void hide() {
		enabled = false;
	}

	public static Mode mode() {
		return mode;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void setHighlight(int axis) {
		highlightAxis = axis;
	}

	private static int highlight(int axis, int rgb) {
		if (axis != highlightAxis) {
			return rgb;
		}
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return (r + (255 - r) * 3 / 5) << 16 | (g + (255 - g) * 3 / 5) << 8 | (b + (255 - b) * 3 / 5);
	}

	private static void submit(LevelRenderContext context) {
		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cameraPos = camera.pos;
		SceneRegistry.Instance highlight = SceneRegistry.selectedManaged();
		if (highlight != null) {
			drawHighlight(context, highlight, cameraPos);
		}
		if (!enabled) {
			return;
		}
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			return;
		}
		double offsetX = instance.x() - cameraPos.x;
		double offsetY = instance.y() - cameraPos.y;
		double offsetZ = instance.z() - cameraPos.z;
		float distance = (float) Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
		float length = Math.max(0.4f, distance * 0.14f);
		float pivot = length * 0.05f;
		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		poseStack.pushPose();
		poseStack.translate(offsetX, offsetY, offsetZ);
		Window window = Minecraft.getInstance().getWindow();
		int guiWidth = window.getGuiScaledWidth();
		int guiHeight = window.getGuiScaledHeight();
		MVP.set(camera.projectionMatrix).mul(camera.viewRotationMatrix)
				.translate((float) offsetX, (float) offsetY, (float) offsetZ);
		gizmoLength = length;
		projected = project(0.0f, 0.0f, 0.0f, guiWidth, guiHeight, -1)
				& project(length, 0.0f, 0.0f, guiWidth, guiHeight, 0)
				& project(0.0f, length, 0.0f, guiWidth, guiHeight, 1)
				& project(0.0f, 0.0f, length, guiWidth, guiHeight, 2);
		collector.submitCustomGeometry(poseStack, OVERLAY, (pose, buffer) -> {
			box(pose, buffer, -pivot, -pivot, -pivot, pivot, pivot, pivot, PIVOT);
			switch (mode) {
				case MOVE -> {
					moveArm(pose, buffer, 0, length, highlight(0, AXIS_X));
					moveArm(pose, buffer, 1, length, highlight(1, AXIS_Y));
					moveArm(pose, buffer, 2, length, highlight(2, AXIS_Z));
				}
				case ROTATE -> {
					ring(pose, buffer, 0, length * 0.92f, length * 0.02f, highlight(0, AXIS_X));
					ring(pose, buffer, 1, length * 0.92f, length * 0.02f, highlight(1, AXIS_Y));
					ring(pose, buffer, 2, length * 0.92f, length * 0.02f, highlight(2, AXIS_Z));
				}
				case SCALE -> {
					scaleArm(pose, buffer, 0, length, highlight(0, AXIS_X));
					scaleArm(pose, buffer, 1, length, highlight(1, AXIS_Y));
					scaleArm(pose, buffer, 2, length, highlight(2, AXIS_Z));
				}
			}
		});
		poseStack.popPose();
	}

	public static int pickAxis(double mouseX, double mouseY) {
		if (!projected) {
			return -1;
		}
		int best = -1;
		double bestDistance = 14.0;
		for (int axis = 0; axis < 3; axis++) {
			double d = segmentDistance(mouseX, mouseY, originX, originY, TIP_X[axis], TIP_Y[axis]);
			if (d < bestDistance) {
				bestDistance = d;
				best = axis;
			}
		}
		return best;
	}

	public static void drag(int axis, double mouseX, double mouseY, double dragX, double dragY) {
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null || !projected || axis < 0 || axis > 2) {
			return;
		}
		if (mode == Mode.ROTATE) {
			double currentAngle = Math.atan2(mouseY - originY, mouseX - originX);
			double previousAngle = Math.atan2(mouseY - dragY - originY, mouseX - dragX - originX);
			double deltaDegrees = Math.toDegrees(currentAngle - previousAngle);
			while (deltaDegrees > 180.0) {
				deltaDegrees -= 360.0;
			}
			while (deltaDegrees < -180.0) {
				deltaDegrees += 360.0;
			}
			float rx = instance.rotationX();
			float ry = instance.rotationY();
			float rz = instance.rotationZ();
			if (axis == 0) {
				rx += (float) deltaDegrees;
			} else if (axis == 1) {
				ry += (float) deltaDegrees;
			} else {
				rz += (float) deltaDegrees;
			}
			instance.rotation(rx, ry, rz);
			return;
		}
		float dx = TIP_X[axis] - originX;
		float dy = TIP_Y[axis] - originY;
		float screenLength = (float) Math.sqrt(dx * dx + dy * dy);
		if (screenLength < 1.0f) {
			return;
		}
		float fraction = (float) ((dragX * dx + dragY * dy) / (screenLength * screenLength));
		if (mode == Mode.MOVE) {
			float delta = fraction * gizmoLength;
			double x = instance.x();
			double y = instance.y();
			double z = instance.z();
			if (axis == 0) {
				x += delta;
			} else if (axis == 1) {
				y += delta;
			} else {
				z += delta;
			}
			instance.position(x, y, z);
		} else {
			instance.scale(Math.max(0.01f, instance.scale() * (1.0f + fraction)));
		}
	}

	private static boolean project(float lx, float ly, float lz, int guiWidth, int guiHeight, int slot) {
		CLIP.set(lx, ly, lz, 1.0f);
		MVP.transform(CLIP);
		if (CLIP.w <= 0.0001f) {
			return false;
		}
		float screenX = (CLIP.x / CLIP.w * 0.5f + 0.5f) * guiWidth;
		float screenY = (0.5f - CLIP.y / CLIP.w * 0.5f) * guiHeight;
		if (slot < 0) {
			originX = screenX;
			originY = screenY;
		} else {
			TIP_X[slot] = screenX;
			TIP_Y[slot] = screenY;
		}
		return true;
	}

	private static double segmentDistance(double px, double py, float ax, float ay, float bx, float by) {
		double dx = bx - ax;
		double dy = by - ay;
		double length2 = dx * dx + dy * dy;
		double t = length2 <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2));
		double cx = ax + t * dx;
		double cy = ay + t * dy;
		return Math.hypot(px - cx, py - cy);
	}

	private static void moveArm(PoseStack.Pose pose, VertexConsumer buffer, int axis, float length, int rgb) {
		float shaftRadius = length * 0.022f;
		float headLength = length * 0.26f;
		float headRadius = length * 0.075f;
		float shaftEnd = length - headLength;
		shaft(pose, buffer, axis, shaftEnd, shaftRadius, rgb);
		cone(pose, buffer, axis, shaftEnd, length, headRadius, rgb);
	}

	private static void scaleArm(PoseStack.Pose pose, VertexConsumer buffer, int axis, float length, int rgb) {
		float shaftRadius = length * 0.022f;
		float handle = length * 0.08f;
		float shaftEnd = length - handle * 2.0f;
		shaft(pose, buffer, axis, shaftEnd, shaftRadius, rgb);
		switch (axis) {
			case 0 -> box(pose, buffer, shaftEnd, -handle, -handle, length, handle, handle, rgb);
			case 1 -> box(pose, buffer, -handle, shaftEnd, -handle, handle, length, handle, rgb);
			default -> box(pose, buffer, -handle, -handle, shaftEnd, handle, handle, length, rgb);
		}
	}

	private static void shaft(PoseStack.Pose pose, VertexConsumer buffer, int axis, float end, float radius, int rgb) {
		switch (axis) {
			case 0 -> box(pose, buffer, 0.0f, -radius, -radius, end, radius, radius, rgb);
			case 1 -> box(pose, buffer, -radius, 0.0f, -radius, radius, end, radius, rgb);
			default -> box(pose, buffer, -radius, -radius, 0.0f, radius, radius, end, rgb);
		}
	}

	private static void cone(PoseStack.Pose pose, VertexConsumer buffer, int axis,
			float baseAlong, float apexAlong, float radius, int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		float prevU = radius;
		float prevV = 0.0f;
		for (int i = 1; i <= CONE_SEGMENTS; i++) {
			float angle = (float) (2.0 * Math.PI * i / CONE_SEGMENTS);
			float u = radius * (float) Math.cos(angle);
			float v = radius * (float) Math.sin(angle);
			vertex(pose, buffer, axis, apexAlong, 0.0f, 0.0f, r, g, b);
			vertex(pose, buffer, axis, baseAlong, prevU, prevV, r, g, b);
			vertex(pose, buffer, axis, baseAlong, u, v, r, g, b);
			vertex(pose, buffer, axis, baseAlong, u, v, r, g, b);
			vertex(pose, buffer, axis, baseAlong, 0.0f, 0.0f, r, g, b);
			vertex(pose, buffer, axis, baseAlong, u, v, r, g, b);
			vertex(pose, buffer, axis, baseAlong, prevU, prevV, r, g, b);
			vertex(pose, buffer, axis, baseAlong, prevU, prevV, r, g, b);
			prevU = u;
			prevV = v;
		}
	}

	private static void ring(PoseStack.Pose pose, VertexConsumer buffer, int axis,
			float radius, float halfWidth, int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		float inner = radius - halfWidth;
		float outer = radius + halfWidth;
		float prevC = 1.0f;
		float prevS = 0.0f;
		for (int i = 1; i <= RING_SEGMENTS; i++) {
			float angle = (float) (2.0 * Math.PI * i / RING_SEGMENTS);
			float c = (float) Math.cos(angle);
			float s = (float) Math.sin(angle);
			vertex(pose, buffer, axis, 0.0f, inner * prevC, inner * prevS, r, g, b);
			vertex(pose, buffer, axis, 0.0f, outer * prevC, outer * prevS, r, g, b);
			vertex(pose, buffer, axis, 0.0f, outer * c, outer * s, r, g, b);
			vertex(pose, buffer, axis, 0.0f, inner * c, inner * s, r, g, b);
			prevC = c;
			prevS = s;
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int axis,
			float along, float u, float v, int r, int g, int b) {
		float x;
		float y;
		float z;
		switch (axis) {
			case 0 -> {
				x = along;
				y = u;
				z = v;
			}
			case 1 -> {
				x = u;
				y = along;
				z = v;
			}
			default -> {
				x = u;
				y = v;
				z = along;
			}
		}
		buffer.addVertex(pose, x, y, z).setColor(r, g, b, 255);
	}

	private static void box(PoseStack.Pose pose, VertexConsumer buffer,
			float ax, float ay, float az, float bx, float by, float bz, int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		quad(pose, buffer, r, g, b, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az);
		quad(pose, buffer, r, g, b, ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz);
		quad(pose, buffer, r, g, b, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz);
		quad(pose, buffer, r, g, b, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az);
		quad(pose, buffer, r, g, b, ax, ay, az, ax, ay, bz, bx, ay, bz, bx, ay, az);
		quad(pose, buffer, r, g, b, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int r, int g, int b,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3) {
		buffer.addVertex(pose, x0, y0, z0).setColor(r, g, b, 255);
		buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, 255);
		buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, 255);
		buffer.addVertex(pose, x3, y3, z3).setColor(r, g, b, 255);
	}

	private static void drawHighlight(LevelRenderContext context, SceneRegistry.Instance instance, Vec3 cameraPos) {
		float[] aabb = instance.model().aabb();
		float scale = instance.scale();
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(instance.x() - cameraPos.x, instance.y() - cameraPos.y, instance.z() - cameraPos.z);
		poseStack.scale(scale, scale, scale);
		context.submitNodeCollector().submitCustomGeometry(poseStack, OVERLAY, (pose, buffer) ->
				wireBox(pose, buffer, aabb[0], aabb[1], aabb[2], aabb[3], aabb[4], aabb[5]));
		poseStack.popPose();
	}

	private static void wireBox(PoseStack.Pose pose, VertexConsumer buffer,
			float x0, float y0, float z0, float x1, float y1, float z1) {
		float extent = Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0));
		float t = Math.max(0.01f, extent * 0.02f);
		box(pose, buffer, x0, y0 - t, z0 - t, x1, y0 + t, z0 + t, HIGHLIGHT);
		box(pose, buffer, x0, y1 - t, z0 - t, x1, y1 + t, z0 + t, HIGHLIGHT);
		box(pose, buffer, x0, y0 - t, z1 - t, x1, y0 + t, z1 + t, HIGHLIGHT);
		box(pose, buffer, x0, y1 - t, z1 - t, x1, y1 + t, z1 + t, HIGHLIGHT);
		box(pose, buffer, x0 - t, y0, z0 - t, x0 + t, y1, z0 + t, HIGHLIGHT);
		box(pose, buffer, x1 - t, y0, z0 - t, x1 + t, y1, z0 + t, HIGHLIGHT);
		box(pose, buffer, x0 - t, y0, z1 - t, x0 + t, y1, z1 + t, HIGHLIGHT);
		box(pose, buffer, x1 - t, y0, z1 - t, x1 + t, y1, z1 + t, HIGHLIGHT);
		box(pose, buffer, x0 - t, y0 - t, z0, x0 + t, y0 + t, z1, HIGHLIGHT);
		box(pose, buffer, x1 - t, y0 - t, z0, x1 + t, y0 + t, z1, HIGHLIGHT);
		box(pose, buffer, x0 - t, y1 - t, z0, x0 + t, y1 + t, z1, HIGHLIGHT);
		box(pose, buffer, x1 - t, y1 - t, z0, x1 + t, y1 + t, z1, HIGHLIGHT);
	}
}
