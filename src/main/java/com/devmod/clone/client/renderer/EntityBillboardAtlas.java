package com.devmod.clone.client.renderer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.client.ui.editor.core.DesignTokens;

/**
 * Manages a dynamic texture atlas containing pre-rendered entity billboards.
 *
 * <p>The atlas is built lazily - entities are rendered to the atlas on first request.
 * This allows efficient batched rendering of distant entities using a single texture.
 *
 * <p>Atlas layout:
 * <pre>
 * +-------+-------+-------+-------+
 * | Zombie| Skele | Creep | Cow   |
 * | 64x64 | 64x64 | 64x64 | 64x64 |
 * +-------+-------+-------+-------+
 * | Pig   | Sheep | ...   | ...   |
 * +-------+-------+-------+-------+
 * </pre>
 */
public final class EntityBillboardAtlas {

    private static final int ATLAS_SIZE = 1024;
    private static final int SPRITE_SIZE = 64;
    private static final int SPRITES_PER_ROW = ATLAS_SIZE / SPRITE_SIZE; // 16

    @Nullable
    private static EntityBillboardAtlas instance;

    @Nullable
    private DynamicTexture atlasTexture;
    @Nullable
    private NativeImage atlasImage;
    @Nullable
    private ResourceLocation atlasLocation;

    private final Map<String, UVRegion> uvMap = new HashMap<>();
    private int nextSlotIndex = 0;
    private boolean initialized = false;

    private EntityBillboardAtlas() {}

    /**
     * Get the singleton instance.
     */
    @Nonnull
    public static EntityBillboardAtlas getInstance() {
        if (instance == null) {
            instance = new EntityBillboardAtlas();
        }
        return Objects.requireNonNull(instance);
    }

    /**
     * Initialize the atlas texture.
     * Must be called on the render thread.
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        // Create atlas image with transparent background
        atlasImage = new NativeImage(NativeImage.Format.RGBA, ATLAS_SIZE, ATLAS_SIZE, true);
        atlasImage.fillRect(0, 0, ATLAS_SIZE, ATLAS_SIZE, DesignTokens.TextureAtlas.TRANSPARENT);

        // Create dynamic texture
        atlasTexture = new DynamicTexture(Objects.requireNonNull(atlasImage));

        // Register texture with Minecraft's texture manager
        atlasLocation = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "dynamic/entity_billboard_atlas");
        Minecraft.getInstance().getTextureManager().register(Objects.requireNonNull(atlasLocation), Objects.requireNonNull(atlasTexture));

        initialized = true;
        DevMod.LOGGER.debug("[Clone] Entity billboard atlas initialized ({}x{})", ATLAS_SIZE, ATLAS_SIZE);
    }

    /**
     * Get UV coordinates for an entity type, rendering it to the atlas if needed.
     *
     * @param entityType The entity type string (e.g., "minecraft:zombie")
     * @return UV region for the entity, or null if atlas is full
     */
    @Nullable
    public UVRegion getOrCreateUV(@Nonnull String entityType) {
        if (!initialized) {
            initialize();
        }

        // Check if already in atlas
        UVRegion existing = uvMap.get(entityType);
        if (existing != null) {
            return existing;
        }

        // Check if atlas is full
        int maxSlots = SPRITES_PER_ROW * SPRITES_PER_ROW; // 256 slots
        if (nextSlotIndex >= maxSlots) {
            DevMod.LOGGER.warn("[Clone] Entity billboard atlas is full, cannot add: {}", entityType);
            return null;
        }

        // Calculate position in atlas
        int slotX = nextSlotIndex % SPRITES_PER_ROW;
        int slotY = nextSlotIndex / SPRITES_PER_ROW;
        int pixelX = slotX * SPRITE_SIZE;
        int pixelY = slotY * SPRITE_SIZE;

        // Render entity to atlas
        NativeImage image = atlasImage;
        if (image == null) {
            DevMod.LOGGER.warn("[Clone] Billboard atlas not ready for {}", entityType);
            return null;
        }

        boolean success = EntityBillboardCache.getInstance().renderEntityToAtlas(
            entityType, image, pixelX, pixelY, SPRITE_SIZE
        );

        if (!success) {
            // Use placeholder for failed renders
            renderPlaceholder(pixelX, pixelY);
        }

        // Upload changes to GPU
        if (atlasTexture != null) {
            atlasTexture.upload();
        }

        // Calculate UV coordinates (normalized 0-1)
        float u0 = (float) pixelX / ATLAS_SIZE;
        float v0 = (float) pixelY / ATLAS_SIZE;
        float u1 = (float) (pixelX + SPRITE_SIZE) / ATLAS_SIZE;
        float v1 = (float) (pixelY + SPRITE_SIZE) / ATLAS_SIZE;

        UVRegion region = new UVRegion(u0, v0, u1, v1);
        uvMap.put(entityType, region);
        nextSlotIndex++;

        DevMod.LOGGER.debug("[Clone] Added entity to billboard atlas: {} at slot {}", entityType, nextSlotIndex - 1);
        return region;
    }

    /**
     * Render a placeholder sprite for failed entity renders.
     */
    private void renderPlaceholder(int x, int y) {
        var image = atlasImage;
        if (image == null) return;

        // Draw a checkerboard pattern as placeholder
        for (int px = 0; px < SPRITE_SIZE; px++) {
            for (int py = 0; py < SPRITE_SIZE; py++) {
                boolean isLight = ((px / 8) + (py / 8)) % 2 == 0;
                int color = isLight
                    ? DesignTokens.TextureAtlas.PLACEHOLDER_LIGHT
                    : DesignTokens.TextureAtlas.PLACEHOLDER_DARK;
                image.setPixelRGBA(x + px, y + py, color);
            }
        }
    }

    /**
     * Get the atlas texture location for binding.
     */
    @Nullable
    public ResourceLocation getAtlasLocation() {
        return atlasLocation;
    }

    /**
     * Check if an entity type is already in the atlas.
     */
    public boolean hasEntity(@Nonnull String entityType) {
        return uvMap.containsKey(entityType);
    }

    /**
     * Get UV coordinates for an entity type (does not create if missing).
     */
    @Nullable
    public UVRegion getUV(@Nonnull String entityType) {
        return uvMap.get(entityType);
    }

    /**
     * Get the number of entities currently in the atlas.
     */
    public int getEntityCount() {
        return uvMap.size();
    }

    /**
     * Check if the atlas is initialized and ready.
     */
    public boolean isReady() {
        return initialized && atlasTexture != null;
    }

    /**
     * Release GPU resources.
     */
    public void close() {
        if (atlasTexture != null) {
            atlasTexture.close();
            atlasTexture = null;
        }
        if (atlasImage != null) {
            atlasImage.close();
            atlasImage = null;
        }
        var location = atlasLocation;
        if (location != null) {
            Minecraft.getInstance().getTextureManager().release(location);
            atlasLocation = null;
        }
        uvMap.clear();
        nextSlotIndex = 0;
        initialized = false;
    }

    /**
     * Reset the singleton instance.
     */
    public static void reset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    /**
     * UV region within the atlas texture.
     */
    public static class UVRegion {
        public final float u0, v0, u1, v1;

        public UVRegion(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }
}
