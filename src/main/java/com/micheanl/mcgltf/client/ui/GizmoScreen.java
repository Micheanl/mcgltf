package com.micheanl.mcgltf.client.ui;

import com.micheanl.mcgltf.render.GizmoRenderer;
import com.micheanl.mcgltf.render.SceneRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class GizmoScreen extends Screen {
	private int axis = -1;
	private boolean rotatingView;

	public GizmoScreen() {
		super(Component.literal("MCglTF Gizmo"));
	}

	@Override
	protected void init() {
		if (SceneRegistry.selected() == null) {
			this.minecraft.gui.setScreen(null);
			return;
		}
		GizmoRenderer.show(GizmoRenderer.mode());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		long window = this.minecraft.getWindow().handle();
		Options options = this.minecraft.options;
		forward(window, options.keyUp, GLFW.GLFW_KEY_W);
		forward(window, options.keyLeft, GLFW.GLFW_KEY_A);
		forward(window, options.keyDown, GLFW.GLFW_KEY_S);
		forward(window, options.keyRight, GLFW.GLFW_KEY_D);
		forward(window, options.keyJump, GLFW.GLFW_KEY_SPACE);
		forward(window, options.keyShift, GLFW.GLFW_KEY_LEFT_SHIFT);
		forward(window, options.keySprint, GLFW.GLFW_KEY_LEFT_CONTROL);
	}

	private static void forward(long window, KeyMapping mapping, int glfwKey) {
		mapping.setDown(GLFW.glfwGetKey(window, glfwKey) == GLFW.GLFW_PRESS);
	}

	@Override
	public void removed() {
		GizmoRenderer.setHighlight(-1);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		GizmoRenderer.setHighlight(axis >= 0 ? axis : GizmoRenderer.pickAxis(mouseX, mouseY));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 1) {
			rotatingView = true;
		} else {
			axis = GizmoRenderer.pickAxis(event.x(), event.y());
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		axis = -1;
		rotatingView = false;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (rotatingView) {
			rotateView(dragX, dragY);
			return true;
		}
		if (axis >= 0) {
			GizmoRenderer.drag(axis, event.x(), event.y(), dragX, dragY);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	private void rotateView(double dragX, double dragY) {
		if (this.minecraft == null || this.minecraft.player == null) {
			return;
		}
		float yaw = this.minecraft.player.getYRot() + (float) dragX * 0.15f;
		float pitch = Math.max(-90.0f, Math.min(90.0f, this.minecraft.player.getXRot() + (float) dragY * 0.15f));
		this.minecraft.player.setYRot(yaw);
		this.minecraft.player.setXRot(pitch);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case GLFW.GLFW_KEY_G -> GizmoRenderer.show(GizmoRenderer.Mode.MOVE);
			case GLFW.GLFW_KEY_R -> GizmoRenderer.show(GizmoRenderer.Mode.ROTATE);
			case GLFW.GLFW_KEY_S -> GizmoRenderer.show(GizmoRenderer.Mode.SCALE);
			case GLFW.GLFW_KEY_ESCAPE -> this.onClose();
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}
}
