package com.micheanl.mcgltf.client.ui;

import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.render.WorldRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public final class SettingsScreen extends Screen {
	private static final int TITLE_COLOR = ARGB.color(255, 232, 232, 236);
	private static final int SECTION_COLOR = ARGB.color(255, 150, 200, 255);
	private static final int WIDGET_WIDTH = 220;

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("mcgltf.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = (this.width - WIDGET_WIDTH) / 2;
		int y = this.height / 4;
		addRenderableWidget(Checkbox.builder(Component.translatable("mcgltf.config.mesh"), this.font)
				.pos(x, y + 16)
				.selected(EditorConfig.meshShader)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcgltf.config.mesh.desc")))
				.onValueChange((checkbox, value) -> {
					EditorConfig.meshShader = value;
					EditorConfig.save();
				})
				.build());
		addRenderableWidget(Checkbox.builder(Component.translatable("mcgltf.config.shaderskin"), this.font)
				.pos(x, y + 44)
				.selected(EditorConfig.shaderSkinning)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcgltf.config.shaderskin.desc")))
				.onValueChange((checkbox, value) -> WorldRenderer.setShaderSkinning(value))
				.build());
		addRenderableWidget(Checkbox.builder(Component.translatable("mcgltf.config.thumbnails"), this.font)
				.pos(x, y + 72)
				.selected(EditorConfig.thumbnails)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcgltf.config.thumbnails.desc")))
				.onValueChange((checkbox, value) -> {
					EditorConfig.thumbnails = value;
					EditorConfig.save();
				})
				.build());
		addRenderableWidget(Checkbox.builder(Component.translatable("mcgltf.config.itemrender"), this.font)
				.pos(x, y + 100)
				.selected(EditorConfig.itemRender)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcgltf.config.itemrender.desc")))
				.onValueChange((checkbox, value) -> {
					EditorConfig.itemRender = value;
					EditorConfig.save();
				})
				.build());
		addRenderableWidget(Checkbox.builder(Component.translatable("mcgltf.config.hud"), this.font)
				.pos(x, y + 128)
				.selected(EditorConfig.hud)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcgltf.config.hud.desc")))
				.onValueChange((checkbox, value) -> {
					EditorConfig.hud = value;
					EditorConfig.save();
				})
				.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
				.bounds((this.width - WIDGET_WIDTH) / 2, this.height - 32, WIDGET_WIDTH, 20)
				.build());
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.text(this.font, this.title, (this.width - this.font.width(this.title)) / 2, 18, TITLE_COLOR, true);
		graphics.text(this.font, Component.translatable("mcgltf.config.render"), (this.width - WIDGET_WIDTH) / 2, this.height / 4, SECTION_COLOR, true);
	}
}
