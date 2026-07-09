package com.micheanl.mcgltf.client.ui;

import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.render.GizmoRenderer;
import com.micheanl.mcgltf.render.SceneRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HudStatus implements HudElement {
	private static final int MARGIN = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int TEXT_COLOR = 0xFFFFFFFF;

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!EditorConfig.hud) {
			return;
		}
		SceneRegistry.Instance instance = SceneRegistry.selected();
		if (instance == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int right = minecraft.getWindow().getGuiScaledWidth() - MARGIN;
		List<Component> lines = new ArrayList<>(3);
		lines.add(title(instance));
		lines.add(transform(instance));
		if (GizmoRenderer.enabled()) {
			lines.add(gizmoLine());
		}
		int y = MARGIN;
		for (Component line : lines) {
			int width = font.width(line);
			graphics.textWithBackdrop(font, line, right - width, y, width, TEXT_COLOR);
			y += LINE_HEIGHT;
		}
	}

	private static Component title(SceneRegistry.Instance instance) {
		return Component.literal("MCglTF ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("▶" + SceneRegistry.selectedIndex() + " ").withStyle(ChatFormatting.GREEN))
				.append(Component.literal(instance.source()).withStyle(ChatFormatting.WHITE));
	}

	private static Component transform(SceneRegistry.Instance instance) {
		return Component.literal(String.format(Locale.ROOT, "@ %.1f %.1f %.1f", instance.x(), instance.y(), instance.z())).withStyle(ChatFormatting.AQUA)
				.append(Component.literal(String.format(Locale.ROOT, "   ×%.3f", instance.scale())).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(String.format(Locale.ROOT, "   ↻ %.0f %.0f %.0f",
						instance.rotationX(), instance.rotationY(), instance.rotationZ())).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	private static Component gizmoLine() {
		String name = switch (GizmoRenderer.mode()) {
			case MOVE -> "移动";
			case ROTATE -> "旋转";
			case SCALE -> "缩放";
		};
		return Component.literal("gizmo ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(name).withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("  拖拽轴 · G/R/S 切换").withStyle(ChatFormatting.DARK_GRAY));
	}
}
