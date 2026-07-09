package com.micheanl.mcgltf.render.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Mth;

public final class LabPbrEncoder {
	private static final float DIELECTRIC_F0_MAX = 0.898f;
	private static final int DIELECTRIC_G_MAX = 229;
	private static final float CLEARCOAT_F0 = 0.04f;
	private static final float SHEEN_SMOOTHNESS_WEIGHT = 0.25f;

	private LabPbrEncoder() {
	}

	public static NativeImage albedo(byte[] baseBytes, float[] baseColorFactor, byte[] occlusionBytes, float occlusionStrength,
			byte[] emissiveBytes, float[] emissiveFactor, float emissiveStrength) {
		NativeImage base = baseBytes != null ? TextureFactory.decode(baseBytes) : null;
		NativeImage occlusion = occlusionBytes != null ? TextureFactory.decode(occlusionBytes) : null;
		NativeImage emissive = emissiveBytes != null ? TextureFactory.decode(emissiveBytes) : null;
		int width = base != null ? base.getWidth() : occlusion != null ? occlusion.getWidth() : emissive != null ? emissive.getWidth() : 1;
		int height = base != null ? base.getHeight() : occlusion != null ? occlusion.getHeight() : emissive != null ? emissive.getHeight() : 1;
		int[] basePixels = base != null ? base.getPixelsABGR() : null;
		int[] occlusionPixels = occlusion != null ? occlusion.getPixelsABGR() : null;
		int[] emissivePixels = emissive != null ? emissive.getPixelsABGR() : null;
		int occlusionWidth = occlusion != null ? occlusion.getWidth() : 0;
		int occlusionHeight = occlusion != null ? occlusion.getHeight() : 0;
		int emissiveWidth = emissive != null ? emissive.getWidth() : 0;
		int emissiveHeight = emissive != null ? emissive.getHeight() : 0;
		float emissiveRed = emissiveFactor[0] * emissiveStrength;
		float emissiveGreen = emissiveFactor[1] * emissiveStrength;
		float emissiveBlue = emissiveFactor[2] * emissiveStrength;
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = basePixels != null ? basePixels[y * width + x] : 0xFFFFFFFF;
				float alpha = ((pixel >> 24) & 0xFF) / 255.0f * baseColorFactor[3];
				float red = (pixel & 0xFF) / 255.0f * baseColorFactor[0];
				float green = ((pixel >> 8) & 0xFF) / 255.0f * baseColorFactor[1];
				float blue = ((pixel >> 16) & 0xFF) / 255.0f * baseColorFactor[2];
				if (occlusionPixels != null) {
					float occ = (occlusionPixels[(y * occlusionHeight / height) * occlusionWidth + (x * occlusionWidth / width)] & 0xFF) / 255.0f;
					float ao = Mth.clamp(1.0f + occlusionStrength * (occ - 1.0f), 0.0f, 1.0f);
					red *= ao;
					green *= ao;
					blue *= ao;
				}
				if (emissivePixels != null) {
					int sample = emissivePixels[(y * emissiveHeight / height) * emissiveWidth + (x * emissiveWidth / width)];
					red += (sample & 0xFF) / 255.0f * emissiveRed;
					green += ((sample >> 8) & 0xFF) / 255.0f * emissiveGreen;
					blue += ((sample >> 16) & 0xFF) / 255.0f * emissiveBlue;
				}
				out.setPixelABGR(x, y, (unorm(alpha) << 24) | (unorm(blue) << 16) | (unorm(green) << 8) | unorm(red));
			}
		}
		close(base, occlusion, emissive);
		return out;
	}

	public static NativeImage normal(byte[] normalBytes, float normalScale) {
		if (normalBytes == null) {
			return null;
		}
		NativeImage normal = TextureFactory.decode(normalBytes);
		if (normal == null) {
			return null;
		}
		int width = normal.getWidth();
		int height = normal.getHeight();
		int[] pixels = normal.getPixelsABGR();
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = pixels[y * width + x];
				float normalX = Mth.clamp((((pixel & 0xFF) / 255.0f) * 2.0f - 1.0f) * normalScale, -1.0f, 1.0f);
				float normalY = Mth.clamp(((((pixel >> 8) & 0xFF) / 255.0f) * 2.0f - 1.0f) * normalScale, -1.0f, 1.0f);
				int encodedRed = Math.round((normalX * 0.5f + 0.5f) * 255.0f);
				int encodedGreen = Math.round((normalY * 0.5f + 0.5f) * 255.0f);
				out.setPixelABGR(x, y, 0xFFFF0000 | (encodedGreen << 8) | encodedRed);
			}
		}
		normal.close();
		return out;
	}

	public static NativeImage specular(byte[] metallicRoughnessBytes, float metallic, float roughness, float ior,
			float specularFactor, float[] specularColorFactor, byte[] specularTextureBytes,
			float clearcoatFactor, float clearcoatRoughness, float[] sheenColorFactor, float sheenRoughness,
			byte[] emissiveBytes, float[] emissiveFactor, float emissiveStrength) {
		NativeImage metallicRoughness = metallicRoughnessBytes != null ? TextureFactory.decode(metallicRoughnessBytes) : null;
		NativeImage specularTexture = specularTextureBytes != null ? TextureFactory.decode(specularTextureBytes) : null;
		NativeImage emissive = emissiveBytes != null ? TextureFactory.decode(emissiveBytes) : null;
		int width = metallicRoughness != null ? metallicRoughness.getWidth()
				: specularTexture != null ? specularTexture.getWidth()
				: emissive != null ? emissive.getWidth() : 1;
		int height = metallicRoughness != null ? metallicRoughness.getHeight()
				: specularTexture != null ? specularTexture.getHeight()
				: emissive != null ? emissive.getHeight() : 1;
		int[] metallicRoughnessPixels = metallicRoughness != null ? metallicRoughness.getPixelsABGR() : null;
		int[] specularTexturePixels = specularTexture != null ? specularTexture.getPixelsABGR() : null;
		int[] emissivePixels = emissive != null ? emissive.getPixelsABGR() : null;
		int specularTextureWidth = specularTexture != null ? specularTexture.getWidth() : 0;
		int specularTextureHeight = specularTexture != null ? specularTexture.getHeight() : 0;
		int emissiveWidth = emissive != null ? emissive.getWidth() : 0;
		int emissiveHeight = emissive != null ? emissive.getHeight() : 0;
		int dielectric = dielectricF0(ior, specularFactor, specularColorFactor);
		float clearcoatSmoothness = 1.0f - (float) Math.sqrt(Mth.clamp(clearcoatRoughness, 0.0f, 1.0f));
		float sheenBoost = sheenColorFactor != null
				? luminance(sheenColorFactor[0], sheenColorFactor[1], sheenColorFactor[2])
						* (1.0f - Mth.clamp(sheenRoughness, 0.0f, 1.0f)) * SHEEN_SMOOTHNESS_WEIGHT
				: 0.0f;
		NativeImage out = new NativeImage(NativeImage.Format.RGBA, width, height, false);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				float pixelRoughness = roughness;
				float pixelMetallic = metallic;
				if (metallicRoughnessPixels != null) {
					int pixel = metallicRoughnessPixels[y * width + x];
					pixelRoughness = ((pixel >> 8) & 0xFF) / 255.0f * roughness;
					pixelMetallic = ((pixel >> 16) & 0xFF) / 255.0f * metallic;
				}
				float smoothness = 1.0f - (float) Math.sqrt(Mth.clamp(pixelRoughness, 0.0f, 1.0f));
				if (clearcoatFactor > 0.0f) {
					smoothness += (Math.max(smoothness, clearcoatSmoothness) - smoothness) * clearcoatFactor;
				}
				smoothness += sheenBoost;
				int red = unorm(smoothness);
				int green;
				if (pixelMetallic >= 0.5f) {
					green = 255;
				} else {
					float f0 = dielectric / 255.0f;
					if (specularTexturePixels != null) {
						int sample = specularTexturePixels[(y * specularTextureHeight / height) * specularTextureWidth + (x * specularTextureWidth / width)];
						f0 *= ((sample >> 24) & 0xFF) / 255.0f;
					}
					if (clearcoatFactor > 0.0f) {
						f0 += (Math.max(f0, CLEARCOAT_F0) - f0) * clearcoatFactor;
					}
					green = Math.min(unorm(f0), DIELECTRIC_G_MAX);
				}
				int alpha = 0;
				if (emissivePixels != null) {
					int pixel = emissivePixels[(y * emissiveHeight / height) * emissiveWidth + (x * emissiveWidth / width)];
					float luma = luminance((pixel & 0xFF) / 255.0f * emissiveFactor[0],
							((pixel >> 8) & 0xFF) / 255.0f * emissiveFactor[1],
							((pixel >> 16) & 0xFF) / 255.0f * emissiveFactor[2]) * emissiveStrength;
					alpha = Math.round(Mth.clamp(luma, 0.0f, 1.0f) * 254.0f);
				}
				out.setPixelABGR(x, y, (alpha << 24) | (green << 8) | red);
			}
		}
		close(metallicRoughness, specularTexture, emissive);
		return out;
	}

	private static int dielectricF0(float ior, float specularFactor, float[] specularColorFactor) {
		float reflectance = (ior - 1.0f) / (ior + 1.0f);
		reflectance *= reflectance;
		float maxColor = Math.max(specularColorFactor[0], Math.max(specularColorFactor[1], specularColorFactor[2]));
		float f0 = Mth.clamp(reflectance * specularFactor * maxColor, 0.0f, DIELECTRIC_F0_MAX);
		return Math.min(Math.round(f0 * 255.0f), DIELECTRIC_G_MAX);
	}

	private static float luminance(float red, float green, float blue) {
		return 0.2126f * red + 0.7152f * green + 0.0722f * blue;
	}

	private static int unorm(float value) {
		return Math.round(Mth.clamp(value, 0.0f, 1.0f) * 255.0f);
	}

	private static void close(NativeImage a, NativeImage b, NativeImage c) {
		if (a != null) {
			a.close();
		}
		if (b != null) {
			b.close();
		}
		if (c != null) {
			c.close();
		}
	}
}
