package com.devmod.foundry.client.model.util;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel.Builder;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import com.devmod.DevMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Block model for setting color, luminosity, and per element uv lock. Similar to {@link MantleItemLayerModel} but for blocks
 */
@SuppressWarnings("unused")  // API
public class FoundryColoredBlockModel extends FoundrySimpleBlockModel {
  /** Model loader to allow doing basic coloring outside of other models */
  public static final IGeometryLoader<FoundrySimpleBlockModel> LOADER = FoundryColoredBlockModel::deserialize;

  /** Colors to use for each piece */
  private final List<ColorData> colorData;

  /**
   * Creates a new colored block model
   * @param parentLocation Location of the parent model, if unset has no parent
   * @param textures       List of textures for iteration, in case the owner is not BlockModel
   * @param parts          List of parts in the model
   * @param colorData      Additional information about colors in the model
   */
  public FoundryColoredBlockModel(@Nullable ResourceLocation parentLocation, Map<String,Either<Material,String>> textures, List<BlockElement> parts, List<ColorData> colorData) {
    super(parentLocation, textures, parts);
    this.colorData = colorData;
  }

  public FoundryColoredBlockModel(FoundrySimpleBlockModel base, List<ColorData> colorData) {
    super(base);
    this.colorData = colorData;
  }

  public List<ColorData> getColorData() {
    return colorData;
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Baked model builder
   * @param owner            Model owner
   * @param part             Part to bake
   * @param emissivity       Emissivity for fullbright, -1 will leave forge in charge, 0-15 will override the forge value
   * @param spriteGetter     Sprite getter
   * @param transform        Transform for the face
   * @param quadTransformer  Forge transformations for the face, this is notably where you should handle color transformations
   * @param uvlock           UV lock for the face, separated to allow overriding the model state
   * @param location         Model location
   */
  public static void bakePart(Builder builder, IGeometryBakingContext owner, BlockElement part, int emissivity, Function<Material,TextureAtlasSprite> spriteGetter, ModelState modelState, IQuadTransformer quadTransformer, boolean uvlock, ResourceLocation location) {
    for (Entry<Direction, BlockElementFace> entry : part.faces.entrySet()) {
      BlockElementFace face = entry.getValue();
      Direction direction = entry.getKey();
      // ensure the name is not prefixed (it always is)
      String texture = face.texture();
      if (texture.charAt(0) == '#') {
        texture = texture.substring(1);
      }
      // bake the face using vanilla method
      TextureAtlasSprite sprite = spriteGetter.apply(owner.getMaterial(texture));

      // Create a model state with the correct UV lock setting
      ModelState effectiveState = uvlock != modelState.isUvLocked()
          ? new SimpleModelState(modelState.getRotation(), uvlock)
          : modelState;

      BakedQuad quad = BlockModel.bakeFace(part, face, sprite, direction, effectiveState);

      // Apply quad transformer for colors
      quadTransformer.processInPlace(quad);

      // Apply emissivity if specified
      if (emissivity > 0) {
        QuadTransformers.settingEmissivity(emissivity).processInPlace(quad);
      }

      // apply cull face
      if (face.cullForDirection() == null) {
        builder.addUnculledFace(quad);
      } else {
        builder.addCulledFace(Direction.rotate(modelState.getRotation().getMatrix(), face.cullForDirection()), quad);
      }
    }
  }

  /**
   * Bakes a list of block part elements into a model
   * @param owner         Model configuration
   * @param elements      Model elements
   * @param spriteGetter  Sprite getter instance
   * @param transform     Model transform
   * @param overrides     Model overrides
   * @param location      Model bake location
   * @return  Baked model
   */
  public static BakedModel bakeModel(IGeometryBakingContext owner, List<BlockElement> elements, List<ColorData> colorData, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides, ResourceLocation location) {
    // iterate parts, adding to the builder
    TextureAtlasSprite particle = spriteGetter.apply(owner.getMaterial("particle"));
    SimpleBakedModel.Builder builder = bakedBuilder(owner, overrides).particle(particle);
    int size = elements.size();
    IQuadTransformer quadTransformer = applyTransform(transform, owner.getRootTransform());
    boolean uvlock = transform.isUvLocked();
    for (int i = 0; i < size; i++) {
      BlockElement part = elements.get(i);
      ColorData colors = getOrDefault(colorData, i, ColorData.DEFAULT);
      if (colors.luminosity != -1 && !location.equals(BAKE_LOCATION)) {
        DevMod.LOGGER.warn("Using deprecated 'luminosity' field on ColoredBlockModel color data for {}, this will be removed in 1.20 in favor of Forge's 'emissivity'.", location);
      }
      IQuadTransformer partTransformer = colors.color == -1 ? quadTransformer : quadTransformer.andThen(applyColorQuadTransformer(colors.color));
      bakePart(builder, owner, part, colors.luminosity, spriteGetter, transform, partTransformer, colors.isUvLock(uvlock), location);
    }
    return builder.build(getRenderTypeGroup(owner));
  }

  @Override
  public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material,TextureAtlasSprite> spriteGetter, ModelState modelTransform, ItemOverrides overrides) {
    return bakeModel(owner, getElements(), colorData, spriteGetter, modelTransform, overrides, BAKE_LOCATION);
  }

  @Override
  public BakedModel bakeWithElements(IGeometryBakingContext owner, List<BlockElement> elements, ModelState transform) {
    return bakeModel(owner, elements, colorData, Material::sprite, transform, ItemOverrides.EMPTY, BAKE_LOCATION);
  }

  private static <E> E getOrDefault(List<E> list, int index, E defaultValue) {
    if (index < 0 || index >= list.size()) {
      return defaultValue;
    }
    return list.get(index);
  }

  /**
   * Data class for setting properties when baking colored elements
   */
  public record ColorData(int color, int luminosity, @Nullable Boolean uvlock) {
    public static final ColorData DEFAULT = new ColorData(-1, -1, null);

    /** Gets the UV lock for the given part */
    public boolean isUvLock(boolean defaultLock) {
      if (uvlock == null) {
        return defaultLock;
      }
      return uvlock;
    }

    public static ColorData fromJson(JsonObject json) {
      int color = -1;
      if (json.has("color")) {
        // Color can be specified as hex string (e.g., "FF191919") or as integer
        JsonElement colorElement = json.get("color");
        if (colorElement.isJsonPrimitive() && colorElement.getAsJsonPrimitive().isString()) {
          // Parse hex color string - use Long.parseLong to handle ARGB values > Integer.MAX_VALUE
          String colorStr = colorElement.getAsString();
          if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
            colorStr = colorStr.substring(2);
          }
          color = (int) Long.parseLong(colorStr, 16);
        } else {
          color = colorElement.getAsInt();
        }
      }
      int luminosity = json.has("luminosity") ? json.get("luminosity").getAsInt() : -1;
      Boolean uvlock = json.has("uvlock") ? json.get("uvlock").getAsBoolean() : null;
      return new ColorData(color, luminosity, uvlock);
    }
  }


  /* Deserializing */

  /** Deserializes the model from JSON */
  public static FoundryColoredBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    FoundrySimpleBlockModel model = FoundrySimpleBlockModel.deserialize(json, context);
    List<ColorData> colorData = List.of();
    if (json.has("colors")) {
      var array = json.getAsJsonArray("colors");
      if (!array.isEmpty()) {
        var parsed = new java.util.ArrayList<ColorData>(array.size());
        for (int i = 0; i < array.size(); i++) {
          parsed.add(ColorData.fromJson(GsonHelper.convertToJsonObject(array.get(i), "colors[" + i + "]")));
        }
        colorData = List.copyOf(parsed);
      }
    }
    return new FoundryColoredBlockModel(model, colorData);
  }


  /* Color utilities */

  /**
   * Converts an ARGB color to an ABGR color, as the commonly used color format is not the format colors end up packed into.
   * This function doubles as its own inverse, not that its needed.
   * @param color  ARGB color
   * @return  ABGR color
   */
  public static int swapColorRedBlue(int color) {
    return (color & 0xFF00FF00) // alpha and green same spot
           | ((color >> 16) & 0x000000FF) // red moves to blue
           | ((color << 16) & 0x00FF0000); // blue moves to red
  }

  /** Quad transformer applying a static color */
  public static IQuadTransformer applyColorQuadTransformer(int color) {
    int abgr = swapColorRedBlue(color);
    return quad -> {
      int[] vertices = quad.getVertices();
      for (int i = 0; i < 4; i++) {
        vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = abgr;
      }
    };
  }
}
