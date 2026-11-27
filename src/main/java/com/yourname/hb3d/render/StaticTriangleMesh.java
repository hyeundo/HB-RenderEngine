package com.yourname.hb3d.render;

import java.util.List;

public record StaticTriangleMesh(
        String modelId,
        List<Triangle> triangles
) {
    public record Triangle(
            float ax,
            float ay,
            float az,
            float bx,
            float by,
            float bz,
            float cx,
            float cy,
            float cz,
            float red,
            float green,
            float blue
    ) {
    }
}
