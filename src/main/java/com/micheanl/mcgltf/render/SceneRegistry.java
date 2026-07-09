package com.micheanl.mcgltf.render;

import com.micheanl.mcgltf.animation.Animator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SceneRegistry {
	private static final CopyOnWriteArrayList<Instance> EDITABLE = new CopyOnWriteArrayList<>();
	private static final CopyOnWriteArrayList<Instance> RENDERED = new CopyOnWriteArrayList<>();
	private static final Map<Object, Instance> MANAGED = new ConcurrentHashMap<>();
	private static int selected = -1;
	private static Object selectedManagedKey;

	private SceneRegistry() {
	}

	public static final class Instance {
		private final GpuModel model;
		private final String source;
		private final Animator animator;
		private double x;
		private double y;
		private double z;
		private float scale;
		private float rotationX;
		private float rotationY;
		private float rotationZ;
		private GpuInstance gpu;

		public Instance(GpuModel model, String source, double x, double y, double z, float scale, Animator animator) {
			this.model = model;
			this.source = source;
			this.x = x;
			this.y = y;
			this.z = z;
			this.scale = scale;
			this.animator = animator;
		}

		public GpuModel model() {
			return model;
		}

		public String source() {
			return source;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		public double z() {
			return z;
		}

		public float scale() {
			return scale;
		}

		public void position(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public void scale(float scale) {
			this.scale = scale;
		}

		public float rotationX() {
			return rotationX;
		}

		public float rotationY() {
			return rotationY;
		}

		public float rotationZ() {
			return rotationZ;
		}

		public boolean rotated() {
			return rotationX != 0.0f || rotationY != 0.0f || rotationZ != 0.0f;
		}

		public void rotation(float x, float y, float z) {
			this.rotationX = x;
			this.rotationY = y;
			this.rotationZ = z;
		}

		public Animator animator() {
			return animator;
		}

		public GpuInstance gpu(int packedLight) {
			if (gpu == null) {
				gpu = GpuInstance.create(model, packedLight);
			}
			return gpu;
		}

		void close() {
			model.close();
			if (gpu != null) {
				gpu.close();
			}
		}
	}

	public static void add(Instance instance) {
		EDITABLE.add(instance);
		RENDERED.add(instance);
		selected = EDITABLE.size() - 1;
	}

	public static List<Instance> all() {
		return EDITABLE;
	}

	public static List<Instance> rendered() {
		return RENDERED;
	}

	public static void putManaged(Object key, Instance instance) {
		Instance previous = MANAGED.put(key, instance);
		if (previous != null) {
			RENDERED.remove(previous);
			previous.close();
		}
		RENDERED.add(instance);
	}

	public static void removeManaged(Object key) {
		Instance previous = MANAGED.remove(key);
		if (previous != null) {
			RENDERED.remove(previous);
			previous.close();
		}
		if (key.equals(selectedManagedKey)) {
			selectedManagedKey = null;
		}
	}

	public static boolean hasManaged(Object key) {
		return MANAGED.containsKey(key);
	}

	public static Instance managed(Object key) {
		return MANAGED.get(key);
	}

	public static void selectManaged(Object key) {
		selectedManagedKey = key;
	}

	public static Instance selectedManaged() {
		return selectedManagedKey == null ? null : MANAGED.get(selectedManagedKey);
	}

	public static int count() {
		return EDITABLE.size();
	}

	public static int selectedIndex() {
		return selected;
	}

	public static boolean select(int index) {
		if (index < 0 || index >= EDITABLE.size()) {
			return false;
		}
		selected = index;
		return true;
	}

	public static boolean remove(int index) {
		if (index < 0 || index >= EDITABLE.size()) {
			return false;
		}
		Instance instance = EDITABLE.remove(index);
		RENDERED.remove(instance);
		instance.close();
		if (EDITABLE.isEmpty()) {
			selected = -1;
		} else if (selected > index || selected >= EDITABLE.size()) {
			selected = Math.max(0, selected - 1);
		}
		return true;
	}

	public static Instance selected() {
		return selected >= 0 && selected < EDITABLE.size() ? EDITABLE.get(selected) : null;
	}

	public static void advance(float seconds) {
		for (Instance instance : RENDERED) {
			instance.animator().advance(seconds);
		}
	}

	public static void clear() {
		for (Instance instance : EDITABLE) {
			RENDERED.remove(instance);
			instance.close();
		}
		EDITABLE.clear();
		selected = -1;
	}
}
