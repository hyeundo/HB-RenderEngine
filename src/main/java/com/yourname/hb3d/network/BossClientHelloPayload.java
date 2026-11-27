package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossClientHelloPayload(int protocolVersion, String modVersion) implements CustomPayload {
    public static final int PROTOCOL_VERSION = 1;
    public static final Id<BossClientHelloPayload> ID = new Id<>(Identifier.of(Hb3dMod.MOD_ID, "boss_hello"));
    public static final PacketCodec<RegistryByteBuf, BossClientHelloPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeVarInt(payload.protocolVersion);
                buf.writeString(payload.modVersion, 32);
            },
            buf -> new BossClientHelloPayload(buf.readVarInt(), buf.readString(32)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
