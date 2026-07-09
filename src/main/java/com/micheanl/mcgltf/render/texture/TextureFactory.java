package com.micheanl.mcgltf.render.texture;

import com.micheanl.mcgltf.MCglTF;
import com.micheanl.mcgltf.format.GltfConstants;
import com.micheanl.mcgltf.scene.Model;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class TextureFactory {
	private static DynamicTexture white;
	private static Identifier whiteId;

	private TextureFactory() {
	}

	public static DynamicTexture register(byte[] bytes, Identifier id) {
		DynamicTexture texture = new DynamicTexture(id::toString, readImage(bytes, id));
		Minecraft.getInstance().getTextureManager().register(id, texture);
		return texture;
	}

	public static DynamicTexture white() {
		if (white == null) {
			whiteId = MCglTF.id("white");
			NativeImage image = new NativeImage(1, 1, false);
			image.setPixelABGR(0, 0, 0xFFFFFFFF);
			white = new DynamicTexture(() -> "mcgltf_white", image);
			Minecraft.getInstance().getTextureManager().register(whiteId, white);
		}
		return white;
	}

	public static Identifier whiteId() {
		white();
		return whiteId;
	}

	public static GpuSampler defaultSampler() {
		return RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.LINEAR, FilterMode.LINEAR, false);
	}

	public static GpuSampler sampler(Model.Sampler def) {
		AddressMode wrapU = address(def.wrapS());
		AddressMode wrapV = address(def.wrapT());
		FilterMode min = def.minFilter() == GltfConstants.NEAREST
				|| def.minFilter() == GltfConstants.NEAREST_MIPMAP_NEAREST
				|| def.minFilter() == GltfConstants.NEAREST_MIPMAP_LINEAR ? FilterMode.NEAREST : FilterMode.LINEAR;
		FilterMode mag = def.magFilter() == GltfConstants.NEAREST ? FilterMode.NEAREST : FilterMode.LINEAR;
		return RenderSystem.getSamplerCache().getSampler(wrapU, wrapV, min, mag, false);
	}

	private static NativeImage readImage(byte[] bytes, Identifier id) {
		NativeImage image = decode(bytes);
		if (image == null) {
			NativeImage fallback = new NativeImage(1, 1, false);
			fallback.setPixelABGR(0, 0, 0xFFFFFFFF);
			return fallback;
		}
		return image;
	}

	public static NativeImage decode(byte[] bytes) {
		ByteBuffer file = MemoryUtil.memAlloc(bytes.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			file.put(bytes).flip();
			IntBuffer width = stack.mallocInt(1);
			IntBuffer height = stack.mallocInt(1);
			IntBuffer channels = stack.mallocInt(1);
			ByteBuffer pixels = STBImage.stbi_load_from_memory(file, width, height, channels, 4);
			if (pixels == null) {
				return null;
			}
			return new NativeImage(NativeImage.Format.RGBA, width.get(0), height.get(0), true, MemoryUtil.memAddress(pixels));
		} finally {
			MemoryUtil.memFree(file);
		}
	}

	private static AddressMode address(int wrap) {
		return wrap == GltfConstants.CLAMP_TO_EDGE ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
	}
}
