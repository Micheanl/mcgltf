package com.micheanl.mcgltf.config;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import com.micheanl.mcgltf.MCglTF;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class EditorConfig {
	public static boolean meshShader = false;
	public static boolean shaderSkinning = true;
	public static boolean thumbnails = true;
	public static boolean itemRender = true;
	public static boolean hud = true;
	public static String modelDirectory = "";

	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(MCglTF.MOD_ID);
	private static final Path FILE = DIR.resolve("config.json");
	private static final Path MODEL_DIR = DIR.resolve("model");
	private static final int MAX_SCAN_DEPTH = 6;

	private EditorConfig() {
	}

	public static List<String> listModels() {
		Path dir = directory();
		if (!Files.isDirectory(dir)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.walk(dir, MAX_SCAN_DEPTH)) {
			return stream.filter(Files::isRegularFile)
					.filter(EditorConfig::isModelFile)
					.map(path -> dir.relativize(path).toString().replace('\\', '/'))
					.sorted()
					.toList();
		} catch (IOException e) {
			return List.of();
		}
	}

	public static Path directory() {
		if (!modelDirectory.isBlank()) {
			return Path.of(modelDirectory);
		}
		ensureModelDir();
		return MODEL_DIR;
	}

	private static void ensureModelDir() {
		try {
			Files.createDirectories(MODEL_DIR);
		} catch (IOException ignored) {
		}
	}

	private static boolean isModelFile(Path path) {
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(".glb") || name.endsWith(".gltf");
	}

	public static void load() {
		ensureModelDir();
		if (Files.exists(FILE)) {
			try {
				Any root = JsonIterator.deserialize(Files.readAllBytes(FILE));
				if (root != null && root.valueType() == ValueType.OBJECT) {
					meshShader = bool(root, "meshShader", meshShader);
					shaderSkinning = bool(root, "shaderSkinning", shaderSkinning);
					thumbnails = bool(root, "thumbnails", thumbnails);
					itemRender = bool(root, "itemRender", itemRender);
					hud = bool(root, "hud", hud);
					modelDirectory = string(root, "modelDirectory", modelDirectory);
					return;
				}
			} catch (Exception ignored) {
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(DIR);
			Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(tmp, toJson());
			try {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ignored) {
		}
	}

	private static boolean bool(Any root, String key, boolean fallback) {
		Any value = root.get(key);
		return value.valueType() == ValueType.BOOLEAN ? value.toBoolean() : fallback;
	}

	private static String string(Any root, String key, String fallback) {
		Any value = root.get(key);
		return value.valueType() == ValueType.STRING ? value.toString() : fallback;
	}

	private static String toJson() {
		return "{\n"
				+ "\t\"meshShader\": " + meshShader + ",\n"
				+ "\t\"shaderSkinning\": " + shaderSkinning + ",\n"
				+ "\t\"thumbnails\": " + thumbnails + ",\n"
				+ "\t\"itemRender\": " + itemRender + ",\n"
				+ "\t\"hud\": " + hud + ",\n"
				+ "\t\"modelDirectory\": \"" + modelDirectory.replace("\\", "\\\\").replace("\"", "\\\"") + "\"\n"
				+ "}\n";
	}
}
