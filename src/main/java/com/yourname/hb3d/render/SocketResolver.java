package com.yourname.hb3d.render;

import com.yourname.hb3d.api.SocketTransform;
import com.yourname.hb3d.asset.ModelDefinition;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.runtime.VisualInstance;

public final class SocketResolver {
    public void populateSockets(VisualInstance instance, ModelDefinition definition, RuntimeModelAsset asset) {
        instance.socketPoseCache().clear();

        definition.sockets().forEach((socketName, nodeName) -> {
            instance.worldNodePoseCache().get(nodeName).or(() -> asset.nodeTransform(nodeName)).ifPresentOrElse(
                    node -> instance.socketPoseCache().put(
                            socketName,
                            new SocketTransform(
                                    node.tx(),
                                    node.ty(),
                                    node.tz(),
                                    0.0F,
                                    0.0F,
                                    0.0F
                            )
                    ),
                    () -> populateFallbackSocket(instance, socketName)
            );
        });
    }

    private void populateFallbackSocket(VisualInstance instance, String socketName) {
        switch (socketName) {
            case "root" -> instance.socketPoseCache().put("root", new SocketTransform(0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F));
            case "head" -> instance.socketPoseCache().put("head", new SocketTransform(0.0, 1.5, 0.0, 0.0F, 0.0F, 0.0F));
            case "right_hand" -> instance.socketPoseCache().put("right_hand", new SocketTransform(0.35, 1.0, 0.0, 0.0F, 0.0F, 0.0F));
            case "weapon" -> instance.socketPoseCache().put("weapon", new SocketTransform(0.45, 0.95, 0.0, 0.0F, 0.0F, 0.0F));
            default -> instance.socketPoseCache().put(socketName, new SocketTransform(0.0, 0.0, 0.0, 0.0F, 0.0F, 0.0F));
        }
    }
}
