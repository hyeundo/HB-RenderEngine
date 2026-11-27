package com.yourname.hb3d.network;

import com.yourname.hb3d.Hb3dMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Authoritative background placement for the player's current server world. */
public record BackgroundSyncPayload(
        String worldName,
        boolean enabled,
        String modelId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float scale
) implements CustomPayload {
    public static final Id<BackgroundSyncPayload> ID =
            new Id<>(Identifier.of(Hb3dMod.MOD_ID, "background_sync"));
    public static final PacketCodec<RegistryByteBuf, BackgroundSyncPayload> CODEC = PacketCodec.ofStatic(
            BackgroundSyncPayload::write, BackgroundSyncPayload::read);

    private static void write(RegistryByteBuf buf, BackgroundSyncPayload payload) {
        buf.writeString(payload.worldName, 128);
        buf.writeBoolean(payload.enabled);
        if (!payload.enabled) return;
        buf.writeString(payload.modelId, 128);
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeFloat(payload.yaw);
        buf.writeFloat(payload.pitch);
        buf.writeFloat(payload.scale);
    }

    private static BackgroundSyncPayload read(RegistryByteBuf buf) {
        String worldName = buf.readString(128);
        if (!buf.readBoolean()) {
            return new BackgroundSyncPayload(worldName, false, "", 0.0, 0.0, 0.0,
                    0.0F, 0.0F, 1.0F);
        }
        return new BackgroundSyncPayload(
                worldName,
                true,
                buf.readString(128),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
