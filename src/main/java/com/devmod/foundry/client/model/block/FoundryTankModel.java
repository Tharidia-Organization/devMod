package com.devmod.foundry.client.model.block;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import com.devmod.foundry.client.model.util.FoundryColoredBlockModel;
import com.devmod.foundry.client.model.util.FoundryExtraTextureContext;
import com.devmod.foundry.client.model.util.FoundrySimpleBlockModel;
import com.devmod.DevMod;
import com.devmod.foundry.config.FoundryConfig;
import com.devmod.foundry.client.model.FoundryModelProperties;
import com.devmod.foundry.client.model.FoundryUniqueGuiModel;
import com.devmod.foundry.item.FoundryTankItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/** Model for tanks with a scalable fluid part. */
public class FoundryTankModel implements IUnbakedGeometry<FoundryTankModel> {
  protected static final ResourceLocation BAKE_LOCATION = ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "dynamic_model_baking");

  public static final IGeometryLoader<FoundryTankModel> LOADER = FoundryTankModel::deserialize;

  protected final FoundrySimpleBlockModel model;
  @Nullable
  protected final FoundrySimpleBlockModel gui;
  protected final FoundryIncrementalFluidCuboid fluid;
  protected final boolean forceModelFluid;

  public FoundryTankModel(FoundrySimpleBlockModel model, @Nullable FoundrySimpleBlockModel gui, FoundryIncrementalFluidCuboid fluid, boolean forceModelFluid) {
    this.model = model;
    this.gui = gui;
    this.fluid = fluid;
    this.forceModelFluid = forceModelFluid;
  }

  @Override
  public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
    model.resolveParents(modelGetter, context);
    if (gui != null) {
      gui.resolveParents(modelGetter, context);
    }
  }

  @Override
  public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides) {
    BakedModel baked = model.bake(owner, baker, spriteGetter, transform, overrides);
    BakedModel bakedGui = baked;
    if (gui != null) {
      bakedGui = gui.bake(owner, baker, spriteGetter, transform, overrides);
    }
    return new Baked(owner, transform, baked, bakedGui, this);
  }

  private static class Baked extends FoundryUniqueGuiModel.Baked {
    private final IGeometryBakingContext owner;
    private final ModelState originalTransforms;
    protected final FoundryTankModel original;
    private final FluidPartOverride overrides = new FluidPartOverride();
    private final Cache<CacheKey, BakedModel> cache = CacheBuilder.newBuilder().maximumSize(64).build();

    private record CacheKey(FluidStack fluid, int increments) {}

    protected Baked(IGeometryBakingContext owner, ModelState transforms, BakedModel baked, BakedModel gui, FoundryTankModel original) {
      super(baked, gui);
      this.owner = owner;
      this.originalTransforms = transforms;
      this.original = original;
    }

    @Override
    public ItemOverrides getOverrides() {
      return overrides;
    }

    private BakedModel bakeWithFluid(IGeometryBakingContext owner, FoundrySimpleBlockModel baseModel, BlockElement fluid, int color, int luminosity) {
      Function<Material, TextureAtlasSprite> spriteGetter = Material::sprite;
      TextureAtlasSprite particle = spriteGetter.apply(owner.getMaterial("particle"));
      SimpleBakedModel.Builder builder = FoundrySimpleBlockModel.bakedBuilder(owner, ItemOverrides.EMPTY).particle(particle);
      IQuadTransformer quadTransformer = FoundrySimpleBlockModel.applyTransform(originalTransforms, owner.getRootTransform());
      for (BlockElement element : baseModel.getElements()) {
        FoundrySimpleBlockModel.bakePart(builder, owner, element, spriteGetter, originalTransforms, quadTransformer, BAKE_LOCATION);
      }
      IQuadTransformer fluidTransformer = color == -1 ? quadTransformer : quadTransformer.andThen(FoundryColoredBlockModel.applyColorQuadTransformer(color));
      FoundryColoredBlockModel.bakePart(builder, owner, fluid, luminosity, spriteGetter, originalTransforms, fluidTransformer, originalTransforms.isUvLocked(), BAKE_LOCATION);
      return builder.build(FoundrySimpleBlockModel.getRenderTypeGroup(owner));
    }

    private BakedModel getModel(CacheKey key) {
      FluidStack stack = key.fluid();
      IClientFluidTypeExtensions attributes = IClientFluidTypeExtensions.of(stack.getFluid());
      FluidType type = stack.getFluid().getFluidType();
      int color = attributes.getTintColor(stack);
      int luminosity = type.getLightLevel(stack);
      Map<String, Material> textures = ImmutableMap.of(
        "fluid", new Material(InventoryMenu.BLOCK_ATLAS, attributes.getStillTexture(stack)),
        "flowing_fluid", new Material(InventoryMenu.BLOCK_ATLAS, attributes.getFlowingTexture(stack))
      );
      IGeometryBakingContext textured = new FoundryExtraTextureContext(owner, textures);

      BlockElement fluid = original.fluid.getPart(key.increments, type.isLighterThanAir());
      BakedModel baked = bakeWithFluid(textured, original.model, fluid, color, luminosity);

      if (original.gui != null) {
        baked = new FoundryUniqueGuiModel.Baked(baked, bakeWithFluid(textured, original.gui, fluid, color, 0));
      }
      return baked;
    }

    private BakedModel getCachedModel(CacheKey fluid) {
      try {
        return cache.get(fluid, () -> getModel(fluid));
      } catch (ExecutionException e) {
        DevMod.LOGGER.error("Failed to bake tank model", e);
        return this;
      }
    }

    private BakedModel getCachedModel(FluidStack fluid, int capacity) {
      int increments = original.fluid.getIncrements();
      return getCachedModel(new CacheKey(fluid.copy(), Mth.clamp(fluid.getAmount() * increments / capacity, 1, increments)));
    }

    @Nonnull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
      if ((original.forceModelFluid || FoundryConfig.CLIENT.tankFluidModel.get()) && data.has(FoundryModelProperties.FLUID_STACK)) {
        FluidStack fluid = data.get(FoundryModelProperties.FLUID_STACK);
        if (fluid != null && !fluid.isEmpty()) {
          int capacity = Objects.requireNonNullElse(data.get(FoundryModelProperties.TANK_CAPACITY), fluid.getAmount());
          return getCachedModel(fluid, capacity).getQuads(state, side, rand, ModelData.EMPTY, renderType);
        }
      }
      return originalModel.getQuads(state, side, rand, data, renderType);
    }

    private class FluidPartOverride extends ItemOverrides {
      @Override
      public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
        if (stack.isEmpty()) {
          return model;
        }
        FluidTank tank = FoundryTankItem.getTank(stack, 1);
        if (tank.isEmpty()) {
          return model;
        }
        return getCachedModel(tank.getFluid(), tank.getCapacity());
      }
    }
  }

  public static FoundryTankModel deserialize(JsonObject json, JsonDeserializationContext context) {
    FoundrySimpleBlockModel model = FoundrySimpleBlockModel.deserialize(json, context);
    FoundrySimpleBlockModel gui = null;
    if (json.has("gui")) {
      gui = FoundrySimpleBlockModel.deserialize(GsonHelper.getAsJsonObject(json, "gui"), context);
    }
    FoundryIncrementalFluidCuboid fluid = FoundryIncrementalFluidCuboid.fromJson(GsonHelper.getAsJsonObject(json, "fluid"));
    boolean forceModelFluid = GsonHelper.getAsBoolean(json, "render_fluid_in_model", false);
    return new FoundryTankModel(model, gui, fluid, forceModelFluid);
  }
}
