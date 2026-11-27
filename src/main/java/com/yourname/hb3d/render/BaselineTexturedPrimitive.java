package com.yourname.hb3d.render;

import net.minecraft.util.Identifier;

public record BaselineTexturedPrimitive(
        String primitiveName,
        String nodeName,
        Identifier textureId,
        StaticMeshBuilder.PrimitiveWorldGeometry geometry
) {
    public String label() {
        return primitiveName + "@" + nodeName;
    }
}
