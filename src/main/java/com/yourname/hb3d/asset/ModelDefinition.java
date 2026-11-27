package com.yourname.hb3d.asset;

import java.util.List;
import java.util.Map;
import net.minecraft.util.Identifier;

public record ModelDefinition(
        String modelId,
        Identifier glbId,
        float renderScale,
        Map<String, String> baseStates,
        Map<String, String> actionStates,
        Map<String, String> sockets,
        Map<String, List<AnimationEventDefinition>> events,
        PlaybackDefinition playback,
        TransitionDefinition transitions
) {
    public String resolveBaseClip(String stateName) {
        return baseStates.get(stateName);
    }

    public String resolveActionClip(String stateName) {
        return actionStates.get(stateName);
    }

    public record PlaybackDefinition(
            List<String> loopClips,
            List<String> holdLastFrameClips
    ) {
        public boolean isLooping(String clipName) {
            return loopClips.contains(clipName);
        }

        public boolean holdsLastFrame(String clipName) {
            return holdLastFrameClips.contains(clipName);
        }
    }

    public record TransitionDefinition(
            int defaultTicks,
            int actionTicks,
            int deathTicks
    ) {
    }
}
