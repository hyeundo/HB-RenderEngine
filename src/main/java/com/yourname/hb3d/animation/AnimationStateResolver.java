package com.yourname.hb3d.animation;

import com.yourname.hb3d.asset.ModelDefinition;

public final class AnimationStateResolver {
    public ResolvedAnimationState resolve(ModelDefinition definition, String baseState, String actionState) {
        String resolvedBaseClip = definition.resolveBaseClip(baseState);
        String resolvedActionClip = null;

        if (actionState != null && !"none".equals(actionState)) {
            resolvedActionClip = definition.resolveActionClip(actionState);
        }

        String activeClip = resolvedActionClip != null ? resolvedActionClip : resolvedBaseClip;
        boolean loop = activeClip != null && definition.playback().isLooping(activeClip);
        boolean holdLastFrame = activeClip != null && definition.playback().holdsLastFrame(activeClip);
        int transitionTicks = transitionTicksFor(actionState, definition.transitions());

        return new ResolvedAnimationState(
                resolvedBaseClip,
                resolvedActionClip,
                activeClip,
                loop,
                holdLastFrame,
                transitionTicks
        );
    }

    private int transitionTicksFor(String actionState, ModelDefinition.TransitionDefinition transitions) {
        if ("death".equals(actionState)) {
            return transitions.deathTicks();
        }
        if (actionState != null && !"none".equals(actionState)) {
            return transitions.actionTicks();
        }
        return transitions.defaultTicks();
    }
}
