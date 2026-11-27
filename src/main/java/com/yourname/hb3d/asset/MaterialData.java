package com.yourname.hb3d.asset;

public record MaterialData(
        String name,
        int baseColorTextureIndex,
        String alphaMode,
        float alphaCutoff,
        boolean doubleSided,
        int normalTextureIndex,
        int metallicRoughnessTextureIndex,
        int emissiveTextureIndex
) {
    public MaterialData {
        alphaMode = alphaMode == null ? "OPAQUE" : alphaMode;
    }
}
