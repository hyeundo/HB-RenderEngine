package com.yourname.hb3d.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualInstance;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import org.joml.Matrix4f;

final class Hb3dCpuFallbackRenderer {
    private final Hb3dModelRenderer modelRenderer = new Hb3dModelRenderer();

    boolean render(RuntimeModelAsset asset, VisualInstance instance, Matrix4f modelView, Matrix4f projection) {
        List<UploadedTexturedPrimitive> textured =
                modelRenderer.prepareAnimatedUploadedTexturedPrimitives(asset, instance, false, 0);
        if (textured.isEmpty()) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        client.gameRenderer.getLightmapTextureManager().enable();
        client.gameRenderer.getOverlayTexture().setupOverlayColor();
        try {
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_ENTITY_SOLID);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            for (UploadedTexturedPrimitive primitive : textured) {
                if (primitive.vertexBuffer().isClosed()) continue;
                RenderSystem.setShaderTexture(0, primitive.textureId());
                primitive.vertexBuffer().bind();
                primitive.vertexBuffer().draw(modelView, projection, RenderSystem.getShader());
            }
            return true;
        } finally {
            net.minecraft.client.gl.VertexBuffer.unbind();
            client.gameRenderer.getOverlayTexture().teardownOverlayColor();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }
    }
}
