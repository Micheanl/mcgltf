package com.micheanl.mcgltf.mixin.client;

import com.micheanl.mcgltf.client.thumbnail.ThumbnailOverlay;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public class CommandSuggestionsListMixin {
	@Shadow
	@Final
	private Rect2i rect;
	@Shadow
	@Final
	private List<Suggestion> suggestionList;
	@Shadow
	private int offset;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mcgltf$thumbnail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		ThumbnailOverlay.render(graphics, mouseX, mouseY,
				rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(), suggestionList, offset);
	}
}
