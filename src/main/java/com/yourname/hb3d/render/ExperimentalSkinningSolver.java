package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.asset.SkinDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

/**
 * Isolated experimental skinning path.
 *
 * <p>The static renderer remains the source of truth. This solver exists so we
 * can iterate on skinning formulas without spreading that logic across the
 * builder. For now it preserves the current "less broken" formula while making
 * the bind-pose target space explicit for later rework.</p>
 */
public final class ExperimentalSkinningSolver {
    public enum SkinMatrixVariant {
        MESH_SPACE,
        WORLD_SPACE,
        WORLD_MESH_COMP,
        WORLD_MESH_COMP_ALIGNED,
        ROOTLESS_WORLD_MESH_COMP
    }

    private record JointSkinContext(
            SkinDefinition skinDefinition,
            Matrix4f meshWorld
    ) {
    }

    public List<Matrix4f> solveSkinMatrices(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            boolean stripExplicitRoot
    ) {
        SkinMatrixVariant variant = stripExplicitRoot
                ? SkinMatrixVariant.ROOTLESS_WORLD_MESH_COMP
                : SkinMatrixVariant.WORLD_MESH_COMP;
        return solveSkinMatrices(asset, primitive, poseOverrides, worldMatrices, variant);
    }

    public List<Matrix4f> solveSkinMatrices(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            SkinMatrixVariant variant
    ) {
        SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
        if (skinDefinition == null) {
            return List.of();
        }

        JointSkinContext context = resolveJointSkinContext(asset, primitive, poseOverrides, worldMatrices);
        Matrix4f explicitRootInverse = variant == SkinMatrixVariant.ROOTLESS_WORLD_MESH_COMP
                ? resolveExplicitRootInverse(asset, poseOverrides, worldMatrices)
                : null;
        Matrix4f meshWorld = context.meshWorld();
        Matrix4f inverseMeshWorld = new Matrix4f(meshWorld).invert();

        List<Matrix4f> matrices = new ArrayList<>();
        for (int i = 0; i < skinDefinition.jointNodeNames().size(); i++) {
            Matrix4f skinMatrix = switch (variant) {
                case MESH_SPACE -> createMeshSpaceSkinMatrix(asset, skinDefinition, poseOverrides, worldMatrices, inverseMeshWorld, i);
                case WORLD_SPACE -> createWorldSpaceSkinMatrix(asset, skinDefinition, poseOverrides, worldMatrices, i);
                case WORLD_MESH_COMP -> createMeshCompSkinMatrix(asset, skinDefinition, poseOverrides, worldMatrices, meshWorld, i);
                case WORLD_MESH_COMP_ALIGNED -> createAlignedMeshCompSkinMatrix(asset, skinDefinition, poseOverrides, worldMatrices, meshWorld, i);
                case ROOTLESS_WORLD_MESH_COMP -> createRootlessSkinMatrix(asset, skinDefinition, poseOverrides, worldMatrices, explicitRootInverse, i);
            };
            matrices.add(skinMatrix);
        }
        return List.copyOf(matrices);
    }

    public float[] solveVertexMeshComp(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
        if (skinDefinition == null) {
            return new float[] {localX, localY, localZ};
        }

        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size()) {
            return new float[] {localX, localY, localZ};
        }

        JointSkinContext context = resolveJointSkinContext(asset, primitive, poseOverrides, worldMatrices);
        float sumWeight = 0.0F;
        org.joml.Vector3f blended = new org.joml.Vector3f();
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(skinBase + i);
            float weight = primitive.weights().get(skinBase + i);
            if (weight <= 0.000001F || jointIndex < 0 || jointIndex >= skinDefinition.jointNodeNames().size()) {
                continue;
            }
            org.joml.Vector3f transformed = transformVertexMeshComp(
                    asset,
                    context.skinDefinition(),
                    poseOverrides,
                    worldMatrices,
                    context.meshWorld(),
                    localX,
                    localY,
                    localZ,
                    jointIndex
            );
            blended.add(transformed.mul(weight));
            sumWeight += weight;
        }
        if (sumWeight > 0.000001F && sumWeight < 0.9999F) {
            blended.div(sumWeight);
        }
        return new float[] {blended.x, blended.y, blended.z};
    }

    public float[] solveVertexMeshCompTop2(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
        if (skinDefinition == null) {
            return new float[] {localX, localY, localZ};
        }

        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size()) {
            return new float[] {localX, localY, localZ};
        }

        int dominantJointIndex = -1;
        int secondJointIndex = -1;
        float dominantWeight = 0.0F;
        float secondWeight = 0.0F;
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(skinBase + i);
            float weight = primitive.weights().get(skinBase + i);
            if (weight <= 0.000001F || jointIndex < 0 || jointIndex >= skinDefinition.jointNodeNames().size()) {
                continue;
            }
            if (weight > dominantWeight) {
                secondWeight = dominantWeight;
                secondJointIndex = dominantJointIndex;
                dominantWeight = weight;
                dominantJointIndex = jointIndex;
            } else if (weight > secondWeight) {
                secondWeight = weight;
                secondJointIndex = jointIndex;
            }
        }

        float blendWeightSum = dominantWeight + secondWeight;
        if (blendWeightSum <= 0.000001F || dominantJointIndex < 0) {
            return new float[] {localX, localY, localZ};
        }

        JointSkinContext context = resolveJointSkinContext(asset, primitive, poseOverrides, worldMatrices);
        org.joml.Vector3f blended = new org.joml.Vector3f();

        org.joml.Vector3f dominant = transformVertexMeshComp(
                asset,
                context.skinDefinition(),
                poseOverrides,
                worldMatrices,
                context.meshWorld(),
                localX,
                localY,
                localZ,
                dominantJointIndex
        );
        blended.add(dominant.mul(dominantWeight / blendWeightSum));

        if (secondJointIndex >= 0 && secondWeight > 0.000001F) {
            org.joml.Vector3f second = transformVertexMeshComp(
                    asset,
                    context.skinDefinition(),
                    poseOverrides,
                    worldMatrices,
                    context.meshWorld(),
                    localX,
                    localY,
                    localZ,
                    secondJointIndex
            );
            blended.add(second.mul(secondWeight / blendWeightSum));
        }

        return new float[] {blended.x, blended.y, blended.z};
    }

    public float[] solveVertexWorldBlendThenMeshTop2(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
        if (skinDefinition == null) {
            return new float[] {localX, localY, localZ};
        }

        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size()) {
            return new float[] {localX, localY, localZ};
        }

        int dominantJointIndex = -1;
        int secondJointIndex = -1;
        float dominantWeight = 0.0F;
        float secondWeight = 0.0F;
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(skinBase + i);
            float weight = primitive.weights().get(skinBase + i);
            if (weight <= 0.000001F || jointIndex < 0 || jointIndex >= skinDefinition.jointNodeNames().size()) {
                continue;
            }
            if (weight > dominantWeight) {
                secondWeight = dominantWeight;
                secondJointIndex = dominantJointIndex;
                dominantWeight = weight;
                dominantJointIndex = jointIndex;
            } else if (weight > secondWeight) {
                secondWeight = weight;
                secondJointIndex = jointIndex;
            }
        }

        float blendWeightSum = dominantWeight + secondWeight;
        if (blendWeightSum <= 0.000001F || dominantJointIndex < 0) {
            return new float[] {localX, localY, localZ};
        }

        JointSkinContext context = resolveJointSkinContext(asset, primitive, poseOverrides, worldMatrices);
        org.joml.Vector3f blendedWorld = new org.joml.Vector3f();

        org.joml.Vector3f dominant = transformVertexWorldSpace(
                asset,
                context.skinDefinition(),
                poseOverrides,
                worldMatrices,
                localX,
                localY,
                localZ,
                dominantJointIndex
        );
        blendedWorld.add(dominant.mul(dominantWeight / blendWeightSum));

        if (secondJointIndex >= 0 && secondWeight > 0.000001F) {
            org.joml.Vector3f second = transformVertexWorldSpace(
                    asset,
                    context.skinDefinition(),
                    poseOverrides,
                    worldMatrices,
                    localX,
                    localY,
                    localZ,
                    secondJointIndex
            );
            blendedWorld.add(second.mul(secondWeight / blendWeightSum));
        }

        context.meshWorld().transformPosition(blendedWorld);
        return new float[] {blendedWorld.x, blendedWorld.y, blendedWorld.z};
    }

    private org.joml.Vector3f transformVertexMeshComp(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Matrix4f meshWorld,
            float localX,
            float localY,
            float localZ,
            int jointIndex
    ) {
        Matrix4f inverseBind = jointIndex < skinDefinition.inverseBindMatrices().size()
                ? toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex))
                : new Matrix4f();
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        org.joml.Vector3f transformed = new org.joml.Vector3f(localX, localY, localZ);
        inverseBind.transformPosition(transformed);
        jointWorld.transformPosition(transformed);
        meshWorld.transformPosition(transformed);
        return transformed;
    }

    private org.joml.Vector3f transformVertexWorldSpace(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            float localX,
            float localY,
            float localZ,
            int jointIndex
    ) {
        Matrix4f inverseBind = jointIndex < skinDefinition.inverseBindMatrices().size()
                ? toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex))
                : new Matrix4f();
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        org.joml.Vector3f transformed = new org.joml.Vector3f(localX, localY, localZ);
        inverseBind.transformPosition(transformed);
        jointWorld.transformPosition(transformed);
        return transformed;
    }

    private JointSkinContext resolveJointSkinContext(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
        if (skinDefinition == null) {
            return new JointSkinContext(null, new Matrix4f());
        }
        Matrix4f meshWorld = resolveWorldMatrix(asset, primitive.nodeName(), poseOverrides, worldMatrices);
        return new JointSkinContext(skinDefinition, meshWorld);
    }

    private Matrix4f createMeshCompSkinMatrix(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Matrix4f meshWorld,
            int jointIndex
    ) {
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        Matrix4f inverseBind = resolveInverseBindMatrix(skinDefinition, jointIndex, false);
        return new Matrix4f(jointWorld).mul(inverseBind).mul(meshWorld);
    }

    private Matrix4f createAlignedMeshCompSkinMatrix(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Matrix4f meshWorld,
            int jointIndex
    ) {
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        Matrix4f inverseBind = resolveInverseBindMatrix(skinDefinition, jointIndex, false);
        return new Matrix4f(meshWorld).mul(jointWorld).mul(inverseBind);
    }



    private Matrix4f createWorldSpaceSkinMatrix(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            int jointIndex
    ) {
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        Matrix4f inverseBind = resolveInverseBindMatrix(skinDefinition, jointIndex, false);
        return new Matrix4f(jointWorld).mul(inverseBind);
    }

    private Matrix4f createMeshSpaceSkinMatrix(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Matrix4f inverseMeshWorld,
            int jointIndex
    ) {
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        Matrix4f inverseBind = resolveInverseBindMatrix(skinDefinition, jointIndex, false);
        return new Matrix4f(inverseMeshWorld).mul(jointWorld).mul(inverseBind);
    }

    private Matrix4f createRootlessSkinMatrix(
            RuntimeModelAsset asset,
            SkinDefinition skinDefinition,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Matrix4f explicitRootInverse,
            int jointIndex
    ) {
        String jointName = skinDefinition.jointNodeNames().get(jointIndex);
        Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
        Matrix4f inverseBind = resolveInverseBindMatrix(skinDefinition, jointIndex, false);
        if (explicitRootInverse == null) {
            return new Matrix4f(jointWorld).mul(inverseBind);
        }
        return new Matrix4f(explicitRootInverse).mul(jointWorld).mul(inverseBind);
    }

    private Matrix4f resolveInverseBindMatrix(SkinDefinition skinDefinition, int jointIndex, boolean rawLayout) {
        if (jointIndex < 0 || jointIndex >= skinDefinition.inverseBindMatrices().size()) {
            return new Matrix4f();
        }
        return rawLayout
                ? toRawMatrix(skinDefinition.inverseBindMatrices().get(jointIndex))
                : toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex));
    }



    private Matrix4f resolveExplicitRootInverse(
            RuntimeModelAsset asset,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        if (!asset.hasNode("n_root")) {
            return null;
        }
        Matrix4f rootWorld = resolveWorldMatrix(asset, "n_root", poseOverrides, worldMatrices);
        return new Matrix4f(rootWorld).invert();
    }

    private Matrix4f resolveWorldMatrix(
            RuntimeModelAsset asset,
            String nodeName,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        return WorldTransformResolver.resolveWorldMatrix(
                asset,
                nodeName,
                name -> poseOverrides.containsKey(name)
                        ? poseOverrides.get(name)
                        : asset.nodeTransform(name).orElse(NodeTransform.identity(name)),
                worldMatrices
        );
    }

    private Matrix4f toMatrix(float[] values) {
        return new Matrix4f(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]
        );
    }

    private Matrix4f toRawMatrix(float[] values) {
        return new Matrix4f(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]
        );
    }

}
