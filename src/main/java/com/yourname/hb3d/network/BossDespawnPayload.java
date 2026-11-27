package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossDespawnPayload(int visualId, long generation) implements CustomPayload {
    public static final Id<BossDespawnPayload> ID = new Id<>(Identifier.of(Hb3dMod.MOD_ID, "boss_despawn"));
    public static final PacketCodec<RegistryByteBuf, BossDespawnPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeVarInt(payload.visualId);
                buf.writeVarLong(payload.generation);
            },
            buf -> new BossDespawnPayload(buf.readVarInt(), buf.readVarLong()));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
