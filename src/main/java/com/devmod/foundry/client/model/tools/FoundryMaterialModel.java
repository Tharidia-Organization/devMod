package com.devmod.foundry.client.model.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfo;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfo.TintedSprite;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfoLoader;
import com.devmod.foundry.client.model.util.FoundryItemLayerModel;
import com.devmod.foundry.client.model.util.FoundryItemLayerPixels;
import com.devmod.foundry.config.FoundryConfig;
import com.devmod.foundry.tool.material.IMaterial;
import com.devmod.foundry.tool.material.MaterialVariantId;
import com.devmod.foundry.tool.part.IMaterialItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Model for an item with material texture variants. */
public class FoundryMaterialModel implements IUnbakedGeometry<FoundryMaterialModel> {
  public static final IGeometryLoader<FoundryMaterialModel> LOADER = FoundryMaterialModel::deserialize;

  @Nullable
  private final MaterialVariantId material;
  private final int index;
  private final Vec2 offset;

  public FoundryMaterialModel(@Nullable MaterialVariantId material, int index, Vec2 offset) {
    this.material = material;
    this.index = index;
    this.offset = offset;
  }

  public static void validateMaterialTextures(IGeometryBakingContext owner, Function<Material, TextureAtlasSprite> spriteGetter, String textureName, @Nullable MaterialVariantId material) {
    Material texture = owner.getMaterial(textureName);
    if (!MissingTextureAtlasSprite.getLocation().equals(texture.texture())) {
      if (material == null) {
        FoundryMaterialRenderInfoLoader.INSTANCE.getAllRenderInfos().forEach(info -> info.getSprite(texture, spriteGetter));
      } else {
        FoundryMaterialRenderInfo info = FoundryMaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
        if (info != null) {
          info.getSprite(texture, spriteGetter, material);
        }
      }
    }
  }

  public static TintedSprite getMaterialSprite(Function<Material, TextureAtlasSprite> spriteGetter, Material texture, MaterialVariantId material) {
    FoundryMaterialRenderInfo info = FoundryMaterialRenderInfoLoader.INSTANCE.getRenderInfo(material);
    if (info != null) {
      return info.getSprite(texture, spriteGetter, material);
    }
    return new TintedSprite(spriteGetter.apply(texture), -1, 0);
  }

  public static List<BakedQuad> getQuadsForMaterial(Function<Material, TextureAtlasSprite> spriteGetter, Material texture, MaterialVariantId material, int tintIndex, Transformation transformation, @Nullable FoundryItemLayerPixels pixels) {
    TintedSprite sprite = getMaterialSprite(spriteGetter, texture, material);
    return FoundryItemLayerModel.getQuadsForSprite(sprite.color(), tintIndex, sprite.sprite(), transformation, sprite.emissivity(), pixels);
  }

  private static BakedModel bakeInternal(IGeometryBakingContext owner, Function<Material, TextureAtlasSprite> spriteGetter, Transformation transform, MaterialVariantId material, int index, ItemOverrides overrides) {
    TintedSprite materialSprite = getMaterialSprite(spriteGetter, owner.getMaterial("texture"), material);
    CompositeModel.Baked.Builder builder = CompositeModel.Baked.builder(owner.useAmbientOcclusion(), false, false, materialSprite.sprite(), overrides, owner.getTransforms());
    builder.addQuads(FoundryItemLayerModel.getDefaultRenderType(owner), FoundryItemLayerModel.getQuadsForSprite(materialSprite.color(), index, materialSprite.sprite(), transform, materialSprite.emissivity()));
    return builder.build();
  }

  @Override
  public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform, ItemOverrides vanillaOverrides) {
    if (FoundryConfig.CLIENT.logMissingMaterialTextures.get()) {
      validateMaterialTextures(owner, spriteGetter, "texture", material);
    }
    Transformation transforms;
    if (Vec2.ZERO.equals(offset)) {
      transforms = Transformation.identity();
    } else {
      transforms = new Transformation(new Vector3f(offset.x / 16, -offset.y / 16, 0), null, null, null);
    }

    ItemOverrides overrides = ItemOverrides.EMPTY;
    if (material == null) {
      overrides = new MaterialOverrideHandler(owner, index, transforms);
    }

    return bakeInternal(owner, spriteGetter, transforms, Objects.requireNonNullElse(material, IMaterial.UNKNOWN_ID), index, overrides);
  }

  private static final class MaterialOverrideHandler extends ItemOverrides {
    private final Map<MaterialVariantId, BakedModel> cache = new ConcurrentHashMap<>();
    private final IGeometryBakingContext owner;
    private final int index;
    private final Transformation itemTransform;

    private MaterialOverrideHandler(IGeometryBakingContext owner, int index, Transformation itemTransform) {
      this.owner = owner;
      this.index = index;
      this.itemTransform = itemTransform;
    }

    @Override
    public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
      MaterialVariantId material = IMaterialItem.getMaterialFromStack(stack);
      return cache.computeIfAbsent(material, this::bakeDynamic);
    }

    private BakedModel bakeDynamic(MaterialVariantId material) {
      return bakeInternal(owner, Material::sprite, itemTransform, material, index, ItemOverrides.EMPTY);
    }
  }

  public static FoundryMaterialModel deserialize(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
    int index = GsonHelper.getAsInt(json, "index", 0);
    MaterialVariantId material = null;
    if (json.has("material")) {
      material = MaterialVariantId.fromJson(json, "material");
    }
    Vec2 offset = Vec2.ZERO;
    if (json.has("offset")) {
      offset = getVec2(json, "offset");
    }
    return new FoundryMaterialModel(material, index, offset);
  }

  public static Vec2 getVec2(JsonObject json, String key) {
    JsonArray array = GsonHelper.getAsJsonArray(json, key);
    if (array.size() != 2) {
      throw new JsonParseException(key + " must be an array of 2 numbers");
    }
    float x = array.get(0).getAsFloat();
    float y = array.get(1).getAsFloat();
    return new Vec2(x, y);
  }
}
