package com.yourname.hb3d.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualController;
import com.yourname.hb3d.runtime.VisualInstance;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class Hb3dRuntimeWorldRenderer {
    private static final float GLTF_UPRIGHT_ROTATION_X = (float) Math.toRadians(90.0D);
    private final GpuSkinnedModelRenderer gpuRenderer = GpuSkinnedModelRenderer.getInstance();
    private final Hb3dCpuFallbackRenderer cpuFallbackRenderer = new Hb3dCpuFallbackRenderer();
    private boolean gpuFailureLogged;

    public void render(WorldRenderContext context, VisualController controller) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.matrixStack() == null) return;

        Vec3d cameraPosition = context.camera().getPos();
        float alpha = context.tickCounter().getTickDelta(false);
        Matrix4f projection = new Matrix4f(context.projectionMatrix());

        for (VisualInstance instance : controller.serverVisuals()) {
            RuntimeModelAsset asset = controller.getAsset(instance).orElse(null);
            if (asset == null || asset.meshPrimitives().isEmpty()) continue;

            VisualInstance.WorldTransform transform = instance.interpolatedWorldTransform(alpha);
            double dx = transform.x() - cameraPosition.x;
            double dy = transform.y() - cameraPosition.y;
            double dz = transform.z() - cameraPosition.z;

            float scale = controller.getDefinitionScale(instance) * transform.scale();

            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix())
                    .translate((float) dx, (float) dy, (float) dz)
                    .rotateY((float) Math.toRadians(-transform.yaw()))
                    .rotateX(GLTF_UPRIGHT_ROTATION_X + (float) Math.toRadians(transform.pitch()))
                    .scale(scale);
            int packedLight = Hb3dLightSampler.sample(
                    client.world, asset, transform.x(), transform.y(), transform.z(), scale);

            if (!gpuRenderer.render(asset, instance, modelView, projection, alpha, packedLight)) {
                cpuFallbackRenderer.render(asset, instance, modelView, projection);
                if (!gpuFailureLogged) {
                    gpuFailureLogged = true;
                    Hb3dMod.LOGGER.error("Runtime boss GPU rendering failed; using CPU fallback; visualId={} model={}",
                            instance.entityId(), instance.modelId());
                }
            }
        }
    }

}
