package com.micheanl.mcgltf.render.texture;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.Supplier;

public final class MipmappedTexture extends AbstractTexture {
	private static final int USAGE = 5;

	public MipmappedTexture(Supplier<String> label, NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int levels = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
		GpuDevice device = RenderSystem.getDevice();
		this.texture = device.createTexture(label, USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, levels);
		this.textureView = device.createTextureView(this.texture);
		this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, true);
		CommandEncoder encoder = device.createCommandEncoder();
		NativeImage level = image;
		for (int i = 0; i < levels; i++) {
			encoder.writeToTexture(this.texture, level, i, 0, 0, 0);
			NativeImage next = i + 1 < levels ? half(level) : null;
			level.close();
			level = next;
		}
	}

	private static NativeImage half(NativeImage src) {
		int sw = src.getWidth();
		int sh = src.getHeight();
		int w = Math.max(1, sw >> 1);
		int h = Math.max(1, sh >> 1);
		NativeImage dst = new NativeImage(NativeImage.Format.RGBA, w, h, false);
		for (int y = 0; y < h; y++) {
			int y0 = y * 2;
			int y1 = Math.min(y0 + 1, sh - 1);
			for (int x = 0; x < w; x++) {
				int x0 = x * 2;
				int x1 = Math.min(x0 + 1, sw - 1);
				dst.setPixel(x, y, avg(src.getPixel(x0, y0), src.getPixel(x1, y0), src.getPixel(x0, y1), src.getPixel(x1, y1)));
			}
		}
		return dst;
	}

	private static int avg(int a, int b, int c, int d) {
		return channel(a, b, c, d, 0) | channel(a, b, c, d, 8) | channel(a, b, c, d, 16) | channel(a, b, c, d, 24);
	}

	private static int channel(int a, int b, int c, int d, int shift) {
		int sum = ((a >>> shift) & 0xFF) + ((b >>> shift) & 0xFF) + ((c >>> shift) & 0xFF) + ((d >>> shift) & 0xFF);
		return ((sum + 2) >> 2) << shift;
	}
}
