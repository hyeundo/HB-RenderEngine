package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.NodeTransform;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Applies the currently selected experimental skinning strategy to positions.
 *
 * <p>This intentionally preserves the existing top1/top2/full-blend behavior
 * so we can refactor structure first and then swap the actual solver math.</p>
 */
public final class ExperimentalSkinningApplier {
    public float[] applyWorldMeshComp(
            MeshPrimitiveData primitive,
            int vertexIndex,
            float localX,
            float localY,
            float localZ,
            NodeTransform fallbackTransform,
            ExperimentalSkinningPolicy.VertexChoice choice,
            List<Matrix4f> skinMatrices
    ) {
        if (skinMatrices.isEmpty()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        if (choice.mode() == ExperimentalSkinningPolicy.VertexMode.FALLBACK) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        if (choice.mode() == ExperimentalSkinningPolicy.VertexMode.DOMINANT && choice.dominantJointIndex() >= 0) {
            Vector3f dominantTransformed = new Vector3f(localX, localY, localZ);
            skinMatrices.get(choice.dominantJointIndex()).transformPosition(dominantTransformed);
            return new float[] {dominantTransformed.x, dominantTransformed.y, dominantTransformed.z};
        }

        float blendWeightSum = choice.blendWeightSum();
        if (blendWeightSum <= 0.000001F) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        Vector3f blended = new Vector3f();
        if (choice.dominantJointIndex() >= 0) {
            Vector3f dominantTransformed = new Vector3f(localX, localY, localZ);
            skinMatrices.get(choice.dominantJointIndex()).transformPosition(dominantTransformed);
            blended.add(dominantTransformed.mul(choice.dominantWeight() / blendWeightSum));
        }
        if (choice.secondJointIndex() >= 0 && choice.secondWeight() > 0.000001F) {
            Vector3f secondTransformed = new Vector3f(localX, localY, localZ);
            skinMatrices.get(choice.secondJointIndex()).transformPosition(secondTransformed);
            blended.add(secondTransformed.mul(choice.secondWeight() / blendWeightSum));
        }
        return new float[] {blended.x, blended.y, blended.z};
    }

    public float[] applyWorldMeshCompTop2MatrixBlend(
            float localX,
            float localY,
            float localZ,
            NodeTransform fallbackTransform,
            ExperimentalSkinningPolicy.VertexChoice choice,
            List<Matrix4f> skinMatrices
    ) {
        if (skinMatrices.isEmpty()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }
        if (choice.mode() == ExperimentalSkinningPolicy.VertexMode.FALLBACK) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }
        if (choice.mode() == ExperimentalSkinningPolicy.VertexMode.DOMINANT && choice.dominantJointIndex() >= 0) {
            Vector3f dominant = new Vector3f(localX, localY, localZ);
            skinMatrices.get(choice.dominantJointIndex()).transformPosition(dominant);
            return new float[] {dominant.x, dominant.y, dominant.z};
        }
        if (choice.mode() != ExperimentalSkinningPolicy.VertexMode.TOP2) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        float blendWeightSum = choice.blendWeightSum();
        if (blendWeightSum <= 0.000001F
                || choice.dominantJointIndex() < 0
                || choice.dominantJointIndex() >= skinMatrices.size()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        Matrix4f blendedMatrix = scaleMatrix(
                new Matrix4f(skinMatrices.get(choice.dominantJointIndex())),
                choice.dominantWeight() / blendWeightSum
        );
        if (choice.secondJointIndex() >= 0
                && choice.secondJointIndex() < skinMatrices.size()
                && choice.secondWeight() > 0.000001F) {
            blendedMatrix.add(scaleMatrix(
                    new Matrix4f(skinMatrices.get(choice.secondJointIndex())),
                    choice.secondWeight() / blendWeightSum
            ));
        }

        Vector3f transformed = new Vector3f(localX, localY, localZ);
        blendedMatrix.transformPosition(transformed);
        return new float[] {transformed.x, transformed.y, transformed.z};
    }

    private Matrix4f scaleMatrix(Matrix4f matrix, float scalar) {
        return matrix.set(
                matrix.m00() * scalar, matrix.m01() * scalar, matrix.m02() * scalar, matrix.m03() * scalar,
                matrix.m10() * scalar, matrix.m11() * scalar, matrix.m12() * scalar, matrix.m13() * scalar,
                matrix.m20() * scalar, matrix.m21() * scalar, matrix.m22() * scalar, matrix.m23() * scalar,
                matrix.m30() * scalar, matrix.m31() * scalar, matrix.m32() * scalar, matrix.m33() * scalar
        );
    }

    public float[] applyFullBlend(
            MeshPrimitiveData primitive,
            int vertexIndex,
            float localX,
            float localY,
            float localZ,
            NodeTransform fallbackTransform,
            List<Matrix4f> skinMatrices
    ) {
        if (primitive.skinIndex() < 0 || primitive.joints().isEmpty() || primitive.weights().isEmpty()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size() || skinMatrices.isEmpty()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        Vector3f blended = new Vector3f();
        float totalWeight = 0.0F;
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(skinBase + i);
            float weight = primitive.weights().get(skinBase + i);
            if (weight <= 0.000001F || jointIndex < 0 || jointIndex >= skinMatrices.size()) {
                continue;
            }
            Vector3f transformed = new Vector3f(localX, localY, localZ);
            skinMatrices.get(jointIndex).transformPosition(transformed);
            blended.add(transformed.mul(weight));
            totalWeight += weight;
        }
        if (totalWeight <= 0.000001F) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }
        if (totalWeight < 0.9999F) {
            blended.div(totalWeight);
        }
        return new float[] {blended.x, blended.y, blended.z};
    }

    private float[] transformPosition(float x, float y, float z, NodeTransform transform) {
        float scaledX = x * transform.sx();
        float scaledY = y * transform.sy();
        float scaledZ = z * transform.sz();

        float qx = transform.rx();
        float qy = transform.ry();
        float qz = transform.rz();
        float qw = transform.rw();

        float tx = 2.0F * (qy * scaledZ - qz * scaledY);
        float ty = 2.0F * (qz * scaledX - qx * scaledZ);
        float tz = 2.0F * (qx * scaledY - qy * scaledX);

        float rotatedX = scaledX + (qw * tx) + (qy * tz - qz * ty);
        float rotatedY = scaledY + (qw * ty) + (qz * tx - qx * tz);
        float rotatedZ = scaledZ + (qw * tz) + (qx * ty - qy * tx);

        return new float[] {
                rotatedX + transform.tx(),
                rotatedY + transform.ty(),
                rotatedZ + transform.tz()
        };
    }
}
