package com.yourname.hb3d.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;

public final class VisualInstanceManager {
    private final Map<Integer, VisualInstance> instances = new ConcurrentHashMap<>();

    public VisualInstance getOrCreate(int entityId) {
        return instances.computeIfAbsent(entityId, VisualInstance::new);
    }

    public Optional<VisualInstance> get(int entityId) {
        return Optional.ofNullable(instances.get(entityId));
    }

    public void remove(int entityId) {
        instances.remove(entityId);
    }

    public Collection<VisualInstance> all() {
        return instances.values();
    }

    public void clearServerControlled() {
        instances.values().removeIf(VisualInstance::isServerControlled);
    }
}
