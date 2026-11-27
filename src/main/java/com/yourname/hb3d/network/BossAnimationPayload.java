package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record BossAnimationPayload(
        int visualId,
        long generation,
        String baseState,
        String actionState,
        long actionSequence
) implements CustomPayload {
    public static final Id<BossAnimationPayload> ID = new Id<>(Identifier.of(Hb3dMod.MOD_ID, "boss_animation"));
    public static final PacketCodec<RegistryByteBuf, BossAnimationPayload> CODEC = PacketCodec.ofStatic(
            BossAnimationPayload::write, BossAnimationPayload::read);

    private static void write(RegistryByteBuf buf, BossAnimationPayload payload) {
        buf.writeVarInt(payload.visualId);
        buf.writeVarLong(payload.generation);
        buf.writeString(payload.baseState, 64);
        buf.writeString(payload.actionState, 64);
        buf.writeVarLong(payload.actionSequence);
    }

    private static BossAnimationPayload read(RegistryByteBuf buf) {
        return new BossAnimationPayload(buf.readVarInt(), buf.readVarLong(), buf.readString(64), buf.readString(64), buf.readVarLong());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
