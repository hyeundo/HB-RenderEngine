package com.yourname.hb3d.render;

import com.yourname.hb3d.Hb3dMod;
import com.yourname.hb3d.asset.ImageData;
import com.yourname.hb3d.asset.MaterialData;
import com.yourname.hb3d.asset.MeshPrimitiveData;
import com.yourname.hb3d.asset.RuntimeModelAsset;
import com.yourname.hb3d.asset.SamplerData;
import com.yourname.hb3d.asset.TextureData;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;

public final class Hb3dEmbeddedTextureCache {
    private static final Hb3dEmbeddedTextureCache INSTANCE = new Hb3dEmbeddedTextureCache();
    private static final float DEBUG_NORMAL_XY_STRENGTH = 3.0F;

    private final Map<String, Identifier> textureIds = new ConcurrentHashMap<>();

    public static Hb3dEmbeddedTextureCache getInstance() {
        return INSTANCE;
    }

    public Optional<Identifier> prepareFirstEmbeddedBaseColorTexture(RuntimeModelAsset asset) {
        if (asset == null) {
            return Optional.empty();
        }

        String cacheKey = asset.modelId() + "|" + asset.sourceId();
        Identifier cached = textureIds.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        for (MeshPrimitiveData primitive : asset.meshPrimitives()) {
            if (!primitive.hasTexCoords()) {
                continue;
            }
            if (primitive.materialIndex() < 0 || primitive.materialIndex() >= asset.materials().size()) {
                continue;
            }
            MaterialData material = asset.materials().get(primitive.materialIndex());
            int textureIndex = material.baseColorTextureIndex();
            if (textureIndex < 0 || textureIndex >= asset.textures().size()) {
                continue;
            }
            TextureData texture = asset.textures().get(textureIndex);
            int imageIndex = texture.sourceImageIndex();
            if (imageIndex < 0 || imageIndex >= asset.images().size()) {
                continue;
            }
            ImageData image = asset.images().get(imageIndex);
            if (!image.hasEmbeddedBytes()) {
                continue;
            }

            try {
                NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(image.bytes()));
                NativeImageBackedTexture textureObject = new NativeImageBackedTexture(nativeImage);
                Identifier dynamicId = Identifier.of(Hb3dMod.MOD_ID, "embedded/" + asset.modelId());
                MinecraftClient.getInstance().getTextureManager().registerTexture(dynamicId, textureObject);
                SamplerData sampler = texture.samplerIndex() >= 0 && texture.samplerIndex() < asset.samplers().size()
                        ? asset.samplers().get(texture.samplerIndex())
                        : null;
                applySampler(textureObject, sampler);
                textureIds.put(cacheKey, dynamicId);
                return Optional.of(dynamicId);
            } catch (IOException exception) {
                Hb3dMod.LOGGER.warn("Failed to decode embedded texture for model {}", asset.modelId(), exception);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    public Optional<Identifier> prepareEmbeddedBaseColorTexture(RuntimeModelAsset asset, MeshPrimitiveData primitive) {
        return prepareEmbeddedMaterialTexture(asset, primitive, MaterialData::baseColorTextureIndex);
    }

    public Optional<Identifier> prepareEmbeddedNormalTexture(RuntimeModelAsset asset, MeshPrimitiveData primitive) {
        return prepareEmbeddedMaterialTexture(asset, primitive, MaterialData::normalTextureIndex, true);
    }

    private Optional<Identifier> prepareEmbeddedMaterialTexture(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            java.util.function.ToIntFunction<MaterialData> textureIndexResolver
    ) {
        return prepareEmbeddedMaterialTexture(asset, primitive, textureIndexResolver, false);
    }

    private Optional<Identifier> prepareEmbeddedMaterialTexture(
            RuntimeModelAsset asset,
            MeshPrimitiveData primitive,
            java.util.function.ToIntFunction<MaterialData> textureIndexResolver,
            boolean reconstructNormalZ
    ) {
        if (asset == null || primitive == null) {
            return Optional.empty();
        }
        if (primitive.materialIndex() < 0 || primitive.materialIndex() >= asset.materials().size()) {
            return Optional.empty();
        }

        MaterialData material = asset.materials().get(primitive.materialIndex());
        int textureIndex = textureIndexResolver.applyAsInt(material);
        if (textureIndex < 0 || textureIndex >= asset.textures().size()) {
            return Optional.empty();
        }

        TextureData texture = asset.textures().get(textureIndex);
        int imageIndex = texture.sourceImageIndex();
        if (imageIndex < 0 || imageIndex >= asset.images().size()) {
            return Optional.empty();
        }
        return prepareEmbeddedImageTexture(asset, texture, imageIndex, reconstructNormalZ);
    }

    public int preparedCount() {
        return textureIds.size();
    }

    public Optional<Identifier> preparedTextureId(RuntimeModelAsset asset) {
        if (asset == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(textureIds.get(asset.modelId() + "|" + asset.sourceId()));
    }

    public void clear() {
        textureIds.clear();
    }

    private Optional<Identifier> prepareEmbeddedImageTexture(RuntimeModelAsset asset, TextureData texture, int imageIndex) {
        return prepareEmbeddedImageTexture(asset, texture, imageIndex, false);
    }

    private Optional<Identifier> prepareEmbeddedImageTexture(
            RuntimeModelAsset asset,
            TextureData texture,
            int imageIndex,
            boolean reconstructNormalZ
    ) {
        ImageData image = asset.images().get(imageIndex);
        if (!image.hasEmbeddedBytes()) {
            return Optional.empty();
        }

        String cacheKey = asset.modelId() + "|" + asset.sourceId() + "|img=" + imageIndex
                + (reconstructNormalZ ? "|normal-rg" : "");
        Identifier cached = textureIds.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(image.bytes()));
            if (reconstructNormalZ) {
                reconstructNormalZ(nativeImage);
            }
            NativeImageBackedTexture textureObject = new NativeImageBackedTexture(nativeImage);
            Identifier dynamicId = Identifier.of(Hb3dMod.MOD_ID, "embedded/" + asset.modelId() + "/" + imageIndex
                    + (reconstructNormalZ ? "_normal_rg" : ""));
            MinecraftClient.getInstance().getTextureManager().registerTexture(dynamicId, textureObject);
            SamplerData sampler = texture.samplerIndex() >= 0 && texture.samplerIndex() < asset.samplers().size()
                    ? asset.samplers().get(texture.samplerIndex())
                    : null;
            applySampler(textureObject, sampler);
            textureIds.put(cacheKey, dynamicId);
            textureIds.putIfAbsent(asset.modelId() + "|" + asset.sourceId(), dynamicId);
            return Optional.of(dynamicId);
        } catch (IOException exception) {
            Hb3dMod.LOGGER.warn("Failed to decode embedded texture for model {} image {}", asset.modelId(), imageIndex, exception);
            return Optional.empty();
        }
    }

    private void reconstructNormalZ(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getColorArgb(x, y);
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                float nx = (red / 127.5F - 1.0F) * DEBUG_NORMAL_XY_STRENGTH;
                float ny = (green / 127.5F - 1.0F) * DEBUG_NORMAL_XY_STRENGTH;
                float xyLengthSquared = nx * nx + ny * ny;
                if (xyLengthSquared > 0.998001F) {
                    float scale = 0.999F / (float) Math.sqrt(xyLengthSquared);
                    nx *= scale;
                    ny *= scale;
                }
                float nz = (float) Math.sqrt(Math.max(0.0F, 1.0F - nx * nx - ny * ny));
                red = Math.max(0, Math.min(255, Math.round((nx * 0.5F + 0.5F) * 255.0F)));
                green = Math.max(0, Math.min(255, Math.round((ny * 0.5F + 0.5F) * 255.0F)));
                int blue = Math.max(0, Math.min(255, Math.round((nz * 0.5F + 0.5F) * 255.0F)));
                image.setColorArgb(x, y, 0xFF000000 | (red << 16) | (green << 8) | blue);
            }
        }
    }

    private void applySampler(NativeImageBackedTexture textureObject, SamplerData sampler) {
        int wrapS = sampler != null ? toGlWrap(sampler.wrapS()) : GL12.GL_REPEAT;
        int wrapT = sampler != null ? toGlWrap(sampler.wrapT()) : GL12.GL_REPEAT;
        RenderSystem.recordRenderCall(() -> {
            textureObject.bindTexture();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapS);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapT);
        });
    }

    private int toGlWrap(int gltfWrap) {
        return switch (gltfWrap) {
            case 33071 -> GL12.GL_CLAMP_TO_EDGE;
            case 33648 -> GL14.GL_MIRRORED_REPEAT;
            case 10497 -> GL11.GL_REPEAT;
            default -> GL11.GL_REPEAT;
        };
    }
}
