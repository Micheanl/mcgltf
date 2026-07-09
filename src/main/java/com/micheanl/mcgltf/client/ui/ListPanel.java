package com.micheanl.mcgltf.client.ui;

import com.micheanl.mcgltf.client.ModelCache;
import com.micheanl.mcgltf.client.command.ModelCommands;
import com.micheanl.mcgltf.client.thumbnail.ThumbnailRenderState;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.render.GizmoRenderer;
import com.micheanl.mcgltf.render.SceneRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ListPanel {
	private static final Identifier FRAME = Identifier.withDefaultNamespace("widget/text_field");
	private static final int ROW_H = 12;
	private static final int PAD = 4;
	private static final int MARGIN = 6;
	private static final int BOTTOM = 16;
	private static final int PAGE = 15;
	private static final int THUMB = 96;
	private static final int THUMB_GAP = 6;
	private static final int SPIN_MS = 8000;
	private static final int NAME_CLIP = 130;
	private static final int SOURCE_CLIP = 80;
	private static final int HOVER_BG = 0x55FFFFFF;
	private static final int TITLE = 0xFF6EE7FF;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GLB = 0xFFFFC94B;
	private static final int GLTF = 0xFF57D0FF;
	private static final int GREEN = 0xFF66E27A;
	private static final int AQUA = 0xFF57D0FF;
	private static final int YELLOW = 0xFFFFE066;
	private static final int GOLD = 0xFFFFB454;
	private static final int RED = 0xFFFF6B6B;

	private static boolean visible;
	private static int page;
	private static final List<Hit> HITS = new ArrayList<>();
	private static String hoverModel;
	private static int hoverY;

	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
	}

	private record Seg(String text, int color, Runnable action) {
	}

	private ListPanel() {
	}

	public static boolean toggle() {
		visible = !visible;
		return visible;
	}

	public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!visible) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		HITS.clear();
		hoverModel = null;

		List<String> models = EditorConfig.listModels();
		List<SceneRegistry.Instance> instances = SceneRegistry.all();
		int pages = Math.max(1, (models.size() + PAGE - 1) / PAGE);
		page = Math.min(Math.max(page, 0), pages - 1);
		int start = page * PAGE;
		int end = Math.min(start + PAGE, models.size());
		int selected = SceneRegistry.selectedIndex();

		List<List<Seg>> rows = new ArrayList<>();
		List<String> rowModel = new ArrayList<>();

		List<Seg> header = new ArrayList<>();
		header.add(new Seg("模型库 " + models.size(), TITLE, null));
		if (pages > 1) {
			boolean prev = page > 0;
			boolean next = page < pages - 1;
			header.add(new Seg("  [<]", prev ? GREEN : GRAY, prev ? () -> page-- : null));
			header.add(new Seg(" " + (page + 1) + "/" + pages + " ", GRAY, null));
			header.add(new Seg("[>]", next ? GREEN : GRAY, next ? () -> page++ : null));
		}
		rows.add(header);
		rowModel.add(null);

		for (int i = start; i < end; i++) {
			String name = models.get(i);
			boolean glb = name.toLowerCase(Locale.ROOT).endsWith(".glb");
			List<Seg> row = new ArrayList<>();
			row.add(new Seg(clip(font, name, NAME_CLIP), glb ? GLB : GLTF, null));
			row.add(new Seg(" [加载]", GREEN, () -> ModelCommands.loadModel(name, 1.0f)));
			row.add(new Seg("[方块]", AQUA, () -> command("mcgltfobj block " + name)));
			row.add(new Seg("[生物]", YELLOW, () -> command("mcgltfobj entity " + name)));
			row.add(new Seg("[物品]", GOLD, () -> command("mcgltfobj item " + name)));
			rows.add(row);
			rowModel.add(name);
		}

		List<Seg> placedHeader = new ArrayList<>();
		placedHeader.add(new Seg("已放置 " + instances.size(), TITLE, null));
		rows.add(placedHeader);
		rowModel.add(null);

		for (int i = 0; i < instances.size(); i++) {
			SceneRegistry.Instance instance = instances.get(i);
			int index = i;
			boolean sel = i == selected;
			List<Seg> row = new ArrayList<>();
			row.add(new Seg((sel ? ">" : " ") + index + " " + clip(font, instance.source(), SOURCE_CLIP), sel ? GREEN : WHITE, null));
			if (sel) {
				row.add(new Seg(" [编辑]", AQUA, () -> edit(index)));
				row.add(new Seg("[脚下]", GREEN, () -> here(index)));
				row.add(new Seg("[删除]", RED, () -> SceneRegistry.remove(index)));
			} else {
				row.add(new Seg(" [选中]", GREEN, () -> SceneRegistry.select(index)));
				row.add(new Seg("[删除]", RED, () -> SceneRegistry.remove(index)));
			}
			rows.add(row);
			rowModel.add(null);
		}

		int panelW = 0;
		for (List<Seg> row : rows) {
			int w = 0;
			for (Seg seg : row) {
				w += font.width(seg.text());
			}
			panelW = Math.max(panelW, w);
		}
		int screenW = minecraft.getWindow().getGuiScaledWidth();
		int screenH = minecraft.getWindow().getGuiScaledHeight();
		int contentRight = screenW - MARGIN;
		int contentLeft = contentRight - panelW;
		int contentBottom = screenH - BOTTOM;
		int contentTop = contentBottom - rows.size() * ROW_H;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME,
				contentLeft - PAD, contentTop - PAD, panelW + PAD * 2, rows.size() * ROW_H + PAD * 2);

		int y = contentTop;
		for (int r = 0; r < rows.size(); r++) {
			List<Seg> row = rows.get(r);
			int x = contentLeft;
			boolean rowHovered = mouseX >= contentLeft && mouseX < contentRight && mouseY >= y && mouseY < y + ROW_H;
			for (Seg seg : row) {
				int w = font.width(seg.text());
				if (seg.action() != null) {
					if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H) {
						graphics.fill(x - 1, y - 1, x + w, y + ROW_H - 1, HOVER_BG);
					}
					HITS.add(new Hit(x, y, x + w, y + ROW_H, seg.action()));
				}
				graphics.text(font, seg.text(), x, y + 2, seg.color(), false);
				x += w;
			}
			if (rowHovered && rowModel.get(r) != null) {
				hoverModel = rowModel.get(r);
				hoverY = y;
			}
			y += ROW_H;
		}

		if (hoverModel != null) {
			if (!ModelCache.available(hoverModel)) {
				ModelCache.request(hoverModel);
			} else {
				int tx1 = contentLeft - PAD - THUMB_GAP;
				int tx0 = tx1 - THUMB;
				int ty0 = Math.min(Math.max(hoverY, PAD), screenH - THUMB - PAD);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME, tx0 - PAD, ty0 - PAD, THUMB + PAD * 2, THUMB + PAD * 2);
				float angle = (System.currentTimeMillis() % SPIN_MS) / (float) SPIN_MS * 360.0f;
				graphics.guiRenderState.addPicturesInPictureState(
						new ThumbnailRenderState(hoverModel, angle, tx0, ty0, tx0 + THUMB, ty0 + THUMB, THUMB * 0.9f, null));
			}
		}
	}

	public static boolean mouseClicked(double mouseX, double mouseY) {
		if (!visible) {
			return false;
		}
		for (int i = HITS.size() - 1; i >= 0; i--) {
			Hit hit = HITS.get(i);
			if (mouseX >= hit.x0() && mouseX < hit.x1() && mouseY >= hit.y0() && mouseY < hit.y1()) {
				hit.action().run();
				return true;
			}
		}
		return false;
	}

	private static void command(String command) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection != null) {
			connection.sendCommand(command);
		}
	}

	private static void edit(int index) {
		if (SceneRegistry.select(index)) {
			GizmoRenderer.show(GizmoRenderer.mode());
			Minecraft.getInstance().gui.setScreen(new GizmoScreen());
		}
	}

	private static void here(int index) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || index < 0 || index >= SceneRegistry.count()) {
			return;
		}
		SceneRegistry.select(index);
		SceneRegistry.all().get(index).position(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
	}

	private static String clip(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
	}
}
