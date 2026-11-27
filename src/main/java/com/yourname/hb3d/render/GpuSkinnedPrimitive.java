package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.MaterialData;
import net.minecraft.util.Identifier;
import java.util.Optional;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

final class GpuSkinnedPrimitive implements AutoCloseable {
    private final MeshPrimitiveData source;
    private final Identifier textureId;
    private final Optional<Identifier> normalTextureId;
    private final MaterialData material;
    private final int vao;
    private final int vertexBuffer;
    private final int indexBuffer;
    private final int indexCount;
    private final int vertexCount;
    private final int skinnedVertexBuffer;
    private final int irisVao;

    GpuSkinnedPrimitive(
            MeshPrimitiveData source,
            Identifier textureId,
            Optional<Identifier> normalTextureId,
            MaterialData material,
            int vao,
            int vertexBuffer,
            int indexBuffer,
            int indexCount,
            int vertexCount,
            int skinnedVertexBuffer,
            int irisVao
    ) {
        this.source = source;
        this.textureId = textureId;
        this.normalTextureId = normalTextureId;
        this.material = material;
        this.vao = vao;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.indexCount = indexCount;
        this.vertexCount = vertexCount;
        this.skinnedVertexBuffer = skinnedVertexBuffer;
        this.irisVao = irisVao;
    }

    MeshPrimitiveData source() {
        return source;
    }

    Identifier textureId() {
        return textureId;
    }

    Optional<Identifier> normalTextureId() {
        return normalTextureId;
    }

    MaterialData material() {
        return material;
    }

    int vao() {
        return vao;
    }

    int indexCount() {
        return indexCount;
    }

    int vertexCount() {
        return vertexCount;
    }

    int vertexBuffer() {
        return vertexBuffer;
    }

    int indexBuffer() {
        return indexBuffer;
    }

    int skinnedVertexBuffer() {
        return skinnedVertexBuffer;
    }

    int irisVao() {
        return irisVao;
    }

    @Override
    public void close() {
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vertexBuffer);
        GL15.glDeleteBuffers(indexBuffer);
        GL15.glDeleteBuffers(skinnedVertexBuffer);
        GL30.glDeleteVertexArrays(irisVao);
    }
}
