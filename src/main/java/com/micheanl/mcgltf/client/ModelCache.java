package com.micheanl.mcgltf.client;

import com.micheanl.mcgltf.asset.LoadResult;
import com.micheanl.mcgltf.asset.ModelLoader;
import com.micheanl.mcgltf.config.EditorConfig;
import com.micheanl.mcgltf.render.GpuModel;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelCache {
	private record Cached(GpuModel model, long modified) {
	}

	private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();

	private ModelCache() {
	}

	public static GpuModel get(String source) {
		Cached cached = CACHE.get(source);
		return cached != null ? cached.model() : null;
	}

	public static boolean available(String source) {
		Cached cached = CACHE.get(source);
		if (cached == null) {
			return false;
		}
		if (cached.modified() != modifiedTime(source)) {
			CACHE.remove(source);
			cached.model().close();
			return false;
		}
		return true;
	}

	public static void request(String source) {
		if (source == null || source.isEmpty() || CACHE.containsKey(source) || !LOADING.add(source)) {
			return;
		}
		Path path = EditorConfig.directory().resolve(source);
		if (!Files.isRegularFile(path)) {
			LOADING.remove(source);
			return;
		}
		long modified = modifiedTime(source);
		ModelLoader.loadAsync(path).whenComplete((result, error) ->
				Minecraft.getInstance().execute(() -> {
					LOADING.remove(source);
					if (error == null && result instanceof LoadResult.Success ok) {
						try {
							CACHE.put(source, new Cached(GpuModel.upload(ok.model()), modified));
						} catch (RuntimeException ignored) {
						}
					}
				}));
	}

	private static long modifiedTime(String source) {
		try {
			return Files.getLastModifiedTime(EditorConfig.directory().resolve(source)).toMillis();
		} catch (IOException e) {
			return 0L;
		}
	}
}
