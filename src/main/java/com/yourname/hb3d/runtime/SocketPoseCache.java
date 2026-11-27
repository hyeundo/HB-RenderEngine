package com.yourname.hb3d.runtime;

import com.yourname.hb3d.api.SocketTransform;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SocketPoseCache {
    private final Map<String, SocketTransform> sockets = new HashMap<>();

    public void put(String socketName, SocketTransform transform) {
        sockets.put(socketName, transform);
    }

    public Optional<SocketTransform> get(String socketName) {
        return Optional.ofNullable(sockets.get(socketName));
    }

    public void clear() {
        sockets.clear();
    }
}
