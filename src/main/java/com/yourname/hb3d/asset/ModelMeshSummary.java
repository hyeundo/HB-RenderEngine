package com.yourname.hb3d.asset;

public record ModelMeshSummary(
        int primitiveCount,
        int totalVertexCount,
        MeshBounds bounds
) {
}
