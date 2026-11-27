package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;

/**
 * Centralizes the heuristic gates that decide how the experimental skinning
 * path handles a vertex. This keeps the current behavior but stops duplicating
 * the same threshold logic across builder/debug code.
 */
public final class ExperimentalSkinningPolicy {
    private static final float SINGLE_JOINT_THRESHOLD = 0.95F;
    private static final float NEAR_RIGID_THRESHOLD = 0.98F;

    public boolean isEligiblePrimitive(MeshPrimitiveData primitive) {
        return primitive.skinIndex() >= 0 && !primitive.joints().isEmpty() && !primitive.weights().isEmpty();
    }

    public VertexChoice analyzeVertex(MeshPrimitiveData primitive, int vertexIndex) {
        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size()) {
            return VertexChoice.fallback();
        }

        float totalWeight = 0.0F;
        float dominantWeight = 0.0F;
        float secondWeight = 0.0F;
        int dominantJointIndex = -1;
        int secondJointIndex = -1;
        int jointIndexMin = Integer.MAX_VALUE;
        int jointIndexMax = Integer.MIN_VALUE;

        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(skinBase + i);
            float weight = primitive.weights().get(skinBase + i);
            if (jointIndex >= 0) {
                jointIndexMin = Math.min(jointIndexMin, jointIndex);
                jointIndexMax = Math.max(jointIndexMax, jointIndex);
            }
            if (weight <= 0.000001F) {
                continue;
            }
            totalWeight += weight;
            if (weight > dominantWeight) {
                secondJointIndex = dominantJointIndex;
                secondWeight = dominantWeight;
                dominantJointIndex = jointIndex;
                dominantWeight = weight;
            } else if (weight > secondWeight) {
                secondJointIndex = jointIndex;
                secondWeight = weight;
            }
        }

        if (jointIndexMin == Integer.MAX_VALUE) {
            jointIndexMin = -1;
            jointIndexMax = -1;
        }

        float dominantThreshold = resolveThreshold(primitive, jointIndexMin, jointIndexMax);
        if (dominantWeight < dominantThreshold || totalWeight <= 0.000001F) {
            return new VertexChoice(
                    VertexMode.FALLBACK,
                    dominantThreshold,
                    totalWeight,
                    dominantWeight,
                    secondWeight,
                    dominantJointIndex,
                    secondJointIndex,
                    jointIndexMin,
                    jointIndexMax
            );
        }

        if (dominantJointIndex >= 0 && dominantWeight >= NEAR_RIGID_THRESHOLD) {
            return new VertexChoice(
                    VertexMode.DOMINANT,
                    dominantThreshold,
                    totalWeight,
                    dominantWeight,
                    secondWeight,
                    dominantJointIndex,
                    secondJointIndex,
                    jointIndexMin,
                    jointIndexMax
            );
        }

        return new VertexChoice(
                VertexMode.TOP2,
                dominantThreshold,
                totalWeight,
                dominantWeight,
                secondWeight,
                dominantJointIndex,
                secondJointIndex,
                jointIndexMin,
                jointIndexMax
        );
    }

    private float resolveThreshold(MeshPrimitiveData primitive, int jointIndexMin, int jointIndexMax) {
        String nodeName = primitive.nodeName() == null ? "" : primitive.nodeName().toLowerCase();
        String primitiveName = primitive.name() == null ? "" : primitive.name().toLowerCase();
        if (nodeName.contains("wing") || primitiveName.contains("wing")) {
            return 0.90F;
        }
        return SINGLE_JOINT_THRESHOLD;
    }

    public enum VertexMode {
        FALLBACK,
        DOMINANT,
        TOP2
    }

    public record VertexChoice(
            VertexMode mode,
            float dominantThreshold,
            float totalWeight,
            float dominantWeight,
            float secondWeight,
            int dominantJointIndex,
            int secondJointIndex,
            int jointIndexMin,
            int jointIndexMax
    ) {
        public static VertexChoice fallback() {
            return new VertexChoice(VertexMode.FALLBACK, SINGLE_JOINT_THRESHOLD, 0.0F, 0.0F, 0.0F, -1, -1, -1, -1);
        }

        public float blendWeightSum() {
            return dominantWeight + secondWeight;
        }
    }
}
