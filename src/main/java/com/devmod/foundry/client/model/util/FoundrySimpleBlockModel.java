package com.devmod.foundry.client.model.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
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
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel.Builder;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import com.devmod.DevMod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * Simpler version of {@link BlockModel} for use in an {@link IUnbakedGeometry}, as the owner handles most block model properties
 */
@SuppressWarnings("WeakerAccess")
public class FoundrySimpleBlockModel implements IUnbakedGeometry<FoundrySimpleBlockModel> {
  /** Model loader for vanilla block model, mainly intended for use in fallback registration */
  public static final IGeometryLoader<FoundrySimpleBlockModel> LOADER = FoundrySimpleBlockModel::deserialize;
  /** Location used for baking dynamic models, name does not matter so just using a constant */
  static final ResourceLocation BAKE_LOCATION = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "dynamic_model_baking");

  /** Parent model location, used to fetch parts and for textures if the owner is not a block model */
  @Nullable
  private ResourceLocation parentLocation;
  /** Model parts for baked model, if empty uses parent parts */
  private final List<BlockElement> parts;
  /** Fallback textures in case the owner does not contain a block model */
  private final Map<String,Either<Material, String>> textures;
  private BlockModel parent;

  /**
   * Creates a new simple block model
   * @param parentLocation  Location of the parent model, if unset has no parent
   * @param textures        List of textures for iteration, in case the owner is not BlockModel
   * @param parts           List of parts in the model
   */
  public FoundrySimpleBlockModel(@Nullable ResourceLocation parentLocation, Map<String,Either<Material,String>> textures, List<BlockElement> parts) {
    this.parts = parts;
    this.textures = textures;
    this.parentLocation = parentLocation;
  }

  public FoundrySimpleBlockModel(FoundrySimpleBlockModel base) {
    this.parts = base.parts;
    this.textures = base.textures;
    this.parentLocation = base.parentLocation;
    this.parent = base.parent;
  }

  @Nullable
  public ResourceLocation getParentLocation() {
    return parentLocation;
  }

  public Map<String, Either<Material, String>> getTextures() {
    return textures;
  }

  public BlockModel getParent() {
    return parent;
  }


  /* Properties */

  /**
   * Gets the elements in this simple block model
   * @return  Elements in the model
   */
  @SuppressWarnings("deprecation")
  public List<BlockElement> getElements() {
    return parts.isEmpty() && parent != null ? parent.getElements() : parts;
  }

  /* Textures */

  @Override
  public void resolveParents(Function<ResourceLocation,UnbakedModel> modelGetter, IGeometryBakingContext owner) {
    // no work if no parent or the parent is fetched already
    if (parent != null || parentLocation == null) {
      return;
    }

    // load the parent model
    UnbakedModel unbaked = modelGetter.apply(parentLocation);
    if (unbaked == null) {
      DevMod.LOGGER.warn("No parent '{}' while loading model '{}'", parentLocation, owner.getModelName());
      unbaked = modelGetter.apply(ModelBakery.MISSING_MODEL_LOCATION);
      parentLocation = ModelBakery.MISSING_MODEL_LOCATION;
    }

    if (!(unbaked instanceof BlockModel blockModel)) {
      throw new IllegalStateException("BlockModel parent has to be a block model.");
    }

    parent = blockModel;
    // Let the parent model resolve its own parents using the vanilla system
    parent.resolveParents(modelGetter);
  }

  /* Baking */

  /** Creates a new builder instance from the given context */
  public static SimpleBakedModel.Builder bakedBuilder(IGeometryBakingContext owner, ItemOverrides overrides) {
    return new SimpleBakedModel.Builder(owner.useAmbientOcclusion(), owner.useBlockLight(), owner.isGui3d(), owner.getTransforms(), overrides);
  }

  /**
   * Bakes a single part of the model into the builder
   * @param builder          Baked model builder
   * @param owner            Model owner
   * @param part             Part to bake
   * @param spriteGetter     Sprite getter
   * @param transform        Model transforms
   * @param quadTransformer  Additional forge transforms
   * @param location         Model location
   */
  public static void bakePart(Builder builder, IGeometryBakingContext owner, BlockElement part, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, IQuadTransformer quadTransformer, ResourceLocation location) {
    for(Direction direction : part.faces.keySet()) {
      BlockElementFace face = part.faces.get(direction);
      // ensure the name is not prefixed (it always is)
      String texture = face.texture();
      if (texture.charAt(0) == '#') {
        texture = texture.substring(1);
      }
      // bake the face
      TextureAtlasSprite sprite = spriteGetter.apply(owner.getMaterial(texture));
      BakedQuad bakedQuad = BlockModel.bakeFace(part, face, sprite, direction, transform);
      quadTransformer.processInPlace(bakedQuad);
      // apply cull face
      //noinspection ConstantConditions  Its nullable, just annotated wrongly
      if (face.cullForDirection() == null) {
        builder.addUnculledFace(bakedQuad);
      } else {
        builder.addCulledFace(Direction.rotate(transform.getRotation().getMatrix(), face.cullForDirection()), bakedQuad);
      }
    }
  }

  /** Gets the render type group from the given model context */
  public static RenderTypeGroup getRenderTypeGroup(IGeometryBakingContext owner) {
    ResourceLocation renderTypeHint = owner.getRenderTypeHint();
    return renderTypeHint != null ? owner.getRenderType(renderTypeHint) : RenderTypeGroup.EMPTY;
  }

  /**
   * Applies the transformation to the model state for an item layer model.
   */
  public static IQuadTransformer applyTransform(ModelState modelState, Transformation transformation) {
    if (transformation.isIdentity()) {
      return QuadTransformers.empty();
    } else {
      return UnbakedGeometryHelper.applyRootTransform(modelState, transformation);
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
  public static BakedModel bakeModel(IGeometryBakingContext owner, List<BlockElement> elements, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides, ResourceLocation location) {
    // iterate parts, adding to the builder
    TextureAtlasSprite particle = spriteGetter.apply(owner.getMaterial("particle"));
    SimpleBakedModel.Builder builder = bakedBuilder(owner, overrides).particle(particle);
    IQuadTransformer quadTransformer = applyTransform(transform, owner.getRootTransform());
    for(BlockElement part : elements) {
      bakePart(builder, owner, part, spriteGetter, transform, quadTransformer, location);
    }
    return builder.build(getRenderTypeGroup(owner));
  }

  @Override
  public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material,TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides) {
    return bakeModel(owner, this.getElements(), spriteGetter, transform, overrides, BAKE_LOCATION);
  }

  /**
   * Same as {@link #bakeDynamic(IGeometryBakingContext, ModelState)} but allows swapping the element list. Makes colored block model easier to work with.
   * @param owner         Model configuration
   * @param transform     Transform to apply
   * @return  Baked model
   */
  public BakedModel bakeWithElements(IGeometryBakingContext owner, List<BlockElement> elements, ModelState transform) {
    return bakeModel(owner, elements, Material::sprite, transform, ItemOverrides.EMPTY, BAKE_LOCATION);
  }

  /**
   * Same as {@link #bake(IGeometryBakingContext, ModelBaker, Function, ModelState, ItemOverrides, ResourceLocation)}, but passes in sensible defaults for values unneeded in dynamic models
   * @param owner         Model configuration
   * @param transform     Transform to apply
   * @return  Baked model
   */
  public BakedModel bakeDynamic(IGeometryBakingContext owner, ModelState transform) {
    return bakeWithElements(owner, this.getElements(), transform);
  }


  /* Deserializing */

  /**
   * Parses a texture reference string, which can either be a texture path or a reference to another texture (starting with #)
   * @param atlas   Atlas location for materials
   * @param texture Texture string (can be "#reference" or "namespace:path")
   * @return Either a Material or a texture reference string
   */
  private static Either<Material, String> parseTextureLocationOrReference(ResourceLocation atlas, String texture) {
    if (texture.charAt(0) == '#') {
      return Either.right(texture.substring(1));
    }
    return Either.left(new Material(atlas, ResourceLocation.parse(texture)));
  }

  /**
   * Deserializes a FoundrySimpleBlockModel from JSON
   * @param json     Json element containing the model
   * @param context  Json Context
   * @return  Serialized JSON
   */
  public static FoundrySimpleBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
    // parent, null if missing
    String parentName = GsonHelper.getAsString(json, "parent", "");
    ResourceLocation parent = parentName.isEmpty() ? null : ResourceLocation.parse(parentName);

    // textures, empty map if missing
    Map<String, Either<Material, String>> textureMap;
    if (json.has("textures")) {
      ResourceLocation atlas = InventoryMenu.BLOCK_ATLAS;
      JsonObject textures = GsonHelper.getAsJsonObject(json, "textures");
      Map<String, Either<Material, String>> builder = new HashMap<>(textures.size());
      for(Entry<String, JsonElement> entry : textures.entrySet()) {
        builder.put(entry.getKey(), parseTextureLocationOrReference(atlas, entry.getValue().getAsString()));
      }
      textureMap = Map.copyOf(builder);
    } else {
      textureMap = Map.of();
    }

    // elements, empty list if missing
    List<BlockElement> parts;
    if (json.has("elements")) {
      parts = getModelElements(context, GsonHelper.getAsJsonArray(json, "elements"), "elements");
    } else {
      parts = List.of();
    }
    return new FoundrySimpleBlockModel(parent, textureMap, parts);
  }

  /**
   * Gets a list of models from a JSON array
   * @param context  Json Context
   * @param element  Json array
   * @return  Model list
   */
  public static List<BlockElement> getModelElements(JsonDeserializationContext context, JsonElement element, String name) {
    // if just one element, array is optional
    if (element.isJsonObject()) {
      // cast ensures we call List.of(BlockElement) instead of List.of(BlockElement[]) as the type is vague
      return List.of((BlockElement)context.deserialize(element.getAsJsonObject(), BlockElement.class));
    }
    // if an array, get array of elements
    if (element.isJsonArray()) {
      JsonArray array = element.getAsJsonArray();
      List<BlockElement> builder = new ArrayList<>(array.size());
      for(JsonElement json : array) {
        builder.add(context.deserialize(json, BlockElement.class));
      }
      return List.copyOf(builder);
    }

    throw new JsonSyntaxException("Missing " + name + ", expected to find a JsonArray or JsonObject");
  }
}
