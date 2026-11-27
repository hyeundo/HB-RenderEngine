package com.yourname.hb3d.render;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

public final class StaticMeshUploader {
    private static final float MESH_ALPHA = 1.0F;

    public UploadedStaticMesh upload(StaticTriangleMesh mesh) {
        VertexBuffer vertexBuffer = VertexBuffer.createAndUpload(
                VertexFormat.DrawMode.TRIANGLES,
                VertexFormats.POSITION_COLOR,
                consumer -> {
                    for (StaticTriangleMesh.Triangle triangle : mesh.triangles()) {
                        emitTriangle(consumer, triangle.ax(), triangle.ay(), triangle.az(), triangle.bx(), triangle.by(), triangle.bz(), triangle.cx(), triangle.cy(), triangle.cz(), triangle.red(), triangle.green(), triangle.blue());
                    }
                }
        );
        return new UploadedStaticMesh(mesh.modelId(), mesh.triangles().size(), vertexBuffer);
    }

    private void emitTriangle(
            net.minecraft.client.render.VertexConsumer consumer,
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float red,
            float green,
            float blue
    ) {
        consumer.vertex(ax, ay, az).color(red, green, blue, MESH_ALPHA);
        consumer.vertex(bx, by, bz).color(red, green, blue, MESH_ALPHA);
        consumer.vertex(cx, cy, cz).color(red, green, blue, MESH_ALPHA);
    }
}
