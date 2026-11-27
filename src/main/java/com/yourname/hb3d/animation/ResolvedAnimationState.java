package com.yourname.hb3d.animation;

public record ResolvedAnimationState(
        String resolvedBaseClip,
        String resolvedActionClip,
        String activeClip,
        boolean loop,
        boolean holdLastFrame,
        int transitionTicks
) {
}
