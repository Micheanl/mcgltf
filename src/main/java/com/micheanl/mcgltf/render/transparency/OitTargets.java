package com.micheanl.mcgltf.render.transparency;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

public final class OitTargets {
	private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
	private static OitTargets instance;

	private GpuTexture accum;
	private GpuTexture reveal;
	private GpuTextureView accumView;
	private GpuTextureView revealView;
	private int width;
	private int height;

	private OitTargets() {
	}

	public static OitTargets ensure(int width, int height) {
		if (instance == null) {
			instance = new OitTargets();
		}
		instance.resize(width, height);
		return instance;
	}

	private void resize(int width, int height) {
		if (accum != null && this.width == width && this.height == height) {
			return;
		}
		close();
		this.width = width;
		this.height = height;
		GpuDevice device = RenderSystem.getDevice();
		accum = device.createTexture("mcgltf_oit_accum", USAGE, GpuFormat.RGBA16_FLOAT, width, height, 1, 1);
		reveal = device.createTexture("mcgltf_oit_reveal", USAGE, GpuFormat.R16_FLOAT, width, height, 1, 1);
		accumView = device.createTextureView(accum);
		revealView = device.createTextureView(reveal);
	}

	public GpuTextureView accumView() {
		return accumView;
	}

	public GpuTextureView revealView() {
		return revealView;
	}

	public void close() {
		if (accumView != null) {
			accumView.close();
			accumView = null;
		}
		if (revealView != null) {
			revealView.close();
			revealView = null;
		}
		if (accum != null) {
			accum.close();
			accum = null;
		}
		if (reveal != null) {
			reveal.close();
			reveal = null;
		}
	}
}
