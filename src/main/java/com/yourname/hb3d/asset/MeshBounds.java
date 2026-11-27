package com.yourname.hb3d.asset;

public record MeshBounds(
        float minX,
        float minY,
        float minZ,
        float maxX,
        float maxY,
        float maxZ
) {
    public float height() {
        return maxY - minY;
    }

    public float centerX() {
        return (minX + maxX) * 0.5F;
    }

    public float centerY() {
        return (minY + maxY) * 0.5F;
    }

    public float centerZ() {
        return (minZ + maxZ) * 0.5F;
    }
}
