package com.yourname.hb3d.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.MaterialData;
import com.yourname.hb3d.asset.NodeTransform;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualInstance;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class GpuSkinnedModelRenderer {
    private static final GpuSkinnedModelRenderer INSTANCE = new GpuSkinnedModelRenderer();
    private static final int VERTEX_FLOATS = 20;
    private static final int VERTEX_STRIDE = VERTEX_FLOATS * Float.BYTES;

    private final Map<String, GpuModel> models = new ConcurrentHashMap<>();
    private final Map<Integer, IrisAttributeLayout> irisAttributeLayouts = new ConcurrentHashMap<>();
    private final GpuSkinningShader shader = new GpuSkinningShader();
    private final GpuTransformSkinningShader irisSkinningShader = new GpuTransformSkinningShader();
    private final SkeletonPoseCalculator poseCalculator = new SkeletonPoseCalculator();
    private final ExperimentalSkinningSolver skinningSolver = new ExperimentalSkinningSolver();
    private boolean disabled;
    private boolean failureLogged;

    public static GpuSkinnedModelRenderer getInstance() {
        return INSTANCE;
    }

    public void warmUp() {
        RenderSystem.assertOnRenderThread();
        if (IrisCompatibility.isShaderPackInUse()) {
            if (!irisSkinningShader.ensureInitialized()) {
                disabled = true;
            }
            return;
        }
        if (!shader.ensureInitialized()) {
            disabled = true;
        }
    }

    public boolean render(
            RuntimeModelAsset asset,
            VisualInstance instance,
            Matrix4f modelView,
            Matrix4f projection,
            float interpolationAlpha,
            int packedLight
    ) {
        RenderSystem.assertOnRenderThread();
        boolean irisShaderPack = IrisCompatibility.isShaderPackInUse();
        if (irisShaderPack) {
            return renderThroughIris(asset, instance, modelView, projection, interpolationAlpha, packedLight);
        }
        if (disabled || !shader.ensureInitialized()) {
            disabled = true;
            return false;
        }

        try {
            GpuModel model = models.computeIfAbsent(assetKey(asset), ignored -> uploadModel(asset));
            if (!model.renderable()) {
                return false;
            }

            Map<String, NodeTransform> poseOverrides = buildPoseOverrides(asset, instance, interpolationAlpha);
            ExperimentalSkinningContext skinningContext = new ExperimentalSkinningContext(
                    asset,
                    poseOverrides,
                    new HashMap<>(),
                    new HashMap<>(),
                    skinningSolver
            );
            NodeGlobalTransformCache globalTransforms = new NodeGlobalTransformCache(
                    asset,
                    nodeName -> poseOverrides.getOrDefault(
                            nodeName,
                            asset.nodeTransform(nodeName).orElse(NodeTransform.identity(nodeName)))
            );
            Recenter recenter = resolveRecenter(asset, globalTransforms);
            shader.bind(modelView, projection, recenter.x(), recenter.y(), recenter.z());

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);

            for (GpuSkinnedPrimitive gpuPrimitive : model.primitives()) {
                MeshPrimitiveData primitive = gpuPrimitive.source();
                List<Matrix4f> boneMatrices = isSkinned(primitive)
                        ? skinningContext.resolveSkinMatrices(
                                primitive,
                                ExperimentalSkinningSolver.SkinMatrixVariant.ROOTLESS_WORLD_MESH_COMP
                        )
                        : List.of();
                if (boneMatrices.size() > GpuSkinningShader.MAX_BONES) {
                    throw new IllegalStateException("Model " + asset.modelId() + " uses " + boneMatrices.size()
                            + " bones; GPU limit is " + GpuSkinningShader.MAX_BONES);
                }

                shader.bindPrimitive(globalTransforms.globalTransform(primitive.nodeName()), boneMatrices);
                shader.bindNormalMapped(gpuPrimitive.normalTextureId().isPresent());
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                MinecraftClient.getInstance().getTextureManager().getTexture(gpuPrimitive.textureId()).bindTexture();
                if (gpuPrimitive.normalTextureId().isPresent()) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE1);
                    MinecraftClient.getInstance().getTextureManager()
                            .getTexture(gpuPrimitive.normalTextureId().get()).bindTexture();
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                }
                GL30.glBindVertexArray(gpuPrimitive.vao());
                GL11.glDrawElements(GL11.GL_TRIANGLES, gpuPrimitive.indexCount(), GL11.GL_UNSIGNED_INT, 0L);
            }

            GL30.glBindVertexArray(0);
            shader.unbind();
            RenderSystem.enableCull();
            return true;
        } catch (RuntimeException exception) {
            GL30.glBindVertexArray(0);
            shader.unbind();
            RenderSystem.enableCull();
            disabled = true;
            if (!failureLogged) {
                failureLogged = true;
                Hb3dMod.LOGGER.error("GPU skinning failed; falling back to CPU rendering", exception);
            }
            return false;
        }
    }

    public void invalidate() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(this::invalidate);
            return;
        }
        models.values().forEach(GpuModel::close);
        models.clear();
        irisAttributeLayouts.clear();
        shader.close();
        irisSkinningShader.close();
        disabled = false;
        failureLogged = false;
    }

    private GpuModel uploadModel(RuntimeModelAsset asset) {
        List<GpuSkinnedPrimitive> primitives = new ArrayList<>();
        for (MeshPrimitiveData primitive : asset.meshPrimitives()) {
            if (primitive.mode() != 4 || primitive.positions().isEmpty() || primitive.texCoords().isEmpty()) {
                continue;
            }
            Identifier textureId = Hb3dEmbeddedTextureCache.getInstance()
                    .prepareEmbeddedBaseColorTexture(asset, primitive)
                    .orElse(null);
            if (textureId == null) {
                continue;
            }
            MaterialData material = primitive.materialIndex() >= 0 && primitive.materialIndex() < asset.materials().size()
                    ? asset.materials().get(primitive.materialIndex())
                    : new MaterialData("default", -1, "OPAQUE", 0.5F, false, -1, -1, -1);
            primitives.add(uploadPrimitive(primitive, textureId, Optional.empty(), material));
        }
        Hb3dMod.LOGGER.info("Uploaded GPU model {} once: primitives={} vertices={}",
                asset.modelId(), primitives.size(), asset.meshSummary().totalVertexCount());
        long normalMappedPrimitives = primitives.stream()
                .filter(primitive -> primitive.normalTextureId().isPresent())
                .count();
        long uniqueNormalTextures = primitives.stream()
                .flatMap(primitive -> primitive.normalTextureId().stream())
                .distinct()
                .count();
        Hb3dMod.LOGGER.info("HB_Renderengine normal maps: model={} mappedPrimitives={} uniqueTextures={}",
                asset.modelId(), normalMappedPrimitives, uniqueNormalTextures);
        return new GpuModel(
                List.copyOf(primitives),
                Optional.empty(),
                Optional.empty());
    }

    private GpuSkinnedPrimitive uploadPrimitive(
            MeshPrimitiveData primitive,
            Identifier textureId,
            Optional<Identifier> normalTextureId,
            MaterialData material
    ) {
        int vertexCount = primitive.vertexCount();
        List<Float> normals = primitive.normals().size() >= vertexCount * 3
                ? primitive.normals()
                : StaticMeshBuilder.generateSmoothNormals(primitive.positions(), primitive.indices());
        List<Float> tangents = generateTangents(primitive, normals);
        FloatBuffer vertices = BufferUtils.createFloatBuffer(vertexCount * VERTEX_FLOATS);
        boolean skinned = isSkinned(primitive);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            put3(vertices, primitive.positions(), vertex * 3, 0.0F, 0.0F, 0.0F);
            put3(vertices, normals, vertex * 3, 0.0F, 1.0F, 0.0F);
            put2(vertices, primitive.texCoords(), vertex * 2, 0.0F, 0.0F);
            int tangentBase = vertex * 4;
            vertices.put(tangents.get(tangentBase));
            vertices.put(tangents.get(tangentBase + 1));
            vertices.put(tangents.get(tangentBase + 2));
            vertices.put(tangents.get(tangentBase + 3));
            int skinBase = vertex * 4;
            for (int component = 0; component < 4; component++) {
                int index = skinBase + component;
                vertices.put(skinned && index < primitive.joints().size() ? primitive.joints().get(index) : 0.0F);
            }
            for (int component = 0; component < 4; component++) {
                int index = skinBase + component;
                float fallback = component == 0 ? 1.0F : 0.0F;
                vertices.put(skinned && index < primitive.weights().size() ? primitive.weights().get(index) : fallback);
            }
        }
        vertices.flip();

        int indexCount = primitive.indices().isEmpty() ? vertexCount : primitive.indices().size();
        IntBuffer indices = BufferUtils.createIntBuffer(indexCount);
        if (primitive.indices().isEmpty()) {
            for (int i = 0; i < vertexCount; i++) {
                indices.put(i);
            }
        } else {
            primitive.indices().forEach(indices::put);
        }
        indices.flip();

        int vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        int ebo = GL15.glGenBuffers();
        int skinnedVbo = GL15.glGenBuffers();
        int irisVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);
        attribute(0, 3, 0);
        attribute(1, 3, 3 * Float.BYTES);
        attribute(2, 2, 6 * Float.BYTES);
        attribute(3, 4, 12 * Float.BYTES);
        attribute(4, 4, 16 * Float.BYTES);
        attribute(5, 4, 8 * Float.BYTES);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, skinnedVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) vertexCount * 10L * Float.BYTES, GL15.GL_STREAM_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return new GpuSkinnedPrimitive(primitive, textureId, normalTextureId, material, vao, vbo, ebo, indexCount,
                vertexCount, skinnedVbo, irisVao);
    }

    private boolean renderThroughIris(
            RuntimeModelAsset asset,
            VisualInstance instance,
            Matrix4f modelView,
            Matrix4f projection,
            float interpolationAlpha,
            int packedLight
    ) {
        if (disabled || !irisSkinningShader.ensureInitialized()) {
            return false;
        }
        try {
            GpuModel model = models.computeIfAbsent(assetKey(asset), ignored -> uploadModel(asset));
            if (!model.renderable()) return false;

            if (model.needsSkinning(instance, interpolationAlpha)) {
                Map<String, NodeTransform> poseOverrides = buildPoseOverrides(asset, instance, interpolationAlpha);
                ExperimentalSkinningContext skinningContext = new ExperimentalSkinningContext(
                        asset, poseOverrides, new HashMap<>(), new HashMap<>(), skinningSolver);
                NodeGlobalTransformCache globalTransforms = new NodeGlobalTransformCache(
                        asset,
                        nodeName -> poseOverrides.getOrDefault(
                                nodeName,
                                asset.nodeTransform(nodeName).orElse(NodeTransform.identity(nodeName)))
                );
                Recenter recenter = resolveRecenter(asset, globalTransforms);

                irisSkinningShader.begin();
                GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
                for (GpuSkinnedPrimitive gpuPrimitive : model.primitives()) {
                    MeshPrimitiveData primitive = gpuPrimitive.source();
                    List<Matrix4f> boneMatrices = isSkinned(primitive)
                            ? skinningContext.resolveSkinMatrices(
                                    primitive,
                                    ExperimentalSkinningSolver.SkinMatrixVariant.ROOTLESS_WORLD_MESH_COMP)
                            : List.of();
                    if (boneMatrices.size() > GpuSkinningShader.MAX_BONES) {
                        throw new IllegalStateException("Model uses more than " + GpuSkinningShader.MAX_BONES + " bones");
                    }
                    transformPrimitive(gpuPrimitive, globalTransforms.globalTransform(primitive.nodeName()), boneMatrices,
                            recenter.x(), recenter.y(), recenter.z());
                }
                GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
                irisSkinningShader.unbind();
                model.markSkinned(instance, interpolationAlpha);
            }
            drawPrimitivesThroughIris(model, modelView, projection, packedLight);
            GL30.glBindVertexArray(0);
            return true;
        } catch (RuntimeException exception) {
            GL30.glBindVertexArray(0);
            GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            irisSkinningShader.unbind();
            if (!failureLogged) {
                failureLogged = true;
                Hb3dMod.LOGGER.error("Iris GPU skinning failed; falling back to CPU rendering", exception);
            }
            return false;
        }
    }

    private void transformPrimitive(
            GpuSkinnedPrimitive primitive,
            Matrix4f primitiveTransform,
            List<Matrix4f> boneMatrices,
            float centerX,
            float minY,
            float centerZ
    ) {
        irisSkinningShader.bind(primitiveTransform, boneMatrices, centerX, minY, centerZ);
        GL30.glBindVertexArray(primitive.vao());
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, primitive.skinnedVertexBuffer());
        GL30.glBeginTransformFeedback(GL11.GL_POINTS);
        GL11.glDrawArrays(GL11.GL_POINTS, 0, primitive.vertexCount());
        GL30.glEndTransformFeedback();
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
    }

    private void drawPrimitivesThroughIris(
            GpuModel model,
            Matrix4f modelView,
            Matrix4f projection,
            int packedLight
    ) {
        List<GpuSkinnedPrimitive> primitives = model.primitives();
        if (primitives.isEmpty()) return;
        Map<MaterialPass, List<GpuSkinnedPrimitive>> batches = new LinkedHashMap<>();
        for (GpuSkinnedPrimitive primitive : primitives) {
            batches.computeIfAbsent(materialPass(primitive.material()), ignored -> new ArrayList<>()).add(primitive);
        }
        for (var batch : batches.entrySet()) {
            drawIrisBatch(batch.getValue(), batch.getKey(), model, modelView, projection, packedLight);
        }
    }

    private void drawIrisBatch(
            List<GpuSkinnedPrimitive> primitives,
            MaterialPass pass,
            GpuModel model,
            Matrix4f modelView,
            Matrix4f projection,
            int packedLight
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.gameRenderer.getLightmapTextureManager().enable();
        client.gameRenderer.getOverlayTexture().setupOverlayColor();
        net.minecraft.client.gl.ShaderProgram irisShader = null;
        Map<Integer, Integer> savedAuxiliaryTextures = new LinkedHashMap<>();
        try {
            RenderSystem.setShaderTexture(0, primitives.getFirst().textureId());
            RenderSystem.setShader(pass.shader());
            irisShader = RenderSystem.getShader();
            if (irisShader == null) throw new IllegalStateException("Iris entity shader is unavailable");
            irisShader.initializeUniforms(VertexFormat.DrawMode.TRIANGLES, modelView, projection, client.getWindow());
            irisShader.bind();
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (program == 0) throw new IllegalStateException("Iris did not bind an active GL program");
            IrisAttributeLayout layout = irisAttributeLayouts.computeIfAbsent(program, this::resolveIrisAttributeLayout);
            captureSamplerTexture(program, "normals", savedAuxiliaryTextures);
            captureSamplerTexture(program, "specular", savedAuxiliaryTextures);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            if (pass.blended()) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            } else {
                RenderSystem.disableBlend();
            }
            if (pass.cull()) RenderSystem.enableCull(); else RenderSystem.disableCull();
            GL13.glActiveTexture(GL13.GL_TEXTURE0);

            for (GpuSkinnedPrimitive primitive : primitives) {
                // Iris observes diffuse texture binds and installs the PBR textures associated
                // with that diffuse texture. Bind our embedded GLB material textures afterwards,
                // otherwise that callback immediately replaces them with Iris' default maps.
                client.getTextureManager().getTexture(primitive.textureId()).bindTexture();
                bindAuxiliarySampler(program, "normals",
                        primitive.normalTextureId().or(() -> model.normalTextureId()));
                bindAuxiliarySampler(program, "specular", model.specularTextureId());
                GL30.glBindVertexArray(primitive.irisVao());
                bindFloatAttribute(layout.position(), primitive.skinnedVertexBuffer(), 3, 10 * Float.BYTES, 0L);
                bindFloatAttribute(layout.normal(), primitive.skinnedVertexBuffer(), 3,
                        10 * Float.BYTES, 3L * Float.BYTES);
                bindFloatAttribute(layout.uv0(), primitive.vertexBuffer(), 2, VERTEX_STRIDE, 6L * Float.BYTES);
                // GLB material images are standalone textures rather than atlas sprites, so their
                // UV rectangle midpoint is always (0.5, 0.5). Shader packs use this value for PBR/POM.
                bindConstantFloat(layout.midTexCoord(), 0.5F, 0.5F, 0.0F, 1.0F);
                bindConstantFloat(layout.color(), 1.0F, 1.0F, 1.0F, 1.0F);
                bindFloatAttribute(layout.tangent(), primitive.skinnedVertexBuffer(), 4,
                        10 * Float.BYTES, 6L * Float.BYTES);
                bindConstantInt(layout.uv1(), 0, 10);
                bindConstantInt(layout.uv2(), packedLight & 0xFFFF, (packedLight >>> 16) & 0xFFFF);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, primitive.indexBuffer());
                GL11.glDrawElements(GL11.GL_TRIANGLES, primitive.indexCount(), GL11.GL_UNSIGNED_INT, 0L);
            }
        } finally {
            GL30.glBindVertexArray(0);
            restoreSamplerTextures(savedAuxiliaryTextures);
            if (irisShader != null) irisShader.unbind();
            client.gameRenderer.getOverlayTexture().teardownOverlayColor();
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.depthMask(true);
        }
    }

    private void captureSamplerTexture(int program, String samplerName, Map<Integer, Integer> savedTextures) {
        int location = GL20.glGetUniformLocation(program, samplerName);
        if (location < 0) return;
        int unit = GL20.glGetUniformi(program, location);
        if (unit <= 0 || unit >= 32 || savedTextures.containsKey(unit)) return;
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        savedTextures.put(unit, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        GL13.glActiveTexture(previousActiveTexture);
    }

    private void restoreSamplerTextures(Map<Integer, Integer> savedTextures) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (var saved : savedTextures.entrySet()) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + saved.getKey());
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, saved.getValue());
        }
        GL13.glActiveTexture(previousActiveTexture);
    }

    private void bindAuxiliarySampler(int program, String samplerName, Optional<Identifier> textureId) {
        if (textureId.isEmpty()) return;
        int location = GL20.glGetUniformLocation(program, samplerName);
        if (location < 0) return;
        int unit = GL20.glGetUniformi(program, location);
        if (unit <= 0 || unit >= 32) return;
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        MinecraftClient.getInstance().getTextureManager().getTexture(textureId.get()).bindTexture();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private MaterialPass materialPass(MaterialData material) {
        String alphaMode = material.alphaMode().toUpperCase(java.util.Locale.ROOT);
        return switch (alphaMode) {
            case "MASK" -> new MaterialPass(
                    material.doubleSided()
                            ? ShaderProgramKeys.RENDERTYPE_ENTITY_CUTOUT_NO_CULL
                            : ShaderProgramKeys.RENDERTYPE_ENTITY_CUTOUT,
                    false,
                    !material.doubleSided());
            case "BLEND" -> new MaterialPass(
                    ShaderProgramKeys.RENDERTYPE_ENTITY_TRANSLUCENT,
                    true,
                    !material.doubleSided());
            default -> new MaterialPass(ShaderProgramKeys.RENDERTYPE_ENTITY_SOLID, false, !material.doubleSided());
        };
    }

    private void bindFloatAttribute(int location, int buffer, int size, int stride, long offset) {
        if (location < 0) return;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL20.glEnableVertexAttribArray(location);
        GL20.glVertexAttribPointer(location, size, GL11.GL_FLOAT, false, stride, offset);
    }

    private void bindConstantFloat(int location, float x, float y, float z, float w) {
        if (location < 0) return;
        GL20.glDisableVertexAttribArray(location);
        GL20.glVertexAttrib4f(location, x, y, z, w);
    }

    private void bindConstantInt(int location, int x, int y) {
        if (location < 0) return;
        GL20.glDisableVertexAttribArray(location);
        GL30.glVertexAttribI2i(location, x, y);
    }

    private int firstAttribute(int program, String[] names) {
        for (String name : names) {
            int location = GL20.glGetAttribLocation(program, name);
            if (location >= 0) return location;
        }
        return -1;
    }

    private IrisAttributeLayout resolveIrisAttributeLayout(int program) {
        // Minecraft binds the entity vertex format to these locations before link.
        // Shader-pack preprocessors are free to rename the GLSL inputs, so names are only a fast-path.
        return new IrisAttributeLayout(
                attributeOrDefault(program, new String[]{"Position", "vaPosition"}, 0),
                attributeOrDefault(program, new String[]{"Normal", "vaNormal"}, 5),
                attributeOrDefault(program, new String[]{"UV0", "vaUV0"}, 2),
                firstAttribute(program, new String[]{"mc_midTexCoord"}),
                attributeOrDefault(program, new String[]{"Color", "vaColor"}, 1),
                firstAttribute(program, new String[]{"at_tangent"}),
                attributeOrDefault(program, new String[]{"UV1", "vaUV1"}, 3),
                attributeOrDefault(program, new String[]{"UV2", "vaUV2"}, 4)
        );
    }

    private int attributeOrDefault(int program, String[] names, int fallbackLocation) {
        int location = firstAttribute(program, names);
        return location >= 0 ? location : fallbackLocation;
    }

    private void attribute(int location, int size, long offset) {
        GL20.glVertexAttribPointer(location, size, GL11.GL_FLOAT, false, VERTEX_STRIDE, offset);
        GL20.glEnableVertexAttribArray(location);
    }

    private Map<String, NodeTransform> buildPoseOverrides(RuntimeModelAsset asset, VisualInstance instance, float alpha) {
        Map<String, NodeTransform> current = instance.localNodePoseCache().snapshot();
        float clamped = Math.max(0.0F, Math.min(1.0F, alpha));
        if (clamped >= 0.999999F) {
            return new HashMap<>(current);
        }
        return poseCalculator.interpolateLocalPoses(
                asset,
                instance.previousLocalNodePoseCache().snapshot(),
                current,
                clamped
        );
    }

    private Matrix4f toMatrix(NodeTransform transform) {
        return new Matrix4f()
                .translation(transform.tx(), transform.ty(), transform.tz())
                .rotate(new Quaternionf(transform.rx(), transform.ry(), transform.rz(), transform.rw()))
                .scale(transform.sx(), transform.sy(), transform.sz());
    }

    private boolean isSkinned(MeshPrimitiveData primitive) {
        return primitive.skinIndex() >= 0 && !primitive.joints().isEmpty() && !primitive.weights().isEmpty();
    }

    private Recenter resolveRecenter(RuntimeModelAsset asset, NodeGlobalTransformCache globalTransforms) {
        if (!asset.skins().isEmpty()) {
            var bounds = asset.meshSummary().bounds();
            return new Recenter(bounds.centerX(), bounds.minY(), bounds.centerZ());
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        Vector3f corner = new Vector3f();
        for (MeshPrimitiveData primitive : asset.meshPrimitives()) {
            var bounds = primitive.bounds();
            Matrix4f transform = globalTransforms.globalTransform(primitive.nodeName());
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        corner.set(
                                x == 0 ? bounds.minX() : bounds.maxX(),
                                y == 0 ? bounds.minY() : bounds.maxY(),
                                z == 0 ? bounds.minZ() : bounds.maxZ()
                        );
                        transform.transformPosition(corner);
                        minX = Math.min(minX, corner.x);
                        minY = Math.min(minY, corner.y);
                        minZ = Math.min(minZ, corner.z);
                        maxX = Math.max(maxX, corner.x);
                        maxY = Math.max(maxY, corner.y);
                        maxZ = Math.max(maxZ, corner.z);
                    }
                }
            }
        }
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)) {
            return new Recenter(0.0F, 0.0F, 0.0F);
        }
        // Static scenes retain their authored vertical origin. Using the lowest
        // node as a floor anchor is unsafe for backgrounds containing distant
        // helper/shadow geometry, while horizontal centering keeps the debug
        // scene around its spawn point.
        return new Recenter((minX + maxX) * 0.5F, 0.0F, (minZ + maxZ) * 0.5F);
    }

    private String assetKey(RuntimeModelAsset asset) {
        return asset.modelId() + "|" + asset.sourceId();
    }

    private record Recenter(float x, float y, float z) {
    }

    private void put3(FloatBuffer target, List<Float> values, int offset, float x, float y, float z) {
        target.put(offset < values.size() ? values.get(offset) : x);
        target.put(offset + 1 < values.size() ? values.get(offset + 1) : y);
        target.put(offset + 2 < values.size() ? values.get(offset + 2) : z);
    }

    private void put2(FloatBuffer target, List<Float> values, int offset, float x, float y) {
        target.put(offset < values.size() ? values.get(offset) : x);
        target.put(offset + 1 < values.size() ? values.get(offset + 1) : y);
    }

    private List<Float> generateTangents(MeshPrimitiveData primitive, List<Float> normals) {
        int vertexCount = primitive.vertexCount();
        float[] tanX = new float[vertexCount];
        float[] tanY = new float[vertexCount];
        float[] tanZ = new float[vertexCount];
        float[] bitanX = new float[vertexCount];
        float[] bitanY = new float[vertexCount];
        float[] bitanZ = new float[vertexCount];
        List<Integer> indices = primitive.indices();
        int count = indices.isEmpty() ? vertexCount : indices.size();
        for (int i = 0; i + 2 < count; i += 3) {
            int a = indices.isEmpty() ? i : indices.get(i);
            int b = indices.isEmpty() ? i + 1 : indices.get(i + 1);
            int c = indices.isEmpty() ? i + 2 : indices.get(i + 2);
            int ap = a * 3, bp = b * 3, cp = c * 3;
            int au = a * 2, bu = b * 2, cu = c * 2;
            if (cp + 2 >= primitive.positions().size() || cu + 1 >= primitive.texCoords().size()) continue;
            float e1x = primitive.positions().get(bp) - primitive.positions().get(ap);
            float e1y = primitive.positions().get(bp + 1) - primitive.positions().get(ap + 1);
            float e1z = primitive.positions().get(bp + 2) - primitive.positions().get(ap + 2);
            float e2x = primitive.positions().get(cp) - primitive.positions().get(ap);
            float e2y = primitive.positions().get(cp + 1) - primitive.positions().get(ap + 1);
            float e2z = primitive.positions().get(cp + 2) - primitive.positions().get(ap + 2);
            float du1 = primitive.texCoords().get(bu) - primitive.texCoords().get(au);
            float dv1 = primitive.texCoords().get(bu + 1) - primitive.texCoords().get(au + 1);
            float du2 = primitive.texCoords().get(cu) - primitive.texCoords().get(au);
            float dv2 = primitive.texCoords().get(cu + 1) - primitive.texCoords().get(au + 1);
            float determinant = du1 * dv2 - du2 * dv1;
            if (Math.abs(determinant) < 0.000001F) continue;
            float r = 1.0F / determinant;
            float tx = (e1x * dv2 - e2x * dv1) * r;
            float ty = (e1y * dv2 - e2y * dv1) * r;
            float tz = (e1z * dv2 - e2z * dv1) * r;
            float bx = (e2x * du1 - e1x * du2) * r;
            float by = (e2y * du1 - e1y * du2) * r;
            float bz = (e2z * du1 - e1z * du2) * r;
            for (int vertex : new int[]{a, b, c}) {
                tanX[vertex] += tx; tanY[vertex] += ty; tanZ[vertex] += tz;
                bitanX[vertex] += bx; bitanY[vertex] += by; bitanZ[vertex] += bz;
            }
        }
        List<Float> result = new ArrayList<>(vertexCount * 4);
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            float nx = normals.get(vertex * 3), ny = normals.get(vertex * 3 + 1), nz = normals.get(vertex * 3 + 2);
            float dot = nx * tanX[vertex] + ny * tanY[vertex] + nz * tanZ[vertex];
            float tx = tanX[vertex] - nx * dot;
            float ty = tanY[vertex] - ny * dot;
            float tz = tanZ[vertex] - nz * dot;
            float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (length < 0.000001F) { tx = 1.0F; ty = 0.0F; tz = 0.0F; }
            else { tx /= length; ty /= length; tz /= length; }
            float cx = ny * tz - nz * ty;
            float cy = nz * tx - nx * tz;
            float cz = nx * ty - ny * tx;
            float handedness = cx * bitanX[vertex] + cy * bitanY[vertex] + cz * bitanZ[vertex] < 0.0F ? -1.0F : 1.0F;
            result.add(tx); result.add(ty); result.add(tz); result.add(handedness);
        }
        return result;
    }

    private static final class GpuModel implements AutoCloseable {
        private final List<GpuSkinnedPrimitive> primitives;
        private final Optional<Identifier> normalTextureId;
        private final Optional<Identifier> specularTextureId;
        private long skinningSignature = Long.MIN_VALUE;

        private GpuModel(
                List<GpuSkinnedPrimitive> primitives,
                Optional<Identifier> normalTextureId,
                Optional<Identifier> specularTextureId
        ) {
            this.primitives = primitives;
            this.normalTextureId = normalTextureId;
            this.specularTextureId = specularTextureId;
        }

        List<GpuSkinnedPrimitive> primitives() {
            return primitives;
        }

        Optional<Identifier> normalTextureId() {
            return normalTextureId;
        }

        Optional<Identifier> specularTextureId() {
            return specularTextureId;
        }

        boolean renderable() {
            return !primitives.isEmpty();
        }

        boolean needsSkinning(VisualInstance instance, float interpolationAlpha) {
            return skinningSignature != signature(instance, interpolationAlpha);
        }

        void markSkinned(VisualInstance instance, float interpolationAlpha) {
            skinningSignature = signature(instance, interpolationAlpha);
        }

        private long signature(VisualInstance instance, float interpolationAlpha) {
            long value = instance.entityId();
            value = value * 31L + instance.actionSequence();
            value = value * 31L + Float.floatToIntBits(instance.animationPlayer().getClipTimeSeconds());
            value = value * 31L + Float.floatToIntBits(interpolationAlpha);
            value = value * 31L + instance.animationPlayer().getActiveClip().map(String::hashCode).orElse(0);
            return value;
        }

        @Override
        public void close() {
            primitives.forEach(GpuSkinnedPrimitive::close);
        }
    }

    private record IrisAttributeLayout(
            int position,
            int normal,
            int uv0,
            int midTexCoord,
            int color,
            int tangent,
            int uv1,
            int uv2
    ) {
    }

    private record MaterialPass(net.minecraft.client.gl.ShaderProgramKey shader, boolean blended, boolean cull) {
    }
}
