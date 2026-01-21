package com.devmod.foundry.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import com.devmod.foundry.client.model.util.FoundryColoredBlockModel;
import com.devmod.foundry.client.model.util.FoundryDynamicBakedWrapper;
import com.devmod.foundry.client.model.util.FoundryGeometryContextWrapper;
import com.devmod.foundry.client.model.util.FoundryModelHelper;
import com.devmod.foundry.client.model.util.FoundryModelTextureIteratable;
import com.devmod.foundry.client.model.util.FoundrySimpleBlockModel;
import com.devmod.foundry.util.FoundryRetexturedHelper;

/**
 * Model that dynamically retextures a list of textures based on data from {@link FoundryRetexturedHelper}.
 */
@SuppressWarnings("WeakerAccess")
public class FoundryRetexturedModel implements IUnbakedGeometry<FoundryRetexturedModel> {
    /** Loader instance */
    public static IGeometryLoader<FoundryRetexturedModel> LOADER = FoundryRetexturedModel::deserialize;

    private final FoundrySimpleBlockModel model;
    private final Set<String> retextured;

    protected FoundryRetexturedModel(FoundrySimpleBlockModel model, Set<String> retextured) {
        this.model = model;
        this.retextured = retextured;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        model.resolveParents(modelGetter, context);
    }

    @Override
    public BakedModel bake(
        IGeometryBakingContext owner,
        ModelBaker baker,
        Function<Material, TextureAtlasSprite> spriteGetter,
        ModelState transform,
        ItemOverrides overrides
    ) {
        BakedModel baked = model.bake(owner, baker, spriteGetter, transform, overrides);
        return new Baked(baked, owner, model, transform, getAllRetextured(owner, model, retextured));
    }

    public static Set<String> getAllRetextured(IGeometryBakingContext owner, FoundrySimpleBlockModel model, Set<String> originalSet) {
        Set<String> retextured = Sets.newHashSet(originalSet);
        for (Map<String, Either<Material, String>> textures : FoundryModelTextureIteratable.of(owner, model)) {
            textures.forEach((name, either) ->
                either.ifRight(parent -> {
                    if (retextured.contains(parent)) {
                        retextured.add(name);
                    }
                })
            );
        }
        return Set.copyOf(retextured);
    }

    public static FoundryRetexturedModel deserialize(JsonObject json, JsonDeserializationContext context) {
        FoundryColoredBlockModel model = FoundryColoredBlockModel.deserialize(json, context);
        Set<String> retextured = getRetexturedNames(json);
        return new FoundryRetexturedModel(model, retextured);
    }

    public static Set<String> getRetexturedNames(JsonObject json) {
        if (json.has("retextured")) {
            JsonElement retextured = json.get("retextured");
            if (retextured.isJsonArray()) {
                JsonArray array = retextured.getAsJsonArray();
                if (array.isEmpty()) {
                    throw new JsonSyntaxException("Must have at least one texture in retextured");
                }
                List<String> builder = new ArrayList<>(array.size());
                for (int i = 0; i < array.size(); i++) {
                    builder.add(GsonHelper.convertToString(array.get(i), "retextured[" + i + "]"));
                }
                return Set.copyOf(builder);
            }
            if (retextured.isJsonPrimitive()) {
                return Set.of(retextured.getAsString());
            }
        }
        throw new JsonSyntaxException("Missing retextured, expected to find a String or a JsonArray");
    }

    public static class Baked extends FoundryDynamicBakedWrapper<BakedModel> {
        private final Map<ResourceLocation, BakedModel> cache = new ConcurrentHashMap<>();
        private final IGeometryBakingContext owner;
        private final FoundrySimpleBlockModel model;
        private final ModelState transform;
        private final Set<String> retextured;
        private final ItemOverrides overrides = new RetexturedOverride();

        public Baked(
            BakedModel baked,
            IGeometryBakingContext owner,
            FoundrySimpleBlockModel model,
            ModelState transform,
            Set<String> retextured
        ) {
            super(baked);
            this.model = model;
            this.owner = owner;
            this.transform = transform;
            this.retextured = retextured;
        }

        private BakedModel getRetexturedModel(ResourceLocation name) {
            return model.bakeDynamic(new RetexturedContext(owner, retextured, name), transform);
        }

        private BakedModel getCachedModel(Block block) {
            return cache.computeIfAbsent(FoundryModelHelper.getParticleTexture(block), this::getRetexturedModel);
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            if (retextured.contains("particle")) {
                Block block = data.get(FoundryRetexturedHelper.BLOCK_PROPERTY);
                if (block != null) {
                    return getCachedModel(block).getParticleIcon(data);
                }
            }
            return originalModel.getParticleIcon(data);
        }

        @Nonnull
        @Override
        public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction direction,
            RandomSource random,
            ModelData data,
            @Nullable RenderType renderType
        ) {
            Block block = data.get(FoundryRetexturedHelper.BLOCK_PROPERTY);
            if (block == null) {
                return originalModel.getQuads(state, direction, random, data, null);
            }
            return getCachedModel(block).getQuads(state, direction, random, data, null);
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        private class RetexturedOverride extends ItemOverrides {
            @Nullable
            @Override
            public BakedModel resolve(
                BakedModel originalModel,
                ItemStack stack,
                @Nullable ClientLevel world,
                @Nullable LivingEntity entity,
                int pSeed
            ) {
                if (stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
                    return originalModel;
                }
                Block block = FoundryRetexturedHelper.getTexture(stack);
                if (block == Blocks.AIR) {
                    return originalModel;
                }
                return getCachedModel(block);
            }
        }
    }

    public static class RetexturedContext extends FoundryGeometryContextWrapper {
        private final Set<String> retextured;
        private final Material texture;

        public RetexturedContext(IGeometryBakingContext base, Set<String> retextured, ResourceLocation texture) {
            super(base);
            this.retextured = retextured;
            this.texture = new Material(InventoryMenu.BLOCK_ATLAS, texture);
        }

        @Override
        public boolean hasMaterial(String name) {
            if (retextured.contains(name)) {
                return !MissingTextureAtlasSprite.getLocation().equals(texture.texture());
            }
            return super.hasMaterial(name);
        }

        @Override
        public Material getMaterial(String name) {
            if (retextured.contains(name)) {
                return texture;
            }
            return super.getMaterial(name);
        }
    }
}
