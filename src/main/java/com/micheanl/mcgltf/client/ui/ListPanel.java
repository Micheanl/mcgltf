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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ListPanel {
	private static final Identifier PANEL_FRAME = Identifier.withDefaultNamespace("friends/toast_background");
	private static final Identifier BTN = Identifier.fromNamespaceAndPath("mcgltf", "panel/button");
	private static final Identifier BTN_HOVER = Identifier.fromNamespaceAndPath("mcgltf", "panel/button_hover");
	private static final Identifier BTN_DANGER = Identifier.fromNamespaceAndPath("mcgltf", "panel/danger");
	private static final Identifier BTN_DANGER_HOVER = Identifier.fromNamespaceAndPath("mcgltf", "panel/danger_hover");
	private static final Identifier SCROLL_L = Identifier.withDefaultNamespace("spectator/scroll_left");
	private static final Identifier SCROLL_R = Identifier.withDefaultNamespace("spectator/scroll_right");
	private static final Identifier FIELD = Identifier.withDefaultNamespace("widget/text_field");
	private static final Identifier ROW_BG = Identifier.fromNamespaceAndPath("mcgltf", "panel/row");
	private static final Identifier ROW_SEL = Identifier.fromNamespaceAndPath("mcgltf", "panel/row_selected");
	private static final Identifier THUMB_FRAME = Identifier.withDefaultNamespace("gamemode_switcher/selection");

	private static final int ROW_H = 14;
	private static final int BTN_H = 20;
	private static final int PAD = 8;
	private static final int MARGIN = 8;
	private static final int BOTTOM = 18;
	private static final int PAGE = 12;
	private static final int THUMB = 96;
	private static final int THUMB_GAP = 10;
	private static final int SPIN_MS = 8000;
	private static final int NAME_CLIP = 140;
	private static final int BTN_PAD_X = 8;
	private static final int BTN_GAP = 4;
	private static final int SEP_H = 7;
	private static final int SEARCH_H = 16;
	private static final int SCROLL_W = 16;

	private static final int TITLE = 0xFFEEEEEE;
	private static final int WHITE = 0xFFEEEEEE;
	private static final int DIM = 0xFF999999;
	private static final int DARK = 0xFFCCCCCC;
	private static final int GLB = 0xFFFFC94B;
	private static final int GLTF = 0xFF57D0FF;
	private static final int BTN_TEXT = 0xFFFFFFFF;
	private static final int BTN_DIM = 0xFF888888;
	private static final int SEL_TEXT = 0xFFFFFFFF;
	private static final int SEPARATOR = 0x22FFFFFF;
	private static final int CURSOR = 0xFFDDDDDD;

	private static boolean visible;
	private static int page;
	private static String searchFilter = "";
	private static boolean searchFocused;
	private static long searchCursorBlink;
	private static String selectedModel;
	private static int selectedInstance = -1;
	private static final List<Hit> HITS = new ArrayList<>();
	private static String hoverModel;
	private static int hoverY;

	private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
	}

	private ListPanel() {
	}

	private static String t(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	public static boolean toggle() {
		visible = !visible;
		if (visible) {
			searchCursorBlink = System.currentTimeMillis();
		}
		return visible;
	}

	public static boolean isSearchFocused() {
		return visible && searchFocused;
	}

	public static void handleSearchKey(int key) {
		if (!searchFocused) {
			return;
		}
		if (key == 259) {
			if (!searchFilter.isEmpty()) {
				searchFilter = searchFilter.substring(0, searchFilter.length() - 1);
				page = 0;
				selectedModel = null;
			}
		} else if (key == 256) {
			searchFilter = "";
			searchFocused = false;
			page = 0;
			selectedModel = null;
		} else if (key == 257 || key == 335) {
			searchFocused = false;
		} else {
			String ch = keyToChar(key);
			if (ch != null) {
				searchFilter += ch;
				page = 0;
				selectedModel = null;
			}
		}
		searchCursorBlink = System.currentTimeMillis();
	}

	private static String keyToChar(int key) {
		if (key >= 65 && key <= 90) return String.valueOf((char) (key + 32));
		if (key >= 48 && key <= 57) return String.valueOf((char) key);
		if (key >= 320 && key <= 329) return String.valueOf((char) (key - 272));
		if (key == 45) return "-";
		if (key == 46) return ".";
		if (key == 95) return "_";
		if (key == 32) return " ";
		if (key == 47) return "/";
		return null;
	}

	public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!visible) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		HITS.clear();
		hoverModel = null;

		List<String> allModels = EditorConfig.listModels();
		List<String> filtered = new ArrayList<>();
		String lowerFilter = searchFilter.toLowerCase(Locale.ROOT);
		for (String m : allModels) {
			if (lowerFilter.isEmpty() || m.toLowerCase(Locale.ROOT).contains(lowerFilter)) {
				filtered.add(m);
			}
		}

		String searchPlaceholder = t("mcgltf.panel.search");
		String txtModels = t("mcgltf.panel.models", 0).replace("0", "");
		String txtPlaced = t("mcgltf.panel.placed", 0).replace("0", "");
		String txtLoad = t("mcgltf.panel.load");
		String txtBlock = t("mcgltf.panel.block");
		String txtEntity = t("mcgltf.panel.entity");
		String txtItem = t("mcgltf.panel.item");
		String txtEdit = t("mcgltf.panel.edit");
		String txtHere = t("mcgltf.panel.here");
		String txtDelete = t("mcgltf.panel.delete");
		String txtSelect = t("mcgltf.panel.select");

		List<SceneRegistry.Instance> instances = SceneRegistry.all();
		int pages = Math.max(1, (filtered.size() + PAGE - 1) / PAGE);
		page = Math.clamp(page, 0, pages - 1);
		int start = page * PAGE;
		int end = Math.min(start + PAGE, filtered.size());

		int panelW = 0;

		int searchW = Math.max(100, font.width(searchFilter.isEmpty() ? searchPlaceholder : searchFilter) + 12);
		panelW = Math.max(panelW, searchW);

		int headerW = font.width(txtModels.trim() + " " + filtered.size());
		if (pages > 1) {
			headerW += SCROLL_W * 2 + 4 + font.width(" " + (page + 1) + "/" + pages + " ") + 8;
		}
		panelW = Math.max(panelW, headerW);

		for (int i = start; i < end; i++) {
			panelW = Math.max(panelW, font.width("  " + clip(font, filtered.get(i), NAME_CLIP)));
		}

		String[] modelBtns = {txtLoad, txtBlock, txtEntity, txtItem};
		int btnRowW = 0;
		for (String label : modelBtns) {
			btnRowW += Math.max(48, font.width(label) + BTN_PAD_X * 2) + BTN_GAP;
		}
		btnRowW -= BTN_GAP;
		panelW = Math.max(panelW, btnRowW);

		panelW = Math.max(panelW, font.width(txtPlaced.trim() + " " + instances.size()));

		for (int i = 0; i < instances.size(); i++) {
			SceneRegistry.Instance inst = instances.get(i);
			panelW = Math.max(panelW, font.width("  " + i + " " + clip(font, inst.source(), 80)));
		}

		String[] instBtns = selectedInstance >= 0 && selectedInstance < instances.size()
			? new String[]{txtEdit, txtHere, txtDelete} : new String[]{txtSelect, txtDelete};
		int instBtnRowW = 0;
		for (String label : instBtns) {
			instBtnRowW += Math.max(48, font.width(label) + BTN_PAD_X * 2) + BTN_GAP;
		}
		instBtnRowW -= BTN_GAP;
		panelW = Math.max(panelW, instBtnRowW);

		int screenW = minecraft.getWindow().getGuiScaledWidth();
		int screenH = minecraft.getWindow().getGuiScaledHeight();
		int contentRight = screenW - MARGIN;
		int contentLeft = contentRight - panelW;
		int innerW = panelW;

		int totalH = PAD;
		totalH += SEARCH_H + 4;
		totalH += 2 + ROW_H;
		totalH += (end - start) * ROW_H;
		totalH += 2 + BTN_H + 4;
		totalH += SEP_H;
		totalH += ROW_H;
		totalH += instances.size() * ROW_H;
		totalH += 2 + BTN_H;
		totalH += PAD;

		int frameTop = screenH - BOTTOM - totalH;
		int frameLeft = contentLeft - PAD;
		int frameW = innerW + PAD * 2;
		int frameH = totalH;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_FRAME, frameLeft, frameTop, frameW, frameH);

		int x = contentLeft;
		int y = frameTop + PAD;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FIELD, x, y, searchW, SEARCH_H);
		String displayFilter = searchFilter.isEmpty() ? searchPlaceholder : searchFilter;
		int filterColor = searchFilter.isEmpty() ? DIM : WHITE;
		graphics.text(font, displayFilter, x + 5, y + 4, filterColor, false);
		if (searchFocused && (System.currentTimeMillis() - searchCursorBlink) % 1000 < 500) {
			int cursorX = x + 5 + font.width(displayFilter);
			graphics.fill(cursorX, y + 2, cursorX + 1, y + SEARCH_H - 2, CURSOR);
		}
		HITS.add(new Hit(x, y, x + searchW, y + SEARCH_H, () -> {
			searchFocused = true;
			searchCursorBlink = System.currentTimeMillis();
		}));
		y += SEARCH_H + 4;

		y += 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROW_BG, x, y, innerW, ROW_H);
		graphics.text(font, t("mcgltf.panel.models", filtered.size()), x + 4, y + 3, TITLE, false);
		if (pages > 1) {
			boolean prev = page > 0;
			boolean next = page < pages - 1;
			String pageStr = (page + 1) + "/" + pages;
			int right = x + innerW;
			int nextX = right - SCROLL_W;
			int pageStrW = font.width(pageStr);
			int prevX = nextX - 4 - pageStrW - 4 - SCROLL_W;
			int pageTextX = prevX + SCROLL_W + 4;

			graphics.text(font, pageStr, pageTextX, y + 3, DIM, false);

			if (prev) {
				boolean hovered = mouseX >= prevX && mouseX < prevX + SCROLL_W && mouseY >= y && mouseY < y + ROW_H;
				if (hovered) graphics.fill(prevX, y, prevX + SCROLL_W, y + ROW_H, 0x33FFFFFF);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLL_L, prevX, y, SCROLL_W, SCROLL_W);
				HITS.add(new Hit(prevX, y, prevX + SCROLL_W, y + ROW_H, () -> page--));
			}

			if (next) {
				boolean hovered = mouseX >= nextX && mouseX < nextX + SCROLL_W && mouseY >= y && mouseY < y + ROW_H;
				if (hovered) graphics.fill(nextX, y, nextX + SCROLL_W, y + ROW_H, 0x33FFFFFF);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLL_R, nextX, y, SCROLL_W, SCROLL_W);
				HITS.add(new Hit(nextX, y, nextX + SCROLL_W, y + ROW_H, () -> page++));
			}
		}
		y += ROW_H;

		for (int i = start; i < end; i++) {
			String name = filtered.get(i);
			boolean glb = name.toLowerCase(Locale.ROOT).endsWith(".glb");
			boolean sel = name.equals(selectedModel);
			String label = (sel ? "▶ " : "  ") + clip(font, name, NAME_CLIP);
			int rowColor = sel ? SEL_TEXT : (glb ? GLB : GLTF);

			boolean rowHovered = mouseX >= x && mouseX < x + innerW && mouseY >= y && mouseY < y + ROW_H;
			if (sel) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROW_SEL, x, y, innerW, ROW_H);
			} else if (rowHovered) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROW_BG, x, y, innerW, ROW_H);
			}
			graphics.text(font, label, x + 4, y + 3, rowColor, false);
			HITS.add(new Hit(x, y, x + innerW, y + ROW_H, () -> {
				selectedModel = sel ? null : name;
				searchFocused = false;
			}));

			if (rowHovered) {
				hoverModel = name;
				hoverY = y;
			}
			y += ROW_H;
		}

		y += 2;
		int btnY = y;
		int btnX = x;
		for (String label : modelBtns) {
			int bw = Math.max(48, font.width(label) + BTN_PAD_X * 2);
			boolean btnHovered = selectedModel != null && mouseX >= btnX && mouseX < btnX + bw && mouseY >= btnY && mouseY < btnY + BTN_H;
			Identifier sprite = btnHovered ? BTN_HOVER : BTN;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, btnX, btnY, bw, BTN_H);
			int textColor = selectedModel != null ? BTN_TEXT : BTN_DIM;
			graphics.text(font, label, btnX + (bw - font.width(label)) / 2, btnY + 6, textColor, false);
			if (selectedModel != null) {
				final String sn = selectedModel;
				HITS.add(new Hit(btnX, btnY, btnX + bw, btnY + BTN_H, actionForModel(label, sn, txtLoad, txtBlock, txtEntity, txtItem)));
			}
			btnX += bw + BTN_GAP;
		}
		y += BTN_H + 4;

		graphics.fill(x + 4, y, x + innerW - 4, y + 1, SEPARATOR);
		y += SEP_H;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROW_BG, x, y, innerW, ROW_H);
		graphics.text(font, t("mcgltf.panel.placed", instances.size()), x + 4, y + 3, TITLE, false);
		y += ROW_H;

		for (int i = 0; i < instances.size(); i++) {
			SceneRegistry.Instance inst = instances.get(i);
			boolean sel = i == selectedInstance;
			String label = (sel ? "▶ " : "  ") + i + " " + clip(font, inst.source(), 80);
			int rowColor = sel ? SEL_TEXT : DARK;

			boolean rowHovered = mouseX >= x && mouseX < x + innerW && mouseY >= y && mouseY < y + ROW_H;
			if (sel) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROW_SEL, x, y, innerW, ROW_H);
			} else if (rowHovered) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ROW_BG, x, y, innerW, ROW_H);
			}
			graphics.text(font, label, x + 4, y + 3, rowColor, false);
			int idx = i;
			HITS.add(new Hit(x, y, x + innerW, y + ROW_H, () -> selectedInstance = (selectedInstance == idx ? -1 : idx)));
			y += ROW_H;
		}

		y += 2;
		btnY = y;
		btnX = x;
		boolean hasSel = selectedInstance >= 0 && selectedInstance < instances.size();
		String[] instBtns2 = hasSel ? new String[]{txtEdit, txtHere, txtDelete} : new String[]{txtSelect, txtDelete};
		for (String label : instBtns2) {
			int bw = Math.max(48, font.width(label) + BTN_PAD_X * 2);
			boolean enabled = hasSel || label.equals(txtSelect);
			boolean btnHovered = enabled && mouseX >= btnX && mouseX < btnX + bw && mouseY >= btnY && mouseY < btnY + BTN_H;
			boolean isDanger = label.equals(txtDelete) && enabled;
			Identifier sprite;
			if (isDanger) {
				sprite = btnHovered ? BTN_DANGER_HOVER : BTN_DANGER;
			} else {
				sprite = btnHovered ? BTN_HOVER : BTN;
			}
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, btnX, btnY, bw, BTN_H);
			int textColor = enabled ? BTN_TEXT : BTN_DIM;
			graphics.text(font, label, btnX + (bw - font.width(label)) / 2, btnY + 6, textColor, false);
			if (enabled) {
				int idx = selectedInstance;
				HITS.add(new Hit(btnX, btnY, btnX + bw, btnY + BTN_H, actionForInstance(label, idx, txtEdit, txtHere, txtDelete, txtSelect)));
			}
			btnX += bw + BTN_GAP;
		}

		if (hoverModel != null) {
			if (!ModelCache.available(hoverModel)) {
				ModelCache.request(hoverModel);
			} else {
				int tx1 = frameLeft - THUMB_GAP;
				int tx0 = tx1 - THUMB;
				int ty0 = Math.clamp(hoverY, PAD, screenH - THUMB - PAD);
				graphics.fill(tx0, ty0, tx0 + THUMB, ty0 + THUMB, 0xFF000000);
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, THUMB_FRAME, tx0, ty0, THUMB, THUMB);
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
		if (searchFocused) {
			searchFocused = false;
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

	private static Runnable actionForModel(String label, String name, String load, String block, String entity, String item) {
		if (load.equals(label)) return () -> ModelCommands.loadModel(name, 1.0f);
		if (block.equals(label)) return () -> command("mcgltfobj block " + name);
		if (entity.equals(label)) return () -> command("mcgltfobj entity " + name);
		if (item.equals(label)) return () -> command("mcgltfobj item " + name);
		return null;
	}

	private static Runnable actionForInstance(String label, int idx, String edit, String here, String delete, String select) {
		if (edit.equals(label)) return () -> edit(idx);
		if (here.equals(label)) return () -> here(idx);
		if (delete.equals(label)) return () -> {
			SceneRegistry.remove(idx);
			if (selectedInstance >= SceneRegistry.count()) {
				selectedInstance = SceneRegistry.count() - 1;
			}
		};
		if (select.equals(label)) return () -> selectedInstance = SceneRegistry.selectedIndex();
		return null;
	}

	private static String clip(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
	}
}
