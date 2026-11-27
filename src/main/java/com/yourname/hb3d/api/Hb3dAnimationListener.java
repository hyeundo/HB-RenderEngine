package com.yourname.hb3d.api;

public interface Hb3dAnimationListener {
    void onAnimationStarted(int entityId, String modelId, String clipName, long actionSequence);

    void onMarker(int entityId, String modelId, String clipName, String markerName, float clipTime, long actionSequence);

    void onAnimationFinished(int entityId, String modelId, String clipName, long actionSequence);
}
