package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import java.util.List;
import org.joml.Matrix4f;

public final class ExperimentalSkinningTraceLogger {
    public void maybeTraceVertex(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Matrix4f meshWorld,
            List<Matrix4f> skinMatrices,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        // Experimental skin trace logging retired after the skinning path was stabilized.
    }
}
