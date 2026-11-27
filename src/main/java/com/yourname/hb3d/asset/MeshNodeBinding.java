package com.yourname.hb3d.asset;

public record MeshNodeBinding(
        int meshIndex,
        String nodeName,
        int skinIndex
) {
}
