package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.MeshBounds;
import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.asset.SkinDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class StaticMeshBuilder {
    private static final float BASE_RED = 0.88F;
    private static final float BASE_GREEN = 0.86F;
    private static final float BASE_BLUE = 0.82F;
    private static final float AMBIENT = 0.58F;
    private static final float DIFFUSE = 0.28F;
    private static final float RIM = 0.12F;
    private static final float LIGHT_X = -0.42F;
    private static final float LIGHT_Y = 0.88F;
    private static final float LIGHT_Z = 0.2F;
    private final ExperimentalSkinningSolver experimentalSkinningSolver = new ExperimentalSkinningSolver();
    private final ExperimentalSkinningPolicy experimentalSkinningPolicy = new ExperimentalSkinningPolicy();
    private final ExperimentalSkinningApplier experimentalSkinningApplier = new ExperimentalSkinningApplier();
    private final ExperimentalSkinningTraceLogger experimentalSkinningTraceLogger = new ExperimentalSkinningTraceLogger();
    private final AuthoritativeSkinningPath authoritativeSkinningPath = new AuthoritativeSkinningPath();
    private final CleanSkinningPath cleanSkinningPath = new CleanSkinningPath();

    public StaticTriangleMesh build(RuntimeModelAsset asset) {
        return build(asset, Map.of(), Map.of(), false, 0);
    }

    public StaticTriangleMesh build(RuntimeModelAsset asset, Map<String, NodeTransform> poseOverrides) {
        return build(asset, poseOverrides, Map.of(), false, 0);
    }

    public StaticTriangleMesh build(RuntimeModelAsset asset, Map<String, NodeTransform> poseOverrides, boolean experimentalSkinning) {
        return build(asset, poseOverrides, Map.of(), experimentalSkinning, 0);
    }

    public StaticTriangleMesh build(RuntimeModelAsset asset, Map<String, NodeTransform> poseOverrides, boolean experimentalSkinning, int experimentalPrimitiveIndex) {
        return build(asset, poseOverrides, Map.of(), experimentalSkinning, experimentalPrimitiveIndex);
    }

    public StaticTriangleMesh build(
            RuntimeModelAsset asset,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> seededWorldMatrices,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex
    ) {
        MeshBounds bounds = asset.meshSummary().bounds();
        ModelSpaceRecenter recenter = new ModelSpaceRecenter(bounds.centerX(), bounds.minY(), bounds.centerZ());
        List<StaticTriangleMesh.Triangle> triangles = new ArrayList<>();
        Map<String, Matrix4f> worldMatrices = new HashMap<>(seededWorldMatrices);
        Map<String, Boolean> experimentalEligibility = new HashMap<>();
        Map<String, List<Matrix4f>> skinMatricesCache = new HashMap<>();

        for (MeshPrimitiveData primitive : asset.meshPrimitives()) {
            if (primitive.mode() != 4) {
                continue;
            }

            NodeTransform nodeTransform = poseOverrides.containsKey(primitive.nodeName())
                    ? poseOverrides.get(primitive.nodeName())
                    : asset.nodeTransform(primitive.nodeName()).orElse(NodeTransform.identity(primitive.nodeName()));

            if (!primitive.indices().isEmpty()) {
                for (int i = 0; i + 2 < primitive.indices().size(); i += 3) {
                    addTriangle(
                            triangles,
                            asset,
                            primitive,
                            nodeTransform,
                            poseOverrides,
                            worldMatrices,
                            experimentalEligibility,
                            skinMatricesCache,
                            experimentalSkinning,
                            experimentalPrimitiveIndex,
                            recenter,
                            primitive.indices().get(i),
                            primitive.indices().get(i + 1),
                            primitive.indices().get(i + 2)
                    );
                }
            } else {
                for (int vertex = 0; vertex + 2 < primitive.vertexCount(); vertex += 3) {
                    addTriangle(
                            triangles,
                            asset,
                            primitive,
                            nodeTransform,
                            poseOverrides,
                            worldMatrices,
                            experimentalEligibility,
                            skinMatricesCache,
                            experimentalSkinning,
                            experimentalPrimitiveIndex,
                            recenter,
                            vertex,
                            vertex + 1,
                            vertex + 2
                    );
                }
            }
        }

        return new StaticTriangleMesh(asset.modelId(), List.copyOf(triangles));
    }

    public PrimitiveWorldGeometry buildPrimitiveWorldGeometry(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex
    ) {
        return buildPrimitiveWorldGeometry(asset, primitive, poseOverrides, Map.of(), experimentalSkinning, experimentalPrimitiveIndex);
    }

    public PrimitiveWorldGeometry buildPrimitiveWorldGeometry(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> seededWorldMatrices,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex
    ) {
        MeshBounds bounds = asset.meshSummary().bounds();
        ModelSpaceRecenter recenter = new ModelSpaceRecenter(bounds.centerX(), bounds.minY(), bounds.centerZ());
        Map<String, Matrix4f> worldMatrices = new HashMap<>(seededWorldMatrices);
        Map<String, Boolean> experimentalEligibility = new HashMap<>();
        Map<String, List<Matrix4f>> skinMatricesCache = new HashMap<>();
        return buildPrimitiveWorldGeometry(
                asset,
                primitive,
                poseOverrides,
                worldMatrices,
                experimentalEligibility,
                skinMatricesCache,
                experimentalSkinning,
                experimentalPrimitiveIndex,
                recenter
        );
    }

    public List<PrimitiveWorldGeometry> buildPrimitiveWorldGeometries(
            RuntimeModelAsset asset,
            List<MeshPrimitiveData> primitives,
            Map<String, NodeTransform> poseOverrides,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex
    ) {
        MeshBounds bounds = asset.meshSummary().bounds();
        ModelSpaceRecenter recenter = new ModelSpaceRecenter(bounds.centerX(), bounds.minY(), bounds.centerZ());
        Map<String, Matrix4f> worldMatrices = new HashMap<>();
        Map<String, Boolean> experimentalEligibility = new HashMap<>();
        Map<String, List<Matrix4f>> skinMatricesCache = new HashMap<>();
        List<PrimitiveWorldGeometry> results = new ArrayList<>(primitives.size());
        for (MeshPrimitiveData primitive : primitives) {
            results.add(buildPrimitiveWorldGeometry(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    experimentalEligibility,
                    skinMatricesCache,
                    experimentalSkinning,
                    experimentalPrimitiveIndex,
                    recenter
            ));
        }
        return List.copyOf(results);
    }

    private PrimitiveWorldGeometry buildPrimitiveWorldGeometry(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, Boolean> experimentalEligibility,
            Map<String, List<Matrix4f>> skinMatricesCache,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            ModelSpaceRecenter recenter
    ) {
        NodeTransform nodeTransform = poseOverrides.containsKey(primitive.nodeName())
                ? poseOverrides.get(primitive.nodeName())
                : asset.nodeTransform(primitive.nodeName()).orElse(NodeTransform.identity(primitive.nodeName()));
        boolean useExperimentalSkinning = experimentalSkinning
                && shouldUseExperimentalSkinning(asset, primitive, worldMatrices, experimentalEligibility, experimentalPrimitiveIndex);

        List<Float> positions = new ArrayList<>(primitive.vertexCount() * 3);
        List<Float> normals = new ArrayList<>(primitive.vertexCount() * 3);
        boolean hasCompleteNormals = primitive.normals().size() >= primitive.vertexCount() * 3;
        for (int vertexIndex = 0; vertexIndex < primitive.vertexCount(); vertexIndex++) {
            float[] position = resolveVertex(
                    asset,
                    primitive,
                    nodeTransform,
                    poseOverrides,
                    worldMatrices,
                    experimentalEligibility,
                    skinMatricesCache,
                    experimentalSkinning,
                    experimentalPrimitiveIndex,
                    vertexIndex,
                    recenter
            );
            if (position == null) {
                positions.add(0.0F);
                positions.add(0.0F);
                positions.add(0.0F);
            } else {
                positions.add(position[0]);
                positions.add(position[1]);
                positions.add(position[2]);
            }

            if (hasCompleteNormals) {
                float[] normal = readNormal(
                        asset,
                        primitive,
                        nodeTransform,
                        poseOverrides,
                        worldMatrices,
                        skinMatricesCache,
                        useExperimentalSkinning,
                        vertexIndex,
                        false
                );
                if (normal == null) {
                    hasCompleteNormals = false;
                } else {
                    normals.add(normal[0]);
                    normals.add(normal[1]);
                    normals.add(normal[2]);
                }
            }
        }

        if (!hasCompleteNormals) {
            normals = generateSmoothNormals(positions, primitive.indices());
        }

        return new PrimitiveWorldGeometry(
                primitive.name(),
                primitive.nodeName(),
                primitive.mode(),
                List.copyOf(positions),
                List.copyOf(normals),
                primitive.texCoords(),
                primitive.indices()
        );
    }

    public record PrimitiveWorldGeometry(
            String primitiveName,
            String nodeName,
            int mode,
            List<Float> positions,
            List<Float> normals,
            List<Float> texCoords,
            List<Integer> indices
    ) {
        public int vertexCount() {
            return positions.size() / 3;
        }
    }

    public SkinningDriftReport analyzeExperimentalPrimitive(
            RuntimeModelAsset asset,
            Map<String, NodeTransform> poseOverrides,
            int experimentalPrimitiveIndex
    ) {
        List<MeshPrimitiveData> skinnedPrimitives = asset.meshPrimitives().stream()
                .filter(entry -> entry.skinIndex() >= 0 && !entry.joints().isEmpty() && !entry.weights().isEmpty())
                .toList();
        if (skinnedPrimitives.isEmpty()) {
            return null;
        }
        Map<String, Matrix4f> worldMatrices = new HashMap<>();
        Map<String, List<Matrix4f>> skinMatricesCache = new HashMap<>();
        Map<String, List<Matrix4f>> rootlessSkinMatricesCache = new HashMap<>();
        ExperimentalSkinningContext skinningContext = new ExperimentalSkinningContext(
                asset,
                poseOverrides,
                worldMatrices,
                skinMatricesCache,
                experimentalSkinningSolver
        );
        ExperimentalSkinningContext rootlessSkinningContext = new ExperimentalSkinningContext(
                asset,
                poseOverrides,
                worldMatrices,
                rootlessSkinMatricesCache,
                experimentalSkinningSolver
        );
        MeshPrimitiveData primitive = skinnedPrimitives.get(Math.floorMod(experimentalPrimitiveIndex, skinnedPrimitives.size()));

        NodeTransform nodeTransform = skinningContext.resolveNodeTransform(primitive.nodeName());
        Matrix4f meshWorld = skinningContext.resolveWorldMatrix(primitive.nodeName());
        int vertices = Math.min(primitive.vertexCount(), primitive.positions().size() / 3);
        float maxDelta = -1.0F;
        float maxBlendedDelta = -1.0F;
        float totalDelta = 0.0F;
        float totalActiveDelta = 0.0F;
        float totalTop2ActualDelta = 0.0F;
        float totalOffsetX = 0.0F;
        float totalOffsetY = 0.0F;
        float totalOffsetZ = 0.0F;
        float totalAuthoritativeDelta = 0.0F;
        float totalAuthoritativeWorldInputDelta = 0.0F;
        float totalFullWorldSpaceDelta = 0.0F;
        float totalFullMeshCompDelta = 0.0F;
        float totalFullMeshCompActiveDelta = 0.0F;
        float totalFullMeshCompDominantDelta = 0.0F;
        float totalFullMeshCompTop2Delta = 0.0F;
        float totalFullMeshCompWorldStaticDelta = 0.0F;
        float totalRootlessDelta = 0.0F;
        int sampledVertices = 0;
        int activeSampledVertices = 0;
        int dominantOnlyVertices = 0;
        int top2Vertices = 0;
        int fallbackVertices = 0;
        int dominantOnlySamples = 0;
        int top2Samples = 0;
        SkinningDriftReport report = null;
        SkinningDriftReport blendedReport = null;
        for (int vertexIndex = 0; vertexIndex < vertices; vertexIndex++) {
            int baseIndex = vertexIndex * 3;
            if (baseIndex + 2 >= primitive.positions().size()) {
                break;
            }
            float localX = primitive.positions().get(baseIndex);
            float localY = primitive.positions().get(baseIndex + 1);
            float localZ = primitive.positions().get(baseIndex + 2);
            float[] staticPosition = transformPosition(localX, localY, localZ, nodeTransform);
            Vector3f parentStatic = new Vector3f(localX, localY, localZ);
            String parentName = asset.nodeHierarchy(primitive.nodeName())
                    .map(com.yourname.hb3d.asset.NodeHierarchy::parentName)
                    .orElse(null);
            if (parentName != null) {
                resolveWorldMatrix(asset, parentName, poseOverrides, worldMatrices).transformPosition(parentStatic);
            }
            Vector3f worldStatic = new Vector3f(localX, localY, localZ);
            meshWorld.transformPosition(worldStatic);
            float[] skinnedPosition = skinPositionWorldMeshComp(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            );
            float[] fullMeshCompPosition = skinPositionFullMeshComp(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            );
            float[] authoritativePosition = skinPositionAuthoritative(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            );
            float[] authoritativeWorldInputPosition = skinPositionAuthoritativeWorldInput(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            );
            float[] fullWorldSpacePosition = skinPositionFullWorldSpace(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            );
            float[] rootlessSkinnedPosition = skinPositionWorldMeshComp(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    rootlessSkinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex,
                    true
            );
            float dx = skinnedPosition[0] - staticPosition[0];
            float dy = skinnedPosition[1] - staticPosition[1];
            float dz = skinnedPosition[2] - staticPosition[2];
            float delta = (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
            totalOffsetX += dx;
            totalOffsetY += dy;
            totalOffsetZ += dz;
            float authoritativeDelta = distance(staticPosition, authoritativePosition);
            float authoritativeWorldInputDelta = distance(staticPosition, authoritativeWorldInputPosition);
            float fullWorldSpaceDelta = distance(staticPosition, fullWorldSpacePosition);
            float fullMeshCompDelta = distance(staticPosition, fullMeshCompPosition);
            float fullMeshCompWorldStaticDelta = distance(
                    new float[] {worldStatic.x, worldStatic.y, worldStatic.z},
                    fullMeshCompPosition
            );
            float rootlessDelta = distance(staticPosition, rootlessSkinnedPosition);
            totalDelta += delta;
            totalAuthoritativeDelta += authoritativeDelta;
            totalAuthoritativeWorldInputDelta += authoritativeWorldInputDelta;
            totalFullWorldSpaceDelta += fullWorldSpaceDelta;
            totalFullMeshCompDelta += fullMeshCompDelta;
            totalFullMeshCompWorldStaticDelta += fullMeshCompWorldStaticDelta;
            totalRootlessDelta += rootlessDelta;
            sampledVertices++;
            int skinBase = vertexIndex * 4;
            ExperimentalSkinningPolicy.VertexChoice choice = experimentalSkinningPolicy.analyzeVertex(primitive, vertexIndex);
            float dominantWeight = choice.dominantWeight();
            float secondWeight = choice.secondWeight();
            int dominantJointIndex = choice.dominantJointIndex();
            int secondJointIndex = choice.secondJointIndex();
            float renormalizedWeightSum = choice.totalWeight();
            switch (choice.mode()) {
                case FALLBACK -> fallbackVertices++;
                case DOMINANT -> {
                    dominantOnlyVertices++;
                    totalFullMeshCompDominantDelta += fullMeshCompDelta;
                    dominantOnlySamples++;
                }
                case TOP2 -> {
                    top2Vertices++;
                    totalTop2ActualDelta += delta;
                    totalFullMeshCompTop2Delta += fullMeshCompDelta;
                    top2Samples++;
                }
            }
            if (choice.mode() != ExperimentalSkinningPolicy.VertexMode.FALLBACK) {
                totalActiveDelta += delta;
                totalFullMeshCompActiveDelta += fullMeshCompDelta;
                activeSampledVertices++;
            }
            if (delta <= maxDelta) {
                continue;
            }
            maxDelta = delta;
            float weightSum = 0.0F;
            SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
            int skinJointCount = skinDefinition != null ? skinDefinition.jointNodeNames().size() : 0;
            int dominantJointDebug = dominantJointIndex;
            int secondJointDebug = secondJointIndex;
            int jointIndexMin = choice.jointIndexMin();
            int jointIndexMax = choice.jointIndexMax();
            StringBuilder contributions = new StringBuilder();
            String dominantJointName = resolveJointName(skinDefinition, dominantJointDebug);
            String secondJointName = resolveJointName(skinDefinition, secondJointDebug);
            contributions.append("choice[mode=")
                    .append(choice.mode().name().toLowerCase())
                    .append(",threshold=")
                    .append(String.format("%.2f", choice.dominantThreshold()))
                    .append(",dominant=")
                    .append(dominantJointName).append("@").append(String.format("%.4f", dominantWeight))
                    .append(",second=")
                    .append(secondJointName).append("@").append(String.format("%.4f", secondWeight))
                    .append(",blendSum=")
                    .append(String.format("%.4f", choice.blendWeightSum()))
                    .append("]");
            for (SkinningContribution contribution : resolveContributions(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            )) {
                weightSum += contribution.weight();
                if (contribution.weight() <= 0.000001F) {
                    continue;
                }
                if (contributions.length() > 0) {
                    contributions.append(" | ");
                }
                contributions.append(contribution.jointName())
                        .append("@w=").append(String.format("%.4f", contribution.weight()))
                        .append(" nwT=(")
                        .append(String.format("%.4f", contribution.nodeWorldTx())).append(",")
                        .append(String.format("%.4f", contribution.nodeWorldTy())).append(",")
                        .append(String.format("%.4f", contribution.nodeWorldTz())).append(")")
                        .append(" nwF=(")
                        .append(String.format("%.4f", contribution.nodeWorldForwardX())).append(",")
                        .append(String.format("%.4f", contribution.nodeWorldForwardY())).append(",")
                        .append(String.format("%.4f", contribution.nodeWorldForwardZ())).append(")")
                        .append(" jwT=(")
                        .append(String.format("%.4f", contribution.jointWorldTx())).append(",")
                        .append(String.format("%.4f", contribution.jointWorldTy())).append(",")
                        .append(String.format("%.4f", contribution.jointWorldTz())).append(")")
                        .append(" jwF=(")
                        .append(String.format("%.4f", contribution.jointWorldForwardX())).append(",")
                        .append(String.format("%.4f", contribution.jointWorldForwardY())).append(",")
                        .append(String.format("%.4f", contribution.jointWorldForwardZ())).append(")")
                        .append(" ib=(")
                        .append(String.format("%.4f", contribution.inverseBindX())).append(",")
                        .append(String.format("%.4f", contribution.inverseBindY())).append(",")
                        .append(String.format("%.4f", contribution.inverseBindZ())).append(")")
                        .append(" world->(")
                        .append(String.format("%.4f", contribution.worldSpaceX())).append(",")
                        .append(String.format("%.4f", contribution.worldSpaceY())).append(",")
                        .append(String.format("%.4f", contribution.worldSpaceZ())).append(")")
                        .append(" meshComp->(")
                        .append(String.format("%.4f", contribution.meshCompX())).append(",")
                        .append(String.format("%.4f", contribution.meshCompY())).append(",")
                        .append(String.format("%.4f", contribution.meshCompZ())).append(")")
                        .append(" dStatic=")
                        .append(String.format("%.4f", distance(
                                staticPosition,
                                new float[] {contribution.meshCompX(), contribution.meshCompY(), contribution.meshCompZ()}
                        )))
                        .append("->(")
                        .append(String.format("%.4f", contribution.x())).append(",")
                        .append(String.format("%.4f", contribution.y())).append(",")
                        .append(String.format("%.4f", contribution.z())).append(")");
            }
            SkinningDriftReport candidate = new SkinningDriftReport(
                    primitive.name(),
                    primitive.nodeName(),
                    primitive.skinIndex(),
                    vertexIndex,
                    nodeTransform.tx(),
                    nodeTransform.ty(),
                    nodeTransform.tz(),
                    meshWorld.m30(),
                    meshWorld.m31(),
                    meshWorld.m32(),
                    staticPosition[0],
                    staticPosition[1],
                    staticPosition[2],
                    parentStatic.x,
                    parentStatic.y,
                    parentStatic.z,
                    worldStatic.x,
                    worldStatic.y,
                    worldStatic.z,
                    skinnedPosition[0],
                    skinnedPosition[1],
                    skinnedPosition[2],
                    delta,
                    sampledVertices == 0 ? 0.0F : totalDelta / sampledVertices,
                    activeSampledVertices == 0 ? 0.0F : totalActiveDelta / activeSampledVertices,
                    top2Samples == 0 ? 0.0F : totalTop2ActualDelta / top2Samples,
                    sampledVertices == 0 ? 0.0F : totalOffsetX / sampledVertices,
                    sampledVertices == 0 ? 0.0F : totalOffsetY / sampledVertices,
                    sampledVertices == 0 ? 0.0F : totalOffsetZ / sampledVertices,
                    authoritativePosition[0],
                    authoritativePosition[1],
                    authoritativePosition[2],
                    authoritativeDelta,
                    sampledVertices == 0 ? 0.0F : totalAuthoritativeDelta / sampledVertices,
                    authoritativeWorldInputPosition[0],
                    authoritativeWorldInputPosition[1],
                    authoritativeWorldInputPosition[2],
                    authoritativeWorldInputDelta,
                    sampledVertices == 0 ? 0.0F : totalAuthoritativeWorldInputDelta / sampledVertices,
                    fullWorldSpacePosition[0],
                    fullWorldSpacePosition[1],
                    fullWorldSpacePosition[2],
                    fullWorldSpaceDelta,
                    sampledVertices == 0 ? 0.0F : totalFullWorldSpaceDelta / sampledVertices,
                    fullMeshCompPosition[0],
                    fullMeshCompPosition[1],
                    fullMeshCompPosition[2],
                    fullMeshCompDelta,
                    sampledVertices == 0 ? 0.0F : totalFullMeshCompDelta / sampledVertices,
                    activeSampledVertices == 0 ? 0.0F : totalFullMeshCompActiveDelta / activeSampledVertices,
                    dominantOnlySamples == 0 ? 0.0F : totalFullMeshCompDominantDelta / dominantOnlySamples,
                    top2Samples == 0 ? 0.0F : totalFullMeshCompTop2Delta / top2Samples,
                    fullMeshCompWorldStaticDelta,
                    sampledVertices == 0 ? 0.0F : totalFullMeshCompWorldStaticDelta / sampledVertices,
                    rootlessSkinnedPosition[0],
                    rootlessSkinnedPosition[1],
                    rootlessSkinnedPosition[2],
                    rootlessDelta,
                    sampledVertices == 0 ? 0.0F : totalRootlessDelta / sampledVertices,
                    jointIndexMin,
                    jointIndexMax,
                    skinJointCount,
                    weightSum,
                    renormalizedWeightSum <= 0.000001F ? 0.0F : 1.0F,
                    dominantOnlyVertices,
                    top2Vertices,
                    fallbackVertices,
                    sampledVertices,
                    contributions.toString()
            );
            report = candidate;
            if (secondWeight > 0.05F && fullMeshCompDelta > maxBlendedDelta) {
                maxBlendedDelta = fullMeshCompDelta;
                blendedReport = candidate;
            }
        }
        if (blendedReport != null) {
            report = blendedReport;
        }
        if (report != null && sampledVertices > 0) {
            report = new SkinningDriftReport(
                    report.primitiveName(),
                    report.nodeName(),
                    report.skinIndex(),
                    report.vertexIndex(),
                    report.nodeLocalTx(),
                    report.nodeLocalTy(),
                    report.nodeLocalTz(),
                    report.meshWorldTx(),
                    report.meshWorldTy(),
                    report.meshWorldTz(),
                    report.staticX(),
                    report.staticY(),
                    report.staticZ(),
                    report.parentStaticX(),
                    report.parentStaticY(),
                    report.parentStaticZ(),
                    report.worldStaticX(),
                    report.worldStaticY(),
                    report.worldStaticZ(),
                    report.skinnedX(),
                    report.skinnedY(),
                    report.skinnedZ(),
                    report.delta(),
                    totalDelta / sampledVertices,
                    activeSampledVertices == 0 ? 0.0F : totalActiveDelta / activeSampledVertices,
                    top2Samples == 0 ? 0.0F : totalTop2ActualDelta / top2Samples,
                    totalOffsetX / sampledVertices,
                    totalOffsetY / sampledVertices,
                    totalOffsetZ / sampledVertices,
                    report.authoritativeX(),
                    report.authoritativeY(),
                    report.authoritativeZ(),
                    report.authoritativeDelta(),
                    totalAuthoritativeDelta / sampledVertices,
                    report.authoritativeWorldInputX(),
                    report.authoritativeWorldInputY(),
                    report.authoritativeWorldInputZ(),
                    report.authoritativeWorldInputDelta(),
                    totalAuthoritativeWorldInputDelta / sampledVertices,
                    report.fullWorldSpaceX(),
                    report.fullWorldSpaceY(),
                    report.fullWorldSpaceZ(),
                    report.fullWorldSpaceDelta(),
                    totalFullWorldSpaceDelta / sampledVertices,
                    report.fullMeshCompX(),
                    report.fullMeshCompY(),
                    report.fullMeshCompZ(),
                    report.fullMeshCompDelta(),
                    totalFullMeshCompDelta / sampledVertices,
                    activeSampledVertices == 0 ? 0.0F : totalFullMeshCompActiveDelta / activeSampledVertices,
                    dominantOnlySamples == 0 ? 0.0F : totalFullMeshCompDominantDelta / dominantOnlySamples,
                    top2Samples == 0 ? 0.0F : totalFullMeshCompTop2Delta / top2Samples,
                    report.fullMeshCompWorldStaticDelta(),
                    totalFullMeshCompWorldStaticDelta / sampledVertices,
                    report.rootlessSkinnedX(),
                    report.rootlessSkinnedY(),
                    report.rootlessSkinnedZ(),
                    report.rootlessDelta(),
                    totalRootlessDelta / sampledVertices,
                    report.jointIndexMin(),
                    report.jointIndexMax(),
                    report.skinJointCount(),
                    report.weightSum(),
                    report.renormalizedWeightSum(),
                    dominantOnlyVertices,
                    top2Vertices,
                    fallbackVertices,
                    sampledVertices,
                    report.contributions()
            );
        }
        return report;
    }

    private String resolveJointName(SkinDefinition skinDefinition, int jointIndex) {
        if (skinDefinition == null || jointIndex < 0 || jointIndex >= skinDefinition.jointNodeNames().size()) {
            return "none";
        }
        return skinDefinition.jointNodeNames().get(jointIndex);
    }

    private void addTriangle(
            List<StaticTriangleMesh.Triangle> triangles,
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            NodeTransform nodeTransform,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, Boolean> experimentalEligibility,
            Map<String, List<Matrix4f>> skinMatricesCache,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            ModelSpaceRecenter recenter,
            int indexA,
            int indexB,
            int indexC
    ) {
        float[] a = resolveVertex(asset, primitive, nodeTransform, poseOverrides, worldMatrices, experimentalEligibility, skinMatricesCache, experimentalSkinning, experimentalPrimitiveIndex, indexA, recenter);
        float[] b = resolveVertex(asset, primitive, nodeTransform, poseOverrides, worldMatrices, experimentalEligibility, skinMatricesCache, experimentalSkinning, experimentalPrimitiveIndex, indexB, recenter);
        float[] c = resolveVertex(asset, primitive, nodeTransform, poseOverrides, worldMatrices, experimentalEligibility, skinMatricesCache, experimentalSkinning, experimentalPrimitiveIndex, indexC, recenter);
        if (a == null || b == null || c == null) {
            return;
        }

        float[] normal = resolveNormal(
                asset,
                primitive,
                nodeTransform,
                poseOverrides,
                worldMatrices,
                experimentalEligibility,
                skinMatricesCache,
                experimentalSkinning,
                experimentalPrimitiveIndex,
                indexA,
                indexB,
                indexC,
                a,
                b,
                c
        );
        float lightDot = Math.max(0.0F, (normal[0] * LIGHT_X) + (normal[1] * LIGHT_Y) + (normal[2] * LIGHT_Z));
        float upwardness = Math.max(0.0F, normal[1]);
        float shade = AMBIENT + (lightDot * DIFFUSE) + (upwardness * RIM);
        float red = clampColor(BASE_RED * shade);
        float green = clampColor(BASE_GREEN * shade);
        float blue = clampColor(BASE_BLUE * shade);

        triangles.add(new StaticTriangleMesh.Triangle(
                a[0], a[1], a[2],
                b[0], b[1], b[2],
                c[0], c[1], c[2],
                red, green, blue
        ));
    }

    private float[] resolveVertex(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            NodeTransform nodeTransform,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, Boolean> experimentalEligibility,
            Map<String, List<Matrix4f>> skinMatricesCache,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            int vertexIndex,
            ModelSpaceRecenter recenter
    ) {
        int baseIndex = vertexIndex * 3;
        if (baseIndex + 2 >= primitive.positions().size()) {
            return null;
        }

        float localX = primitive.positions().get(baseIndex);
        float localY = primitive.positions().get(baseIndex + 1);
        float localZ = primitive.positions().get(baseIndex + 2);
        boolean skinnedPrimitive = isSkinnedPrimitive(primitive);
        boolean useExperimentalSkinning = experimentalSkinning && shouldUseExperimentalSkinning(asset, primitive, worldMatrices, experimentalEligibility, experimentalPrimitiveIndex);
        float[] transformed;
        if (useExperimentalSkinning) {
            transformed = skinPositionWorldMeshComp(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, localX, localY, localZ, vertexIndex, false);
        } else if (skinnedPrimitive) {
            transformed = skinPositionWorldMeshComp(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, localX, localY, localZ, vertexIndex, false);
        } else {
            transformed = transformPosition(localX, localY, localZ, nodeTransform);
        }

        return recenter.apply(transformed);
    }

    private float[] resolveNormal(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            NodeTransform nodeTransform,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, Boolean> experimentalEligibility,
            Map<String, List<Matrix4f>> skinMatricesCache,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            int indexA,
            int indexB,
            int indexC,
            float[] a,
            float[] b,
            float[] c
    ) {
        if (primitive.normals().isEmpty()) {
            return computeFaceNormal(a, b, c);
        }

        boolean useExperimentalSkinning = experimentalSkinning
                && shouldUseExperimentalSkinning(asset, primitive, worldMatrices, experimentalEligibility, experimentalPrimitiveIndex);
        boolean useSkinnedNormals = useExperimentalSkinning || isSkinnedPrimitive(primitive);
        float[] na = readNormal(asset, primitive, nodeTransform, poseOverrides, worldMatrices, skinMatricesCache, useSkinnedNormals, indexA, false);
        float[] nb = readNormal(asset, primitive, nodeTransform, poseOverrides, worldMatrices, skinMatricesCache, useSkinnedNormals, indexB, false);
        float[] nc = readNormal(asset, primitive, nodeTransform, poseOverrides, worldMatrices, skinMatricesCache, useSkinnedNormals, indexC, false);
        if (na == null || nb == null || nc == null) {
            return computeFaceNormal(a, b, c);
        }

        float nx = na[0] + nb[0] + nc[0];
        float ny = na[1] + nb[1] + nc[1];
        float nz = na[2] + nb[2] + nc[2];
        float length = (float) Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
        if (length < 0.000001F) {
            return computeFaceNormal(a, b, c);
        }
        return new float[] {nx / length, ny / length, nz / length};
    }

    private float[] readNormal(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            NodeTransform nodeTransform,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            boolean experimentalSkinning,
            int vertexIndex,
            boolean stripExplicitRoot
    ) {
        int baseIndex = vertexIndex * 3;
        if (baseIndex + 2 >= primitive.normals().size()) {
            return null;
        }
        float nx = primitive.normals().get(baseIndex);
        float ny = primitive.normals().get(baseIndex + 1);
        float nz = primitive.normals().get(baseIndex + 2);
        float[] transformed = experimentalSkinning
                ? skinNormalWorldMeshComp(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, nx, ny, nz, vertexIndex, stripExplicitRoot)
                : transformDirection(nx, ny, nz, nodeTransform);
        nx = transformed[0];
        ny = transformed[1];
        nz = transformed[2];
        float length = (float) Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
        if (length < 0.000001F) {
            return null;
        }
        return new float[] {nx / length, ny / length, nz / length};
    }


    private boolean isSkinnedPrimitive(MeshPrimitiveData primitive) {
        return primitive.skinIndex() >= 0 && !primitive.joints().isEmpty() && !primitive.weights().isEmpty();
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

    private float[] transformDirection(float x, float y, float z, NodeTransform transform) {
        float scaledX = Math.abs(transform.sx()) > 0.000001F ? x / transform.sx() : x;
        float scaledY = Math.abs(transform.sy()) > 0.000001F ? y / transform.sy() : y;
        float scaledZ = Math.abs(transform.sz()) > 0.000001F ? z / transform.sz() : z;

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

        return new float[] {rotatedX, rotatedY, rotatedZ};
    }

    static List<Float> generateSmoothNormals(List<Float> positions, List<Integer> indices) {
        int vertexCount = positions.size() / 3;
        float[] accumulated = new float[vertexCount * 3];
        if (!indices.isEmpty()) {
            for (int i = 0; i + 2 < indices.size(); i += 3) {
                accumulateFaceNormal(positions, accumulated, indices.get(i), indices.get(i + 1), indices.get(i + 2));
            }
        } else {
            for (int vertex = 0; vertex + 2 < vertexCount; vertex += 3) {
                accumulateFaceNormal(positions, accumulated, vertex, vertex + 1, vertex + 2);
            }
        }

        Map<PositionKey, float[]> weldedNormals = new HashMap<>();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = vertex * 3;
            PositionKey key = new PositionKey(positions.get(base), positions.get(base + 1), positions.get(base + 2));
            float[] normal = weldedNormals.computeIfAbsent(key, ignored -> new float[3]);
            normal[0] += accumulated[base];
            normal[1] += accumulated[base + 1];
            normal[2] += accumulated[base + 2];
        }

        List<Float> result = new ArrayList<>(vertexCount * 3);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int base = vertex * 3;
            PositionKey key = new PositionKey(positions.get(base), positions.get(base + 1), positions.get(base + 2));
            float[] normal = weldedNormals.get(key);
            float length = (float) Math.sqrt((normal[0] * normal[0]) + (normal[1] * normal[1]) + (normal[2] * normal[2]));
            if (length < 0.000001F) {
                result.add(0.0F);
                result.add(1.0F);
                result.add(0.0F);
            } else {
                result.add(normal[0] / length);
                result.add(normal[1] / length);
                result.add(normal[2] / length);
            }
        }
        return result;
    }

    private static void accumulateFaceNormal(List<Float> positions, float[] accumulated, int a, int b, int c) {
        int vertexCount = positions.size() / 3;
        if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount) {
            return;
        }
        int ai = a * 3;
        int bi = b * 3;
        int ci = c * 3;
        float abx = positions.get(bi) - positions.get(ai);
        float aby = positions.get(bi + 1) - positions.get(ai + 1);
        float abz = positions.get(bi + 2) - positions.get(ai + 2);
        float acx = positions.get(ci) - positions.get(ai);
        float acy = positions.get(ci + 1) - positions.get(ai + 1);
        float acz = positions.get(ci + 2) - positions.get(ai + 2);
        float nx = (aby * acz) - (abz * acy);
        float ny = (abz * acx) - (abx * acz);
        float nz = (abx * acy) - (aby * acx);
        for (int vertex : new int[] {a, b, c}) {
            int base = vertex * 3;
            accumulated[base] += nx;
            accumulated[base + 1] += ny;
            accumulated[base + 2] += nz;
        }
    }

    private record PositionKey(float x, float y, float z) {
    }

    private float[] computeFaceNormal(float[] a, float[] b, float[] c) {
        float abx = b[0] - a[0];
        float aby = b[1] - a[1];
        float abz = b[2] - a[2];
        float acx = c[0] - a[0];
        float acy = c[1] - a[1];
        float acz = c[2] - a[2];

        float nx = (aby * acz) - (abz * acy);
        float ny = (abz * acx) - (abx * acz);
        float nz = (abx * acy) - (aby * acx);
        float length = (float) Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
        if (length < 0.000001F) {
            return new float[] {0.0F, 1.0F, 0.0F};
        }
        return new float[] {nx / length, ny / length, nz / length};
    }

    private float clampColor(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private boolean shouldUseExperimentalSkinning(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, Matrix4f> worldMatrices,
            Map<String, Boolean> experimentalEligibility,
            int experimentalPrimitiveIndex
    ) {
        String cacheKey = primitive.name() + "|" + primitive.nodeName() + "|" + primitive.skinIndex() + "|" + experimentalPrimitiveIndex;
        if (experimentalEligibility.containsKey(cacheKey)) {
            return experimentalEligibility.get(cacheKey);
        }

        boolean eligible = isExperimentalPrimitive(asset, primitive, experimentalPrimitiveIndex);
        experimentalEligibility.put(cacheKey, eligible);
        return eligible;
    }

    private boolean isExperimentalPrimitive(RuntimeModelAsset asset, MeshPrimitiveData primitive, int experimentalPrimitiveIndex) {
        return experimentalSkinningPolicy.isEligiblePrimitive(primitive);
    }

    private float[] skinPositionWorldMeshComp(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        return skinPositionWorldMeshComp(
                asset,
                primitive,
                poseOverrides,
                worldMatrices,
                skinMatricesCache,
                localX,
                localY,
                localZ,
                vertexIndex,
                false
        );
    }

    private float[] skinPositionWorldMeshComp(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex,
            boolean stripExplicitRoot
    ) {
        NodeTransform fallbackTransform = resolveNodeTransform(asset, primitive.nodeName(), poseOverrides);
        if (primitive.skinIndex() < 0 || primitive.joints().isEmpty() || primitive.weights().isEmpty()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size()) {
            return transformPosition(localX, localY, localZ, fallbackTransform);
        }

        ExperimentalSkinningPolicy.VertexChoice choice = experimentalSkinningPolicy.analyzeVertex(primitive, vertexIndex);
        List<Matrix4f> skinMatrices = stripExplicitRoot
                ? resolveExperimentalSkinMatrices(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, true)
                : resolveExperimentalSkinMatrices(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, false);
        if (!stripExplicitRoot) {
            experimentalSkinningTraceLogger.maybeTraceVertex(
                    asset,
                    primitive,
                    resolveWorldMatrix(asset, primitive.nodeName(), poseOverrides, worldMatrices),
                    skinMatrices,
                    localX,
                    localY,
                    localZ,
                    vertexIndex
            );
            return cleanSkinningPath.applyFullBlendWorldInput(
                    asset,
                    primitive,
                    poseOverrides,
                    worldMatrices,
                    skinMatricesCache,
                    localX,
                    localY,
                    localZ,
                    vertexIndex,
                    fallbackTransform
            );
        }
        return experimentalSkinningApplier.applyWorldMeshComp(
                primitive,
                vertexIndex,
                localX,
                localY,
                localZ,
                fallbackTransform,
                choice,
                skinMatrices
        );
    }

    private List<SkinningContribution> resolveContributions(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        SkinDefinition skinDefinition = asset.skin(primitive.skinIndex()).orElse(null);
        if (skinDefinition == null) {
            return List.of();
        }
        int base = vertexIndex * 4;
        if (base + 3 >= primitive.joints().size() || base + 3 >= primitive.weights().size()) {
            return List.of();
        }
        NodeTransform primitiveNodeLocal = resolveNodeTransform(asset, primitive.nodeName(), poseOverrides);
        Matrix4f nodeWorld = resolveWorldMatrix(asset, primitive.nodeName(), poseOverrides, worldMatrices);
        Vector3f nodeForward = new Vector3f(0.0F, 0.0F, 1.0F);
        nodeWorld.transformDirection(nodeForward);
        nodeForward.normalize();
        List<Matrix4f> skinMatrices = resolveExperimentalSkinMatrices(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, false);
        List<SkinningContribution> contributions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(base + i);
            float weight = primitive.weights().get(base + i);
            if (jointIndex < 0 || jointIndex >= skinDefinition.jointNodeNames().size()) {
                continue;
            }
            String jointName = skinDefinition.jointNodeNames().get(jointIndex);
            Matrix4f jointWorld = resolveWorldMatrix(asset, jointName, poseOverrides, worldMatrices);
            Vector3f jointForward = new Vector3f(0.0F, 0.0F, 1.0F);
            jointWorld.transformDirection(jointForward);
            jointForward.normalize();
            Matrix4f meshWorld = resolveWorldMatrix(asset, primitive.nodeName(), poseOverrides, worldMatrices);
            Matrix4f relative = new Matrix4f(meshWorld).invert().mul(jointWorld);
            Matrix4f parentRelative = resolveParentRelativeMatrix(asset, primitive.nodeName(), jointWorld, poseOverrides, worldMatrices);
            Matrix4f rootRelative = resolveRootRelativeMatrix(asset, jointWorld, poseOverrides, worldMatrices);
            Vector3f inverseBindOnly = new Vector3f(localX, localY, localZ);
            if (jointIndex < skinDefinition.inverseBindMatrices().size()) {
                toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex)).transformPosition(inverseBindOnly);
            }
            Vector3f transformed = new Vector3f(localX, localY, localZ);
            if (jointIndex < skinMatrices.size()) {
                skinMatrices.get(jointIndex).transformPosition(transformed);
            }
            Vector3f worldSpaceTransformed = new Vector3f(localX, localY, localZ);
            Vector3f rootRelativeTransformed = new Vector3f(localX, localY, localZ);
            if (jointIndex < skinDefinition.inverseBindMatrices().size()) {
                Matrix4f worldSpaceSkin = new Matrix4f(jointWorld).mul(toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex)));
                worldSpaceSkin.transformPosition(worldSpaceTransformed);
                Matrix4f rootRelativeSkin = new Matrix4f(rootRelative).mul(toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex)));
                rootRelativeSkin.transformPosition(rootRelativeTransformed);
            }
            Vector3f meshCompTransformed = new Vector3f(localX, localY, localZ);
            Vector3f meshFirstTransformed = new Vector3f(localX, localY, localZ);
            Vector3f meshMiddleTransformed = new Vector3f(localX, localY, localZ);
            Vector3f nodeLocalCompTransformed = new Vector3f(localX, localY, localZ);
            Vector3f parentCompTransformed = new Vector3f(localX, localY, localZ);
            if (jointIndex < skinDefinition.inverseBindMatrices().size()) {
                Matrix4f inverseBind = toMatrix(skinDefinition.inverseBindMatrices().get(jointIndex));
                Matrix4f meshCompSkin = new Matrix4f(jointWorld)
                        .mul(inverseBind)
                        .mul(meshWorld);
                meshCompSkin.transformPosition(meshCompTransformed);
                Matrix4f meshFirstSkin = new Matrix4f(meshWorld)
                        .mul(jointWorld)
                        .mul(inverseBind);
                meshFirstSkin.transformPosition(meshFirstTransformed);
                Matrix4f meshMiddleSkin = new Matrix4f(jointWorld)
                        .mul(meshWorld)
                        .mul(inverseBind);
                meshMiddleSkin.transformPosition(meshMiddleTransformed);
                Matrix4f nodeLocalCompSkin = new Matrix4f(jointWorld)
                        .mul(inverseBind)
                        .mul(new Matrix4f().translationRotateScale(
                                primitiveNodeLocal.tx(),
                                primitiveNodeLocal.ty(),
                                primitiveNodeLocal.tz(),
                                primitiveNodeLocal.rx(),
                                primitiveNodeLocal.ry(),
                                primitiveNodeLocal.rz(),
                                primitiveNodeLocal.rw(),
                                primitiveNodeLocal.sx(),
                                primitiveNodeLocal.sy(),
                                primitiveNodeLocal.sz()
                        ));
                nodeLocalCompSkin.transformPosition(nodeLocalCompTransformed);
                Matrix4f parentCompSkin = new Matrix4f(jointWorld)
                        .mul(inverseBind)
                        .mul(resolveParentWorldMatrix(asset, primitive.nodeName(), poseOverrides, worldMatrices));
                parentCompSkin.transformPosition(parentCompTransformed);
            }
            contributions.add(new SkinningContribution(
                    jointName,
                    weight,
                    nodeWorld.m30(),
                    nodeWorld.m31(),
                    nodeWorld.m32(),
                    nodeForward.x,
                    nodeForward.y,
                    nodeForward.z,
                    jointWorld.m30(),
                    jointWorld.m31(),
                    jointWorld.m32(),
                    jointForward.x,
                    jointForward.y,
                    jointForward.z,
                    relative.m30(),
                    relative.m31(),
                    relative.m32(),
                    parentRelative.m30(),
                    parentRelative.m31(),
                    parentRelative.m32(),
                    rootRelative.m30(),
                    rootRelative.m31(),
                    rootRelative.m32(),
                    inverseBindOnly.x,
                    inverseBindOnly.y,
                    inverseBindOnly.z,
                    worldSpaceTransformed.x,
                    worldSpaceTransformed.y,
                    worldSpaceTransformed.z,
                    rootRelativeTransformed.x,
                    rootRelativeTransformed.y,
                    rootRelativeTransformed.z,
                    meshCompTransformed.x,
                    meshCompTransformed.y,
                    meshCompTransformed.z,
                    meshFirstTransformed.x,
                    meshFirstTransformed.y,
                    meshFirstTransformed.z,
                    meshMiddleTransformed.x,
                    meshMiddleTransformed.y,
                    meshMiddleTransformed.z,
                    nodeLocalCompTransformed.x,
                    nodeLocalCompTransformed.y,
                    nodeLocalCompTransformed.z,
                    parentCompTransformed.x,
                    parentCompTransformed.y,
                    parentCompTransformed.z,
                    transformed.x,
                    transformed.y,
                    transformed.z
            ));
        }
        return List.copyOf(contributions);
    }

    private Matrix4f resolveParentRelativeMatrix(
            RuntimeModelAsset asset,
            String nodeName,
            Matrix4f jointWorld,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        String parentName = asset.nodeHierarchy(nodeName)
                .map(com.yourname.hb3d.asset.NodeHierarchy::parentName)
                .orElse(null);
        if (parentName == null || parentName.equals(nodeName)) {
            return new Matrix4f(jointWorld);
        }
        Matrix4f parentWorld = resolveWorldMatrix(asset, parentName, poseOverrides, worldMatrices);
        return new Matrix4f(parentWorld).invert().mul(jointWorld);
    }

    private List<Matrix4f> resolveExperimentalSkinMatrices(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            boolean stripExplicitRoot
    ) {
        ExperimentalSkinningContext context = new ExperimentalSkinningContext(
                asset,
                poseOverrides,
                worldMatrices,
                skinMatricesCache,
                experimentalSkinningSolver
        );
        return context.resolveSkinMatrices(primitive, stripExplicitRoot);
    }

    private List<Matrix4f> resolveAuthoritativeSkinMatrices(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache
    ) {
        String cacheKey = primitive.name() + "|" + primitive.nodeName() + "|" + primitive.skinIndex() + "|authoritative-live";
        if (skinMatricesCache.containsKey(cacheKey)) {
            return skinMatricesCache.get(cacheKey);
        }
        List<Matrix4f> matrices = authoritativeSkinningPath.buildJointMatrices(asset, primitive, poseOverrides, worldMatrices);
        skinMatricesCache.put(cacheKey, matrices);
        return matrices;
    }

    private Matrix4f resolveParentWorldMatrix(
            RuntimeModelAsset asset,
            String nodeName,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        String parentName = asset.nodeHierarchy(nodeName)
                .map(com.yourname.hb3d.asset.NodeHierarchy::parentName)
                .orElse(null);
        if (parentName == null || parentName.equals(nodeName)) {
            return new Matrix4f();
        }
        return resolveWorldMatrix(asset, parentName, poseOverrides, worldMatrices);
    }

    private Matrix4f resolveRootRelativeMatrix(
            RuntimeModelAsset asset,
            Matrix4f jointWorld,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        Matrix4f explicitRootInverse = resolveExplicitRootInverse(asset, poseOverrides, worldMatrices);
        if (explicitRootInverse == null) {
            return new Matrix4f(jointWorld);
        }
        return new Matrix4f(explicitRootInverse).mul(jointWorld);
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

    private float distance(float[] a, float[] b) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        return (float) Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }

    private float[] skinNormalWorldMeshComp(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float nx,
            float ny,
            float nz,
            int vertexIndex,
            boolean stripExplicitRoot
    ) {
        if (primitive.skinIndex() < 0 || primitive.joints().isEmpty() || primitive.weights().isEmpty()) {
            return transformDirection(nx, ny, nz, resolveNodeTransform(asset, primitive.nodeName(), poseOverrides));
        }

        int skinBase = vertexIndex * 4;
        if (skinBase + 3 >= primitive.joints().size() || skinBase + 3 >= primitive.weights().size()) {
            return transformDirection(nx, ny, nz, resolveNodeTransform(asset, primitive.nodeName(), poseOverrides));
        }

        List<Matrix4f> skinMatrices = resolveExperimentalSkinMatrices(asset, primitive, poseOverrides, worldMatrices, skinMatricesCache, stripExplicitRoot);
        if (skinMatrices.isEmpty()) {
            return transformDirection(nx, ny, nz, resolveNodeTransform(asset, primitive.nodeName(), poseOverrides));
        }

        Vector3f blended = new Vector3f();
        float totalWeight = 0.0F;
        for (int i = 0; i < 4; i++) {
            int jointIndex = primitive.joints().get(skinBase + i);
            float weight = primitive.weights().get(skinBase + i);
            if (weight <= 0.000001F || jointIndex < 0 || jointIndex >= skinMatrices.size()) {
                continue;
            }

            Vector3f transformed = new Vector3f(nx, ny, nz);
            skinMatrices.get(jointIndex).transformDirection(transformed);
            blended.add(transformed.mul(weight));
            totalWeight += weight;
        }

        if (totalWeight <= 0.000001F) {
            return transformDirection(nx, ny, nz, resolveNodeTransform(asset, primitive.nodeName(), poseOverrides));
        }
        if (totalWeight < 0.9999F) {
            blended.div(totalWeight);
        }
        return new float[] {blended.x, blended.y, blended.z};
    }

    private float[] skinPositionFullMeshComp(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        NodeTransform fallbackTransform = resolveNodeTransform(asset, primitive.nodeName(), poseOverrides);
        return cleanSkinningPath.applyFullBlendWorldInput(
                asset,
                primitive,
                poseOverrides,
                worldMatrices,
                skinMatricesCache,
                localX,
                localY,
                localZ,
                vertexIndex,
                fallbackTransform
        );
    }

    private float[] skinPositionFullWorldSpace(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        NodeTransform fallbackTransform = resolveNodeTransform(asset, primitive.nodeName(), poseOverrides);
        List<Matrix4f> skinMatrices = new ExperimentalSkinningContext(
                asset,
                poseOverrides,
                worldMatrices,
                skinMatricesCache,
                experimentalSkinningSolver
        ).resolveSkinMatrices(primitive, ExperimentalSkinningSolver.SkinMatrixVariant.WORLD_SPACE);
        return experimentalSkinningApplier.applyFullBlend(
                primitive,
                vertexIndex,
                localX,
                localY,
                localZ,
                fallbackTransform,
                skinMatrices
        );
    }

    private float[] skinPositionAuthoritative(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        NodeTransform fallbackTransform = resolveNodeTransform(asset, primitive.nodeName(), poseOverrides);
        String cacheKey = primitive.name() + "|" + primitive.nodeName() + "|" + primitive.skinIndex() + "|authoritative";
        List<Matrix4f> jointMatrices = skinMatricesCache.get(cacheKey);
        if (jointMatrices == null) {
            jointMatrices = authoritativeSkinningPath.buildJointMatrices(asset, primitive, poseOverrides, worldMatrices);
            skinMatricesCache.put(cacheKey, jointMatrices);
        }
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

    private float[] skinPositionAuthoritativeWorldInput(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices,
            Map<String, List<Matrix4f>> skinMatricesCache,
            float localX,
            float localY,
            float localZ,
            int vertexIndex
    ) {
        NodeTransform fallbackTransform = resolveNodeTransform(asset, primitive.nodeName(), poseOverrides);
        return cleanSkinningPath.applyFullBlendWorldInput(
                asset,
                primitive,
                poseOverrides,
                worldMatrices,
                skinMatricesCache,
                localX,
                localY,
                localZ,
                vertexIndex,
                fallbackTransform
        );
    }

    private NodeTransform resolveNodeTransform(
            RuntimeModelAsset asset,
            String nodeName,
            Map<String, NodeTransform> poseOverrides
    ) {
        return new ExperimentalSkinningContext(asset, poseOverrides, new HashMap<>(), new HashMap<>(), experimentalSkinningSolver)
                .resolveNodeTransform(nodeName);
    }

    private Matrix4f resolveWorldMatrix(
            RuntimeModelAsset asset,
            String nodeName,
            Map<String, NodeTransform> poseOverrides,
            Map<String, Matrix4f> worldMatrices
    ) {
        return new ExperimentalSkinningContext(asset, poseOverrides, worldMatrices, new HashMap<>(), experimentalSkinningSolver)
                .resolveWorldMatrix(nodeName);
    }

    private boolean isNodeDescendantOf(RuntimeModelAsset asset, String nodeName, String ancestorName) {
        String current = nodeName;
        while (current != null && !current.isBlank()) {
            if (current.equals(ancestorName)) {
                return true;
            }
            current = asset.nodeHierarchy(current)
                    .map(com.yourname.hb3d.asset.NodeHierarchy::parentName)
                    .orElse(null);
        }
        return false;
    }

    private Matrix4f toMatrix(float[] values) {
        return new Matrix4f(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]
        );
    }

    private Matrix4f toTransformMatrix(NodeTransform transform) {
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
}
