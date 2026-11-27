package com.yourname.hb3d.runtime;

import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.network.BackgroundSyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/** Owns the single static GLB background associated with the current Multiverse world. */
public final class WorldBackgroundController {
    private static final int BACKGROUND_VISUAL_ID = Integer.MIN_VALUE + 1;

    private ClientWorld clientWorld;
    private long generation;
    private String activeWorldName;
    private String activeModelId;

    public void tick(MinecraftClient client, VisualController visuals) {
        if (client.world == clientWorld) return;
        if (clientWorld != null) clear(visuals);
        clientWorld = client.world;
    }

    public void apply(BackgroundSyncPayload payload, VisualController visuals) {
        validateWorldName(payload.worldName());
        if (!payload.enabled()) {
            clear(visuals);
            activeWorldName = payload.worldName();
            return;
        }

        boolean samePlacement = payload.worldName().equals(activeWorldName)
                && payload.modelId().equals(activeModelId)
                && generation != 0L;
        if (samePlacement) {
            visuals.updateServerTransform(BACKGROUND_VISUAL_ID, generation,
                    payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch(), payload.scale(), true);
        } else {
            clear(visuals);
            generation = nextGeneration(generation);
            visuals.spawnServerVisual(BACKGROUND_VISUAL_ID, generation, payload.modelId(),
                    payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch(), payload.scale(), "idle");
        }
        activeWorldName = payload.worldName();
        activeModelId = payload.modelId();
        Hb3dMod.LOGGER.info("Applied world background: world={} model={} position=({}, {}, {}) scale={}",
                activeWorldName, activeModelId, payload.x(), payload.y(), payload.z(), payload.scale());
    }

    public void clear(VisualController visuals) {
        if (generation != 0L) {
            visuals.despawnServerVisual(BACKGROUND_VISUAL_ID, generation);
        }
        activeWorldName = null;
        activeModelId = null;
    }

    public String activeWorldName() {
        return activeWorldName;
    }

    private long nextGeneration(long current) {
        return current == Long.MAX_VALUE ? 1L : current + 1L;
    }

    private void validateWorldName(String worldName) {
        if (worldName == null || worldName.isBlank() || worldName.length() > 128) {
            throw new IllegalArgumentException("Invalid Multiverse world name");
        }
    }
}
