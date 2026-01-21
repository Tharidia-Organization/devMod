package com.devmod.foundry.client.model.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.Block;

/**
 * Utilities to help in custom models.
 */
public final class FoundryModelHelper {
    private static final Map<Block, ResourceLocation> TEXTURE_NAME_CACHE = new ConcurrentHashMap<>();

    /** Listener instance to clear cache. */
    public static final ResourceManagerReloadListener LISTENER = manager -> TEXTURE_NAME_CACHE.clear();

    private FoundryModelHelper() {}

    @SuppressWarnings("deprecation")
    private static ResourceLocation getParticleTextureInternal(Block block) {
        TextureAtlasSprite particle = Minecraft.getInstance()
            .getModelManager()
            .getBlockModelShaper()
            .getBlockModel(block.defaultBlockState())
            .getParticleIcon();
        if (particle != null) {
            return particle.contents().name();
        }
        return MissingTextureAtlasSprite.getLocation();
    }

    /**
     * Gets the name of a particle texture for a block, using the cached value if present.
     */
    public static ResourceLocation getParticleTexture(Block block) {
        return TEXTURE_NAME_CACHE.computeIfAbsent(block, FoundryModelHelper::getParticleTextureInternal);
    }
}
