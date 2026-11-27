package com.yourname.hb3d.render;

public record BindPoseValidationReport(
        String primitiveName,
        String nodeName,
        int skinIndex,
        int sampledVertices,
        String bestVariant,
        float averageError,
        float maxError,
        float meshSpaceAverageError,
        float meshSpaceMaxError,
        float worldSpaceAverageError,
        float worldSpaceMaxError,
        float worldMeshCompAverageError,
        float worldMeshCompMaxError
) {
    public static BindPoseValidationReport empty() {
        return new BindPoseValidationReport(
                "none",
                "none",
                -1,
                0,
                "none",
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F
        );
    }
}
