package com.yourname.hb3d.render;

import com.yourname.hb3d.asset.RuntimeModelAsset;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

final class Hb3dLightSampler {
    private Hb3dLightSampler() {
    }

    static int sample(ClientWorld world, RuntimeModelAsset asset, double x, double y, double z, float scale) {
        BlockPos center = BlockPos.ofFloored(x, y, z);
        int skyLight = world.getLightLevel(LightType.SKY, center);
        int blockLight = world.getLightLevel(LightType.BLOCK, center);

        var bounds = asset.meshSummary().bounds();
        double width = Math.max(bounds.maxX() - bounds.minX(), bounds.maxZ() - bounds.minZ()) * Math.abs(scale);
        double height = (bounds.maxY() - bounds.minY()) * Math.abs(scale);
        double radius = Math.max(1.0D, Math.min(8.0D, width * 0.5D));
        double upper = Math.max(1.0D, Math.min(8.0D, height * 0.5D));

        double[][] offsets = {
                {1.0D, 0.0D, 0.0D}, {-1.0D, 0.0D, 0.0D},
                {0.0D, 0.0D, 1.0D}, {0.0D, 0.0D, -1.0D},
                {radius, 0.0D, 0.0D}, {-radius, 0.0D, 0.0D},
                {0.0D, 0.0D, radius}, {0.0D, 0.0D, -radius},
                {0.0D, upper, 0.0D}, {0.0D, -1.0D, 0.0D},
                {radius * 0.7D, upper, 0.0D}, {-radius * 0.7D, upper, 0.0D},
                {0.0D, upper, radius * 0.7D}, {0.0D, upper, -radius * 0.7D}
        };
        for (double[] offset : offsets) {
            BlockPos sample = BlockPos.ofFloored(x + offset[0], y + offset[1], z + offset[2]);
            blockLight = Math.max(blockLight, world.getLightLevel(LightType.BLOCK, sample));
        }
        return LightmapTextureManager.pack(blockLight, skyLight);
    }
}
