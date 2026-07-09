package com.micheanl.mcgltf.client;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.block.ModelObjects;
import com.micheanl.mcgltf.client.command.ModelCommands;
import com.micheanl.mcgltf.client.item.ItemRenderProperty;
import com.micheanl.mcgltf.client.item.ItemTypeProperty;
import com.micheanl.mcgltf.client.item.ModelSpecialRenderer;
import com.micheanl.mcgltf.client.thumbnail.ThumbnailRenderer;
import com.micheanl.mcgltf.client.ui.HudStatus;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.render.GizmoRenderer;
import com.micheanl.mcgltf.render.WorldRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public final class MCglTFClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EditorConfig.load();
		ModelCommands.register();
		WorldRenderer.init();
		GizmoRenderer.install();
		ModelObjectClient.install();
		EntityRenderers.register(ModelObjects.MODEL_ENTITY, NoopRenderer::new);
		PictureInPictureRendererRegistry.register(context -> new ThumbnailRenderer());
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, MCglTF.id("status"), new HudStatus());
		SpecialModelRenderers.ID_MAPPER.put(MCglTF.id("model"), ModelSpecialRenderer.Unbaked.MAP_CODEC);
		SelectItemModelProperties.ID_MAPPER.put(MCglTF.id("item_render"), ItemRenderProperty.TYPE);
		SelectItemModelProperties.ID_MAPPER.put(MCglTF.id("item_type"), ItemTypeProperty.TYPE);
	}
}
