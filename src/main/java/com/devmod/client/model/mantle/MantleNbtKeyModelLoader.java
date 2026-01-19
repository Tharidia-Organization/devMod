package com.devmod.client.model.mantle;

import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
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
 * Minimal Mantle NBT key loader that falls back to a default texture layer.
 * NBT-driven variant selection is ignored to keep models bakeable.
 */
public class MantleNbtKeyModelLoader implements IGeometryLoader<MantleNbtKeyModelLoader.Geometry> {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mantle", "nbt_key");
    public static final MantleNbtKeyModelLoader INSTANCE = new MantleNbtKeyModelLoader();

    private MantleNbtKeyModelLoader() {}

    @Override
    public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
        ensureLayer0Texture(json);
        BlockModel model = context.deserialize(json, BlockModel.class);
        return new Geometry(model);
    }

    private void ensureLayer0Texture(JsonObject json) {
        if (!json.has("textures") || !json.get("textures").isJsonObject()) {
            return;
        }

        JsonObject textures = json.getAsJsonObject("textures");
        if (textures.has("layer0")) {
            return;
        }

        String fallback = null;
        if (textures.has("default") && textures.get("default").isJsonPrimitive()) {
            fallback = textures.get("default").getAsString();
        }

        if (fallback == null) {
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    fallback = entry.getValue().getAsString();
                    break;
                }
            }
        }

        if (fallback == null) {
            return;
        }

        textures.addProperty("layer0", fallback);
        if (!textures.has("particle")) {
            textures.addProperty("particle", fallback);
        }
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
