package com.yourname.hb3d.debug;

import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.api.Hb3dAnimationListener;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualController;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class Hb3dDebugHarness implements Hb3dAnimationListener {
    private static final float TARGET_DEBUG_HEIGHT_BLOCKS = 4.5F;

    private boolean enabled;
    private int debugEntityId = Integer.MIN_VALUE;
    private boolean modelBound;
    private int selectedClipIndex = -1;

    public boolean isEnabled() {
        return enabled;
    }

    public int debugEntityId() {
        return debugEntityId;
    }

    public void tick(MinecraftClient client, VisualController visualController) {
        if (!enabled || client.player == null) {
            return;
        }

        debugEntityId = client.player.getId();
        if (!modelBound) {
            Hb3dMod.getModelRegistry().firstDefinition().ifPresentOrElse(definition -> {
                visualController.bindModel(debugEntityId, definition.modelId());
                modelBound = true;
            }, () -> sendMessage(client, "HB_Renderengine no model definitions loaded"));
        }

        if (selectedClipIndex < 0) {
            playNextClip(client, visualController);
        }
        pushHud(client, visualController);
    }

    private void playNextClip(MinecraftClient client, VisualController visualController) {
        List<String> clips = visualController.debugGetAsset(debugEntityId)
                .map(RuntimeModelAsset::clipNames)
                .orElse(List.of());
        if (clips.isEmpty()) {
            sendMessage(client, "HB_Renderengine no animation clips loaded");
            return;
        }

        selectedClipIndex = Math.floorMod(selectedClipIndex + 1, clips.size());
        String clipName = clips.get(selectedClipIndex);
        if (visualController.debugPlayClip(debugEntityId, clipName, true, false, 4)) {
            sendMessage(client, "HB_Renderengine animation: " + clipName
                    + " (" + (selectedClipIndex + 1) + "/" + clips.size() + ")");
        } else {
            sendMessage(client, "HB_Renderengine failed to play animation: " + clipName);
        }
    }

    private void pushHud(MinecraftClient client, VisualController visualController) {
        String clip = visualController.getCurrentClip(debugEntityId).orElse("none");
        String clipProgress = visualController.debugGetInstance(debugEntityId)
                .flatMap(instance -> visualController.debugGetAsset(debugEntityId)
                        .map(asset -> {
                            float current = instance.animationPlayer().getClipTimeSeconds();
                            float duration = asset.clipDurationSeconds(clip).orElse(0.0F);
                            if (duration > 0.0F) {
                                return String.format(" %.2f/%.2f", current, duration);
                            }
                            return String.format(" %.2f", current);
                        }))
                .orElse("");
        int primitiveCount = visualController.debugGetAsset(debugEntityId)
                .map(asset -> asset.meshPrimitives().size())
                .orElse(0);
        client.player.sendMessage(Text.literal("HB_Renderengine [" + clip + clipProgress + "] mesh=" + primitiveCount), true);
    }

    private void sendMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }

    public float computeDebugScale(RuntimeModelAsset asset) {
        float modelHeight = asset.meshSummary().bounds().maxY() - asset.meshSummary().bounds().minY();
        float configuredScale = Hb3dMod.getModelRegistry()
                .get(asset.modelId())
                .map(com.yourname.hb3d.asset.ModelDefinition::renderScale)
                .orElse(1.0F);
        if (modelHeight <= 0.0001F) {
            return configuredScale;
        }

        float normalizedScale = TARGET_DEBUG_HEIGHT_BLOCKS / modelHeight;
        return Math.max(configuredScale, Math.min(normalizedScale, configuredScale * 2.5F));
    }

    @Override
    public void onAnimationStarted(int entityId, String modelId, String clipName, long actionSequence) {
    }

    @Override
    public void onMarker(int entityId, String modelId, String clipName, String markerName, float clipTime, long actionSequence) {
    }

    @Override
    public void onAnimationFinished(int entityId, String modelId, String clipName, long actionSequence) {
    }
}
