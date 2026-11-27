package com.yourname.hb3d.asset;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelAssetCache {
    private final Map<String, RuntimeModelAsset> assets = new ConcurrentHashMap<>();

    public void put(RuntimeModelAsset asset) {
        assets.put(asset.modelId(), asset);
    }

    public Optional<RuntimeModelAsset> get(String modelId) {
        return Optional.ofNullable(assets.get(modelId));
    }

    public void clear() {
        assets.clear();
    }
}
