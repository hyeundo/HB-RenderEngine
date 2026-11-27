package com.yourname.hb3d;

import com.yourname.hb3d.asset.ModelDefinitionLoader;
import com.yourname.hb3d.render.Hb3dEmbeddedTextureCache;
import com.yourname.hb3d.render.Hb3dSidecarTextureCache;
import com.yourname.hb3d.render.GpuSkinnedModelRenderer;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public final class Hb3dClientResources implements SimpleSynchronousResourceReloadListener {
    private final ModelDefinitionLoader modelDefinitionLoader = new ModelDefinitionLoader();

    @Override
    public Identifier getFabricId() {
        return Identifier.of(Hb3dMod.MOD_ID, "client_resources");
    }

    @Override
    public void reload(ResourceManager manager) {
        modelDefinitionLoader.reload(manager, Hb3dMod.getModelRegistry());
        Hb3dMod.getVisualController().invalidateAssets();
        Hb3dEmbeddedTextureCache.getInstance().clear();
        Hb3dSidecarTextureCache.getInstance().clear();
        GpuSkinnedModelRenderer.getInstance().invalidate();
        GpuSkinnedModelRenderer.getInstance().warmUp();
        Hb3dMod.LOGGER.info("Reloaded HB_Renderengine model definitions");
    }
}
