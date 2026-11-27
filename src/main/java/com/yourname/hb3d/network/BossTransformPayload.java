package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossTransformPayload(
        int visualId,
        long generation,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float scale,
        boolean teleport
) implements CustomPayload {
    public static final Id<BossTransformPayload> ID = new Id<>(Identifier.of(Hb3dMod.MOD_ID, "boss_transform"));
    public static final PacketCodec<RegistryByteBuf, BossTransformPayload> CODEC = PacketCodec.ofStatic(
            BossTransformPayload::write, BossTransformPayload::read);

    private static void write(RegistryByteBuf buf, BossTransformPayload payload) {
        buf.writeVarInt(payload.visualId);
        buf.writeVarLong(payload.generation);
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeFloat(payload.yaw);
        buf.writeFloat(payload.pitch);
        buf.writeFloat(payload.scale);
        buf.writeBoolean(payload.teleport);
    }

    private static BossTransformPayload read(RegistryByteBuf buf) {
        return new BossTransformPayload(buf.readVarInt(), buf.readVarLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
