package slimeknights.tconstruct.library.client.model;

import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.CompositeModel;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.tconstruct.TConstruct;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Fluid container model with tinting support. */
public record FluidContainerModel(FluidStack fluid, boolean flipGas) implements IUnbakedGeometry<FluidContainerModel> {
  public static final IGeometryLoader<FluidContainerModel> LOADER = FluidContainerModel::deserialize;

  public static final Transformation FLUID_TRANSFORM = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1.002f), new Quaternionf());

  public static FluidContainerModel deserialize(JsonObject json, JsonDeserializationContext context) {
    FluidStack fluidStack = FluidStack.EMPTY;
    if (json.has("fluid")) {
      JsonElement fluidElement = json.get("fluid");
      Fluid fluid;
      if (fluidElement.isJsonObject()) {
        JsonObject fluidObject = fluidElement.getAsJsonObject();
        ResourceLocation name = ResourceLocation.tryParse(GsonHelper.getAsString(fluidObject, "name"));
        fluid = name != null ? BuiltInRegistries.FLUID.get(name) : net.minecraft.world.level.material.Fluids.EMPTY;
      } else {
        ResourceLocation name = ResourceLocation.tryParse(GsonHelper.convertToString(fluidElement, "fluid"));
        fluid = name != null ? BuiltInRegistries.FLUID.get(name) : net.minecraft.world.level.material.Fluids.EMPTY;
      }
      fluidStack = new FluidStack(fluid, FluidType.BUCKET_VOLUME);
    }
    boolean flipGas = GsonHelper.getAsBoolean(json, "flip_gas", true);
    return new FluidContainerModel(fluidStack, flipGas);
  }

  @Nullable
  private static TextureAtlasSprite getSprite(IGeometryBakingContext context, Function<Material, TextureAtlasSprite> spriteGetter, String key) {
    if (context.hasMaterial(key)) {
      return spriteGetter.apply(context.getMaterial(key));
    }
    return null;
  }

  private static BakedModel bakeInternal(IGeometryBakingContext context, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation, FluidStack fluid, boolean flipGas) {
    IClientFluidTypeExtensions clientFluid = IClientFluidTypeExtensions.of(fluid.getFluid());
    TextureAtlasSprite baseSprite = getSprite(context, spriteGetter, "base");
    TextureAtlasSprite fluidSprite = !fluid.isEmpty() ? spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, clientFluid.getStillTexture(fluid))) : null;

    TextureAtlasSprite particleSprite = getSprite(context, spriteGetter, "particle");
    if (particleSprite == null) {
      particleSprite = fluidSprite;
    }
    if (particleSprite == null) {
      particleSprite = baseSprite;
    }
    if (particleSprite == null) {
      TConstruct.LOG.error("No valid particle sprite for fluid container model, you should supply either 'base' or 'particle'");
      particleSprite = spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, MissingTextureAtlasSprite.getLocation()));
    }

    if (flipGas && !fluid.isEmpty() && fluid.getFluid().getFluidType().isLighterThanAir()) {
      modelState = new SimpleModelState(modelState.getRotation().compose(new Transformation(null, new Quaternionf(0, 0, 1, 0), null, null)));
    }

    CompositeModel.Baked.Builder modelBuilder = CompositeModel.Baked.builder(context, particleSprite, overrides, context.getTransforms());
    RenderTypeGroup renderTypes = DynamicFluidContainerModel.getLayerRenderTypes(false);

    if (baseSprite != null) {
      modelBuilder.addQuads(renderTypes, UnbakedGeometryHelper.bakeElements(
        UnbakedGeometryHelper.createUnbakedItemElements(0, baseSprite),
        $ -> baseSprite, modelState
      ));
    }

    if (fluidSprite != null) {
      List<BakedQuad> quads = UnbakedGeometryHelper.bakeElements(
        UnbakedGeometryHelper.createUnbakedItemMaskElements(1, spriteGetter.apply(context.getMaterial("fluid"))),
        $ -> fluidSprite,
        new SimpleModelState(modelState.getRotation().compose(FLUID_TRANSFORM), modelState.isUvLocked())
      );

      RenderTypeGroup fluidRenderTypes = renderTypes;
      int light = fluid.getFluid().getFluidType().getLightLevel(fluid);
      if (light > 0) {
        fluidRenderTypes = DynamicFluidContainerModel.getLayerRenderTypes(true);
        QuadTransformers.settingEmissivity(light).processInPlace(quads);
      }
      int color = clientFluid.getTintColor(fluid);
      if (color != -1) {
        ColoredBlockModel.applyColorQuadTransformer(color).processInPlace(quads);
      }
      modelBuilder.addQuads(fluidRenderTypes, quads);
    }
    return modelBuilder.build();
  }

  private static final ResourceLocation BAKE_LOCATION = TConstruct.getResource("fluid_container_dynamic");

  @Override
  public BakedModel bake(IGeometryBakingContext context, ModelBaker bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
    context = StandaloneGeometryBakingContext.builder(context).withGui3d(false).withUseBlockLight(false).build(BAKE_LOCATION);
    if (fluid.isEmpty()) {
      overrides = new ContainedFluidOverrideHandler(context, overrides, modelState, flipGas);
    }
    return bakeInternal(context, spriteGetter, modelState, overrides, BAKE_LOCATION, fluid, flipGas);
  }

  private static final class ContainedFluidOverrideHandler extends ItemOverrides {
    private static final ResourceLocation BAKE_LOCATION = TConstruct.getResource("copper_can_dynamic");

    private final Map<FluidStack, BakedModel> cache = Maps.newHashMap();
    private final IGeometryBakingContext context;
    private final ItemOverrides nested;
    private final ModelState modelState;
    private final boolean flipGas;

    private ContainedFluidOverrideHandler(IGeometryBakingContext context, ItemOverrides nested, ModelState modelState, boolean flipGas) {
      this.context = context;
      this.nested = nested;
      this.modelState = modelState;
      this.flipGas = flipGas;
    }

    private BakedModel getUncachedModel(FluidStack fluid) {
      return bakeInternal(context, Material::sprite, modelState, ItemOverrides.EMPTY, BAKE_LOCATION, fluid, flipGas);
    }

    @Override
    public BakedModel resolve(BakedModel originalModel, ItemStack stack, @Nullable ClientLevel world, @Nullable LivingEntity entity, int seed) {
      BakedModel overriden = nested.resolve(originalModel, stack, world, entity, seed);
      if (overriden != originalModel) {
        return overriden;
      }
      Optional<FluidStack> optional = FluidUtil.getFluidContained(stack);
      if (optional.isPresent()) {
        FluidStack fluid = optional.get();
        fluid.setAmount(FluidType.BUCKET_VOLUME);
        return cache.computeIfAbsent(fluid, this::getUncachedModel);
      }
      return originalModel;
    }
  }
}
