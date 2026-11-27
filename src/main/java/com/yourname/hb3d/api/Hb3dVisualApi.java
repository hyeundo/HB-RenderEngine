package com.yourname.hb3d.api;

import java.util.Optional;

public interface Hb3dVisualApi {
    void bindModel(int entityId, String modelId);

    void clearModel(int entityId);

    void setBaseState(int entityId, String stateName);

    void setActionState(int entityId, String stateName, long actionSequence);

    void clearActionState(int entityId);

    Optional<SocketTransform> getSocketTransform(int entityId, String socketName);

    Optional<String> getCurrentClip(int entityId);

    boolean isClipPlaying(int entityId, String clipName);

    void registerAnimationListener(Hb3dAnimationListener listener);

    void unregisterAnimationListener(Hb3dAnimationListener listener);
}
