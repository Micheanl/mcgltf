package com.micheanl.mcgltf.render.gl;

import org.lwjgl.opengl.GL20C;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class ShaderProgram implements AutoCloseable {
	private final int id;
	private final Map<String, Integer> locations = new HashMap<>();

	private ShaderProgram(int id) {
		this.id = id;
	}

	public static String read(String path) {
		try (InputStream in = ShaderProgram.class.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("缺少 " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("读取着色器失败: " + path, e);
		}
	}

	public static ShaderProgram link(Map<Integer, String> stages) {
		int program = GL20C.glCreateProgram();
		int[] shaders = new int[stages.size()];
		int n = 0;
		for (Map.Entry<Integer, String> stage : stages.entrySet()) {
			int shader = compile(stage.getKey(), stage.getValue());
			GL20C.glAttachShader(program, shader);
			shaders[n++] = shader;
		}
		GL20C.glLinkProgram(program);
		for (int shader : shaders) {
			GL20C.glDeleteShader(shader);
		}
		if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) {
			String log = GL20C.glGetProgramInfoLog(program);
			GL20C.glDeleteProgram(program);
			throw new IllegalStateException("链接失败: " + log);
		}
		return new ShaderProgram(program);
	}

	private static int compile(int type, String source) {
		int shader = GL20C.glCreateShader(type);
		GL20C.glShaderSource(shader, source);
		GL20C.glCompileShader(shader);
		if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0) {
			String log = GL20C.glGetShaderInfoLog(shader);
			GL20C.glDeleteShader(shader);
			throw new IllegalStateException("编译失败: " + log);
		}
		return shader;
	}

	public int id() {
		return id;
	}

	public void use() {
		GL20C.glUseProgram(id);
	}

	public int uniform(String name) {
		return locations.computeIfAbsent(name, n -> GL20C.glGetUniformLocation(id, n));
	}

	@Override
	public void close() {
		GL20C.glDeleteProgram(id);
	}
}
