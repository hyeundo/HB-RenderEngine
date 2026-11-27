package com.yourname.hb3d.network;

import com.yourname.hb3d.runtime.VisualController;

public final class VisualStateSyncHandler {
    private final VisualController visualController;

    public VisualStateSyncHandler(VisualController visualController) {
        this.visualController = visualController;
    }

    public void apply(VisualStateSyncPayload payload) {
        visualController.bindModel(payload.entityId(), payload.modelId());
        visualController.setBaseState(payload.entityId(), payload.baseState());
        visualController.setActionState(payload.entityId(), payload.actionState(), payload.actionSequence());
    }
}
