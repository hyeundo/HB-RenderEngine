package com.yourname.hb3d.asset;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelRegistry {
    private final Map<String, ModelDefinition> definitions = new ConcurrentHashMap<>();

    public void register(ModelDefinition definition) {
        definitions.put(definition.modelId(), definition);
    }

    public Optional<ModelDefinition> get(String modelId) {
        return Optional.ofNullable(definitions.get(modelId));
    }

    public Optional<ModelDefinition> firstDefinition() {
        return definitions.values().stream().findFirst();
    }

    public void clear() {
        definitions.clear();
    }
}
