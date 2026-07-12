package com.micheanl.mcgltf.client.thumbnail;

import com.micheanl.mcgltf.client.ModelCache;
import com.micheanl.mcgltf.config.EditorConfig;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

public final class ThumbnailOverlay {
	private static final int LINE_HEIGHT = 12;
	private static final int SIZE = 96;
	private static final int GAP = 4;
	private static final int SPIN_PERIOD_MS = 8000;
	private static final Identifier FRAME = Identifier.withDefaultNamespace("gamemode_switcher/selection");

	private ThumbnailOverlay() {
	}

	public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			int rectX, int rectY, int rectWidth, int rectHeight, List<Suggestion> suggestions, int offset) {
		if (!EditorConfig.thumbnails) {
			return;
		}
		if (mouseX < rectX || mouseX >= rectX + rectWidth || mouseY < rectY || mouseY >= rectY + rectHeight) {
			return;
		}
		int index = offset + (mouseY - rectY) / LINE_HEIGHT;
		if (index < 0 || index >= suggestions.size()) {
			return;
		}
		String text = suggestions.get(index).getText();
		String lower = text.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".glb") && !lower.endsWith(".gltf")) {
			return;
		}
		if (!ModelCache.available(text)) {
			ModelCache.request(text);
			return;
		}
		int x0 = rectX;
		int y1 = rectY - GAP;
		int y0 = y1 - SIZE;
		graphics.fill(x0, y0, x0 + SIZE, y0 + SIZE, 0xFF000000);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, x0, y0, SIZE, SIZE);
		float angle = (System.currentTimeMillis() % SPIN_PERIOD_MS) / (float) SPIN_PERIOD_MS * 360.0f;
		graphics.guiRenderState.addPicturesInPictureState(
				new ThumbnailRenderState(text, angle, x0, y0, x0 + SIZE, y1, SIZE * 0.9f, null));
	}
}
