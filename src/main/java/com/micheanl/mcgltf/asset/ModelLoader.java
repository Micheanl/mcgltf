package com.micheanl.mcgltf.asset;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.any.Any;
import com.jsoniter.spi.JsonException;
import com.micheanl.mcgltf.format.BufferResolver;
import com.micheanl.mcgltf.format.GlbContainer;
import com.micheanl.mcgltf.format.GltfDocument;
import com.micheanl.mcgltf.format.GltfException;
import com.micheanl.mcgltf.format.GltfValidator;
import com.micheanl.mcgltf.format.ValidationIssue;
import com.micheanl.mcgltf.scene.Model;
import com.micheanl.mcgltf.scene.build.ModelAssembler;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModelLoader {
	private static final ExecutorService POOL = Executors.newFixedThreadPool(
			Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
			Thread.ofPlatform().daemon().name("mcgltf-parse-", 0).factory());

	private ModelLoader() {
	}

	public static CompletableFuture<LoadResult> loadAsync(Path file) {
		return CompletableFuture.supplyAsync(() -> load(file), POOL);
	}

	public static LoadResult load(Path file) {
		List<ValidationIssue> issues = new ArrayList<>();
		try (Arena arena = Arena.ofConfined();
				FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			long size = channel.size();
			MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
			ByteBuffer mapped = segment.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
			byte[] json;
			ByteBuffer bin = null;
			if (GlbContainer.isGlb(mapped)) {
				GlbContainer glb = GlbContainer.parse(mapped);
				json = glb.json();
				bin = glb.binaryChunk();
			} else {
				json = new byte[(int) size];
				mapped.get(0, json);
			}
			Any root = JsonIterator.deserialize(json);
			if (root == null || root.valueType() != ValueType.OBJECT) {
				issues.add(ValidationIssue.error("JSON root is not an object"));
				return new LoadResult.Failure(issues);
			}
			GltfDocument gltf = GltfDocument.read(root);
			issues.addAll(GltfValidator.validate(gltf));
			if (issues.stream().anyMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR)) {
				return new LoadResult.Failure(issues);
			}
			BufferResolver sources = new BufferResolver(gltf, file.getParent(), bin);
			Model model = ModelAssembler.assemble(gltf, sources, String.valueOf(file.getFileName()), issues);
			return new LoadResult.Success(model, issues);
		} catch (GltfException e) {
			issues.add(ValidationIssue.error(e.getMessage()));
			return new LoadResult.Failure(issues);
		} catch (JsonException e) {
			issues.add(ValidationIssue.error("JSON parse failed: " + e.getMessage()));
			return new LoadResult.Failure(issues);
		} catch (IOException e) {
			issues.add(ValidationIssue.error("file read failed: " + e.getMessage()));
			return new LoadResult.Failure(issues);
		} catch (Exception e) {
			issues.add(ValidationIssue.error("unexpected exception: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
			return new LoadResult.Failure(issues);
		}
	}
}
