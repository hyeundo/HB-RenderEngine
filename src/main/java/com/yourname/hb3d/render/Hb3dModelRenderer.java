package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public final class Hb3dModelRenderer {
    private final StaticMeshBuilder staticMeshBuilder = new StaticMeshBuilder();
    private final Map<String, StaticTriangleMesh> staticMeshes = new ConcurrentHashMap<>();
    private final StaticMeshUploader staticMeshUploader = new StaticMeshUploader();
    private final Map<String, UploadedStaticMesh> uploadedStaticMeshes = new ConcurrentHashMap<>();
    private final Map<String, BaselineTexturedPrimitiveSet> baselineTexturedPrimitiveSets = new ConcurrentHashMap<>();
    private final TexturedPrimitiveUploader texturedPrimitiveUploader = new TexturedPrimitiveUploader();
    private final SkeletonPoseCalculator poseCalculator = new SkeletonPoseCalculator();
    private final Map<String, UploadedTexturedPrimitive> uploadedTexturedPrimitives = new ConcurrentHashMap<>();
    private final Map<Integer, UploadedStaticMesh> animatedUploadedStaticMeshes = new ConcurrentHashMap<>();
    private final Map<Integer, String> animatedMeshKeys = new ConcurrentHashMap<>();
    private final Map<Integer, List<UploadedTexturedPrimitive>> animatedUploadedTexturedPrimitives = new ConcurrentHashMap<>();
    private final Map<Integer, String> animatedTexturedKeys = new ConcurrentHashMap<>();
    private final Map<String, List<TexturedPrimitiveTarget>> texturedPrimitiveTargets = new ConcurrentHashMap<>();

    public StaticTriangleMesh prepareStaticMesh(RuntimeModelAsset asset) {
        return staticMeshes.computeIfAbsent(asset.modelId(), ignored -> staticMeshBuilder.build(asset));
    }

    public UploadedStaticMesh prepareUploadedStaticMesh(RuntimeModelAsset asset) {
        return uploadedStaticMeshes.computeIfAbsent(asset.modelId(), ignored -> {
            StaticTriangleMesh staticMesh = prepareStaticMesh(asset);
            return staticMeshUploader.upload(staticMesh);
        });
    }

    public UploadedStaticMesh prepareAnimatedUploadedStaticMesh(RuntimeModelAsset asset, VisualInstance instance) {
        return prepareAnimatedUploadedStaticMesh(asset, instance, false);
    }

    public UploadedStaticMesh prepareAnimatedUploadedStaticMesh(RuntimeModelAsset asset, VisualInstance instance, boolean experimentalSkinning) {
        return prepareAnimatedUploadedStaticMesh(asset, instance, experimentalSkinning, 0);
    }

    public UploadedStaticMesh prepareAnimatedUploadedStaticMesh(RuntimeModelAsset asset, VisualInstance instance, boolean experimentalSkinning, int experimentalPrimitiveIndex) {
        return prepareAnimatedUploadedStaticMesh(asset, instance, experimentalSkinning, experimentalPrimitiveIndex, 1.0F);
    }

    public UploadedStaticMesh prepareAnimatedUploadedStaticMesh(
            RuntimeModelAsset asset,
            VisualInstance instance,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            float interpolationAlpha
    ) {
        String meshKey = buildAnimatedMeshKey(asset, instance, experimentalSkinning, experimentalPrimitiveIndex, interpolationAlpha);
        UploadedStaticMesh cached = animatedUploadedStaticMeshes.get(instance.entityId());
        if (cached != null && meshKey.equals(animatedMeshKeys.get(instance.entityId()))) {
            return cached;
        }

        UploadedStaticMesh previous = animatedUploadedStaticMeshes.remove(instance.entityId());
        if (previous != null) {
            previous.close();
        }

        Map<String, NodeTransform> poseOverrides = buildRenderPoseOverrides(asset, instance, interpolationAlpha);
        StaticTriangleMesh animatedMesh = staticMeshBuilder.build(asset, poseOverrides, experimentalSkinning, experimentalPrimitiveIndex);
        UploadedStaticMesh uploaded = staticMeshUploader.upload(animatedMesh);
        animatedUploadedStaticMeshes.put(instance.entityId(), uploaded);
        animatedMeshKeys.put(instance.entityId(), meshKey);
        return uploaded;
    }

    public List<UploadedTexturedPrimitive> prepareAnimatedUploadedTexturedPrimitives(
            RuntimeModelAsset asset,
            VisualInstance instance,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex
    ) {
        return prepareAnimatedUploadedTexturedPrimitives(asset, instance, experimentalSkinning, experimentalPrimitiveIndex, 1.0F);
    }

    public List<UploadedTexturedPrimitive> prepareAnimatedUploadedTexturedPrimitives(
            RuntimeModelAsset asset,
            VisualInstance instance,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            float interpolationAlpha
    ) {
        String meshKey = buildAnimatedMeshKey(asset, instance, experimentalSkinning, experimentalPrimitiveIndex, interpolationAlpha) + "|textured";
        List<UploadedTexturedPrimitive> cached = animatedUploadedTexturedPrimitives.get(instance.entityId());
        if (cached != null && meshKey.equals(animatedTexturedKeys.get(instance.entityId()))) {
            return cached;
        }

        List<UploadedTexturedPrimitive> previous = animatedUploadedTexturedPrimitives.remove(instance.entityId());
        if (previous != null) {
            previous.forEach(UploadedTexturedPrimitive::close);
        }

        Map<String, NodeTransform> poseOverrides = buildRenderPoseOverrides(asset, instance, interpolationAlpha);
        List<TexturedPrimitiveTarget> texturedTargets = prepareTexturedPrimitiveTargets(asset);
        List<com.yourname.hb3d.asset.MeshPrimitiveData> texturedPrimitives = texturedTargets.stream()
                .map(TexturedPrimitiveTarget::primitive)
                .toList();
        List<StaticMeshBuilder.PrimitiveWorldGeometry> geometries = staticMeshBuilder.buildPrimitiveWorldGeometries(
                asset,
                texturedPrimitives,
                poseOverrides,
                experimentalSkinning,
                experimentalPrimitiveIndex
        );
        Map<Identifier, List<StaticMeshBuilder.PrimitiveWorldGeometry>> groupedGeometries = new java.util.LinkedHashMap<>();
        Map<Identifier, String> batchLabels = new java.util.LinkedHashMap<>();
        for (int i = 0; i < texturedTargets.size(); i++) {
            TexturedPrimitiveTarget target = texturedTargets.get(i);
            StaticMeshBuilder.PrimitiveWorldGeometry geometry = geometries.get(i);
            if (geometry.vertexCount() <= 0) {
                continue;
            }
            groupedGeometries.computeIfAbsent(target.textureId(), ignored -> new ArrayList<>()).add(geometry);
            batchLabels.putIfAbsent(target.textureId(), target.primitive().name());
        }

        List<UploadedTexturedPrimitive> uploaded = new ArrayList<>(groupedGeometries.size());
        for (Map.Entry<Identifier, List<StaticMeshBuilder.PrimitiveWorldGeometry>> entry : groupedGeometries.entrySet()) {
            StaticMeshBuilder.PrimitiveWorldGeometry mergedGeometry = mergePrimitiveWorldGeometries(entry.getValue());
            if (mergedGeometry.vertexCount() <= 0) {
                continue;
            }
            uploaded.add(texturedPrimitiveUploader.upload(
                    batchLabels.getOrDefault(entry.getKey(), "merged"),
                    "merged",
                    entry.getKey(),
                    mergedGeometry,
                    null
            ));
        }
        List<UploadedTexturedPrimitive> immutable = List.copyOf(uploaded);
        animatedUploadedTexturedPrimitives.put(instance.entityId(), immutable);
        animatedTexturedKeys.put(instance.entityId(), meshKey);
        return immutable;
    }

    private String buildAnimatedMeshKey(
            RuntimeModelAsset asset,
            VisualInstance instance,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex,
            float interpolationAlpha
    ) {
        float quantizedAlpha = quantizeInterpolationAlpha(interpolationAlpha);
        String baseKey = asset.modelId()
                + "|clip=" + instance.animationPlayer().getActiveClip().orElse("")
                + "|time=" + Float.floatToIntBits(instance.animationPlayer().getClipTimeSeconds())
                + "|interp=" + Float.floatToIntBits(quantizedAlpha)
                + "|seq=" + instance.actionSequence()
                + "|base=" + instance.baseState()
                + "|action=" + instance.actionState();
        if (experimentalSkinning) {
            return baseKey + "|exp|" + experimentalPrimitiveIndex;
        }
        return baseKey;
    }

    private Map<String, NodeTransform> buildRenderPoseOverrides(RuntimeModelAsset asset, VisualInstance instance, float interpolationAlpha) {
        float quantizedAlpha = quantizeInterpolationAlpha(interpolationAlpha);
        Map<String, NodeTransform> current = instance.localNodePoseCache().snapshot();
        if (quantizedAlpha >= 0.999999F) {
            return new HashMap<>(current);
        }
        return poseCalculator.interpolateLocalPoses(
                asset,
                instance.previousLocalNodePoseCache().snapshot(),
                current,
                quantizedAlpha
        );
    }

    private float quantizeInterpolationAlpha(float interpolationAlpha) {
        float clamped = Math.max(0.0F, Math.min(1.0F, interpolationAlpha));
        return Math.round(clamped * 4.0F) / 4.0F;
    }

    public StaticTriangleMesh getPreparedStaticMesh(String modelId) {
        return staticMeshes.get(modelId);
    }

    public UploadedStaticMesh getPreparedUploadedStaticMesh(String modelId) {
        return uploadedStaticMeshes.get(modelId);
    }

    public StaticMeshBuilder.PrimitiveWorldGeometry preparePrimitiveWorldGeometry(
            RuntimeModelAsset asset,
            com.yourname.hb3d.asset.MeshPrimitiveData primitive,
            VisualInstance instance,
            boolean experimentalSkinning,
            int experimentalPrimitiveIndex
    ) {
        Map<String, com.yourname.hb3d.asset.NodeTransform> poseOverrides = new HashMap<>(instance.localNodePoseCache().snapshot());
        return staticMeshBuilder.buildPrimitiveWorldGeometry(
                asset,
                primitive,
                poseOverrides,
                experimentalSkinning,
                experimentalPrimitiveIndex
        );
    }

    public List<BaselineTexturedPrimitive> prepareBaselineTexturedPrimitives(RuntimeModelAsset asset) {
        return prepareBaselineTexturedPrimitiveSet(asset).primitives();
    }

    public BaselineTexturedPrimitiveSet prepareBaselineTexturedPrimitiveSet(RuntimeModelAsset asset) {
        return baselineTexturedPrimitiveSets.computeIfAbsent(asset.modelId(), ignored -> {
        List<TexturedPrimitiveTarget> targets = prepareTexturedPrimitiveTargets(asset);
        if (targets.isEmpty()) {
            return new BaselineTexturedPrimitiveSet(asset.modelId(), List.of());
        }

        List<BaselineTexturedPrimitive> texturedPrimitives = new ArrayList<>();
        for (TexturedPrimitiveTarget target : targets) {
            var primitive = target.primitive();
            StaticMeshBuilder.PrimitiveWorldGeometry geometry = staticMeshBuilder.buildPrimitiveWorldGeometry(
                    asset,
                    primitive,
                    Map.of(),
                    false,
                    0
            );
            if (geometry.vertexCount() <= 0) {
                continue;
            }
            texturedPrimitives.add(new BaselineTexturedPrimitive(
                    primitive.name(),
                    primitive.nodeName(),
                    target.textureId(),
                    geometry
            ));
        }
        return new BaselineTexturedPrimitiveSet(asset.modelId(), List.copyOf(texturedPrimitives));
        });
    }

    public UploadedTexturedPrimitive prepareUploadedBaselineTexturedPrimitive(RuntimeModelAsset asset, int texturedPrimitiveIndex) {
        BaselineTexturedPrimitiveSet primitiveSet = prepareBaselineTexturedPrimitiveSet(asset);
        if (primitiveSet.isEmpty()) {
            return null;
        }
        BaselineTexturedPrimitive selected = primitiveSet.primitives()
                .get(Math.floorMod(texturedPrimitiveIndex, primitiveSet.primitiveCount()));
        String cacheKey = asset.modelId() + "|tex#" + Math.floorMod(texturedPrimitiveIndex, primitiveSet.primitiveCount());
        return uploadedTexturedPrimitives.computeIfAbsent(cacheKey, ignored -> texturedPrimitiveUploader.upload(selected, null));
    }

    public List<UploadedTexturedPrimitive> prepareUploadedBaselineTexturedPrimitives(RuntimeModelAsset asset) {
        BaselineTexturedPrimitiveSet primitiveSet = prepareBaselineTexturedPrimitiveSet(asset);
        if (primitiveSet.isEmpty()) {
            return List.of();
        }

        List<UploadedTexturedPrimitive> uploaded = new ArrayList<>(primitiveSet.primitiveCount());
        for (int i = 0; i < primitiveSet.primitiveCount(); i++) {
            BaselineTexturedPrimitive primitive = primitiveSet.primitives().get(i);
            String cacheKey = asset.modelId() + "|tex#" + i;
            uploaded.add(uploadedTexturedPrimitives.computeIfAbsent(cacheKey, ignored -> texturedPrimitiveUploader.upload(primitive, null)));
        }
        return List.copyOf(uploaded);
    }

    private boolean isRenderableTexturedPrimitive(com.yourname.hb3d.asset.MeshPrimitiveData primitive) {
        return primitive.mode() == 4
                && !primitive.positions().isEmpty()
                && !primitive.texCoords().isEmpty();
    }

    private boolean usesEmbeddedBaseColorTexture(RuntimeModelAsset asset, com.yourname.hb3d.asset.MeshPrimitiveData primitive) {
        if (primitive.materialIndex() < 0 || primitive.materialIndex() >= asset.materials().size()) {
            return false;
        }
        int textureIndex = asset.materials().get(primitive.materialIndex()).baseColorTextureIndex();
        if (textureIndex < 0 || textureIndex >= asset.textures().size()) {
            return false;
        }
        int imageIndex = asset.textures().get(textureIndex).sourceImageIndex();
        return imageIndex >= 0
                && imageIndex < asset.images().size()
                && asset.images().get(imageIndex).hasEmbeddedBytes();
    }

    private List<TexturedPrimitiveTarget> prepareTexturedPrimitiveTargets(RuntimeModelAsset asset) {
        return texturedPrimitiveTargets.computeIfAbsent(asset.modelId(), ignored -> {
            List<TexturedPrimitiveTarget> targets = new ArrayList<>();
            for (var primitive : asset.meshPrimitives()) {
                if (!isRenderableTexturedPrimitive(primitive) || !usesEmbeddedBaseColorTexture(asset, primitive)) {
                    continue;
                }
                Identifier textureId = Hb3dEmbeddedTextureCache.getInstance()
                        .prepareEmbeddedBaseColorTexture(asset, primitive)
                        .orElse(null);
                if (textureId == null) {
                    continue;
                }
                targets.add(new TexturedPrimitiveTarget(primitive, textureId));
            }
            return List.copyOf(targets);
        });
    }

    private StaticMeshBuilder.PrimitiveWorldGeometry mergePrimitiveWorldGeometries(List<StaticMeshBuilder.PrimitiveWorldGeometry> geometries) {
        if (geometries.isEmpty()) {
            return new StaticMeshBuilder.PrimitiveWorldGeometry("merged", "merged", 4, List.of(), List.of(), List.of(), List.of());
        }
        if (geometries.size() == 1) {
            return geometries.get(0);
        }

        List<Float> positions = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> texCoords = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        int vertexOffset = 0;
        for (StaticMeshBuilder.PrimitiveWorldGeometry geometry : geometries) {
            positions.addAll(geometry.positions());
            normals.addAll(geometry.normals());
            texCoords.addAll(geometry.texCoords());
            if (!geometry.indices().isEmpty()) {
                for (Integer index : geometry.indices()) {
                    indices.add(index + vertexOffset);
                }
            } else {
                for (int i = 0; i < geometry.vertexCount(); i++) {
                    indices.add(vertexOffset + i);
                }
            }
            vertexOffset += geometry.vertexCount();
        }
        return new StaticMeshBuilder.PrimitiveWorldGeometry(
                "merged",
                "merged",
                4,
                List.copyOf(positions),
                List.copyOf(normals),
                List.copyOf(texCoords),
                List.copyOf(indices)
        );
    }

    public void invalidate() {
        uploadedStaticMeshes.values().forEach(UploadedStaticMesh::close);
        uploadedStaticMeshes.clear();
        uploadedTexturedPrimitives.values().forEach(UploadedTexturedPrimitive::close);
        uploadedTexturedPrimitives.clear();
        animatedUploadedTexturedPrimitives.values().forEach(list -> list.forEach(UploadedTexturedPrimitive::close));
        animatedUploadedTexturedPrimitives.clear();
        animatedTexturedKeys.clear();
        baselineTexturedPrimitiveSets.clear();
        animatedUploadedStaticMeshes.values().forEach(UploadedStaticMesh::close);
        animatedUploadedStaticMeshes.clear();
        animatedMeshKeys.clear();
        staticMeshes.clear();
        texturedPrimitiveTargets.clear();
    }

    public void render(VisualInstance instance) {
        // Runtime drawing is still handled by the debug renderer.
        // This renderer now owns the static mesh build path used for the next rendering step.
    }

    private record TexturedPrimitiveTarget(com.yourname.hb3d.asset.MeshPrimitiveData primitive, Identifier textureId) {
    }
}
