package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossSpawnPayload(
        int visualId,
        long generation,
        String modelId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float scale,
        String baseState
) implements CustomPayload {
    public static final Id<BossSpawnPayload> ID = new Id<>(Identifier.of(Hb3dMod.MOD_ID, "boss_spawn"));
    public static final PacketCodec<RegistryByteBuf, BossSpawnPayload> CODEC = PacketCodec.ofStatic(
            BossSpawnPayload::write, BossSpawnPayload::read);

    private static void write(RegistryByteBuf buf, BossSpawnPayload payload) {
        buf.writeVarInt(payload.visualId);
        buf.writeVarLong(payload.generation);
        buf.writeString(payload.modelId, 128);
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeFloat(payload.yaw);
        buf.writeFloat(payload.pitch);
        buf.writeFloat(payload.scale);
        buf.writeString(payload.baseState, 64);
    }

    private static BossSpawnPayload read(RegistryByteBuf buf) {
        return new BossSpawnPayload(buf.readVarInt(), buf.readVarLong(), buf.readString(128),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readString(64));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
