package com.micheanl.mcgltf.animation;

import com.micheanl.mcgltf.scene.Model;
import com.micheanl.mcgltf.scene.Model.Channel;
import com.micheanl.mcgltf.scene.Model.Interpolation;
import org.joml.Quaternionf;

public final class ClipSampler {
	private static final Quaternionf Q0 = new Quaternionf();
	private static final Quaternionf Q1 = new Quaternionf();

	private ClipSampler() {
	}

	public static void sample(Model.Clip clip, float time, SkeletonPose pose) {
		for (Channel channel : clip.channels()) {
			switch (channel.path()) {
				case TRANSLATION -> sampleVector(channel, time, pose.local, channel.node() * 10, 3);
				case SCALE -> sampleVector(channel, time, pose.local, channel.node() * 10 + 7, 3);
				case ROTATION -> sampleRotation(channel, time, pose.local, channel.node() * 10 + 3);
				case WEIGHTS -> {
					float[] target = pose.weights[channel.node()];
					if (target != null) {
						sampleVector(channel, time, target, 0, channel.comps());
					}
				}
			}
		}
	}

	private static int segment(float[] times, float time) {
		int lo = 0;
		int hi = times.length - 1;
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (times[mid] <= time) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return lo;
	}

	private static void sampleVector(Channel channel, float time, float[] dst, int dstOffset, int comps) {
		float[] times = channel.times();
		float[] values = channel.values();
		Interpolation interp = channel.interp();
		int stride = interp == Interpolation.CUBIC ? comps * 3 : comps;
		int valueOffset = interp == Interpolation.CUBIC ? comps : 0;
		int last = times.length - 1;
		if (time <= times[0]) {
			System.arraycopy(values, valueOffset, dst, dstOffset, comps);
			return;
		}
		if (time >= times[last]) {
			System.arraycopy(values, last * stride + valueOffset, dst, dstOffset, comps);
			return;
		}
		int i = segment(times, time);
		float t0 = times[i];
		float delta = times[i + 1] - t0;
		float u = (time - t0) / delta;
		switch (interp) {
			case STEP -> System.arraycopy(values, i * stride + valueOffset, dst, dstOffset, comps);
			case LINEAR -> {
				int a = i * stride;
				int b = (i + 1) * stride;
				for (int c = 0; c < comps; c++) {
					dst[dstOffset + c] = values[a + c] + (values[b + c] - values[a + c]) * u;
				}
			}
			case CUBIC -> {
				int base0 = i * stride;
				int base1 = (i + 1) * stride;
				for (int c = 0; c < comps; c++) {
					float p0 = values[base0 + comps + c];
					float m0 = values[base0 + comps * 2 + c] * delta;
					float p1 = values[base1 + comps + c];
					float m1 = values[base1 + c] * delta;
					dst[dstOffset + c] = hermite(p0, m0, p1, m1, u);
				}
			}
		}
	}

	private static void sampleRotation(Channel channel, float time, float[] dst, int dstOffset) {
		float[] times = channel.times();
		float[] values = channel.values();
		Interpolation interp = channel.interp();
		int stride = interp == Interpolation.CUBIC ? 12 : 4;
		int valueOffset = interp == Interpolation.CUBIC ? 4 : 0;
		int last = times.length - 1;
		if (time <= times[0]) {
			System.arraycopy(values, valueOffset, dst, dstOffset, 4);
			return;
		}
		if (time >= times[last]) {
			System.arraycopy(values, last * stride + valueOffset, dst, dstOffset, 4);
			return;
		}
		int i = segment(times, time);
		float t0 = times[i];
		float delta = times[i + 1] - t0;
		float u = (time - t0) / delta;
		switch (interp) {
			case STEP -> System.arraycopy(values, i * stride + valueOffset, dst, dstOffset, 4);
			case LINEAR -> {
				int a = i * stride;
				int b = (i + 1) * stride;
				Q0.set(values[a], values[a + 1], values[a + 2], values[a + 3]);
				Q1.set(values[b], values[b + 1], values[b + 2], values[b + 3]);
				Q0.slerp(Q1, u);
				dst[dstOffset] = Q0.x;
				dst[dstOffset + 1] = Q0.y;
				dst[dstOffset + 2] = Q0.z;
				dst[dstOffset + 3] = Q0.w;
			}
			case CUBIC -> {
				int base0 = i * stride;
				int base1 = (i + 1) * stride;
				float x = hermite(values[base0 + 4], values[base0 + 8] * delta, values[base1 + 4], values[base1] * delta, u);
				float y = hermite(values[base0 + 5], values[base0 + 9] * delta, values[base1 + 5], values[base1 + 1] * delta, u);
				float z = hermite(values[base0 + 6], values[base0 + 10] * delta, values[base1 + 6], values[base1 + 2] * delta, u);
				float w = hermite(values[base0 + 7], values[base0 + 11] * delta, values[base1 + 7], values[base1 + 3] * delta, u);
				float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
				if (length < 1.0e-8f) {
					x = 0.0f;
					y = 0.0f;
					z = 0.0f;
					w = 1.0f;
					length = 1.0f;
				}
				dst[dstOffset] = x / length;
				dst[dstOffset + 1] = y / length;
				dst[dstOffset + 2] = z / length;
				dst[dstOffset + 3] = w / length;
			}
		}
	}

	private static float hermite(float p0, float m0, float p1, float m1, float u) {
		float u2 = u * u;
		float u3 = u2 * u;
		return (2.0f * u3 - 3.0f * u2 + 1.0f) * p0
				+ (u3 - 2.0f * u2 + u) * m0
				+ (-2.0f * u3 + 3.0f * u2) * p1
				+ (u3 - u2) * m1;
	}
}
