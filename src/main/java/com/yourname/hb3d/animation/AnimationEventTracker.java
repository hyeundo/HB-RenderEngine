package com.yourname.hb3d.animation;

import java.util.HashSet;
import java.util.Set;

public final class AnimationEventTracker {
    private final Set<String> firedMarkers = new HashSet<>();

    public boolean tryMarkFired(String markerName) {
        return firedMarkers.add(markerName);
    }

    public void reset() {
        firedMarkers.clear();
    }
}
