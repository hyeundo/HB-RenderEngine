package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.asset.SkinDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class BindPoseValidator {
    private final ExperimentalSkinningSolver experimentalSkinningSolver = new ExperimentalSkinningSolver();
    private final StaticMeshBuilder staticMeshBuilder = new StaticMeshBuilder();

    public BindPoseValidationReport validateFirstSkinnedPrimitive(RuntimeModelAsset asset) {
        MeshPrimitiveData primitive = asset.meshPrimitives().stream()
                .filter(entry -> entry.skinIndex() >= 0 && !entry.joints().isEmpty() && !entry.weights().isEmpty())
                .findFirst()
                .orElse(null);
        return validatePrimitive(asset, primitive);
    }

    public BindPoseValidationReport validateSelectedSkinnedPrimitive(RuntimeModelAsset asset, int selectedIndex) {
        List<MeshPrimitiveData> skinnedPrimitives = asset.meshPrimitives().stream()
                .filter(entry -> entry.skinIndex() >= 0 && !entry.joints().isEmpty() && !entry.weights().isEmpty())
                .toList();
        if (skinnedPrimitives.isEmpty()) {
            return BindPoseValidationReport.empty();
        }
        int index = Math.floorMod(selectedIndex, skinnedPrimitives.size());
        return validatePrimitive(asset, skinnedPrimitives.get(index));
    }

    private BindPoseValidationReport validatePrimitive(RuntimeModelAsset asset, MeshPrimitiveData primitive) {
        if (primitive == null) {
            return BindPoseValidationReport.empty();
        }

        SkinDefinition skin = asset.skin(primitive.skinIndex()).orElse(null);
        if (skin == null) {
            return BindPoseValidationReport.empty();
        }

        Map<String, Matrix4f> worldMatrices = new HashMap<>();
        List<Matrix4f> meshSpaceSkinMatrices = experimentalSkinningSolver.solveSkinMatrices(
                asset,
                primitive,
                Map.of(),
                worldMatrices,
                ExperimentalSkinningSolver.SkinMatrixVariant.MESH_SPACE
        );
        List<Matrix4f> worldSpaceSkinMatrices = experimentalSkinningSolver.solveSkinMatrices(
                asset,
                primitive,
                Map.of(),
                worldMatrices,
                ExperimentalSkinningSolver.SkinMatrixVariant.WORLD_SPACE
        );
        List<Matrix4f> worldMeshCompSkinMatrices = experimentalSkinningSolver.solveSkinMatrices(
                asset,
                primitive,
                Map.of(),
                worldMatrices,
                ExperimentalSkinningSolver.SkinMatrixVariant.WORLD_MESH_COMP
        );
        if (meshSpaceSkinMatrices.isEmpty() || worldSpaceSkinMatrices.isEmpty() || worldMeshCompSkinMatrices.isEmpty()) {
            return BindPoseValidationReport.empty();
        }

        StaticMeshBuilder.PrimitiveWorldGeometry baselineGeometry = staticMeshBuilder.buildPrimitiveWorldGeometry(
                asset,
                primitive,
                Map.of(),
                false,
                0
        );
        if (baselineGeometry.vertexCount() <= 0 || baselineGeometry.positions().size() < 3) {
            return BindPoseValidationReport.empty();
        }

        float meshSpaceTotalError = 0.0F;
        float meshSpaceMaxError = 0.0F;
        float worldSpaceTotalError = 0.0F;
        float worldSpaceMaxError = 0.0F;
        float worldMeshCompTotalError = 0.0F;
        float worldMeshCompMaxError = 0.0F;
        int sampledVertices = 0;
        int limit = Math.min(primitive.vertexCount(), 256);
        for (int vertexIndex = 0; vertexIndex < limit; vertexIndex++) {
            int baseIndex = vertexIndex * 3;
            int weightBase = vertexIndex * 4;
            if (baseIndex + 2 >= primitive.positions().size() || weightBase + 3 >= primitive.weights().size() || weightBase + 3 >= primitive.joints().size()) {
                break;
            }

            float localX = primitive.positions().get(baseIndex);
            float localY = primitive.positions().get(baseIndex + 1);
            float localZ = primitive.positions().get(baseIndex + 2);
            int baselineBaseIndex = vertexIndex * 3;
            if (baselineBaseIndex + 2 >= baselineGeometry.positions().size()) {
                break;
            }
            Vector3f staticExpected = new Vector3f(
                    baselineGeometry.positions().get(baselineBaseIndex),
                    baselineGeometry.positions().get(baselineBaseIndex + 1),
                    baselineGeometry.positions().get(baselineBaseIndex + 2)
            );
            Vector3f meshSpaceSkinned = applySkinning(localX, localY, localZ, primitive, vertexIndex, meshSpaceSkinMatrices);
            float meshDx = meshSpaceSkinned.x - staticExpected.x;
            float meshDy = meshSpaceSkinned.y - staticExpected.y;
            float meshDz = meshSpaceSkinned.z - staticExpected.z;
            float meshError = (float) Math.sqrt((meshDx * meshDx) + (meshDy * meshDy) + (meshDz * meshDz));
            meshSpaceTotalError += meshError;
            meshSpaceMaxError = Math.max(meshSpaceMaxError, meshError);

            Vector3f worldSkinned = applySkinning(localX, localY, localZ, primitive, vertexIndex, worldSpaceSkinMatrices);
            float worldDx = worldSkinned.x - staticExpected.x;
            float worldDy = worldSkinned.y - staticExpected.y;
            float worldDz = worldSkinned.z - staticExpected.z;
            float worldError = (float) Math.sqrt((worldDx * worldDx) + (worldDy * worldDy) + (worldDz * worldDz));
            worldSpaceTotalError += worldError;
            worldSpaceMaxError = Math.max(worldSpaceMaxError, worldError);

            Vector3f worldMeshCompSkinned = applySkinning(localX, localY, localZ, primitive, vertexIndex, worldMeshCompSkinMatrices);
            float worldMeshCompDx = worldMeshCompSkinned.x - staticExpected.x;
            float worldMeshCompDy = worldMeshCompSkinned.y - staticExpected.y;
            float worldMeshCompDz = worldMeshCompSkinned.z - staticExpected.z;
            float worldMeshCompError = (float) Math.sqrt(
                    (worldMeshCompDx * worldMeshCompDx) +
                    (worldMeshCompDy * worldMeshCompDy) +
                    (worldMeshCompDz * worldMeshCompDz)
            );
            worldMeshCompTotalError += worldMeshCompError;
            worldMeshCompMaxError = Math.max(worldMeshCompMaxError, worldMeshCompError);
            sampledVertices++;
        }

        float meshSpaceAverageError = sampledVertices == 0 ? 0.0F : meshSpaceTotalError / sampledVertices;
        float worldSpaceAverageError = sampledVertices == 0 ? 0.0F : worldSpaceTotalError / sampledVertices;
        float worldMeshCompAverageError = sampledVertices == 0 ? 0.0F : worldMeshCompTotalError / sampledVertices;

        String bestVariant = "mesh_space";
        float bestAverage = meshSpaceAverageError;
        float bestMax = meshSpaceMaxError;
        if (worldSpaceAverageError < bestAverage) {
            bestVariant = "world_space";
            bestAverage = worldSpaceAverageError;
            bestMax = worldSpaceMaxError;
        }
        if (worldMeshCompAverageError < bestAverage) {
            bestVariant = "world_mesh_comp";
            bestAverage = worldMeshCompAverageError;
            bestMax = worldMeshCompMaxError;
        }

        return new BindPoseValidationReport(
                primitive.name(),
                primitive.nodeName(),
                primitive.skinIndex(),
                sampledVertices,
                bestVariant,
                bestAverage,
                bestMax,
                meshSpaceAverageError,
                meshSpaceMaxError,
                worldSpaceAverageError,
                worldSpaceMaxError,
                worldMeshCompAverageError,
                worldMeshCompMaxError
        );
    }

    private Vector3f applySkinning(
            float localX,
            float localY,
            float localZ,
            MeshPrimitiveData primitive,
            int vertexIndex,
            List<Matrix4f> skinMatrices
    ) {
        int weightBase = vertexIndex * 4;
        Vector3f blended = new Vector3f();
        float totalWeight = 0.0F;
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(weightBase + i);
            float weight = primitive.weights().get(weightBase + i);
            if (weight <= 0.000001F || jointIndex < 0 || jointIndex >= skinMatrices.size()) {
                continue;
            }
            Vector3f transformed = new Vector3f(localX, localY, localZ);
            skinMatrices.get(jointIndex).transformPosition(transformed);
            blended.add(transformed.mul(weight));
            totalWeight += weight;
        }
        if (totalWeight <= 0.000001F) {
            return new Vector3f(localX, localY, localZ);
        }
        if (totalWeight < 0.9999F) {
            blended.div(totalWeight);
        }
        return blended;
    }

}
