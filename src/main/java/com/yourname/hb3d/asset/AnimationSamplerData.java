package com.yourname.hb3d.asset;

import java.util.List;

public record AnimationSamplerData(
        String targetNode,
        String targetPath,
        String interpolation,
        List<Float> keyTimes,
        List<Float> values,
        int componentWidth
) {
}
