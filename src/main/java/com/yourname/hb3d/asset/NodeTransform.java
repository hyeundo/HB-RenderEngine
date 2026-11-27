package com.yourname.hb3d.asset;

public record NodeTransform(
        String name,
        float tx,
        float ty,
        float tz,
        float rx,
        float ry,
        float rz,
        float rw,
        float sx,
        float sy,
        float sz
) {
    public static NodeTransform identity(String name) {
        return new NodeTransform(name, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
