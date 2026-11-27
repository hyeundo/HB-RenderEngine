package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.debug.Hb3dDebugHarness;
import com.yourname.hb3d.runtime.VisualController;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class Hb3dDebugWorldRenderer {
    private static final Vec3d DEBUG_ORIGIN = new Vec3d(0.0D, -60.0D, 0.0D);
    private static final float DEBUG_UPRIGHT_ROTATION_X_RAD = (float) Math.toRadians(90.0D);
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("HB_Renderengine");
    private static boolean pathProbeLogged = false;
    private final Hb3dModelRenderer modelRenderer = new Hb3dModelRenderer();
    private final GpuSkinnedModelRenderer gpuRenderer = GpuSkinnedModelRenderer.getInstance();
    private boolean uploadedMeshDrawDisabled;
    private long lastFpsLogMillis;

    public void render(WorldRenderContext context, Hb3dDebugHarness debugHarness, VisualController visualController) {
        if (!debugHarness.isEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }

        if (context.matrixStack() == null || context.consumers() == null) {
            return;
        }

        visualController.debugGetAsset(debugHarness.debugEntityId()).ifPresent(asset -> {
            if (asset.meshPrimitives().isEmpty()) {
                return;
            }

            var instanceOptional = visualController.debugGetInstance(debugHarness.debugEntityId());
            if (instanceOptional.isEmpty()) {
                return;
            }
            var instance = instanceOptional.get();

            MatrixStack matrices = context.matrixStack();
            Camera camera = context.camera();
            Vec3d cameraPos = camera.getPos();
            float interpolationAlpha = context.tickCounter().getTickDelta(false);
            Vec3d origin = DEBUG_ORIGIN;
            float renderScale = debugHarness.computeDebugScale(asset);
            Matrix4f modelView = createModelView(cameraPos, origin, renderScale);
            Matrix4f projection = new Matrix4f(context.projectionMatrix());
            int packedLight = Hb3dLightSampler.sample(
                    client.world, asset, origin.x, origin.y, origin.z, renderScale);

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            boolean renderedOnGpu = gpuRenderer.render(
                    asset, instance, modelView, projection, interpolationAlpha, packedLight);
            if (!renderedOnGpu) {
                java.util.List<UploadedTexturedPrimitive> uploadedTexturedPrimitives =
                        modelRenderer.prepareAnimatedUploadedTexturedPrimitives(asset, instance, false, 0);
                UploadedStaticMesh uploadedStaticMesh = uploadedTexturedPrimitives.isEmpty()
                        ? modelRenderer.prepareAnimatedUploadedStaticMesh(asset, instance, false, 0)
                        : null;
                if (!uploadedTexturedPrimitives.isEmpty()) {
                    for (UploadedTexturedPrimitive uploadedTexturedPrimitive : uploadedTexturedPrimitives) {
                        drawUploadedTexturedPrimitive(modelView, projection, uploadedTexturedPrimitive);
                    }
                } else {
                    drawUploadedMesh(modelView, projection, uploadedStaticMesh);
                }
                logCpuFps(
                        client,
                        asset,
                        uploadedTexturedPrimitives,
                        uploadedStaticMesh,
                        IrisCompatibility.isShaderPackInUse() ? "IRIS_COMPAT" : "CPU_FALLBACK"
                );
            } else {
                logGpuFps(client, asset, IrisCompatibility.isShaderPackInUse()
                        ? "GPU_SKINNING_IRIS"
                        : "GPU_SKINNING");
            }

            matrices.pop();
        });
    }

    private Matrix4f createModelView(Vec3d cameraPos, Vec3d origin, float renderScale) {
        return new Matrix4f(RenderSystem.getModelViewMatrix())
                .translate(
                        (float) (origin.x - cameraPos.x),
                        (float) (origin.y - cameraPos.y),
                        (float) (origin.z - cameraPos.z)
                )
                .rotateX(DEBUG_UPRIGHT_ROTATION_X_RAD)
                .scale(renderScale);
    }

    private void logGpuFps(MinecraftClient client, RuntimeModelAsset asset, String path) {
        long now = System.currentTimeMillis();
        if ((now - lastFpsLogMillis) < 1000L) {
            return;
        }
        lastFpsLogMillis = now;
        LOGGER.info("HB_Renderengine perf: fps={} model={} path={}", client.getCurrentFps(), asset.modelId(), path);
    }

    private void logCpuFps(
            MinecraftClient client,
            RuntimeModelAsset asset,
            java.util.List<UploadedTexturedPrimitive> uploadedTexturedPrimitives,
            UploadedStaticMesh uploadedStaticMesh,
            String path
    ) {
        long now = System.currentTimeMillis();
        if ((now - lastFpsLogMillis) < 1000L) {
            return;
        }
        lastFpsLogMillis = now;
        int texturedTriangles = uploadedTexturedPrimitives.stream()
                .mapToInt(UploadedTexturedPrimitive::triangleCount)
                .sum();
        int staticTriangles = uploadedStaticMesh != null ? uploadedStaticMesh.triangleCount() : 0;
        LOGGER.info(
                "HB_Renderengine perf: fps={} model={} path={} texturedPrimitives={} texturedTriangles={} staticTriangles={}",
                client.getCurrentFps(),
                asset.modelId(),
                path,
                uploadedTexturedPrimitives.size(),
                texturedTriangles,
                staticTriangles
        );
    }

    private void drawUploadedMesh(
            Matrix4f modelView,
            Matrix4f projection,
            UploadedStaticMesh uploadedStaticMesh
    ) {
        if (uploadedMeshDrawDisabled || uploadedStaticMesh == null || uploadedStaticMesh.vertexBuffer().isClosed()) {
            return;
        }

        try {
            if (!pathProbeLogged) {
                LOGGER.info("HB_Renderengine PATH PROBE: CPU fallback renderer active");
                pathProbeLogged = true;
            }

            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            uploadedStaticMesh.vertexBuffer().bind();
            uploadedStaticMesh.vertexBuffer().draw(modelView, projection, RenderSystem.getShader());
            net.minecraft.client.gl.VertexBuffer.unbind();
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
        } catch (RuntimeException exception) {
            uploadedMeshDrawDisabled = true;
        }
    }

    private void drawUploadedTexturedPrimitive(
            Matrix4f modelView,
            Matrix4f projection,
            UploadedTexturedPrimitive uploadedTexturedPrimitive
    ) {
        if (uploadedTexturedPrimitive == null || uploadedTexturedPrimitive.vertexBuffer().isClosed()) {
            return;
        }

        try {
            if (!pathProbeLogged) {
                LOGGER.info("HB_Renderengine PATH PROBE: CPU fallback renderer active");
                pathProbeLogged = true;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            client.gameRenderer.getLightmapTextureManager().enable();
            client.gameRenderer.getOverlayTexture().setupOverlayColor();
            RenderSystem.setShaderTexture(0, uploadedTexturedPrimitive.textureId());
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_ENTITY_SOLID);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            uploadedTexturedPrimitive.vertexBuffer().bind();
            uploadedTexturedPrimitive.vertexBuffer().draw(modelView, projection, RenderSystem.getShader());
            net.minecraft.client.gl.VertexBuffer.unbind();
            client.gameRenderer.getOverlayTexture().teardownOverlayColor();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        } catch (RuntimeException exception) {
            MinecraftClient.getInstance().gameRenderer.getOverlayTexture().teardownOverlayColor();
            uploadedMeshDrawDisabled = true;
        }
    }

}
