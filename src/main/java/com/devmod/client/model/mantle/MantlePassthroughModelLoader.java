package com.devmod.client.model.mantle;

import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/**
 * Minimal Mantle loader that bakes the JSON as a standard BlockModel.
 * Mantle-specific fields (connected textures, retexturing, emissive layers) are ignored.
 */
public class MantlePassthroughModelLoader implements IGeometryLoader<MantlePassthroughModelLoader.Geometry> {
    public static final ResourceLocation CONNECTED_ID = ResourceLocation.fromNamespaceAndPath("mantle", "connected");
    public static final ResourceLocation RETEXTURED_ID = ResourceLocation.fromNamespaceAndPath("mantle", "retextured");
    public static final ResourceLocation ITEM_LAYER_ID = ResourceLocation.fromNamespaceAndPath("mantle", "item_layer");
    public static final ResourceLocation COLORED_BLOCK_ID = ResourceLocation.fromNamespaceAndPath("mantle", "colored_block");
    public static final MantlePassthroughModelLoader INSTANCE = new MantlePassthroughModelLoader();

    private MantlePassthroughModelLoader() {}

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        BlockModel model = context.deserialize(json, BlockModel.class);
        return new Geometry(model);
    }

    public static class Geometry implements IUnbakedGeometry<Geometry> {
        private final BlockModel model;

        public Geometry(BlockModel model) {
            this.model = model;
        }

        @Override
        public BakedModel bake(
            IGeometryBakingContext context,
            ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter,
            ModelState modelState,
            ItemOverrides overrides
        ) {
            return model.bake(baker, spriteGetter, modelState);
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            model.resolveParents(modelGetter);
        }
    }
}
