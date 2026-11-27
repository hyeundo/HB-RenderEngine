package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.joml.Matrix4f;

/**
 * MCglTF-style authoritative cache for node global transforms.
 *
 * <p>Instead of rebuilding world matrices ad-hoc in several places, skinning
 * paths can ask this cache for the resolved global matrix of any node.
 */
public final class NodeGlobalTransformCache {
    private final RuntimeModelAsset asset;
    private final Function<String, NodeTransform> localTransformResolver;
    private final Map<String, Matrix4f> cache = new HashMap<>();

    public NodeGlobalTransformCache(
            RuntimeModelAsset asset,
            Function<String, NodeTransform> localTransformResolver
    ) {
        this.asset = asset;
        this.localTransformResolver = localTransformResolver;
    }

    public Matrix4f globalTransform(String nodeName) {
        Matrix4f existing = cache.get(nodeName);
        if (existing != null) {
            return existing;
        }
        Matrix4f resolved = resolveGlobalTransform(nodeName);
        cache.put(nodeName, resolved);
        return resolved;
    }

    public Map<String, Matrix4f> snapshot() {
        return Map.copyOf(cache);
    }

    private Matrix4f resolveGlobalTransform(String nodeName) {
        Matrix4f local = WorldTransformResolver.toMatrix(localTransformResolver.apply(nodeName));
        return asset.nodeHierarchy(nodeName)
                .flatMap(hierarchy -> {
                    String parentName = hierarchy.parentName();
                    return parentName == null || parentName.isBlank()
                            ? java.util.Optional.empty()
                            : java.util.Optional.of(parentName);
                })
                .map(parentName -> new Matrix4f(globalTransform(parentName)).mul(local))
                .orElse(local);
    }
}
