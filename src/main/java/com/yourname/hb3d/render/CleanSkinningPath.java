package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Production-facing skinning path used by the renderer.
 *
 * <p>Remove the explicit scene-root transform before blending joint matrices
 * so skinning uses the same model space as the mesh builder.</p>
 */
public final class CleanSkinningPath {
    private final ExperimentalSkinningSolver experimentalSkinningSolver = new ExperimentalSkinningSolver();
    private final ExperimentalSkinningApplier experimentalSkinningApplier = new ExperimentalSkinningApplier();

    public List<Matrix4f> buildJointMatrices(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache
    ) {
        String cacheKey = primitive.name() + "|" + primitive.nodeName() + "|" + primitive.skinIndex() + "|clean_rootless_world_mesh_comp";
        List<Matrix4f> jointMatrices = skinMatricesCache.get(cacheKey);
        if (jointMatrices == null) {
            ExperimentalSkinningContext context = new ExperimentalSkinningContext(
                    asset,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    experimentalSkinningSolver
            );
            jointMatrices = context.resolveSkinMatrices(
                    primitive,
                    ExperimentalSkinningSolver.SkinMatrixVariant.ROOTLESS_WORLD_MESH_COMP
            );
            skinMatricesCache.put(cacheKey, jointMatrices);
        }
        return jointMatrices;
    }

    public float[] applyFullBlendWorldInput(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex,
            NodeTransform fallbackTransform
    ) {
        List<Matrix4f> jointMatrices = buildJointMatrices(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache);
        return experimentalSkinningApplier.applyFullBlend(
                primitive,
                vertexIndex,
                localX,
                localY,
                localZ,
                fallbackTransform,
                jointMatrices
        );
    }

    public float[] applyPolicyWorldInput(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex,
            float staticX,
            float staticY,
            float staticZ,
            NodeTransform fallbackTransform,
            ExperimentalSkinningPolicy.VertexChoice choice
    ) {
        if (choice.mode() == ExperimentalSkinningPolicy.VertexMode.FALLBACK) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        List<Matrix4f> jointMatrices = buildJointMatrices(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache);

        if (choice.mode() == ExperimentalSkinningPolicy.VertexMode.DOMINANT && choice.dominantJointIndex() >= 0) {
            Vector3f dominant = new Vector3f(localX, localY, localZ);
            jointMatrices.get(choice.dominantJointIndex()).transformPosition(dominant);
            return new float[] {dominant.x, dominant.y, dominant.z};
        }

        float blendWeightSum = choice.blendWeightSum();
        if (blendWeightSum <= 0.000001F
                || choice.dominantJointIndex() < 0
                || choice.dominantJointIndex() >= jointMatrices.size()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        Matrix4f blendedMatrix = scaleMatrix(
                new Matrix4f(jointMatrices.get(choice.dominantJointIndex())),
                choice.dominantWeight() / blendWeightSum
        );
        if (choice.secondJointIndex() >= 0
                && choice.secondJointIndex() < jointMatrices.size()
                && choice.secondWeight() > 0.000001F) {
            blendedMatrix.add(scaleMatrix(
                    new Matrix4f(jointMatrices.get(choice.secondJointIndex())),
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
