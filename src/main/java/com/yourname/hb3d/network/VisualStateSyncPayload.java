package com.yourname.hb3d.network;

public record VisualStateSyncPayload(
        int entityId,
        String modelId,
        String baseState,
        String actionState,
        long actionSequence
) {
}
