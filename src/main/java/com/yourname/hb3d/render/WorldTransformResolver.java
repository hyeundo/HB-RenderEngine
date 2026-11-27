package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import java.util.Map;
import java.util.function.Function;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class WorldTransformResolver {
    private WorldTransformResolver() {
    }

    static Matrix4f toMatrix(NodeTransform transform) {
        return new Matrix4f().translationRotateScale(
                transform.tx(),
                transform.ty(),
                transform.tz(),
                transform.rx(),
                transform.ry(),
                transform.rz(),
                transform.rw(),
                transform.sx(),
                transform.sy(),
                transform.sz()
        );
    }

    static NodeTransform fromMatrix(String nodeName, Matrix4f matrix) {
        Vector3f translation = matrix.getTranslation(new Vector3f());
        Quaternionf rotation = matrix.getNormalizedRotation(new Quaternionf());
        Vector3f scale = matrix.getScale(new Vector3f());
        return new NodeTransform(
                nodeName,
                translation.x,
                translation.y,
                translation.z,
                rotation.x,
                rotation.y,
                rotation.z,
                rotation.w,
                scale.x,
                scale.y,
                scale.z
        );
    }

    static NodeTransform resolveWorldTransform(
            RuntimeModelAsset asset,
            String nodeName,
            Function<String, NodeTransform> localResolver,
            Map<String, NodeTransform> worldCache
    ) {
        if (worldCache.containsKey(nodeName)) {
            return worldCache.get(nodeName);
        }

        NodeTransform local = localResolver.apply(nodeName);
        String parentName = asset.nodeHierarchy(nodeName)
                .map(com.yourname.hb3d.asset.NodeHierarchy::parentName)
                .orElse(null);

        if (parentName == null || parentName.equals(nodeName)) {
            worldCache.put(nodeName, local);
            return local;
        }

        NodeTransform parentWorld = resolveWorldTransform(asset, parentName, localResolver, worldCache);
        Matrix4f combinedMatrix = toMatrix(parentWorld).mul(toMatrix(local), new Matrix4f());
        NodeTransform combined = fromMatrix(nodeName, combinedMatrix);
        worldCache.put(nodeName, combined);
        return combined;
    }

    static Matrix4f resolveWorldMatrix(
            RuntimeModelAsset asset,
            String nodeName,
            Function<String, NodeTransform> localResolver,
            Map<String, Matrix4f> worldCache
    ) {
        if (worldCache.containsKey(nodeName)) {
            return new Matrix4f(worldCache.get(nodeName));
        }

        NodeTransform local = localResolver.apply(nodeName);
        String parentName = asset.nodeHierarchy(nodeName)
                .map(com.yourname.hb3d.asset.NodeHierarchy::parentName)
                .orElse(null);
        Matrix4f localMatrix = toMatrix(local);
        Matrix4f world = parentName == null || parentName.equals(nodeName)
                ? localMatrix
                : resolveWorldMatrix(asset, parentName, localResolver, worldCache).mul(localMatrix, new Matrix4f());
        worldCache.put(nodeName, new Matrix4f(world));
        return world;
    }
}
