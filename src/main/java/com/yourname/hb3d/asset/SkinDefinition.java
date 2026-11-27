package com.yourname.hb3d.asset;

import java.util.List;

public record SkinDefinition(
        int skinIndex,
        String name,
        String skeletonRootName,
        List<String> jointNodeNames,
        List<float[]> inverseBindMatrices
) {
}
