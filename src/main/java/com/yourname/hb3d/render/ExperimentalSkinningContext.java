package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

/**
 * Shared context for experimental skinning evaluation.
 *
 * <p>This isolates pose lookup, world-matrix resolution, and skin-matrix cache
 * management from {@link StaticMeshBuilder} so later solver changes can happen
 * in one place.</p>
 */
public final class ExperimentalSkinningContext {
    private final RuntimeModelAsset asset;
    private final Map<String, NodeTransform> poseOverrides;
    private final Map<String, Matrix4f> worldMatrices;
    private final Map<String, List<Matrix4f>> skinMatricesCache;
    private final ExperimentalSkinningSolver solver;

    public ExperimentalSkinningContext(
            RuntimeModelAsset asset,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            ExperimentalSkinningSolver solver
    ) {
        this.asset = asset;
        this.poseOverrides = poseOverrides;
        this.worldMatrices = worldMatrices;
        this.skinMatricesCache = skinMatricesCache;
        this.solver = solver;
    }

    public NodeTransform resolveNodeTransform(String nodeName) {
        return poseOverrides.containsKey(nodeName)
                ? poseOverrides.get(nodeName)
                : asset.nodeTransform(nodeName).orElse(NodeTransform.identity(nodeName));
    }

    public Matrix4f resolveWorldMatrix(String nodeName) {
        return WorldTransformResolver.resolveWorldMatrix(asset, nodeName, this::resolveNodeTransform, worldMatrices);
    }

    public List<Matrix4f> resolveSkinMatrices(MeshPrimitiveData primitive, boolean stripExplicitRoot) {
        String cacheKey = primitive.name() + "|" + primitive.nodeName() + "|" + primitive.skinIndex() + "|" + stripExplicitRoot;
        if (skinMatricesCache.containsKey(cacheKey)) {
            return skinMatricesCache.get(cacheKey);
        }

        List<Matrix4f> matrices = solver.solveSkinMatrices(
                asset,
                primitive,
                poseOverrides,
                worldMatrices,
                stripExplicitRoot
        );
        skinMatricesCache.put(cacheKey, matrices);
        return matrices;
    }

    public List<Matrix4f> resolveSkinMatrices(
            MeshPrimitiveData primitive,
            ExperimentalSkinningSolver.SkinMatrixVariant variant
    ) {
        String cacheKey = primitive.name() + "|" + primitive.nodeName() + "|" + primitive.skinIndex() + "|" + variant.name();
        if (skinMatricesCache.containsKey(cacheKey)) {
            return skinMatricesCache.get(cacheKey);
        }

        List<Matrix4f> matrices = solver.solveSkinMatrices(
                asset,
                primitive,
                poseOverrides,
                worldMatrices,
                variant
        );
        skinMatricesCache.put(cacheKey, matrices);
        return matrices;
    }
}
