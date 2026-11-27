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
 * Minimal glTF skinning path intended to mirror the spec as directly as
 * possible:
 *
 * <pre>
 * jointMatrix = jointWorld * inverseBind
 * vertex' = sum(weight * (jointMatrix * vertex))
 * </pre>
 *
 * <p>This path deliberately avoids the current experimental heuristics so we
 * can compare a clean skinning implementation against the baseline renderer.
 */
public final class AuthoritativeSkinningPath {
    public List<Matrix4f> buildJointMatrices(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        NodeGlobalTransformCache globalTransformCache = new NodeGlobalTransformCache(
                asset,
                name -> poseOverrides.containsKey(name)
                        ? poseOverrides.get(name)
                        : asset.nodeTransform(name).orElse(NodeTransform.identity(name))
        );
        SkinDefinition skin = asset.skin(primitive.skinIndex()).orElse(null);
        if (skin == null) {
            return List.of();
        }

        List<Matrix4f> result = new ArrayList<>(skin.jointNodeNames().size());
        for (int jointIndex = 0; jointIndex < skin.jointNodeNames().size(); jointIndex++) {
            String jointName = skin.jointNodeNames().get(jointIndex);
            Matrix4f jointWorld = globalTransformCache.globalTransform(jointName);
            Matrix4f inverseBind = jointIndex < skin.inverseBindMatrices().size()
                    ? toMatrix(skin.inverseBindMatrices().get(jointIndex))
                    : new Matrix4f();
            result.add(new Matrix4f(jointWorld).mul(inverseBind));
        }
        return List.copyOf(result);
    }


    private Matrix4f toMatrix(float[] values) {
        return new Matrix4f(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]
        );
    }
}
