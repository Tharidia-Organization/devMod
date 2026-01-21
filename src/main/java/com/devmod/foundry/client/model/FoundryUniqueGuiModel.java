package com.devmod.foundry.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import com.devmod.foundry.client.model.util.FoundrySimpleBlockModel;

import java.util.function.Function;

/** Model providing a variant for the GUI. */
public class FoundryUniqueGuiModel implements IUnbakedGeometry<FoundryUniqueGuiModel> {
  public static final IGeometryLoader<FoundryUniqueGuiModel> LOADER = FoundryUniqueGuiModel::deserialize;

  protected final FoundrySimpleBlockModel model;
  protected final FoundrySimpleBlockModel gui;

  public FoundryUniqueGuiModel(FoundrySimpleBlockModel model, FoundrySimpleBlockModel gui) {
    this.model = model;
    this.gui = gui;
  }

  @Override
  public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
    model.resolveParents(modelGetter, context);
    gui.resolveParents(modelGetter, context);
  }

  @Override
  public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides) {
    return new Baked(
      model.bake(owner, baker, spriteGetter, transform, overrides),
      gui.bake(owner, baker, spriteGetter, transform, overrides)
    );
  }

  /** Wrapper that swaps the model for the GUI. */
  public static class Baked extends BakedModelWrapper<BakedModel> {
    private final BakedModel gui;

    public Baked(BakedModel base, BakedModel gui) {
      super(base);
      this.gui = gui;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext itemDisplay, PoseStack mat, boolean applyLeftHandTransform) {
      if (itemDisplay == ItemDisplayContext.GUI) {
        return gui.applyTransform(itemDisplay, mat, applyLeftHandTransform);
      }
      return originalModel.applyTransform(itemDisplay, mat, applyLeftHandTransform);
    }
  }

  public static FoundryUniqueGuiModel deserialize(JsonObject json, JsonDeserializationContext context) {
    return new FoundryUniqueGuiModel(
      FoundrySimpleBlockModel.deserialize(json, context),
      FoundrySimpleBlockModel.deserialize(GsonHelper.getAsJsonObject(json, "gui"), context)
    );
  }
}
