package com.micheanl.mcgltf.mixin.iris;

import com.micheanl.mcgltf.compat.iris.IrisPbrTextures;
import net.irisshaders.iris.pbr.texture.PBRTextureHolder;
import net.irisshaders.iris.pbr.texture.PBRTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PBRTextureManager.class)
public class MixinPBRTextureManager {
	@Inject(method = "getOrLoadHolder", at = @At("HEAD"), cancellable = true)
	private void mcgltf$override(int id, CallbackInfoReturnable<PBRTextureHolder> cir) {
		PBRTextureHolder holder = IrisPbrTextures.get(id);
		if (holder != null) {
			cir.setReturnValue(holder);
		}
	}

	@Inject(method = "getHolder", at = @At("HEAD"), cancellable = true)
	private void mcgltf$overrideGet(int id, CallbackInfoReturnable<PBRTextureHolder> cir) {
		PBRTextureHolder holder = IrisPbrTextures.get(id);
		if (holder != null) {
			cir.setReturnValue(holder);
		}
	}
}
