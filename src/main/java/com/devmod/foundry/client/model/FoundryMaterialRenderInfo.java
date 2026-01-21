package com.devmod.foundry.client.model;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import com.devmod.foundry.tool.material.MaterialVariantId;

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
        -1,
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

    /**
     * Get the sprite for this material using a model baking sprite getter.
     * @param baseTexture The base texture material
     * @param spriteGetter The sprite fetcher from the model baker
     * @return TintedSprite with the resolved sprite and color
     */
    public TintedSprite getSprite(Material baseTexture, Function<Material, TextureAtlasSprite> spriteGetter) {
        return getSprite(baseTexture, spriteGetter, null);
    }

    /**
     * Get the sprite for this material using a model baking sprite getter and variant ID.
     * @param baseTexture The base texture material
     * @param spriteGetter The sprite fetcher from the model baker
     * @param variant The material variant (optional)
     * @return TintedSprite with the resolved sprite and color
     */
    public TintedSprite getSprite(Material baseTexture, Function<Material, TextureAtlasSprite> spriteGetter, @Nullable MaterialVariantId variant) {
        if (texture != null) {
            TintedSprite override = getSpriteForLocation(baseTexture, spriteGetter, texture);
            if (override != null) {
                return override;
            }
        }

        ResourceLocation baseLocation = baseTexture.texture();
        String suffix = variant != null ? variant.getSuffix() : materialId.getPath();
        ResourceLocation materialTexture = ResourceLocation.fromNamespaceAndPath(
            baseLocation.getNamespace(),
            baseLocation.getPath() + "_" + suffix
        );
        TintedSprite tinted = getSpriteForLocation(baseTexture, spriteGetter, materialTexture);
        if (tinted != null) {
            return tinted;
        }

        for (String fallback : fallbacks) {
            ResourceLocation fallbackTexture = ResourceLocation.fromNamespaceAndPath(
                baseLocation.getNamespace(),
                baseLocation.getPath() + "_" + fallback
            );
            tinted = getSpriteForLocation(baseTexture, spriteGetter, fallbackTexture);
            if (tinted != null) {
                return tinted;
            }
        }

        TextureAtlasSprite sprite = spriteGetter.apply(baseTexture);
        return new TintedSprite(sprite, color, luminosity);
    }

    @Nullable
    private TintedSprite getSpriteForLocation(Material baseTexture, Function<Material, TextureAtlasSprite> spriteGetter, ResourceLocation location) {
        TextureAtlasSprite sprite = spriteGetter.apply(new Material(baseTexture.atlasLocation(), location));
        if (isMissingSprite(sprite)) {
            return null;
        }
        return new TintedSprite(sprite, color, luminosity);
    }

    private static TextureAtlasSprite getAtlasSprite(ResourceLocation location) {
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getModelManager()
            .getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(location);
        if (sprite == null) {
            return Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(MissingTextureAtlasSprite.getLocation());
        }
        return sprite;
    }

    private static boolean isMissingSprite(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /**
     * A sprite with associated tint color and luminosity.
     */
    public record TintedSprite(
        TextureAtlasSprite sprite,
        int color,
        int luminosity
    ) {
        public int emissivity() {
            return luminosity;
        }

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
