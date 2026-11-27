package com.yourname.hb3d.render;

/**
 * Final Minecraft-facing placement adjustment applied after model-space
 * positions are resolved.
 *
 * <p>This is intentionally separated from skinning so raw GLB/model space and
 * Minecraft recentering do not get conflated.
 */
public record ModelSpaceRecenter(
        float centerX,
        float minY,
        float centerZ
) {
    public float[] apply(float[] position) {
        return new float[] {
                position[0] - centerX,
                position[1] - minY,
                position[2] - centerZ
        };
    }
}
