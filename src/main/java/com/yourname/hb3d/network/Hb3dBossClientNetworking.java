package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.runtime.VisualController;
import com.yourname.hb3d.runtime.WorldBackgroundController;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

public final class Hb3dBossClientNetworking {
    private Hb3dBossClientNetworking() {
    }

    public static void registerReceivers(VisualController controller, WorldBackgroundController backgrounds) {
        ClientPlayNetworking.registerGlobalReceiver(BossSpawnPayload.ID, (payload, context) ->
                context.client().execute(() -> safely("spawn", () -> controller.spawnServerVisual(
                        payload.visualId(), payload.generation(), payload.modelId(), payload.x(), payload.y(), payload.z(),
                        payload.yaw(), payload.pitch(), payload.scale(), payload.baseState()))));
        ClientPlayNetworking.registerGlobalReceiver(BossTransformPayload.ID, (payload, context) ->
                context.client().execute(() -> safely("transform", () -> controller.updateServerTransform(
                        payload.visualId(), payload.generation(), payload.x(), payload.y(), payload.z(), payload.yaw(), payload.pitch(),
                        payload.scale(), payload.teleport()))));
        ClientPlayNetworking.registerGlobalReceiver(BossAnimationPayload.ID, (payload, context) ->
                context.client().execute(() -> safely("animation", () -> controller.updateServerAnimation(
                        payload.visualId(), payload.generation(), payload.baseState(), payload.actionState(), payload.actionSequence()))));
        ClientPlayNetworking.registerGlobalReceiver(BossDespawnPayload.ID, (payload, context) ->
                context.client().execute(() -> safely("despawn", () ->
                        controller.despawnServerVisual(payload.visualId(), payload.generation()))));
        ClientPlayNetworking.registerGlobalReceiver(BackgroundSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> safely("background sync", () ->
                        backgrounds.apply(payload, controller))));
    }

    public static void sendHello() {
        if (!ClientPlayNetworking.canSend(BossClientHelloPayload.ID)) return;
        String version = FabricLoader.getInstance().getModContainer(Hb3dMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        ClientPlayNetworking.send(new BossClientHelloPayload(BossClientHelloPayload.PROTOCOL_VERSION, version));
    }

    private static void safely(String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            Hb3dMod.LOGGER.warn("Ignored invalid boss {} payload", operation, exception);
        }
    }
}
