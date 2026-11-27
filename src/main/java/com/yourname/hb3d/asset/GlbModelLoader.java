package com.yourname.hb3d.asset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yourname.hb3d.Hb3dMod;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class GlbModelLoader {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK_TYPE = 0x4E4F534A;
    private static final int BIN_CHUNK_TYPE = 0x004E4942;
    private static final int COMPONENT_TYPE_BYTE = 5120;
    private static final int COMPONENT_TYPE_FLOAT = 5126;
    private static final int COMPONENT_TYPE_SHORT = 5122;
    private static final int COMPONENT_TYPE_INT = 5124;
    private static final int COMPONENT_TYPE_UNSIGNED_BYTE = 5121;
    private static final int COMPONENT_TYPE_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_TYPE_UNSIGNED_INT = 5125;

    public RuntimeModelAsset load(ModelDefinition definition) {
        try (InputStream stream = openResource(definition.glbId())) {
            if (stream == null) {
                Hb3dMod.LOGGER.warn("Missing GLB resource for model {} at {}", definition.modelId(), definition.glbId());
                return loadPlaceholder(definition);
            }

            byte[] bytes = readAllBytes(stream);
            GlbContents contents = readContents(definition.glbId(), bytes);
            ParsedAnimationData animationData = parseAnimationData(contents.json(), contents.binChunk());

            return new RuntimeModelAsset(
                    definition.modelId(),
                    definition.glbId(),
                    animationData.clipNames(),
                    animationData.clipDurationsSeconds(),
                    animationData.nodeNames(),
                    animationData.nodeTransforms(),
                    animationData.nodeHierarchies(),
                    animationData.skins(),
                    animationData.clipChannels(),
                    animationData.clipSamplers(),
                    animationData.meshPrimitives(),
                    animationData.materials(),
                    animationData.textures(),
                    animationData.images(),
                    animationData.samplers(),
                    animationData.meshSummary(),
                    contents.json()
            );
        } catch (Exception exception) {
            Hb3dMod.LOGGER.warn("Failed to parse GLB {} for model {}, using placeholder metadata", definition.glbId(), definition.modelId(), exception);
            return loadPlaceholder(definition);
        }
    }

    public RuntimeModelAsset loadPlaceholder(ModelDefinition definition) {
        return new RuntimeModelAsset(
                definition.modelId(),
                definition.glbId(),
                List.of("idle", "walk", "run", "attack_1", "hit", "death"),
                Map.of(
                        "idle", 1.2F,
                        "walk", 0.9F,
                        "run", 0.75F,
                        "attack_1", 0.8F,
                        "hit", 0.5F,
                        "death", 1.1F
                ),
                List.of("Root", "Head", "RightHandSocket", "WeaponSocket"),
                Map.of(
                        "Root", NodeTransform.identity("Root"),
                        "Head", new NodeTransform("Head", 0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F),
                        "RightHandSocket", new NodeTransform("RightHandSocket", 0.35F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F),
                        "WeaponSocket", new NodeTransform("WeaponSocket", 0.45F, 0.95F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F)
                ),
                Map.of(
                        "Root", new NodeHierarchy("Root", null),
                        "Head", new NodeHierarchy("Head", "Root"),
                        "RightHandSocket", new NodeHierarchy("RightHandSocket", "Root"),
                        "WeaponSocket", new NodeHierarchy("WeaponSocket", "RightHandSocket")
                ),
                Map.of(),
                Map.of(
                        "idle", List.of(new AnimationChannelMetadata("Root", "translation", "LINEAR", 1)),
                        "walk", List.of(new AnimationChannelMetadata("Root", "translation", "LINEAR", 1)),
                        "run", List.of(new AnimationChannelMetadata("Root", "translation", "LINEAR", 1)),
                        "attack_1", List.of(new AnimationChannelMetadata("RightHandSocket", "rotation", "LINEAR", 1)),
                        "hit", List.of(new AnimationChannelMetadata("Head", "rotation", "LINEAR", 1)),
                        "death", List.of(new AnimationChannelMetadata("Root", "rotation", "LINEAR", 1))
                ),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new ModelMeshSummary(0, 0, new MeshBounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F)),
                null
        );
    }

    private InputStream openResource(Identifier id) {
        String path = "assets/" + id.getNamespace() + "/" + id.getPath();
        return GlbModelLoader.class.getClassLoader().getResourceAsStream(path);
    }

    private byte[] readAllBytes(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private GlbContents readContents(Identifier sourceId, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12) {
            throw new IllegalStateException("GLB file is too small: " + sourceId);
        }

        int magic = buffer.getInt();
        int version = buffer.getInt();
        int length = buffer.getInt();

        if (magic != GLB_MAGIC) {
            throw new IllegalStateException("Invalid GLB magic for " + sourceId);
        }
        if (version != 2) {
            throw new IllegalStateException("Unsupported GLB version " + version + " for " + sourceId);
        }
        if (length > bytes.length) {
            throw new IllegalStateException("Declared GLB length exceeds file size for " + sourceId);
        }

        String json = null;
        byte[] binChunk = null;
        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                throw new IllegalStateException("Invalid GLB chunk length in " + sourceId);
            }

            byte[] chunkBytes = new byte[chunkLength];
            buffer.get(chunkBytes);
            if (chunkType == JSON_CHUNK_TYPE) {
                json = new String(chunkBytes, StandardCharsets.UTF_8).trim();
            } else if (chunkType == BIN_CHUNK_TYPE) {
                binChunk = chunkBytes;
            }
        }

        if (json == null) {
            throw new IllegalStateException("Missing JSON chunk in GLB: " + sourceId);
        }

        return new GlbContents(json, binChunk);
    }

    private ParsedAnimationData parseAnimationData(String json, byte[] binChunk) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray nodes = root.has("nodes") ? root.getAsJsonArray("nodes") : new JsonArray();
        JsonArray animations = root.has("animations") ? root.getAsJsonArray("animations") : new JsonArray();
        List<String> nodeNames = readNodeNames(nodes);
        Map<String, NodeTransform> nodeTransforms = readNodeTransforms(nodes);
        Map<String, NodeHierarchy> nodeHierarchies = readNodeHierarchies(nodes, nodeNames);
        Map<Integer, SkinDefinition> skins = readSkins(root, nodeNames, binChunk);
        Map<Integer, List<MeshNodeBinding>> meshBindings = readMeshBindings(nodes, nodeNames);
        List<String> clipNames = new ArrayList<>();
        Map<String, Float> clipDurationsSeconds = new LinkedHashMap<>();
        Map<String, List<AnimationChannelMetadata>> clipChannels = new LinkedHashMap<>();
        Map<String, List<AnimationSamplerData>> clipSamplers = new LinkedHashMap<>();
        List<MeshPrimitiveData> meshPrimitives = readMeshPrimitives(root, binChunk, meshBindings);
        List<MaterialData> materials = readMaterials(root);
        List<TextureData> textures = readTextures(root);
        List<ImageData> images = readImages(root, binChunk);
        List<SamplerData> samplers = readSamplers(root);
        ModelMeshSummary meshSummary = summarizeMesh(meshPrimitives);

        int unnamedIndex = 0;
        for (JsonElement element : animations) {
            JsonObject animation = element.getAsJsonObject();
            String clipName = animation.has("name") ? animation.get("name").getAsString() : "animation_" + unnamedIndex++;
            clipNames.add(clipName);
            clipDurationsSeconds.put(clipName, (float) resolveAnimationDurationSeconds(root, animation, binChunk).orElse(1.0D));
            clipChannels.put(clipName, readAnimationChannels(root, animation, nodeNames));
            clipSamplers.put(clipName, readAnimationSamplers(root, animation, nodeNames, binChunk));
        }

        if (clipNames.isEmpty()) {
            clipNames.addAll(List.of("idle", "walk", "run", "attack_1", "hit", "death"));
            clipDurationsSeconds.put("idle", 1.2F);
            clipDurationsSeconds.put("walk", 0.9F);
            clipDurationsSeconds.put("run", 0.75F);
            clipDurationsSeconds.put("attack_1", 0.8F);
            clipDurationsSeconds.put("hit", 0.5F);
            clipDurationsSeconds.put("death", 1.1F);
            clipChannels.put("idle", List.of(new AnimationChannelMetadata("Root", "translation", "LINEAR", 1)));
            clipChannels.put("walk", List.of(new AnimationChannelMetadata("Root", "translation", "LINEAR", 1)));
            clipChannels.put("run", List.of(new AnimationChannelMetadata("Root", "translation", "LINEAR", 1)));
            clipChannels.put("attack_1", List.of(new AnimationChannelMetadata("RightHandSocket", "rotation", "LINEAR", 1)));
            clipChannels.put("hit", List.of(new AnimationChannelMetadata("Head", "rotation", "LINEAR", 1)));
            clipChannels.put("death", List.of(new AnimationChannelMetadata("Root", "rotation", "LINEAR", 1)));
        }

        return new ParsedAnimationData(
                List.copyOf(clipNames),
                Map.copyOf(clipDurationsSeconds),
                List.copyOf(nodeNames),
                Map.copyOf(nodeTransforms),
                Map.copyOf(nodeHierarchies),
                Map.copyOf(skins),
                copyChannelMap(clipChannels),
                copySamplerMap(clipSamplers),
                List.copyOf(meshPrimitives),
                List.copyOf(materials),
                List.copyOf(textures),
                List.copyOf(images),
                List.copyOf(samplers),
                meshSummary
        );
    }

    private List<MeshPrimitiveData> readMeshPrimitives(
            JsonObject root,
            byte[] binChunk,
            Map<Integer, List<MeshNodeBinding>> meshBindings
    ) {
        if (binChunk == null || !root.has("meshes")) {
            return List.of();
        }

        JsonArray meshes = root.getAsJsonArray("meshes");
        List<MeshPrimitiveData> primitives = new ArrayList<>();
        for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
            JsonObject mesh = meshes.get(meshIndex).getAsJsonObject();
            String meshName = mesh.has("name") ? mesh.get("name").getAsString() : "mesh_" + meshIndex;
            List<MeshNodeBinding> bindings = meshBindings.getOrDefault(
                    meshIndex,
                    List.of(new MeshNodeBinding(meshIndex, meshName, -1))
            );
            if (!mesh.has("primitives")) {
                continue;
            }

            JsonArray meshPrimitives = mesh.getAsJsonArray("primitives");
            for (int primitiveIndex = 0; primitiveIndex < meshPrimitives.size(); primitiveIndex++) {
                JsonObject primitive = meshPrimitives.get(primitiveIndex).getAsJsonObject();
                if (!primitive.has("attributes")) {
                    continue;
                }
                JsonObject attributes = primitive.getAsJsonObject("attributes");
                if (!attributes.has("POSITION")) {
                    continue;
                }

                int accessorIndex = attributes.get("POSITION").getAsInt();
                List<Float> positions = readFloatAccessor(root, accessorIndex, binChunk);
                List<Float> texCoords = attributes.has("TEXCOORD_0")
                        ? readFloatAccessor(root, attributes.get("TEXCOORD_0").getAsInt(), binChunk)
                        : List.of();
                List<Float> normals = attributes.has("NORMAL")
                        ? readFloatAccessor(root, attributes.get("NORMAL").getAsInt(), binChunk)
                        : List.of();
                List<Integer> joints = attributes.has("JOINTS_0")
                        ? readIntAccessor(root, attributes.get("JOINTS_0").getAsInt(), binChunk)
                        : List.of();
                List<Float> weights = attributes.has("WEIGHTS_0")
                        ? readFloatAccessor(root, attributes.get("WEIGHTS_0").getAsInt(), binChunk)
                        : List.of();
                int vertexCount = readAccessorCount(root, accessorIndex);
                List<Integer> indices = primitive.has("indices")
                        ? readIndexAccessor(root, primitive.get("indices").getAsInt(), binChunk)
                        : List.of();
                int materialIndex = primitive.has("material")
                        ? primitive.get("material").getAsInt()
                        : -1;
                int mode = primitive.has("mode") ? primitive.get("mode").getAsInt() : 4;
                if (!positions.isEmpty() && vertexCount > 0) {
                    MeshBounds bounds = computeBounds(positions);
                    for (int bindingIndex = 0; bindingIndex < bindings.size(); bindingIndex++) {
                        MeshNodeBinding binding = bindings.get(bindingIndex);
                        primitives.add(new MeshPrimitiveData(
                                meshName + "_instance_" + bindingIndex + "_primitive_" + primitiveIndex,
                                binding.nodeName(),
                                binding.skinIndex(),
                                positions,
                                texCoords,
                                normals,
                                joints,
                                weights,
                                indices,
                                materialIndex,
                                mode,
                                vertexCount,
                                bounds
                        ));
                    }
                }
            }
        }
        return List.copyOf(primitives);
    }

    private List<MaterialData> readMaterials(JsonObject root) {
        if (!root.has("materials")) {
            return List.of();
        }

        JsonArray materials = root.getAsJsonArray("materials");
        List<MaterialData> result = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            JsonObject material = materials.get(i).getAsJsonObject();
            String name = material.has("name") ? material.get("name").getAsString() : "material_" + i;
            int baseColorTextureIndex = -1;
            int metallicRoughnessTextureIndex = -1;
            if (material.has("pbrMetallicRoughness")) {
                JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
                if (pbr.has("baseColorTexture")) {
                    JsonObject textureInfo = pbr.getAsJsonObject("baseColorTexture");
                    if (textureInfo.has("index")) {
                        baseColorTextureIndex = textureInfo.get("index").getAsInt();
                    }
                }
                if (pbr.has("metallicRoughnessTexture")) {
                    metallicRoughnessTextureIndex = pbr.getAsJsonObject("metallicRoughnessTexture")
                            .get("index").getAsInt();
                }
            }
            String alphaMode = material.has("alphaMode") ? material.get("alphaMode").getAsString() : "OPAQUE";
            float alphaCutoff = material.has("alphaCutoff") ? material.get("alphaCutoff").getAsFloat() : 0.5F;
            boolean doubleSided = material.has("doubleSided") && material.get("doubleSided").getAsBoolean();
            int normalTextureIndex = material.has("normalTexture")
                    ? material.getAsJsonObject("normalTexture").get("index").getAsInt() : -1;
            int emissiveTextureIndex = material.has("emissiveTexture")
                    ? material.getAsJsonObject("emissiveTexture").get("index").getAsInt() : -1;
            result.add(new MaterialData(name, baseColorTextureIndex, alphaMode, alphaCutoff, doubleSided,
                    normalTextureIndex, metallicRoughnessTextureIndex, emissiveTextureIndex));
        }
        return List.copyOf(result);
    }

    private List<TextureData> readTextures(JsonObject root) {
        if (!root.has("textures")) {
            return List.of();
        }

        JsonArray textures = root.getAsJsonArray("textures");
        List<TextureData> result = new ArrayList<>();
        for (int i = 0; i < textures.size(); i++) {
            JsonObject texture = textures.get(i).getAsJsonObject();
            int sourceImageIndex = texture.has("source") ? texture.get("source").getAsInt() : -1;
            int samplerIndex = texture.has("sampler") ? texture.get("sampler").getAsInt() : -1;
            result.add(new TextureData(sourceImageIndex, samplerIndex));
        }
        return List.copyOf(result);
    }

    private List<ImageData> readImages(JsonObject root, byte[] binChunk) {
        if (!root.has("images")) {
            return List.of();
        }

        JsonArray images = root.getAsJsonArray("images");
        List<ImageData> result = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            JsonObject image = images.get(i).getAsJsonObject();
            String uri = image.has("uri") ? image.get("uri").getAsString() : null;
            String mimeType = image.has("mimeType") ? image.get("mimeType").getAsString() : null;
            int bufferView = image.has("bufferView") ? image.get("bufferView").getAsInt() : -1;
            byte[] imageBytes = bufferView >= 0 ? readBufferViewBytes(root, bufferView, binChunk) : null;
            result.add(new ImageData(uri, mimeType, bufferView, imageBytes));
        }
        return List.copyOf(result);
    }

    private byte[] readBufferViewBytes(JsonObject root, int bufferViewIndex, byte[] binChunk) {
        if (binChunk == null || !root.has("bufferViews")) {
            return null;
        }
        JsonArray bufferViews = root.getAsJsonArray("bufferViews");
        if (bufferViewIndex < 0 || bufferViewIndex >= bufferViews.size()) {
            return null;
        }

        JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
        int byteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int byteLength = bufferView.has("byteLength") ? bufferView.get("byteLength").getAsInt() : 0;
        if (byteLength <= 0 || byteOffset < 0 || byteOffset + byteLength > binChunk.length) {
            return null;
        }

        byte[] slice = new byte[byteLength];
        System.arraycopy(binChunk, byteOffset, slice, 0, byteLength);
        return slice;
    }

    private List<SamplerData> readSamplers(JsonObject root) {
        if (!root.has("samplers")) {
            return List.of();
        }

        JsonArray samplers = root.getAsJsonArray("samplers");
        List<SamplerData> result = new ArrayList<>();
        for (int i = 0; i < samplers.size(); i++) {
            JsonObject sampler = samplers.get(i).getAsJsonObject();
            int magFilter = sampler.has("magFilter") ? sampler.get("magFilter").getAsInt() : -1;
            int minFilter = sampler.has("minFilter") ? sampler.get("minFilter").getAsInt() : -1;
            int wrapS = sampler.has("wrapS") ? sampler.get("wrapS").getAsInt() : 10497;
            int wrapT = sampler.has("wrapT") ? sampler.get("wrapT").getAsInt() : 10497;
            result.add(new SamplerData(magFilter, minFilter, wrapS, wrapT));
        }
        return List.copyOf(result);
    }

    private Map<Integer, List<MeshNodeBinding>> readMeshBindings(JsonArray nodes, List<String> nodeNames) {
        Map<Integer, List<MeshNodeBinding>> bindings = new LinkedHashMap<>();
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            JsonObject node = nodes.get(nodeIndex).getAsJsonObject();
            if (!node.has("mesh")) {
                continue;
            }
            int meshIndex = node.get("mesh").getAsInt();
            int skinIndex = node.has("skin") ? node.get("skin").getAsInt() : -1;
            String nodeName = nodeIndex >= 0 && nodeIndex < nodeNames.size() ? nodeNames.get(nodeIndex) : "node_" + nodeIndex;
            bindings.computeIfAbsent(meshIndex, ignored -> new ArrayList<>())
                    .add(new MeshNodeBinding(meshIndex, nodeName, skinIndex));
        }
        Map<Integer, List<MeshNodeBinding>> immutable = new LinkedHashMap<>();
        bindings.forEach((meshIndex, meshNodeBindings) ->
                immutable.put(meshIndex, List.copyOf(meshNodeBindings)));
        return Map.copyOf(immutable);
    }

    private Map<Integer, SkinDefinition> readSkins(JsonObject root, List<String> nodeNames, byte[] binChunk) {
        if (!root.has("skins")) {
            return Map.of();
        }

        JsonArray skins = root.getAsJsonArray("skins");
        Map<Integer, SkinDefinition> results = new LinkedHashMap<>();
        for (int skinIndex = 0; skinIndex < skins.size(); skinIndex++) {
            JsonObject skin = skins.get(skinIndex).getAsJsonObject();
            String name = skin.has("name") ? skin.get("name").getAsString() : "skin_" + skinIndex;
            String skeletonRootName = null;
            if (skin.has("skeleton")) {
                int skeletonNodeIndex = skin.get("skeleton").getAsInt();
                skeletonRootName = skeletonNodeIndex >= 0 && skeletonNodeIndex < nodeNames.size()
                        ? nodeNames.get(skeletonNodeIndex)
                        : "unknown_skeleton_root_" + skeletonNodeIndex;
            }
            List<String> jointNodeNames = new ArrayList<>();
            if (skin.has("joints")) {
                JsonArray joints = skin.getAsJsonArray("joints");
                for (JsonElement jointElement : joints) {
                    int nodeIndex = jointElement.getAsInt();
                    jointNodeNames.add(nodeIndex >= 0 && nodeIndex < nodeNames.size() ? nodeNames.get(nodeIndex) : "unknown_joint_" + nodeIndex);
                }
            }
            List<float[]> inverseBindMatrices = skin.has("inverseBindMatrices")
                    ? readMatrixAccessor(root, skin.get("inverseBindMatrices").getAsInt(), binChunk)
                    : List.of();
            results.put(skinIndex, new SkinDefinition(skinIndex, name, skeletonRootName, List.copyOf(jointNodeNames), inverseBindMatrices));
        }
        return Map.copyOf(results);
    }

    private MeshBounds computeBounds(List<Float> positions) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i + 2 < positions.size(); i += 3) {
            float x = positions.get(i);
            float y = positions.get(i + 1);
            float z = positions.get(i + 2);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        return new MeshBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private ModelMeshSummary summarizeMesh(List<MeshPrimitiveData> primitives) {
        if (primitives.isEmpty()) {
            return new ModelMeshSummary(0, 0, new MeshBounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        }

        int totalVertexCount = 0;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (MeshPrimitiveData primitive : primitives) {
            totalVertexCount += primitive.vertexCount();
            MeshBounds bounds = primitive.bounds();
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            minZ = Math.min(minZ, bounds.minZ());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
            maxZ = Math.max(maxZ, bounds.maxZ());
        }

        return new ModelMeshSummary(
                primitives.size(),
                totalVertexCount,
                new MeshBounds(minX, minY, minZ, maxX, maxY, maxZ)
        );
    }

    private List<String> readNodeNames(JsonArray nodes) {
        List<String> nodeNames = new ArrayList<>();
        int unnamedIndex = 0;
        for (JsonElement element : nodes) {
            JsonObject node = element.getAsJsonObject();
            if (node.has("name")) {
                nodeNames.add(node.get("name").getAsString());
            } else {
                nodeNames.add("node_" + unnamedIndex);
            }
            unnamedIndex++;
        }
        return nodeNames;
    }

    private Map<String, NodeTransform> readNodeTransforms(JsonArray nodes) {
        Map<String, NodeTransform> transforms = new LinkedHashMap<>();
        int unnamedIndex = 0;
        for (JsonElement element : nodes) {
            JsonObject node = element.getAsJsonObject();
            String name = node.has("name") ? node.get("name").getAsString() : "node_" + unnamedIndex;
            transforms.put(name, readNodeTransform(name, node));
            unnamedIndex++;
        }
        return transforms;
    }

    private NodeTransform readNodeTransform(String name, JsonObject node) {
        if (node.has("matrix")) {
            JsonArray matrixValues = node.getAsJsonArray("matrix");
            if (matrixValues.size() >= 16) {
                Matrix4f matrix = new Matrix4f(
                        matrixValues.get(0).getAsFloat(), matrixValues.get(1).getAsFloat(), matrixValues.get(2).getAsFloat(), matrixValues.get(3).getAsFloat(),
                        matrixValues.get(4).getAsFloat(), matrixValues.get(5).getAsFloat(), matrixValues.get(6).getAsFloat(), matrixValues.get(7).getAsFloat(),
                        matrixValues.get(8).getAsFloat(), matrixValues.get(9).getAsFloat(), matrixValues.get(10).getAsFloat(), matrixValues.get(11).getAsFloat(),
                        matrixValues.get(12).getAsFloat(), matrixValues.get(13).getAsFloat(), matrixValues.get(14).getAsFloat(), matrixValues.get(15).getAsFloat()
                );
                Vector3f translation = matrix.getTranslation(new Vector3f());
                Quaternionf rotation = matrix.getNormalizedRotation(new Quaternionf());
                Vector3f scale = matrix.getScale(new Vector3f());
                return new NodeTransform(
                        name,
                        translation.x, translation.y, translation.z,
                        rotation.x, rotation.y, rotation.z, rotation.w,
                        scale.x, scale.y, scale.z
                );
            }
        }

        float[] translation = readFloatArray(node, "translation", 3, new float[] {0.0F, 0.0F, 0.0F});
        float[] rotation = readFloatArray(node, "rotation", 4, new float[] {0.0F, 0.0F, 0.0F, 1.0F});
        float[] scale = readFloatArray(node, "scale", 3, new float[] {1.0F, 1.0F, 1.0F});
        return new NodeTransform(
                name,
                translation[0], translation[1], translation[2],
                rotation[0], rotation[1], rotation[2], rotation[3],
                scale[0], scale[1], scale[2]
        );
    }

    private Map<String, NodeHierarchy> readNodeHierarchies(JsonArray nodes, List<String> nodeNames) {
        Map<String, NodeHierarchy> hierarchy = new LinkedHashMap<>();
        Map<Integer, Integer> parentByChild = new LinkedHashMap<>();

        for (int parentIndex = 0; parentIndex < nodes.size(); parentIndex++) {
            JsonObject node = nodes.get(parentIndex).getAsJsonObject();
            if (!node.has("children")) {
                continue;
            }
            JsonArray children = node.getAsJsonArray("children");
            for (JsonElement childElement : children) {
                parentByChild.put(childElement.getAsInt(), parentIndex);
            }
        }

        for (int i = 0; i < nodeNames.size(); i++) {
            String name = nodeNames.get(i);
            Integer parentIndex = parentByChild.get(i);
            String parentName = parentIndex != null && parentIndex >= 0 && parentIndex < nodeNames.size()
                    ? nodeNames.get(parentIndex)
                    : null;
            hierarchy.put(name, new NodeHierarchy(name, parentName));
        }

        return hierarchy;
    }

    private float[] readFloatArray(JsonObject object, String key, int expectedLength, float[] defaults) {
        if (!object.has(key)) {
            return defaults;
        }

        JsonArray array = object.getAsJsonArray(key);
        float[] values = defaults.clone();
        int length = Math.min(expectedLength, array.size());
        for (int i = 0; i < length; i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private OptionalDouble resolveAnimationDurationSeconds(JsonObject root, JsonObject animation, byte[] binChunk) {
        if (!animation.has("samplers")) {
            return OptionalDouble.empty();
        }

        JsonArray samplers = animation.getAsJsonArray("samplers");
        OptionalDouble longest = OptionalDouble.empty();
        for (JsonElement samplerElement : samplers) {
            JsonObject sampler = samplerElement.getAsJsonObject();
            if (!sampler.has("input")) {
                continue;
            }

            int accessorIndex = sampler.get("input").getAsInt();
            OptionalDouble accessorMax = readAccessorMaxTime(root, accessorIndex, binChunk);
            if (accessorMax.isEmpty()) {
                continue;
            }

            if (longest.isEmpty() || accessorMax.getAsDouble() > longest.getAsDouble()) {
                longest = accessorMax;
            }
        }
        return longest;
    }

    private List<AnimationChannelMetadata> readAnimationChannels(JsonObject root, JsonObject animation, List<String> nodeNames) {
        if (!animation.has("channels") || !animation.has("samplers")) {
            return List.of();
        }

        JsonArray channels = animation.getAsJsonArray("channels");
        JsonArray samplers = animation.getAsJsonArray("samplers");
        List<AnimationChannelMetadata> results = new ArrayList<>();

        for (JsonElement channelElement : channels) {
            JsonObject channel = channelElement.getAsJsonObject();
            int samplerIndex = channel.get("sampler").getAsInt();
            if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
                continue;
            }

            JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
            JsonObject target = channel.getAsJsonObject("target");
            int nodeIndex = target.has("node") ? target.get("node").getAsInt() : -1;
            String targetNode = nodeIndex >= 0 && nodeIndex < nodeNames.size() ? nodeNames.get(nodeIndex) : "unknown_node_" + nodeIndex;
            String targetPath = target.has("path") ? target.get("path").getAsString() : "unknown";
            String interpolation = sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "LINEAR";
            int keyframeCount = sampler.has("input") ? readAccessorCount(root, sampler.get("input").getAsInt()) : 0;

            results.add(new AnimationChannelMetadata(targetNode, targetPath, interpolation, keyframeCount));
        }

        return List.copyOf(results);
    }

    private List<AnimationSamplerData> readAnimationSamplers(JsonObject root, JsonObject animation, List<String> nodeNames, byte[] binChunk) {
        if (binChunk == null || !animation.has("channels") || !animation.has("samplers")) {
            return List.of();
        }

        JsonArray channels = animation.getAsJsonArray("channels");
        JsonArray samplers = animation.getAsJsonArray("samplers");
        List<AnimationSamplerData> results = new ArrayList<>();

        for (JsonElement channelElement : channels) {
            JsonObject channel = channelElement.getAsJsonObject();
            int samplerIndex = channel.get("sampler").getAsInt();
            if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
                continue;
            }

            JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
            JsonObject target = channel.getAsJsonObject("target");
            int nodeIndex = target.has("node") ? target.get("node").getAsInt() : -1;
            String targetNode = nodeIndex >= 0 && nodeIndex < nodeNames.size() ? nodeNames.get(nodeIndex) : "unknown_node_" + nodeIndex;
            String targetPath = target.has("path") ? target.get("path").getAsString() : "unknown";
            String interpolation = sampler.has("interpolation") ? sampler.get("interpolation").getAsString() : "LINEAR";

            if (!sampler.has("input") || !sampler.has("output")) {
                continue;
            }

            int inputAccessor = sampler.get("input").getAsInt();
            int outputAccessor = sampler.get("output").getAsInt();
            List<Float> keyTimes = readFloatAccessor(root, inputAccessor, binChunk);
            List<Float> values = readFloatAccessor(root, outputAccessor, binChunk);
            int componentWidth = readAccessorComponentWidth(root, outputAccessor);

            if (!keyTimes.isEmpty() && !values.isEmpty()) {
                results.add(new AnimationSamplerData(
                        targetNode,
                        targetPath,
                        interpolation,
                        keyTimes,
                        values,
                        componentWidth
                ));
            }
        }

        return List.copyOf(results);
    }

    private int readAccessorCount(JsonObject root, int accessorIndex) {
        JsonArray accessors = root.has("accessors") ? root.getAsJsonArray("accessors") : null;
        if (accessors == null || accessorIndex < 0 || accessorIndex >= accessors.size()) {
            return 0;
        }
        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        return accessor.has("count") ? accessor.get("count").getAsInt() : 0;
    }

    private List<Float> readFloatAccessor(JsonObject root, int accessorIndex, byte[] binChunk) {
        JsonArray accessors = root.has("accessors") ? root.getAsJsonArray("accessors") : null;
        JsonArray bufferViews = root.has("bufferViews") ? root.getAsJsonArray("bufferViews") : null;
        if (accessors == null || bufferViews == null || accessorIndex < 0 || accessorIndex >= accessors.size()) {
            return List.of();
        }

        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        if (!accessor.has("bufferView")) {
            return List.of();
        }

        int componentType = accessor.has("componentType") ? accessor.get("componentType").getAsInt() : -1;
        int bytesPerComponent = switch (componentType) {
            case COMPONENT_TYPE_FLOAT, COMPONENT_TYPE_UNSIGNED_INT, COMPONENT_TYPE_INT -> 4;
            case COMPONENT_TYPE_UNSIGNED_SHORT, COMPONENT_TYPE_SHORT -> 2;
            case COMPONENT_TYPE_UNSIGNED_BYTE, COMPONENT_TYPE_BYTE -> 1;
            default -> 0;
        };
        if (bytesPerComponent == 0) {
            return List.of();
        }
        boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();

        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        if (bufferViewIndex < 0 || bufferViewIndex >= bufferViews.size()) {
            return List.of();
        }

        JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        int componentWidth = readAccessorComponentWidth(root, accessorIndex);
        int accessorByteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        int bufferViewByteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int byteStride = bufferView.has("byteStride") ? bufferView.get("byteStride").getAsInt() : componentWidth * bytesPerComponent;
        int start = bufferViewByteOffset + accessorByteOffset;

        List<Float> values = new ArrayList<>(count * componentWidth);
        for (int i = 0; i < count; i++) {
            int elementOffset = start + (i * byteStride);
            for (int component = 0; component < componentWidth; component++) {
                int componentOffset = elementOffset + (component * bytesPerComponent);
                if (componentOffset + bytesPerComponent > binChunk.length) {
                    return List.of();
                }
                ByteBuffer buffer = ByteBuffer.wrap(binChunk, componentOffset, bytesPerComponent).order(ByteOrder.LITTLE_ENDIAN);
                float value = switch (componentType) {
                    case COMPONENT_TYPE_FLOAT -> buffer.getFloat();
                    case COMPONENT_TYPE_BYTE -> {
                        int raw = buffer.get();
                        yield normalized ? Math.max(raw / 127.0F, -1.0F) : raw;
                    }
                    case COMPONENT_TYPE_UNSIGNED_BYTE -> {
                        int raw = Byte.toUnsignedInt(buffer.get());
                        yield normalized ? (raw / 255.0F) : raw;
                    }
                    case COMPONENT_TYPE_SHORT -> {
                        int raw = buffer.getShort();
                        yield normalized ? Math.max(raw / 32767.0F, -1.0F) : raw;
                    }
                    case COMPONENT_TYPE_UNSIGNED_SHORT -> {
                        int raw = Short.toUnsignedInt(buffer.getShort());
                        yield normalized ? (raw / 65535.0F) : raw;
                    }
                    case COMPONENT_TYPE_INT -> {
                        int raw = buffer.getInt();
                        yield normalized ? Math.max((float) (raw / 2147483647.0), -1.0F) : raw;
                    }
                    case COMPONENT_TYPE_UNSIGNED_INT -> {
                        long raw = Integer.toUnsignedLong(buffer.getInt());
                        yield normalized ? (float) (raw / 4294967295.0) : (float) raw;
                    }
                    default -> Float.NaN;
                };
                if (Float.isNaN(value)) {
                    return List.of();
                }
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private List<Integer> readIndexAccessor(JsonObject root, int accessorIndex, byte[] binChunk) {
        return readIntAccessor(root, accessorIndex, binChunk);
    }

    private List<float[]> readMatrixAccessor(JsonObject root, int accessorIndex, byte[] binChunk) {
        List<Float> values = readFloatAccessor(root, accessorIndex, binChunk);
        if (values.isEmpty()) {
            return List.of();
        }

        List<float[]> matrices = new ArrayList<>();
        for (int i = 0; i + 15 < values.size(); i += 16) {
            float[] matrix = new float[16];
            for (int j = 0; j < 16; j++) {
                matrix[j] = values.get(i + j);
            }
            matrices.add(matrix);
        }
        return List.copyOf(matrices);
    }

    private List<Integer> readIntAccessor(JsonObject root, int accessorIndex, byte[] binChunk) {
        JsonArray accessors = root.has("accessors") ? root.getAsJsonArray("accessors") : null;
        JsonArray bufferViews = root.has("bufferViews") ? root.getAsJsonArray("bufferViews") : null;
        if (accessors == null || bufferViews == null || accessorIndex < 0 || accessorIndex >= accessors.size()) {
            return List.of();
        }

        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        if (!accessor.has("bufferView")) {
            return List.of();
        }

        int componentType = accessor.has("componentType") ? accessor.get("componentType").getAsInt() : -1;
        int bytesPerComponent = switch (componentType) {
            case COMPONENT_TYPE_BYTE, COMPONENT_TYPE_UNSIGNED_BYTE -> 1;
            case COMPONENT_TYPE_SHORT, COMPONENT_TYPE_UNSIGNED_SHORT -> 2;
            case COMPONENT_TYPE_INT, COMPONENT_TYPE_UNSIGNED_INT -> 4;
            default -> 0;
        };
        if (bytesPerComponent == 0) {
            return List.of();
        }

        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        if (bufferViewIndex < 0 || bufferViewIndex >= bufferViews.size()) {
            return List.of();
        }

        JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
        int count = accessor.get("count").getAsInt();
        int componentWidth = readAccessorComponentWidth(root, accessorIndex);
        if (componentWidth <= 0) {
            return List.of();
        }
        int accessorByteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        int bufferViewByteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int byteStride = bufferView.has("byteStride") ? bufferView.get("byteStride").getAsInt() : componentWidth * bytesPerComponent;
        int start = bufferViewByteOffset + accessorByteOffset;

        List<Integer> values = new ArrayList<>(count * componentWidth);
        for (int i = 0; i < count; i++) {
            int elementOffset = start + (i * byteStride);
            for (int component = 0; component < componentWidth; component++) {
                int componentOffset = elementOffset + (component * bytesPerComponent);
                if (componentOffset + bytesPerComponent > binChunk.length) {
                    return List.of();
                }

                ByteBuffer buffer = ByteBuffer.wrap(binChunk, componentOffset, bytesPerComponent).order(ByteOrder.LITTLE_ENDIAN);
                int value = switch (componentType) {
                    case COMPONENT_TYPE_BYTE -> buffer.get();
                    case COMPONENT_TYPE_UNSIGNED_BYTE -> Byte.toUnsignedInt(buffer.get());
                    case COMPONENT_TYPE_SHORT -> buffer.getShort();
                    case COMPONENT_TYPE_UNSIGNED_SHORT -> Short.toUnsignedInt(buffer.getShort());
                    case COMPONENT_TYPE_INT -> buffer.getInt();
                    case COMPONENT_TYPE_UNSIGNED_INT -> buffer.getInt();
                    default -> -1;
                };
                if (value < 0) {
                    return List.of();
                }
                values.add(value);
            }
        }

        return List.copyOf(values);
    }

    private int readAccessorComponentWidth(JsonObject root, int accessorIndex) {
        JsonArray accessors = root.has("accessors") ? root.getAsJsonArray("accessors") : null;
        if (accessors == null || accessorIndex < 0 || accessorIndex >= accessors.size()) {
            return 0;
        }
        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        String type = accessor.has("type") ? accessor.get("type").getAsString() : "SCALAR";
        return switch (type) {
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            case "MAT2" -> 4;
            case "MAT3" -> 9;
            case "MAT4" -> 16;
            default -> 1;
        };
    }

    private Map<String, List<AnimationChannelMetadata>> copyChannelMap(Map<String, List<AnimationChannelMetadata>> source) {
        Map<String, List<AnimationChannelMetadata>> copy = new LinkedHashMap<>();
        source.forEach((clipName, channels) -> copy.put(clipName, List.copyOf(channels)));
        return Map.copyOf(copy);
    }

    private Map<String, List<AnimationSamplerData>> copySamplerMap(Map<String, List<AnimationSamplerData>> source) {
        Map<String, List<AnimationSamplerData>> copy = new LinkedHashMap<>();
        source.forEach((clipName, samplers) -> copy.put(clipName, List.copyOf(samplers)));
        return Map.copyOf(copy);
    }

    private OptionalDouble readAccessorMaxTime(JsonObject root, int accessorIndex, byte[] binChunk) {
        JsonArray accessors = root.has("accessors") ? root.getAsJsonArray("accessors") : null;
        if (accessors == null || accessorIndex < 0 || accessorIndex >= accessors.size()) {
            return OptionalDouble.empty();
        }

        JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
        if (accessor.has("max")) {
            JsonArray maxArray = accessor.getAsJsonArray("max");
            if (!maxArray.isEmpty()) {
                return OptionalDouble.of(maxArray.get(0).getAsDouble());
            }
        }

        if (binChunk == null) {
            return OptionalDouble.empty();
        }
        if (!accessor.has("bufferView")) {
            return OptionalDouble.empty();
        }
        if (!"SCALAR".equals(accessor.get("type").getAsString())) {
            return OptionalDouble.empty();
        }
        if (accessor.get("componentType").getAsInt() != COMPONENT_TYPE_FLOAT) {
            return OptionalDouble.empty();
        }

        JsonArray bufferViews = root.has("bufferViews") ? root.getAsJsonArray("bufferViews") : null;
        int bufferViewIndex = accessor.get("bufferView").getAsInt();
        if (bufferViews == null || bufferViewIndex < 0 || bufferViewIndex >= bufferViews.size()) {
            return OptionalDouble.empty();
        }

        JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();
        int accessorByteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        int bufferViewByteOffset = bufferView.has("byteOffset") ? bufferView.get("byteOffset").getAsInt() : 0;
        int count = accessor.get("count").getAsInt();
        int byteStride = bufferView.has("byteStride") ? bufferView.get("byteStride").getAsInt() : Float.BYTES;
        int start = bufferViewByteOffset + accessorByteOffset;

        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            int elementOffset = start + (i * byteStride);
            if (elementOffset + Float.BYTES > binChunk.length) {
                return OptionalDouble.empty();
            }

            float value = ByteBuffer.wrap(binChunk, elementOffset, Float.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getFloat();
            if (value > max) {
                max = value;
            }
        }

        return max == Double.NEGATIVE_INFINITY ? OptionalDouble.empty() : OptionalDouble.of(max);
    }

    private record ParsedAnimationData(
            List<String> clipNames,
            Map<String, Float> clipDurationsSeconds,
            List<String> nodeNames,
            Map<String, NodeTransform> nodeTransforms,
            Map<String, NodeHierarchy> nodeHierarchies,
            Map<Integer, SkinDefinition> skins,
            Map<String, List<AnimationChannelMetadata>> clipChannels,
            Map<String, List<AnimationSamplerData>> clipSamplers,
            List<MeshPrimitiveData> meshPrimitives,
            List<MaterialData> materials,
            List<TextureData> textures,
            List<ImageData> images,
            List<SamplerData> samplers,
            ModelMeshSummary meshSummary
    ) {
    }

    private record GlbContents(
            String json,
            byte[] binChunk
    ) {
    }
}
