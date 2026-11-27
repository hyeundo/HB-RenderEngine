package com.yourname.hb3d.render;

import java.util.List;

public record BaselineTexturedPrimitiveSet(
        String modelId,
        List<BaselineTexturedPrimitive> primitives
) {
    public int primitiveCount() {
        return primitives.size();
    }

    public boolean isEmpty() {
        return primitives.isEmpty();
    }
}
