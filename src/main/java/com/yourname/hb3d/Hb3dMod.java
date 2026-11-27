package com.yourname.hb3d;

import com.yourname.hb3d.api.Hb3dVisualApi;
import com.yourname.hb3d.asset.ModelDefinitionLoader;
import com.yourname.hb3d.asset.ModelRegistry;
import com.yourname.hb3d.runtime.VisualController;
import com.yourname.hb3d.network.Hb3dBossNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hb3dMod implements ModInitializer {
    public static final String MOD_ID = "hb3d";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static VisualController visualController;
    private static Hb3dVisualApi visualApi;
    private static ModelRegistry modelRegistry;

    @Override
    public void onInitialize() {
        modelRegistry = new ModelRegistry();
        visualController = new VisualController(modelRegistry);
        visualApi = visualController;
        new ModelDefinitionLoader().loadBuiltinDefinitions(modelRegistry);
        Hb3dBossNetworking.registerPayloadTypes();
        LOGGER.info("Initialized HB_Renderengine runtime scaffolding");
    }

    public static Hb3dVisualApi getVisualApi() {
        return visualApi;
    }

    public static VisualController getVisualController() {
        return visualController;
    }

    public static ModelRegistry getModelRegistry() {
        return modelRegistry;
    }
}
