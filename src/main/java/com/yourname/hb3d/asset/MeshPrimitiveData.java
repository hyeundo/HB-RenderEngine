package com.yourname.hb3d.asset;

import java.util.List;

public record MeshPrimitiveData(
        String name,
        String nodeName,
        int skinIndex,
        List<Float> positions,
        List<Float> texCoords,
        List<Float> normals,
        List<Integer> joints,
        List<Float> weights,
        List<Integer> indices,
        int materialIndex,
        int mode,
        int vertexCount,
        MeshBounds bounds
) {
    public boolean hasTexCoords() {
        return texCoords != null && !texCoords.isEmpty();
    }
}
