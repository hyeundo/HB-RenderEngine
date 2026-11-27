package com.yourname.hb3d.render;

import java.util.Optional;
import net.minecraft.util.Identifier;

public record Hb3dSidecarTextureSet(
        Optional<Identifier> normalTextureId,
        Optional<Identifier> convertedNormalTextureId,
        Optional<Identifier> specularTextureId
) {
}
