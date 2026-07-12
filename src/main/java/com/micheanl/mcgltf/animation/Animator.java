package com.micheanl.mcgltf.animation;

import com.micheanl.mcgltf.scene.Model;

public final class Animator {
	public enum Loop {
		ONCE,
		LOOP,
		PINGPONG
	}

	private final Model model;
	private final SkeletonPose pose;
	private final SkeletonPose fadePose;
	private int clip = -1;
	private float time;
	private float speed = 1.0f;
	private Loop loop = Loop.LOOP;
	private boolean playing;
	private int direction = 1;
	private int fromClip = -1;
	private float fromTime;
	private float fadeElapsed;
	private float fadeDuration;

	public Animator(Model model) {
		this.model = model;
		this.pose = new SkeletonPose(model);
		this.fadePose = new SkeletonPose(model);
		pose.reset();
		pose.computeGlobals();
		if (model.clips().length > 0) {
			play(0);
		}   
	}

	public int clipIndex(String name) {
		try {
			int index = Integer.parseInt(name);
			return index >= 0 && index < model.clips().length ? index : -1;
		} catch (NumberFormatException e) {
			Model.Clip[] clips = model.clips();
			for (int i = 0; i < clips.length; i++) {
				if (clips[i].name().equals(name)) {
					return i;
				}
			}
			return -1;
		}
	}

	public boolean play(int index) {
		if (index < 0 || index >= model.clips().length) {
			return false;
		}
		clip = index;
		time = 0.0f;
		direction = 1;
		playing = true;
		fromClip = -1;
		return true;
	}

	public boolean crossfade(int index, float seconds) {
		if (index < 0 || index >= model.clips().length) {
			return false;
		}
		if (clip < 0 || seconds <= 0.0f) {
			return play(index);
		}
		fromClip = clip;
		fromTime = time;
		fadeDuration = seconds;
		fadeElapsed = 0.0f;
		clip = index;
		time = 0.0f;
		direction = 1;
		playing = true;
		return true;
	}

	public void stop() {
		playing = false;
		clip = -1;
		fromClip = -1;
	}

	public void setPlaying(boolean value) {
		playing = value;
	}

	public void setSpeed(float value) {
		speed = value;
	}

	public void setLoop(Loop value) {
		loop = value;
	}

	public void seek(float seconds) {
		if (clip < 0) {
			return;
		}
		float duration = model.clips()[clip].duration();
		time = duration <= 0.0f ? 0.0f : Math.clamp(seconds, 0.0f, duration);
	}

	public void advance(float seconds) {
		if (!playing || clip < 0) {
			return;
		}
		if (fromClip >= 0) {
			fadeElapsed += seconds;
			if (fadeElapsed >= fadeDuration) {
				fromClip = -1;
			}
		}
		float duration = model.clips()[clip].duration();
		if (duration <= 0.0f) {
			time = 0.0f;
			return;
		}
		time += seconds * speed * direction;
		switch (loop) {
			case ONCE -> {
				if (time >= duration) {
					time = duration;
					playing = false;
				} else if (time < 0.0f) {
					time = 0.0f;
				}
			}
			case LOOP -> {
				time %= duration;
				if (time < 0.0f) {
					time += duration;
				}
			}
			case PINGPONG -> {
				if (time > duration) {
					time = 2.0f * duration - time;
					direction = -1;
				} else if (time < 0.0f) {
					time = -time;
					direction = 1;
				}
			}
		}
	}

	public SkeletonPose evaluate() {
		pose.reset();
		if (clip >= 0) {
			ClipSampler.sample(model.clips()[clip], time, pose);
		}
		if (fromClip >= 0 && fadeDuration > 0.0f) {
			fadePose.reset();
			ClipSampler.sample(model.clips()[fromClip], fromTime, fadePose);
			pose.blend(fadePose, Math.clamp(fadeElapsed / fadeDuration, 0.0f, 1.0f));
		}
		pose.computeGlobals();
		return pose;
	}

	public SkeletonPose restPose() {
		pose.reset();
		pose.computeGlobals();
		return pose;
	}
}
