package com.micheanl.mcgltf.client.thumbnail;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

public record ThumbnailRenderState(String source, float angle, int x0, int y0, int x1, int y1, float scale,
		ScreenRectangle scissorArea) implements PictureInPictureRenderState {
	@Override
	public ScreenRectangle bounds() {
		return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
	}
}
