package com.micheanl.mcgltf.format;

import com.jsoniter.ValueType;
import com.jsoniter.any.Any;

import java.util.ArrayList;
import java.util.List;

public final class JsonReader {
	private JsonReader() {
	}

	public static Any get(Any obj, String key) {
		if (obj == null) {
			return null;
		}
		Any value = obj.get(key);
		ValueType type = value.valueType();
		return type == ValueType.INVALID || type == ValueType.NULL ? null : value;
	}

	public static boolean has(Any obj, String key) {
		return get(obj, key) != null;
	}

	public static String string(Any obj, String key) {
		Any value = get(obj, key);
		return value == null ? null : value.toString();
	}

	public static int intOrDefault(Any obj, String key, int fallback) {
		Any value = get(obj, key);
		return value == null ? fallback : value.toInt();
	}

	public static Integer optInt(Any obj, String key) {
		Any value = get(obj, key);
		return value == null ? null : value.toInt();
	}

	public static long longOrDefault(Any obj, String key, long fallback) {
		Any value = get(obj, key);
		return value == null ? fallback : value.toLong();
	}

	public static float floatOrDefault(Any obj, String key, float fallback) {
		Any value = get(obj, key);
		return value == null ? fallback : value.toFloat();
	}

	public static Float optFloat(Any obj, String key) {
		Any value = get(obj, key);
		return value == null ? null : value.toFloat();
	}

	public static Boolean optBoolean(Any obj, String key) {
		Any value = get(obj, key);
		return value == null ? null : value.toBoolean();
	}

	public static float[] floats(Any obj, String key) {
		Any array = get(obj, key);
		if (array == null) {
			return null;
		}
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).toFloat();
		}
		return out;
	}

	public static int[] ints(Any obj, String key) {
		Any array = get(obj, key);
		if (array == null) {
			return null;
		}
		int[] out = new int[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).toInt();
		}
		return out;
	}

	public static List<String> strings(Any obj, String key) {
		Any array = get(obj, key);
		if (array == null) {
			return null;
		}
		List<String> out = new ArrayList<>(array.size());
		for (int i = 0; i < array.size(); i++) {
			out.add(array.get(i).toString());
		}
		return out;
	}
}
