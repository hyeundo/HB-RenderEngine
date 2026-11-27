package com.yourname.hb3d.asset;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Identifier;

public final class RuntimeModelAsset {
    private final String modelId;
    private final Identifier sourceId;
    private final List<String> clipNames;
    private final Map<String, Float> clipDurationsSeconds;
    private final List<String> nodeNames;
    private final Map<String, NodeTransform> nodeTransforms;
    private final Map<String, NodeHierarchy> nodeHierarchies;
    private final Map<Integer, SkinDefinition> skins;
    private final Map<String, java.util.List<AnimationChannelMetadata>> clipChannels;
    private final Map<String, java.util.List<AnimationSamplerData>> clipSamplers;
    private final List<MeshPrimitiveData> meshPrimitives;
    private final List<MaterialData> materials;
    private final List<TextureData> textures;
    private final List<ImageData> images;
    private final List<SamplerData> samplers;
    private final ModelMeshSummary meshSummary;
    private final String rawGltfJson;

    public RuntimeModelAsset(
            String modelId,
            Identifier sourceId,
            List<String> clipNames,
            Map<String, Float> clipDurationsSeconds,
            List<String> nodeNames,
            Map<String, NodeTransform> nodeTransforms,
            Map<String, NodeHierarchy> nodeHierarchies,
            Map<Integer, SkinDefinition> skins,
            Map<String, java.util.List<AnimationChannelMetadata>> clipChannels,
            Map<String, java.util.List<AnimationSamplerData>> clipSamplers,
            List<MeshPrimitiveData> meshPrimitives,
            List<MaterialData> materials,
            List<TextureData> textures,
            List<ImageData> images,
            List<SamplerData> samplers,
            ModelMeshSummary meshSummary,
            String rawGltfJson
    ) {
        this.modelId = modelId;
        this.sourceId = sourceId;
        this.clipNames = List.copyOf(clipNames);
        this.clipDurationsSeconds = Map.copyOf(clipDurationsSeconds);
        this.nodeNames = List.copyOf(nodeNames);
        this.nodeTransforms = Map.copyOf(nodeTransforms);
        this.nodeHierarchies = Map.copyOf(nodeHierarchies);
        this.skins = Map.copyOf(skins);
        this.clipChannels = Map.copyOf(clipChannels);
        this.clipSamplers = Map.copyOf(clipSamplers);
        this.meshPrimitives = List.copyOf(meshPrimitives);
        this.materials = List.copyOf(materials);
        this.textures = List.copyOf(textures);
        this.images = List.copyOf(images);
        this.samplers = List.copyOf(samplers);
        this.meshSummary = meshSummary;
        this.rawGltfJson = rawGltfJson;
    }

    public String modelId() {
        return modelId;
    }

    public Identifier sourceId() {
        return sourceId;
    }

    public List<String> clipNames() {
        return Collections.unmodifiableList(clipNames);
    }

    public Optional<Float> clipDurationSeconds(String clipName) {
        return Optional.ofNullable(clipDurationsSeconds.get(clipName));
    }

    public List<String> nodeNames() {
        return Collections.unmodifiableList(nodeNames);
    }

    public boolean hasNode(String nodeName) {
        return nodeNames.contains(nodeName);
    }

    public Optional<NodeTransform> nodeTransform(String nodeName) {
        return Optional.ofNullable(nodeTransforms.get(nodeName));
    }

    public java.util.Optional<NodeHierarchy> nodeHierarchy(String nodeName) {
        return java.util.Optional.ofNullable(nodeHierarchies.get(nodeName));
    }

    public Optional<SkinDefinition> skin(int skinIndex) {
        return Optional.ofNullable(skins.get(skinIndex));
    }

    public Map<Integer, SkinDefinition> skins() {
        return Collections.unmodifiableMap(skins);
    }

    public java.util.List<AnimationChannelMetadata> clipChannels(String clipName) {
        return clipChannels.getOrDefault(clipName, List.of());
    }

    public java.util.List<AnimationSamplerData> clipSamplers(String clipName) {
        return clipSamplers.getOrDefault(clipName, List.of());
    }

    public List<MeshPrimitiveData> meshPrimitives() {
        return Collections.unmodifiableList(meshPrimitives);
    }

    public List<MaterialData> materials() {
        return Collections.unmodifiableList(materials);
    }

    public List<TextureData> textures() {
        return Collections.unmodifiableList(textures);
    }

    public List<ImageData> images() {
        return Collections.unmodifiableList(images);
    }

    public List<SamplerData> samplers() {
        return Collections.unmodifiableList(samplers);
    }

    public ModelMeshSummary meshSummary() {
        return meshSummary;
    }

    public Optional<String> rawGltfJson() {
        return Optional.ofNullable(rawGltfJson);
    }
}
