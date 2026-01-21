package com.devmod.foundry.client.model.block;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.fluids.FluidStack;
import com.devmod.foundry.client.model.FoundryRetexturedModel;
import com.devmod.foundry.client.model.FoundryRetexturedModel.RetexturedContext;
import com.devmod.foundry.client.model.util.FoundryColoredBlockModel;
import com.devmod.foundry.client.model.util.FoundryColoredBlockModel.ColorData;
import com.devmod.foundry.client.model.util.FoundryDynamicBakedWrapper;
import com.devmod.foundry.client.model.util.FoundryModelHelper;
import com.devmod.foundry.client.model.util.FoundrySimpleBlockModel;
import com.devmod.foundry.util.FoundryJsonHelper;
import com.devmod.foundry.util.FoundryLogicHelper;
import com.devmod.foundry.util.FoundryRetexturedHelper;
import com.devmod.DevMod;
import com.devmod.foundry.client.model.FoundryModelProperties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Model that replaces fluid textures with the fluid from model data. */
public class FoundryFluidTextureModel implements IUnbakedGeometry<FoundryFluidTextureModel> {
  public static final IGeometryLoader<FoundryFluidTextureModel> LOADER = FoundryFluidTextureModel::deserialize;

  private final FoundryColoredBlockModel model;
  private final Set<String> fluids;
  private final Set<String> retextured;

  public FoundryFluidTextureModel(FoundryColoredBlockModel model, Set<String> fluids, Set<String> retextured) {
    this.model = model;
    this.fluids = fluids;
    this.retextured = retextured;
  }

  @Override
  public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
    model.resolveParents(modelGetter, context);
  }

  private static String trimTextureName(String name) {
    if (name.charAt(0) == '#') {
      return name.substring(1);
    }
    return name;
  }


  @Override
  public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides) {
    BakedModel baked = model.bake(owner, baker, spriteGetter, transform, overrides);

    Set<String> fluidTextures = this.fluids.isEmpty() ? Collections.emptySet() : FoundryRetexturedModel.getAllRetextured(owner, model, this.fluids);
    List<BlockElement> elements = model.getElements();
    int size = elements.size();
    BitSet fluidParts = new BitSet(size);
    if (!fluidTextures.isEmpty()) {
      for (int i = 0; i < size; i++) {
        BlockElement part = elements.get(i);
        long fluidFaces = part.faces.values().stream()
          .filter(face -> fluidTextures.contains(trimTextureName(face.texture())))
          .count();
        if (fluidFaces > 0) {
          if (fluidFaces < part.faces.size()) {
            DevMod.LOGGER.warn("Mixed fluid and non-fluid elements in model, may cause unexpected results");
          }
          fluidParts.set(i);
        }
      }
    }
    Set<String> retextured = this.retextured.isEmpty() ? Collections.emptySet() : FoundryRetexturedModel.getAllRetextured(owner, this.model, this.retextured);
    return new Baked(baked, elements, model.getColorData(), owner, transform, fluidTextures, fluidParts, retextured);
  }

  private record BakedCacheKey(FluidStack fluid, @Nullable ResourceLocation texture) {}

  private static class Baked extends FoundryDynamicBakedWrapper<BakedModel> {
    private final Map<BakedCacheKey, BakedModel> cache = new ConcurrentHashMap<>();
    private final List<BlockElement> elements;
    private final List<ColorData> colorData;
    private final IGeometryBakingContext owner;
    private final ModelState transform;
    private final Set<String> fluids;
    private final BitSet fluidParts;
    private final Set<String> retextured;
    private final ItemOverrides overrides = new RetexturedOverride();

    protected Baked(BakedModel originalModel, List<BlockElement> elements, List<ColorData> colorData, IGeometryBakingContext owner, ModelState transform, Set<String> fluids, BitSet fluidParts, Set<String> retextured) {
      super(originalModel);
      this.elements = elements;
      this.colorData = colorData;
      this.owner = owner;
      this.transform = transform;
      this.fluids = fluids;
      this.fluidParts = fluidParts;
      this.retextured = retextured;
    }

    private BakedModel getFoundryRetexturedModel(BakedCacheKey key) {
      Function<Material, TextureAtlasSprite> spriteGetter = Material::sprite;

      IGeometryBakingContext textured = this.owner;
      if (key.texture != null) {
        textured = new RetexturedContext(textured, this.retextured, key.texture);
      }

      IQuadTransformer quadTransformer = FoundrySimpleBlockModel.applyTransform(transform, owner.getRootTransform());
      IQuadTransformer fluidTransformer = quadTransformer;

      int luminosity = 0;
      if (!key.fluid.isEmpty()) {
        IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(key.fluid.getFluid());
        int color = attributes.getTintColor(key.fluid);
        if (color != -1) {
          fluidTransformer = FoundryColoredBlockModel.applyColorQuadTransformer(color).andThen(quadTransformer);
        }
        luminosity = key.fluid.getFluid().getFluidType().getLightLevel(key.fluid);
        textured = new RetexturedContext(textured, this.fluids, attributes.getStillTexture(key.fluid));
      }

      TextureAtlasSprite particle = spriteGetter.apply(textured.getMaterial("particle"));
      SimpleBakedModel.Builder builder = FoundrySimpleBlockModel.bakedBuilder(owner, ItemOverrides.EMPTY).particle(particle);

      boolean defaultUvLock = transform.isUvLocked();
      int size = elements.size();
      for (int i = 0; i < size; i++) {
        BlockElement element = elements.get(i);
        ColorData colors = FoundryLogicHelper.getOrDefault(colorData, i, ColorData.DEFAULT);
        if (fluidParts.get(i)) {
          FoundryColoredBlockModel.bakePart(builder, textured, element, luminosity, spriteGetter, transform, fluidTransformer, colors.isUvLock(defaultUvLock), FoundryTankModel.BAKE_LOCATION);
        } else {
          int partColor = colors.color();
          IQuadTransformer partTransformer = partColor == -1 ? quadTransformer : FoundryColoredBlockModel.applyColorQuadTransformer(partColor).andThen(quadTransformer);
          FoundryColoredBlockModel.bakePart(builder, textured, element, colors.luminosity(), spriteGetter, transform, partTransformer, colors.isUvLock(defaultUvLock), FoundryTankModel.BAKE_LOCATION);
        }
      }
      return builder.build(FoundrySimpleBlockModel.getRenderTypeGroup(owner));
    }

    private BakedModel getCachedModel(BakedCacheKey key) {
      return this.cache.computeIfAbsent(key, this::getFoundryRetexturedModel);
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random, ModelData data, @Nullable RenderType renderType) {
      FluidStack fluid = fluids.isEmpty() ? FluidStack.EMPTY : data.get(FoundryModelProperties.FLUID_STACK);
      if (fluid == null) {
        fluid = FluidStack.EMPTY;
      }
      Block block = retextured.isEmpty() ? null : data.get(FoundryRetexturedHelper.BLOCK_PROPERTY);
      if (!fluid.isEmpty() || block != null) {
        BakedCacheKey key = new BakedCacheKey(fluid, block != null ? FoundryModelHelper.getParticleTexture(block) : null);
        return getCachedModel(key).getQuads(state, direction, random, data, renderType);
      }
      return originalModel.getQuads(state, direction, random, data, renderType);
    }

    @Override
    public ItemOverrides getOverrides() {
      return overrides;
    }

    private class RetexturedOverride extends ItemOverrides {
      @Nullable
      @Override
      public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int pSeed) {
        if (stack.isEmpty() || !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
          return originalModel;
        }

        Block block = FoundryRetexturedHelper.getTexture(stack);
        if (block == Blocks.AIR) {
          return originalModel;
        }

        return getCachedModel(new BakedCacheKey(FluidStack.EMPTY, FoundryModelHelper.getParticleTexture(block)));
      }
    }
  }

  public static FoundryFluidTextureModel deserialize(JsonObject json, JsonDeserializationContext context) {
    FoundryColoredBlockModel model = FoundryColoredBlockModel.deserialize(json, context);
    Set<String> fluids = Collections.emptySet();
    if (json.has("fluids")) {
      fluids = ImmutableSet.copyOf(FoundryJsonHelper.parseList(json, "fluids", GsonHelper::convertToString));
    }
    Set<String> retextured = Collections.emptySet();
    if (json.has("retextured")) {
      retextured = ImmutableSet.copyOf(FoundryJsonHelper.parseList(json, "retextured", GsonHelper::convertToString));
    }
    return new FoundryFluidTextureModel(model, fluids, retextured);
  }
}
