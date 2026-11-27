package com.yourname.hb3d.asset;

public record AnimationChannelMetadata(
        String targetNode,
        String targetPath,
        String interpolation,
        int keyframeCount
) {
}
