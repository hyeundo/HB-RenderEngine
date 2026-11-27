package com.yourname.hb3d.runtime;

import com.yourname.hb3d.asset.NodeTransform;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class NodePoseCache {
    private final Map<String, NodeTransform> poses = new HashMap<>();

    public void put(String nodeName, NodeTransform transform) {
        poses.put(nodeName, transform);
    }

    public Optional<NodeTransform> get(String nodeName) {
        return Optional.ofNullable(poses.get(nodeName));
    }

    public Map<String, NodeTransform> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(poses));
    }

    public void clear() {
        poses.clear();
    }
}
