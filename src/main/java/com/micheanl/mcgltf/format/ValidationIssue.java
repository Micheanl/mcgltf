package com.micheanl.mcgltf.format;

public record ValidationIssue(Severity severity, String message) {
	public enum Severity {
		ERROR,
		WARNING
	}

	public static ValidationIssue error(String message) {
		return new ValidationIssue(Severity.ERROR, message);
	}

	public static ValidationIssue warning(String message) {
		return new ValidationIssue(Severity.WARNING, message);
	}
}
