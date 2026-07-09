package com.micheanl.mcgltf.asset;

import com.micheanl.mcgltf.format.ValidationIssue;
import com.micheanl.mcgltf.scene.Model;

import java.util.List;

public sealed interface LoadResult {
	List<ValidationIssue> issues();

	record Success(Model model, List<ValidationIssue> issues) implements LoadResult {
	}

	record Failure(List<ValidationIssue> issues) implements LoadResult {
	}
}
