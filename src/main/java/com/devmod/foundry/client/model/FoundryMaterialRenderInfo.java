package com.devmod.foundry.client.model;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Client-side render info for a foundry material.
 * Contains texture, color, and fallback information for rendering.
 */
public record FoundryMaterialRenderInfo(
    ResourceLocation materialId,
    @Nullable ResourceLocation texture,
    List<String> fallbacks,
    int color,
    int luminosity
) {
    public static final FoundryMaterialRenderInfo EMPTY = new FoundryMaterialRenderInfo(
        ResourceLocation.withDefaultNamespace("empty"),
        null,
        List.of(),
        0xFFFFFFFF,
        0
    );

    /**
     * Get the sprite for this material, trying fallbacks if needed.
     * @param baseTexture The base texture location (e.g., "devmod:item/tool/pickaxe/head")
     * @return TintedSprite with the resolved sprite and color
     */
    public TintedSprite getSprite(ResourceLocation baseTexture) {
        // If we have a specific texture override, use it
        if (texture != null) {
            TextureAtlasSprite sprite = getAtlasSprite(texture);
            if (sprite != null && !isMissingSprite(sprite)) {
                return new TintedSprite(sprite, color, luminosity);
            }
        }

        // Try material-specific suffix on base texture
        // e.g., "devmod:item/tool/pickaxe/head" + "_iron" -> "devmod:item/tool/pickaxe/head_iron"
        String materialSuffix = "_" + materialId.getPath();
        ResourceLocation materialTexture = ResourceLocation.fromNamespaceAndPath(
            baseTexture.getNamespace(),
            baseTexture.getPath() + materialSuffix
        );
        TextureAtlasSprite sprite = getAtlasSprite(materialTexture);
        if (sprite != null && !isMissingSprite(sprite)) {
            return new TintedSprite(sprite, color, luminosity);
        }

        // Try fallbacks
        for (String fallback : fallbacks) {
            ResourceLocation fallbackTexture = ResourceLocation.fromNamespaceAndPath(
                baseTexture.getNamespace(),
                baseTexture.getPath() + "_" + fallback
            );
            sprite = getAtlasSprite(fallbackTexture);
            if (sprite != null && !isMissingSprite(sprite)) {
                return new TintedSprite(sprite, color, luminosity);
            }
        }

        // Final fallback: use base texture with color tint
        sprite = getAtlasSprite(baseTexture);
        return new TintedSprite(sprite, color, luminosity);
    }

    @Nullable
    private static TextureAtlasSprite getAtlasSprite(ResourceLocation location) {
        return Minecraft.getInstance()
            .getModelManager()
            .getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(location);
    }

    private static boolean isMissingSprite(TextureAtlasSprite sprite) {
        return sprite.contents().name().getPath().equals("missingno");
    }

    /**
     * A sprite with associated tint color and luminosity.
     */
    public record TintedSprite(
        TextureAtlasSprite sprite,
        int color,
        int luminosity
    ) {
        public float red() {
            return ((color >> 16) & 0xFF) / 255f;
        }

        public float green() {
            return ((color >> 8) & 0xFF) / 255f;
        }

        public float blue() {
            return (color & 0xFF) / 255f;
        }

        public float alpha() {
            int a = (color >> 24) & 0xFF;
            return a == 0 ? 1.0f : a / 255f;
        }
    }
}
