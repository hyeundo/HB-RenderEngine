package com.yourname.hb3d.animation;

import java.util.Optional;

public final class AnimationPlayer {
    private String activeClip;
    private String previousClip;
    private float clipTimeSeconds;
    private int blendTicksRemaining;
    private boolean loop;
    private boolean holdLastFrame;

    public void play(String clipName, boolean loop, boolean holdLastFrame, int transitionTicks) {
        if (clipName == null || clipName.isBlank()) {
            return;
        }

        previousClip = activeClip;
        activeClip = clipName;
        clipTimeSeconds = 0.0F;
        blendTicksRemaining = Math.max(0, transitionTicks);
        this.loop = loop;
        this.holdLastFrame = holdLastFrame;
    }

    public void tick(float deltaSeconds) {
        if (activeClip == null) {
            return;
        }

        clipTimeSeconds += Math.max(0.0F, deltaSeconds);
        if (blendTicksRemaining > 0) {
            blendTicksRemaining--;
        }
    }

    public Optional<String> getActiveClip() {
        return Optional.ofNullable(activeClip);
    }

    public Optional<String> getPreviousClip() {
        return Optional.ofNullable(previousClip);
    }

    public float getClipTimeSeconds() {
        return clipTimeSeconds;
    }

    public int getBlendTicksRemaining() {
        return blendTicksRemaining;
    }

    public boolean isLoop() {
        return loop;
    }

    public boolean isHoldLastFrame() {
        return holdLastFrame;
    }

    public boolean isPlaying(String clipName) {
        return activeClip != null && activeClip.equals(clipName);
    }

    public void setClipTimeSeconds(float clipTimeSeconds) {
        this.clipTimeSeconds = Math.max(0.0F, clipTimeSeconds);
    }
}
