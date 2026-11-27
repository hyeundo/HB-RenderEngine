package com.yourname.hb3d.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class BackgroundPacketEncoder {
    byte[] encode(String worldName, BackgroundPlacement placement) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(96);
            DataOutputStream output = new DataOutputStream(bytes);
            writeString(output, worldName);
            output.writeBoolean(placement != null && placement.enabled());
            if (placement != null && placement.enabled()) {
                writeString(output, placement.modelId());
                output.writeDouble(placement.x());
                output.writeDouble(placement.y());
                output.writeDouble(placement.z());
                output.writeFloat(placement.yaw());
                output.writeFloat(placement.pitch());
                output.writeFloat(placement.scale());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Failed to encode HB3D background packet", impossible);
        }
    }

    private void writeString(DataOutputStream output, String value) throws IOException {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 128) throw new IllegalArgumentException("HB3D string exceeds 128 UTF-8 bytes");
        writeVarInt(output, utf8.length);
        output.write(utf8);
    }

    private void writeVarInt(DataOutputStream output, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }
}
