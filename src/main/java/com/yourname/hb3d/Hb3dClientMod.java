package com.yourname.hb3d;

import com.yourname.hb3d.debug.Hb3dDebugHarness;
import com.yourname.hb3d.render.Hb3dDebugWorldRenderer;
import com.yourname.hb3d.render.Hb3dRuntimeWorldRenderer;
import com.yourname.hb3d.network.Hb3dBossClientNetworking;
import com.yourname.hb3d.runtime.WorldBackgroundController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resource.ResourceType;

public final class Hb3dClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Hb3dDebugHarness debugHarness = new Hb3dDebugHarness();
        WorldBackgroundController backgroundController = new WorldBackgroundController();
        Hb3dDebugWorldRenderer debugWorldRenderer = new Hb3dDebugWorldRenderer();
        Hb3dRuntimeWorldRenderer runtimeWorldRenderer = new Hb3dRuntimeWorldRenderer();
        Hb3dMod.getVisualController().registerAnimationListener(debugHarness);
        Hb3dBossClientNetworking.registerReceivers(Hb3dMod.getVisualController(), backgroundController);
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new Hb3dClientResources());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            backgroundController.tick(client, Hb3dMod.getVisualController());
            debugHarness.tick(client, Hb3dMod.getVisualController());
            Hb3dMod.getVisualController().tick(1.0F / 20.0F);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                runtimeWorldRenderer.render(context, Hb3dMod.getVisualController()));
        WorldRenderEvents.AFTER_ENTITIES.register(context ->
                debugWorldRenderer.render(context, debugHarness, Hb3dMod.getVisualController()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            backgroundController.clear(Hb3dMod.getVisualController());
            Hb3dMod.getVisualController().clearServerVisuals();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                Hb3dBossClientNetworking.sendHello());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                com.yourname.hb3d.render.GpuSkinnedModelRenderer.getInstance().invalidate());
        Hb3dMod.LOGGER.info("Initialized HB_Renderengine client scaffolding");
    }
}
