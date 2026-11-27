package com.yourname.hb3d.render;

import net.minecraft.client.gl.VertexBuffer;

public record UploadedStaticMesh(
        String modelId,
        int triangleCount,
        VertexBuffer vertexBuffer
) implements AutoCloseable {
    @Override
    public void close() {
        vertexBuffer.close();
    }
}
