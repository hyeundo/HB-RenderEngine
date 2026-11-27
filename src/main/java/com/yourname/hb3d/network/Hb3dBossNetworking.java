package com.yourname.hb3d.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class Hb3dBossNetworking {
    private Hb3dBossNetworking() {
    }

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playC2S().register(BossClientHelloPayload.ID, BossClientHelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossSpawnPayload.ID, BossSpawnPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossTransformPayload.ID, BossTransformPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossAnimationPayload.ID, BossAnimationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BossDespawnPayload.ID, BossDespawnPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BackgroundSyncPayload.ID, BackgroundSyncPayload.CODEC);
    }

}
