package com.yourname.hb3d.render;

import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

public final class Hb3dSidecarTextureCache {
    private static final Hb3dSidecarTextureCache INSTANCE = new Hb3dSidecarTextureCache();

    private final Map<String, Hb3dSidecarTextureSet> cache = new ConcurrentHashMap<>();
    private final Map<String, NativeImage> imageCache = new ConcurrentHashMap<>();

    public static Hb3dSidecarTextureCache getInstance() {
        return INSTANCE;
    }

    public Hb3dSidecarTextureSet prepare(RuntimeModelAsset asset) {
        if (asset == null) {
            return new Hb3dSidecarTextureSet(Optional.empty(), Optional.empty(), Optional.empty());
        }
        return cache.computeIfAbsent(asset.modelId(), this::load);
    }

    public void clear() {
        imageCache.values().forEach(NativeImage::close);
        imageCache.clear();
        cache.clear();
    }

    public Optional<NativeImage> loadImage(RuntimeModelAsset asset, String suffix) {
        if (asset == null) {
            return Optional.empty();
        }
        String key = asset.modelId() + "|" + suffix;
        NativeImage cached = imageCache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        NativeImage loaded = decodeImage(asset.modelId(), suffix);
        if (loaded == null) {
            return Optional.empty();
        }
        imageCache.put(key, loaded);
        return Optional.of(loaded);
    }

    private Hb3dSidecarTextureSet load(String modelId) {
        return new Hb3dSidecarTextureSet(
                prepareTexture(modelId, "n"),
                prepareTexture(modelId, "n_conv"),
                prepareTexture(modelId, "s")
        );
    }

    private Optional<Identifier> prepareTexture(String modelId, String suffix) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier resourceId = resolveSidecarResource(client, modelId, suffix);
        if (resourceId == null) {
            return Optional.empty();
        }
        Optional<Resource> resource = client.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        try (InputStream stream = resource.get().getInputStream()) {
            NativeImage image = NativeImage.read(stream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier dynamicId = Identifier.of(Hb3dMod.MOD_ID, "sidecar/" + modelId + "/" + suffix);
            client.getTextureManager().registerTexture(dynamicId, texture);
            return Optional.of(dynamicId);
        } catch (IOException exception) {
            Hb3dMod.LOGGER.warn("Failed to load sidecar texture {} for model {}", suffix, modelId, exception);
            return Optional.empty();
        }
    }

    private NativeImage decodeImage(String modelId, String suffix) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier resourceId = resolveSidecarResource(client, modelId, suffix);
        if (resourceId == null) {
            return null;
        }
        Optional<Resource> resource = client.getResourceManager().getResource(resourceId);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream stream = resource.get().getInputStream()) {
            return NativeImage.read(stream);
        } catch (IOException exception) {
            Hb3dMod.LOGGER.warn("Failed to decode sidecar image {} for model {}", suffix, modelId, exception);
            return null;
        }
    }

    private Identifier resolveSidecarResource(MinecraftClient client, String modelId, String suffix) {
        String[] candidates = {
                modelId.toLowerCase() + "_" + suffix + ".png",
                modelId + "_" + suffix + ".png"
        };
        for (String candidate : candidates) {
            Identifier resourceId = Identifier.of(Hb3dMod.MOD_ID, "textures/entity/" + candidate);
            if (client.getResourceManager().getResource(resourceId).isPresent()) {
                return resourceId;
            }
        }
        return null;
    }
}
