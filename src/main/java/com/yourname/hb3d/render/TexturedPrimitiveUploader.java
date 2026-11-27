package com.yourname.hb3d.render;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public final class TexturedPrimitiveUploader {
    public UploadedTexturedPrimitive upload(BaselineTexturedPrimitive primitive, NativeImage detailNormalImage) {
        return upload(
                primitive.primitiveName(),
                primitive.nodeName(),
                primitive.textureId(),
                primitive.geometry(),
                detailNormalImage
        );
    }

    public UploadedTexturedPrimitive upload(
            String primitiveName,
            String nodeName,
            Identifier textureId,
            StaticMeshBuilder.PrimitiveWorldGeometry geometry,
            NativeImage detailNormalImage
    ) {
        VertexBuffer vertexBuffer = VertexBuffer.createAndUpload(
                VertexFormat.DrawMode.TRIANGLES,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                consumer -> emitGeometry(consumer, geometry, detailNormalImage)
        );
        int triangleCount = !geometry.indices().isEmpty()
                ? geometry.indices().size() / 3
                : geometry.vertexCount() / 3;
        return new UploadedTexturedPrimitive(
                primitiveName,
                nodeName,
                textureId,
                triangleCount,
                vertexBuffer
        );
    }

    private void emitGeometry(net.minecraft.client.render.VertexConsumer consumer, StaticMeshBuilder.PrimitiveWorldGeometry geometry, NativeImage detailNormalImage) {
        if (!geometry.indices().isEmpty()) {
            for (int i = 0; i + 2 < geometry.indices().size(); i += 3) {
                emitVertex(consumer, geometry, geometry.indices().get(i), detailNormalImage);
                emitVertex(consumer, geometry, geometry.indices().get(i + 1), detailNormalImage);
                emitVertex(consumer, geometry, geometry.indices().get(i + 2), detailNormalImage);
            }
            return;
        }

        for (int vertex = 0; vertex + 2 < geometry.vertexCount(); vertex += 3) {
            emitVertex(consumer, geometry, vertex, detailNormalImage);
            emitVertex(consumer, geometry, vertex + 1, detailNormalImage);
            emitVertex(consumer, geometry, vertex + 2, detailNormalImage);
        }
    }

    private void emitVertex(net.minecraft.client.render.VertexConsumer consumer, StaticMeshBuilder.PrimitiveWorldGeometry geometry, int vertexIndex, NativeImage detailNormalImage) {
        int posBase = vertexIndex * 3;
        int texBase = vertexIndex * 2;
        int normalBase = vertexIndex * 3;
        if (posBase + 2 >= geometry.positions().size()) {
            return;
        }
        float x = geometry.positions().get(posBase);
        float y = geometry.positions().get(posBase + 1);
        float z = geometry.positions().get(posBase + 2);
        float u = texBase + 1 < geometry.texCoords().size() ? geometry.texCoords().get(texBase) : 0.0F;
        float v = texBase + 1 < geometry.texCoords().size() ? geometry.texCoords().get(texBase + 1) : 0.0F;
        float nx = normalBase + 2 < geometry.normals().size() ? geometry.normals().get(normalBase) : 0.0F;
        float ny = normalBase + 2 < geometry.normals().size() ? geometry.normals().get(normalBase + 1) : 1.0F;
        float nz = normalBase + 2 < geometry.normals().size() ? geometry.normals().get(normalBase + 2) : 0.0F;
        consumer.vertex(x, y, z)
                .color(1.0F, 1.0F, 1.0F, 1.0F)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(nx, ny, nz);
    }
}
