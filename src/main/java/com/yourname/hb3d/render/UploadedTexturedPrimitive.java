package com.yourname.hb3d.render;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.util.Identifier;

public record UploadedTexturedPrimitive(
        String primitiveName,
        String nodeName,
        Identifier textureId,
        int triangleCount,
        VertexBuffer vertexBuffer
) implements AutoCloseable {
    public String label() {
        return primitiveName + "@" + nodeName;
    }

    @Override
    public void close() {
        vertexBuffer.close();
    }
}
