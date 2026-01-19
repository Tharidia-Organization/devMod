package slimeknights.tconstruct.library.client.materials;

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

/** Render info for a material, used by tool models. */
public record MaterialRenderInfo(
  MaterialVariantId id,
  @Nullable ResourceLocation texture,
  List<String> fallbacks,
  int vertexColor,
  int luminosity
) {
  /** Gets the texture for this render info. */
  public TintedSprite getSprite(Material base, Function<Material, TextureAtlasSprite> spriteGetter) {
    TextureAtlasSprite sprite;
    if (texture != null) {
      sprite = trySprite(base, getSuffix(texture), spriteGetter);
      if (sprite != null) {
        return new TintedSprite(sprite, -1, luminosity);
      }
    }
    for (String fallback : fallbacks) {
      sprite = trySprite(base, fallback, spriteGetter);
      if (sprite != null) {
        return new TintedSprite(sprite, vertexColor, luminosity);
      }
    }
    return new TintedSprite(spriteGetter.apply(base), vertexColor, luminosity);
  }

  @Nullable
  private static TextureAtlasSprite trySprite(Material base, String suffix, Function<Material, TextureAtlasSprite> spriteGetter) {
    Material materialTexture = getMaterial(base.texture(), suffix);
    TextureAtlasSprite sprite = spriteGetter.apply(materialTexture);
    if (!MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name())) {
      return sprite;
    }
    return null;
  }

  public static String getSuffix(ResourceLocation material) {
    if ("minecraft".equals(material.getNamespace())) {
      return material.getPath();
    }
    return material.getNamespace() + "_" + material.getPath();
  }

  private static Material getMaterial(ResourceLocation texture, String suffix) {
    return new Material(InventoryMenu.BLOCK_ATLAS, ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), texture.getPath() + "_" + suffix));
  }

  /** Sprite with a color tint and emissivity. */
  public record TintedSprite(TextureAtlasSprite sprite, int color, int emissivity) {}
}
